package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"
)

type excludeEvent struct {
	Type  string `json:"type"`
	Found string `json:"found"`
	File  string `json:"file"`
}

type strictGrandReport struct {
	Exclusions *[]strictGrandReportExclusion `json:"exclusions"`
}

type strictGrandReportExclusion struct {
	TextHash *string `json:"t-hash"`
	FileHash *string `json:"f-hash"`
}

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
		"Cannot exclude event type %q: only \"found\" is supported. No files were changed.",
		e.eventType,
	)
}

type excludeCommandError struct {
	message string
}

func isExcludeCommand(args []string) bool {
	return len(args) > 0 && args[0] == "exclude"
}

func runExcludeCommand(args []string, stdout, stderr io.Writer) int {
	errorOutput := newLineOutput(stderr)
	if len(args) != 3 {
		_ = writeHelp(errorOutput)
		return 1
	}
	reportPath, err := updateExclusions(args[1], args[2])
	if err != nil {
		writeFatal(errorOutput, "%v", err)
		return 1
	}
	if err := newLineOutput(stdout).text("Exclusions file was updated: %s", reportPath); err != nil {
		writeFatal(errorOutput, "Cannot write updated exclusions path: %v", err)
		return 1
	}
	return 0
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

func updateExclusions(root, encodedEvent string) (string, error) {
	event, err := parseExcludeEvent(encodedEvent)
	if err != nil {
		return "", err
	}
	rootInfo, err := os.Stat(root)
	if err != nil {
		return "", &excludeCommandError{message: fmt.Sprintf("Cannot inspect FOLDER_PATH %q: %v.", root, err)}
	}
	if !rootInfo.IsDir() {
		return "", &excludeCommandError{message: fmt.Sprintf("FOLDER_PATH %q is not a directory.", root)}
	}

	reportPath, err := filepath.Abs(filepath.Join(root, ".qubership", "grand-report.json"))
	if err != nil {
		return "", &excludeCommandError{message: fmt.Sprintf("Cannot resolve exclusions file: %v.", err)}
	}
	var report grandReport
	content, err := os.ReadFile(reportPath)
	switch {
	case err == nil:
		report, err = decodeGrandReport(content)
		if err != nil {
			return "", &excludeCommandError{
				message: fmt.Sprintf("Cannot parse exclusions file %q: %v.", reportPath, err),
			}
		}
	case os.IsNotExist(err):
		report.Exclusions = []grandReportExclusion{}
	default:
		return "", &excludeCommandError{
			message: fmt.Sprintf("Cannot read exclusions file %q: %v.", reportPath, err),
		}
	}

	addition := grandReportExclusion{
		TextHash: sha256Hex(event.Found),
		FileHash: sha256Hex(event.File),
	}
	filtered := report.Exclusions[:0]
	for _, existing := range report.Exclusions {
		if existing.TextHash != addition.TextHash || existing.FileHash != addition.FileHash {
			filtered = append(filtered, existing)
		}
	}
	report.Exclusions = append(filtered, addition)
	sort.Slice(report.Exclusions, func(i, j int) bool {
		if report.Exclusions[i].FileHash == report.Exclusions[j].FileHash {
			return report.Exclusions[i].TextHash < report.Exclusions[j].TextHash
		}
		return report.Exclusions[i].FileHash < report.Exclusions[j].FileHash
	})

	content, err = json.MarshalIndent(report, "", "  ")
	if err != nil {
		return "", &excludeCommandError{message: fmt.Sprintf("Cannot encode exclusions file %q: %v.", reportPath, err)}
	}
	content = append(content, '\n')

	directory := filepath.Dir(reportPath)
	if err := os.MkdirAll(directory, 0o755); err != nil {
		return "", &excludeCommandError{message: fmt.Sprintf("Cannot create exclusions directory %q: %v.", directory, err)}
	}
	temporary, err := os.CreateTemp(directory, ".grand-report-*.tmp")
	if err != nil {
		return "", &excludeCommandError{message: fmt.Sprintf("Cannot create temporary exclusions file: %v.", err)}
	}
	temporaryPath := temporary.Name()
	defer os.Remove(temporaryPath)
	if _, err := temporary.Write(content); err != nil {
		_ = temporary.Close()
		return "", &excludeCommandError{message: fmt.Sprintf("Cannot write temporary exclusions file: %v.", err)}
	}
	if err := temporary.Close(); err != nil {
		return "", &excludeCommandError{message: fmt.Sprintf("Cannot close temporary exclusions file: %v.", err)}
	}
	if err := os.Rename(temporaryPath, reportPath); err != nil {
		return "", &excludeCommandError{message: fmt.Sprintf("Cannot replace exclusions file %q: %v.", reportPath, err)}
	}
	return reportPath, nil
}

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

func parseExcludeEvent(encoded string) (excludeEvent, error) {
	encoded = strings.TrimSpace(encoded)
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
	if event.Type != "found" {
		return excludeEvent{}, &unsupportedExcludeEventTypeError{eventType: event.Type}
	}
	if event.Found == "" {
		return excludeEvent{}, &excludeCommandError{
			message: "Cannot exclude event: \"found\" must be a nonempty string.",
		}
	}
	if event.File == "" {
		return excludeEvent{}, &excludeCommandError{
			message: "Cannot exclude event: \"file\" must be a nonempty string.",
		}
	}
	return event, nil
}
