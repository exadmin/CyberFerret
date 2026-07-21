package main

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

type finding struct {
	Type     string `json:"type"`
	Key      string `json:"key"`
	Found    string `json:"found"`
	Position int    `json:"position"`
	File     string `json:"file"`
}

type excludedPathEvent struct {
	Type string `json:"type"`
	File string `json:"file"`
}

type listPathEvent struct {
	Type   string `json:"type"`
	File   string `json:"file,omitempty"`
	Folder string `json:"folder,omitempty"`
}

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
	return scanFilesConfigured(root, files, loaded, mode, exclusionSet{}, false, output, errors)
}

func scanFilesWithExclusions(
	root string,
	files []string,
	loaded dictionary,
	mode scanMode,
	exclusions exclusionSet,
	output, errors *lineOutput,
) (scanResult, error) {
	return scanFilesConfigured(root, files, loaded, mode, exclusions, false, output, errors)
}

func scanFilesConfigured(
	root string,
	files []string,
	loaded dictionary,
	mode scanMode,
	exclusions exclusionSet,
	verbose bool,
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

		for _, currentSignature := range loaded.signatures {
			if _, excluded := currentSignature.excludedExtensions[extension]; excluded {
				continue
			}
			for _, match := range currentSignature.expression.FindAllIndex(content, -1) {
				exact := string(content[match[0]:match[1]])
				if exclusions.excludesMatch(relativePath, exact) {
					if mode == modeJSON {
						if err := output.json(finding{
							Type:     "excluded",
							Key:      currentSignature.key,
							Found:    exact,
							Position: match[0],
							File:     relativePath,
						}); err != nil {
							return scanResult{}, fmt.Errorf("write excluded finding: %w", err)
						}
					}
					continue
				}
				if loaded.isAllowed(exact) {
					if mode == modeJSON {
						if err := output.json(finding{
							Type:     "allowed",
							Key:      currentSignature.key,
							Found:    exact,
							Position: match[0],
							File:     relativePath,
						}); err != nil {
							return scanResult{}, fmt.Errorf("write allowed finding: %w", err)
						}
					}
					continue
				}
				foundAny = true
				if mode == modeQuick {
					if err := output.text(
						"Signature %q found in %s at position %d",
						currentSignature.key,
						relativePath,
						match[0],
					); err != nil {
						return scanResult{}, fmt.Errorf("write quick finding: %w", err)
					}
					return scanResult{found: true, scannedCount: scannedCount}, nil
				}
				if err := output.json(finding{
					Type:     "found",
					Key:      currentSignature.key,
					Found:    exact,
					Position: match[0],
					File:     relativePath,
				}); err != nil {
					return scanResult{}, fmt.Errorf("write JSON finding: %w", err)
				}
			}
		}
	}

	return scanResult{found: foundAny, scannedCount: scannedCount}, nil
}

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
