package main

import (
	"bytes"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path"
	"path/filepath"
	"sort"
	"strings"

	"github.com/gofrs/flock"
)

// excludeEvent is the part of a [finding] the exclude command acts on. The remaining finding fields
// are ignored, so an event copied verbatim out of the scanner's JSON output is a valid argument.
type excludeEvent struct {
	Type   string `json:"type"`
	Found  string `json:"found"`
	File   string `json:"file"`
	Folder string `json:"folder"`
}

type exclusionOperation string

const (
	exclusionAdd    exclusionOperation = "add"
	exclusionRemove exclusionOperation = "remove"
)

type exclusionChange struct {
	ReportPath string
	Changed    bool
}

// strictGrandReport mirrors [grandReport] for decoding only. Its Exclusions pointer, and the
// pointer fields of [strictGrandReportExclusion], tell an absent key from a present one, which is
// what lets [decodeGrandReport] reject a report missing "exclusions", "t-hash", or "f-hash"
// instead of substituting a zero value.
type strictGrandReport struct {
	Exclusions *[]strictGrandReportExclusion `json:"exclusions"`
}

type strictGrandReportExclusion struct {
	TextHash *string `json:"t-hash"`
	FileHash *string `json:"f-hash"`
}

// UnmarshalJSON decodes one exclusion, requiring the object to hold "t-hash" followed by "f-hash"
// and nothing else. Field order carries no meaning in JSON otherwise, so a report that spells the
// two keys in the other order is rejected rather than accepted.
func (e *strictGrandReportExclusion) UnmarshalJSON(content []byte) error {
	decoder := json.NewDecoder(bytes.NewReader(content))
	opening, err := decoder.Token()
	if err != nil {
		return err
	}
	if opening != json.Delim('{') {
		return fmt.Errorf("exclusion must be an object")
	}

	textKey, err := decoder.Token()
	if err != nil {
		return err
	}
	if textKey != "t-hash" {
		return fmt.Errorf("exclusion must start with \"t-hash\"")
	}
	if err := decoder.Decode(&e.TextHash); err != nil {
		return err
	}

	fileKey, err := decoder.Token()
	if err != nil {
		return err
	}
	if fileKey != "f-hash" {
		return fmt.Errorf("\"f-hash\" must follow \"t-hash\"")
	}
	if err := decoder.Decode(&e.FileHash); err != nil {
		return err
	}
	if decoder.More() {
		return fmt.Errorf("exclusion contains unexpected fields")
	}
	closing, err := decoder.Token()
	if err != nil {
		return err
	}
	if closing != json.Delim('}') {
		return fmt.Errorf("exclusion must end after \"f-hash\"")
	}
	return nil
}

type unsupportedExcludeEventTypeError struct {
	eventType string
}

func (e *unsupportedExcludeEventTypeError) Error() string {
	return fmt.Sprintf(
		"Cannot exclude event type %q: supported types are \"found\", \"file\", and \"folder\". No files were changed.",
		e.eventType,
	)
}

// excludeCommandError reports a failure that left every file as it was. Error appends that promise
// to message, so message must not repeat it and must end with its own punctuation.
type excludeCommandError struct {
	message string
}

func isExcludeCommand(args []string) bool {
	return len(args) > 0 && args[0] == "exclude"
}

func runExcludeCommand(args []string, stdout, stderr io.Writer) exitStatus {
	errorOutput := newLineOutput(stderr)
	operation, root, encodedEvent, legacy, ok := parseExcludeInvocation(args)
	if !ok {
		_ = writeHelp(errorOutput)
		return exitFailure
	}
	if legacy {
		event, err := parseExcludeEvent(encodedEvent)
		if err != nil {
			writeFatal(errorOutput, "%v", err)
			return exitFailure
		}
		if event.Type != "found" {
			writeFatal(
				errorOutput,
				"The legacy exclude form accepts only type \"found\"; use \"exclude add\" for type %q. No files were changed.",
				event.Type,
			)
			return exitFailure
		}
	}
	change, err := updateExclusion(root, encodedEvent, operation)
	if err != nil {
		writeFatal(errorOutput, "%v", err)
		return exitFailure
	}
	message := exclusionResultMessage(operation, change.Changed, legacy)
	if err := newLineOutput(stdout).text("%s: %s", message, change.ReportPath); err != nil {
		writeFatal(errorOutput, "Cannot write updated exclusions path: %v", err)
		return exitFailure
	}
	return exitClean
}

func parseExcludeInvocation(args []string) (exclusionOperation, string, string, bool, bool) {
	if len(args) == 3 {
		return exclusionAdd, args[1], args[2], true, true
	}
	if len(args) == 4 {
		operation := exclusionOperation(args[1])
		if operation == exclusionAdd || operation == exclusionRemove {
			return operation, args[2], args[3], false, true
		}
	}
	return "", "", "", false, false
}

func exclusionResultMessage(operation exclusionOperation, changed, legacy bool) string {
	if legacy {
		return "Exclusions file was updated"
	}
	if operation == exclusionAdd {
		if changed {
			return "Exclusion added"
		}
		return "Exclusion already exists"
	}
	if changed {
		return "Exclusion removed"
	}
	return "Exclusion does not exist"
}

type shellCommand struct {
	Label   string
	Command string
}

func quotePOSIXArgument(value string) string {
	return "'" + strings.ReplaceAll(value, "'", "'\"'\"'") + "'"
}

func quotePowerShellArgument(value string) string {
	return "'" + strings.ReplaceAll(value, "'", "''") + "'"
}

// quoteCmdArgument quotes value for a cmd.exe command line under the CommandLineToArgvW rules: a
// run of backslashes is doubled only when a quote or the end of the argument follows it, and a
// quote is escaped by that doubled run plus one more backslash.
func quoteCmdArgument(value string) string {
	var quoted strings.Builder
	quoted.WriteByte('"')
	backslashes := 0
	for _, current := range value {
		if current == '\\' {
			backslashes++
			continue
		}
		if current == '"' {
			quoted.WriteString(strings.Repeat("\\", backslashes*2+1))
			quoted.WriteRune('"')
			backslashes = 0
			continue
		}
		quoted.WriteString(strings.Repeat("\\", backslashes))
		backslashes = 0
		quoted.WriteRune(current)
	}
	quoted.WriteString(strings.Repeat("\\", backslashes*2))
	quoted.WriteByte('"')
	return quoted.String()
}

// formatExcludeCommands renders the cfcli exclude invocation that would suppress event, once for
// each supported shell: POSIX, PowerShell, then cmd.exe, always in that order and whatever
// operating system cfcli runs on.
func formatExcludeCommands(root string, event finding) ([]shellCommand, error) {
	absoluteRoot, err := filepath.Abs(root)
	if err != nil {
		return nil, fmt.Errorf("resolve repository path: %w", err)
	}
	encoded, err := json.Marshal(event)
	if err != nil {
		return nil, fmt.Errorf("encode finding: %w", err)
	}
	rootArgument := filepath.Clean(absoluteRoot)
	eventArgument := "JSON: " + string(encoded)
	return []shellCommand{
		{
			Label: "POSIX",
			Command: "cfcli exclude " + quotePOSIXArgument(rootArgument) + " " +
				quotePOSIXArgument(eventArgument),
		},
		{
			Label: "PowerShell",
			Command: "cfcli exclude " + quotePowerShellArgument(rootArgument) + " " +
				quotePowerShellArgument(eventArgument),
		},
		{
			Label: "cmd.exe",
			Command: "cfcli exclude " + quoteCmdArgument(rootArgument) + " " +
				quoteCmdArgument(eventArgument),
		},
	}, nil
}

func (e *excludeCommandError) Error() string {
	return e.message + " No files were changed."
}

// updateExclusions adds the event encoded in encodedEvent to root/.qubership/grand-report.json,
// creating the directory and the file when they are absent, and returns the absolute path of the
// report it wrote. The new content goes to a temporary file in the same directory and is renamed
// over the old one, and every rejection happens before that rename, so a failure leaves an existing
// report byte-for-byte unchanged.
func updateExclusions(root, encodedEvent string) (string, error) {
	change, err := updateExclusion(root, encodedEvent, exclusionAdd)
	return change.ReportPath, err
}

func updateExclusion(root, encodedEvent string, operation exclusionOperation) (exclusionChange, error) {
	event, err := parseExcludeEvent(encodedEvent)
	if err != nil {
		return exclusionChange{}, err
	}
	rootInfo, err := os.Stat(root)
	if err != nil {
		return exclusionChange{}, &excludeCommandError{message: fmt.Sprintf("Cannot inspect FOLDER_PATH %q: %v.", root, err)}
	}
	if !rootInfo.IsDir() {
		return exclusionChange{}, &excludeCommandError{message: fmt.Sprintf("FOLDER_PATH %q is not a directory.", root)}
	}
	target, err := exclusionForEvent(event)
	if err != nil {
		return exclusionChange{}, err
	}

	reportPath, err := filepath.Abs(filepath.Join(root, ".qubership", "grand-report.json"))
	if err != nil {
		return exclusionChange{}, &excludeCommandError{message: fmt.Sprintf("Cannot resolve exclusions file: %v.", err)}
	}
	directory := filepath.Dir(reportPath)
	if err := os.MkdirAll(directory, 0o755); err != nil {
		return exclusionChange{}, &excludeCommandError{message: fmt.Sprintf("Cannot create exclusions directory %q: %v.", directory, err)}
	}
	reportLock := flock.New(filepath.Join(directory, "grand-report.lock"))
	if err := reportLock.Lock(); err != nil {
		return exclusionChange{}, &excludeCommandError{message: fmt.Sprintf("Cannot lock exclusions file %q: %v.", reportPath, err)}
	}
	locked := true
	defer func() {
		if locked {
			_ = reportLock.Unlock()
		}
	}()

	var report grandReport
	content, err := os.ReadFile(reportPath)
	switch {
	case err == nil:
		report, err = decodeGrandReport(content)
		if err != nil {
			return exclusionChange{}, &excludeCommandError{
				message: fmt.Sprintf("Cannot parse exclusions file %q: %v.", reportPath, err),
			}
		}
	case os.IsNotExist(err):
		report.Exclusions = []grandReportExclusion{}
	default:
		return exclusionChange{}, &excludeCommandError{
			message: fmt.Sprintf("Cannot read exclusions file %q: %v.", reportPath, err),
		}
	}

	matchCount := 0
	filtered := report.Exclusions[:0]
	for _, existing := range report.Exclusions {
		if existing.TextHash == target.TextHash && existing.FileHash == target.FileHash {
			matchCount++
		} else {
			filtered = append(filtered, existing)
		}
	}
	changed := false
	switch operation {
	case exclusionAdd:
		if matchCount == 1 {
			return exclusionChange{ReportPath: reportPath, Changed: false}, nil
		}
		report.Exclusions = append(filtered, target)
		changed = true
	case exclusionRemove:
		if matchCount == 0 {
			return exclusionChange{ReportPath: reportPath, Changed: false}, nil
		}
		report.Exclusions = filtered
		changed = true
	default:
		return exclusionChange{}, &excludeCommandError{
			message: fmt.Sprintf("Cannot update exclusion: unsupported operation %q.", operation),
		}
	}
	sort.Slice(report.Exclusions, func(i, j int) bool {
		if report.Exclusions[i].FileHash == report.Exclusions[j].FileHash {
			return report.Exclusions[i].TextHash < report.Exclusions[j].TextHash
		}
		return report.Exclusions[i].FileHash < report.Exclusions[j].FileHash
	})

	content, err = json.MarshalIndent(report, "", "  ")
	if err != nil {
		return exclusionChange{}, &excludeCommandError{message: fmt.Sprintf("Cannot encode exclusions file %q: %v.", reportPath, err)}
	}
	content = append(content, '\n')

	temporary, err := os.CreateTemp(directory, ".grand-report-*.tmp")
	if err != nil {
		return exclusionChange{}, &excludeCommandError{message: fmt.Sprintf("Cannot create temporary exclusions file: %v.", err)}
	}
	temporaryPath := temporary.Name()
	defer os.Remove(temporaryPath)
	if _, err := temporary.Write(content); err != nil {
		_ = temporary.Close()
		return exclusionChange{}, &excludeCommandError{message: fmt.Sprintf("Cannot write temporary exclusions file: %v.", err)}
	}
	if err := temporary.Close(); err != nil {
		return exclusionChange{}, &excludeCommandError{message: fmt.Sprintf("Cannot close temporary exclusions file: %v.", err)}
	}
	if err := os.Rename(temporaryPath, reportPath); err != nil {
		return exclusionChange{}, &excludeCommandError{message: fmt.Sprintf("Cannot replace exclusions file %q: %v.", reportPath, err)}
	}
	if err := reportLock.Unlock(); err != nil {
		return exclusionChange{ReportPath: reportPath, Changed: changed}, fmt.Errorf("unlock exclusions file %q: %w", reportPath, err)
	}
	locked = false
	return exclusionChange{ReportPath: reportPath, Changed: changed}, nil
}

// decodeGrandReport parses a report strictly: an empty object decodes as an empty report, and
// anything else must be an "exclusions" array whose objects carry "t-hash" and "f-hash" and nothing
// more. Unknown fields, duplicate keys, and trailing content are errors rather than omissions,
// because [updateExclusions] rewrites the whole file and whatever this decoder drops would be lost.
func decodeGrandReport(content []byte) (grandReport, error) {
	var compact bytes.Buffer
	if json.Compact(&compact, content) == nil && compact.String() == "{}" {
		return grandReport{Exclusions: []grandReportExclusion{}}, nil
	}
	if err := validateUniqueJSONKeys(content); err != nil {
		return grandReport{}, err
	}

	decoder := json.NewDecoder(bytes.NewReader(content))
	decoder.DisallowUnknownFields()
	var encoded strictGrandReport
	if err := decoder.Decode(&encoded); err != nil {
		return grandReport{}, err
	}
	if err := decoder.Decode(&struct{}{}); err != io.EOF {
		if err == nil {
			return grandReport{}, fmt.Errorf("unexpected content after JSON object")
		}
		return grandReport{}, err
	}
	if encoded.Exclusions == nil {
		return grandReport{}, fmt.Errorf("\"exclusions\" must be an array")
	}

	report := grandReport{Exclusions: make([]grandReportExclusion, 0, len(*encoded.Exclusions))}
	for index, item := range *encoded.Exclusions {
		if item.TextHash == nil {
			return grandReport{}, fmt.Errorf("exclusion %d is missing \"t-hash\"", index)
		}
		if item.FileHash == nil {
			return grandReport{}, fmt.Errorf("exclusion %d is missing \"f-hash\"", index)
		}
		report.Exclusions = append(report.Exclusions, grandReportExclusion{
			TextHash: *item.TextHash,
			FileHash: *item.FileHash,
		})
	}
	return report, nil
}

// validateUniqueJSONKeys rejects content that repeats a field name in any of its objects.
// encoding/json keeps the last of a repeated key without complaining, which would silently drop the
// earlier value on the next rewrite.
func validateUniqueJSONKeys(content []byte) error {
	decoder := json.NewDecoder(bytes.NewReader(content))
	decoder.UseNumber()
	if err := validateJSONValue(decoder); err != nil {
		return err
	}
	if _, err := decoder.Token(); err != io.EOF {
		if err == nil {
			return fmt.Errorf("unexpected content after JSON value")
		}
		return err
	}
	return nil
}

func validateJSONValue(decoder *json.Decoder) error {
	token, err := decoder.Token()
	if err != nil {
		return err
	}
	delim, composite := token.(json.Delim)
	if !composite {
		return nil
	}

	switch delim {
	case '{':
		keys := make(map[string]struct{})
		for decoder.More() {
			keyToken, err := decoder.Token()
			if err != nil {
				return err
			}
			key, ok := keyToken.(string)
			if !ok {
				return fmt.Errorf("JSON object key must be a string")
			}
			if _, duplicate := keys[key]; duplicate {
				return fmt.Errorf("duplicate JSON field %q", key)
			}
			keys[key] = struct{}{}
			if err := validateJSONValue(decoder); err != nil {
				return err
			}
		}
	case '[':
		for decoder.More() {
			if err := validateJSONValue(decoder); err != nil {
				return err
			}
		}
	default:
		return fmt.Errorf("unexpected JSON delimiter %q", delim)
	}
	_, err = decoder.Token()
	return err
}

func exclusionForEvent(event excludeEvent) (grandReportExclusion, error) {
	var textHash string
	var rawPath string
	var fieldName string
	switch event.Type {
	case "found":
		textHash = sha256Hex(event.Found)
		rawPath = event.File
		fieldName = "file"
	case "file":
		textHash = fullPathExclusionHash
		rawPath = event.File
		fieldName = "file"
	case "folder":
		textHash = fullPathExclusionHash
		rawPath = event.Folder
		fieldName = "folder"
	default:
		return grandReportExclusion{}, &unsupportedExcludeEventTypeError{eventType: event.Type}
	}
	normalized, err := normalizeExcludePath(rawPath, fieldName)
	if err != nil {
		return grandReportExclusion{}, err
	}
	return grandReportExclusion{TextHash: textHash, FileHash: sha256Hex(normalized)}, nil
}

func normalizeExcludePath(rawPath, fieldName string) (string, error) {
	normalizedSeparators := strings.ReplaceAll(rawPath, "\\", "/")
	cleaned := path.Clean(normalizedSeparators)
	if normalizedSeparators == "" || cleaned == "." {
		return "", &excludeCommandError{
			message: fmt.Sprintf("Cannot exclude event: %q must be a nonempty relative path.", fieldName),
		}
	}
	if strings.HasPrefix(cleaned, "/") || cleaned == ".." || strings.HasPrefix(cleaned, "../") ||
		(len(cleaned) >= 2 && cleaned[1] == ':') {
		return "", &excludeCommandError{
			message: fmt.Sprintf("Cannot exclude event: %q must stay within FOLDER_PATH.", fieldName),
		}
	}
	return cleaned, nil
}

// parseExcludeEvent decodes encoded into an [excludeEvent], tolerating the "JSON:" line prefix that
// cfcli itself prints so a finding can be pasted straight from the scan output.
func parseExcludeEvent(encoded string) (excludeEvent, error) {
	encoded = strings.TrimSpace(encoded)
	if strings.HasPrefix(encoded, "BASE64:") {
		decoded, err := base64.RawURLEncoding.DecodeString(strings.TrimSpace(strings.TrimPrefix(encoded, "BASE64:")))
		if err != nil {
			return excludeEvent{}, &excludeCommandError{
				message: fmt.Sprintf("Cannot decode Base64 exclude event: %v.", err),
			}
		}
		encoded = string(decoded)
	}
	if strings.HasPrefix(encoded, "JSON:") {
		encoded = strings.TrimSpace(strings.TrimPrefix(encoded, "JSON:"))
	}

	var event excludeEvent
	if err := json.Unmarshal([]byte(encoded), &event); err != nil {
		return excludeEvent{}, &excludeCommandError{
			message: fmt.Sprintf("Cannot parse exclude event: %v.", err),
		}
	}
	if event.Type == "" {
		return excludeEvent{}, &excludeCommandError{
			message: "Cannot exclude event: \"type\" must be a nonempty string.",
		}
	}
	if event.Type != "found" && event.Type != "file" && event.Type != "folder" {
		return excludeEvent{}, &unsupportedExcludeEventTypeError{eventType: event.Type}
	}
	if event.Type == "found" && event.Found == "" {
		return excludeEvent{}, &excludeCommandError{
			message: "Cannot exclude event: \"found\" must be a nonempty string.",
		}
	}
	if (event.Type == "found" || event.Type == "file") && event.File == "" {
		return excludeEvent{}, &excludeCommandError{
			message: "Cannot exclude event: \"file\" must be a nonempty relative path.",
		}
	}
	if event.Type == "folder" && event.Folder == "" {
		return excludeEvent{}, &excludeCommandError{
			message: "Cannot exclude event: \"folder\" must be a nonempty relative path.",
		}
	}
	return event, nil
}
