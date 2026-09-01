package main

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

// maxFindingsPerSignaturePerFile caps the found events one file may report for
// one signature key. Signatures that share a key share the cap. An allowed or
// excluded match stops the current signature's scan of the file without
// counting against the cap.
const maxFindingsPerSignaturePerFile = 5

// finding is the JSON event carrying one signature match.
//
// Type is found, allowed, or excluded, and only a found event makes the scan
// report a failure. Line is the 1-based line holding the match, and File is
// relative to the scan root with / separators.
type finding struct {
	Type  string `json:"type"`
	Key   string `json:"key"`
	Found string `json:"found"`
	Line  int    `json:"line"`
	File  string `json:"file"`
}

// excludedPathEvent is the JSON event for a file or folder the grand report
// excludes whole. A single match suppressed by an exact-text exclusion is
// reported as a [finding] with Type excluded instead.
type excludedPathEvent struct {
	Type string `json:"type"`
	File string `json:"file"`
}

// listPathEvent is the JSON event verbose mode emits for a folder or file before
// the scan reaches it. Exactly one of File and Folder is set, and a folder is
// reported once however many of its files follow.
type listPathEvent struct {
	Type   string `json:"type"`
	File   string `json:"file,omitempty"`
	Folder string `json:"folder,omitempty"`
}

// scanResult summarizes one scan pass.
//
// found is true when at least one match survived both the dictionary allowed
// list and the grand-report exclusions. scannedCount counts the files that were
// read, so it excludes files skipped by a path exclusion or a read error, and in
// quick mode it stops at the file holding the first finding.
type scanResult struct {
	found        bool
	scannedCount int
}

func scanFiles(
	root string,
	files []string,
	loaded dictionary,
	mode scanMode,
	output, errors *lineOutput,
) (scanResult, error) {
	return scanFilesConfigured(root, files, loaded, mode, exclusionSet{}, false, false, output, errors)
}

func scanFilesWithExclusions(
	root string,
	files []string,
	loaded dictionary,
	mode scanMode,
	exclusions exclusionSet,
	output, errors *lineOutput,
) (scanResult, error) {
	return scanFilesConfigured(root, files, loaded, mode, exclusions, false, false, output, errors)
}

// scanFilesConfigured scans files for dictionary signature matches and reports
// them to output as JSON. files holds absolute paths under root, and root anchors
// the relative paths that appear in the events and in the exclusion lookups. A
// file that cannot be read produces a warning on errors and is not counted as
// scanned; the returned error covers only path resolution and failed writes.
//
// modeJSON also reports a match the dictionary allowed list or the grand report
// suppresses, then stops scanning that signature in the current file. modeQuick
// reports neither and also stops that signature. It returns as soon as one
// match survives both, leaving the remaining files unread; printDetails then adds the
// copy-ready cfcli exclude commands for that match, and without it output
// carries a one-line hint. verbose emits a [listPathEvent] for each folder and
// file the scan walks, in either mode; a path the exclusions name is listed and
// then reported as an [excludedPathEvent], and below an excluded folder only
// such paths are listed.
func scanFilesConfigured(
	root string,
	files []string,
	loaded dictionary,
	mode scanMode,
	exclusions exclusionSet,
	verbose bool,
	printDetails bool,
	output, errors *lineOutput,
) (scanResult, error) {
	absoluteRoot, err := filepath.Abs(root)
	if err != nil {
		return scanResult{}, fmt.Errorf("resolve scan root %q: %w", root, err)
	}
	absoluteRoot = filepath.Clean(absoluteRoot)
	foundAny := false
	scannedCount := 0
	emittedPathExclusions := make(map[string]struct{})
	emittedFolders := make(map[string]struct{})
	for _, path := range files {
		relativePath, err := filepath.Rel(absoluteRoot, path)
		if err != nil {
			return scanResult{}, fmt.Errorf("make path %q relative to %q: %w", path, root, err)
		}
		relativePath = filepath.ToSlash(relativePath)
		excludedPaths := exclusions.excludedPaths(relativePath)
		if verbose {
			excludedPathSet := make(map[string]struct{}, len(excludedPaths))
			for _, excludedPath := range excludedPaths {
				excludedPathSet[excludedPath] = struct{}{}
			}
			insideExcludedFolder := false
			for _, folder := range relativeParentPaths(relativePath) {
				_, folderExcluded := excludedPathSet[folder]
				if insideExcludedFolder && !folderExcluded {
					continue
				}
				if _, emitted := emittedFolders[folder]; !emitted {
					if err := output.json(listPathEvent{Type: "list", Folder: folder}); err != nil {
						return scanResult{}, fmt.Errorf("write listed folder: %w", err)
					}
					emittedFolders[folder] = struct{}{}
				}
				if folderExcluded {
					if _, emitted := emittedPathExclusions[folder]; !emitted {
						if err := output.json(excludedPathEvent{Type: "excluded", File: folder}); err != nil {
							return scanResult{}, fmt.Errorf("write excluded folder: %w", err)
						}
						emittedPathExclusions[folder] = struct{}{}
					}
					insideExcludedFolder = true
				}
			}
			if _, fileExcluded := excludedPathSet[relativePath]; fileExcluded {
				if err := output.json(listPathEvent{Type: "list", File: relativePath}); err != nil {
					return scanResult{}, fmt.Errorf("write listed file: %w", err)
				}
				if _, emitted := emittedPathExclusions[relativePath]; !emitted {
					if err := output.json(excludedPathEvent{Type: "excluded", File: relativePath}); err != nil {
						return scanResult{}, fmt.Errorf("write excluded file: %w", err)
					}
					emittedPathExclusions[relativePath] = struct{}{}
				}
			}
			if len(excludedPaths) > 0 {
				continue
			}
			if err := output.json(listPathEvent{Type: "list", File: relativePath}); err != nil {
				return scanResult{}, fmt.Errorf("write listed file: %w", err)
			}
		}
		if len(excludedPaths) > 0 {
			if mode == modeJSON {
				for _, excludedPath := range excludedPaths {
					if _, emitted := emittedPathExclusions[excludedPath]; emitted {
						continue
					}
					if err := output.json(excludedPathEvent{Type: "excluded", File: excludedPath}); err != nil {
						return scanResult{}, fmt.Errorf("write excluded path: %w", err)
					}
					emittedPathExclusions[excludedPath] = struct{}{}
				}
			}
			continue
		}
		content, err := os.ReadFile(path)
		if err != nil {
			if writeErr := errors.text("Cannot read file %q: %v", path, err); writeErr != nil {
				return scanResult{}, fmt.Errorf("write file-read warning: %w", writeErr)
			}
			continue
		}
		scannedCount++
		extension := strings.ToLower(strings.TrimPrefix(filepath.Ext(path), "."))
		findingCounts := make(map[string]int)

		for _, currentSignature := range loaded.signatures {
			if _, excluded := currentSignature.excludedExtensions[extension]; excluded {
				continue
			}
			if findingCounts[currentSignature.key] >= maxFindingsPerSignaturePerFile {
				continue
			}
			lineCursor := 0
			line := 1
			for _, match := range currentSignature.expression.FindAllIndex(content, -1) {
				lineCursor, line = lineAt(content, match[0], lineCursor, line)
				exact := string(content[match[0]:match[1]])
				if exclusions.excludesMatch(relativePath, exact) {
					if mode == modeJSON {
						if err := output.json(finding{
							Type:  "excluded",
							Key:   currentSignature.key,
							Found: exact,
							Line:  line,
							File:  relativePath,
						}); err != nil {
							return scanResult{}, fmt.Errorf("write excluded finding: %w", err)
						}
					}
					break
				}
				if loaded.isAllowed(exact) {
					if mode == modeJSON {
						if err := output.json(finding{
							Type:  "allowed",
							Key:   currentSignature.key,
							Found: exact,
							Line:  line,
							File:  relativePath,
						}); err != nil {
							return scanResult{}, fmt.Errorf("write allowed finding: %w", err)
						}
					}
					break
				}
				foundAny = true
				findingCounts[currentSignature.key]++
				event := newFinding(currentSignature.key, exact, line, relativePath)
				if mode == modeQuick {
					if err := output.json(event); err != nil {
						return scanResult{}, fmt.Errorf("write quick finding: %w", err)
					}
					if printDetails {
						commands, err := formatExcludeCommands(root, event)
						if err != nil {
							return scanResult{}, fmt.Errorf("format quick exclusion commands: %w", err)
						}
						for _, command := range commands {
							if err := output.text("%s: %s", command.Label, command.Command); err != nil {
								return scanResult{}, fmt.Errorf(
									"write %s quick exclusion command: %w",
									command.Label,
									err,
								)
							}
						}
					} else if err := output.text(
						"To print exclusion commands, run cfcli with --mode=quick --print=details.",
					); err != nil {
						return scanResult{}, fmt.Errorf("write quick exclusion hint: %w", err)
					}
					return scanResult{found: true, scannedCount: scannedCount}, nil
				}
				if err := output.json(event); err != nil {
					return scanResult{}, fmt.Errorf("write JSON finding: %w", err)
				}
				if findingCounts[currentSignature.key] == maxFindingsPerSignaturePerFile {
					break
				}
			}
		}
	}

	return scanResult{found: foundAny, scannedCount: scannedCount}, nil
}

func newFinding(key, exact string, line int, relativePath string) finding {
	return finding{
		Type:  "found",
		Key:   key,
		Found: exact,
		Line:  line,
		File:  relativePath,
	}
}

// lineAt advances from cursor to offset over content and reports the 1-based
// number of the line holding offset. LF, CRLF, and a lone CR each end one line.
//
// Calls have to walk content forward: cursor and line come from the previous call
// on the same content, or from 0 and 1 at the start, and offset must not be
// before cursor. The returned cursor is offset, ready to be passed back.
func lineAt(content []byte, offset, cursor, line int) (int, int) {
	for cursor < offset {
		switch content[cursor] {
		case '\r':
			line++
			cursor++
			if cursor < offset && content[cursor] == '\n' {
				cursor++
			}
		case '\n':
			line++
			cursor++
		default:
			cursor++
		}
	}
	return cursor, line
}

// relativeParentPaths returns the ancestor folders of a slash-separated relative
// path, outermost first, excluding both the scan root and the path itself. A path
// directly under the root yields an empty slice.
func relativeParentPaths(relativePath string) []string {
	parents := make([]string, 0)
	for parent := filepath.ToSlash(filepath.Dir(filepath.FromSlash(relativePath))); parent != "." && parent != ""; parent = filepath.ToSlash(filepath.Dir(filepath.FromSlash(parent))) {
		parents = append(parents, parent)
	}
	for left, right := 0, len(parents)-1; left < right; left, right = left+1, right-1 {
		parents[left], parents[right] = parents[right], parents[left]
	}
	return parents
}
