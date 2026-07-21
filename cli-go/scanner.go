package main

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

type finding struct {
	Key      string `json:"key"`
	Found    string `json:"found"`
	Position int    `json:"position"`
	File     string `json:"file"`
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
	absoluteRoot, err := filepath.Abs(root)
	if err != nil {
		return scanResult{}, fmt.Errorf("resolve scan root %q: %w", root, err)
	}
	absoluteRoot = filepath.Clean(absoluteRoot)
	foundAny := false
	scannedCount := 0
	for _, path := range files {
		content, err := os.ReadFile(path)
		if err != nil {
			if writeErr := errors.text("Cannot read file %q: %v", path, err); writeErr != nil {
				return scanResult{}, fmt.Errorf("write file-read warning: %w", writeErr)
			}
			continue
		}
		scannedCount++
		relativePath, err := filepath.Rel(absoluteRoot, path)
		if err != nil {
			return scanResult{}, fmt.Errorf("make path %q relative to %q: %w", path, root, err)
		}
		relativePath = filepath.ToSlash(relativePath)
		extension := strings.ToLower(strings.TrimPrefix(filepath.Ext(path), "."))

		for _, currentSignature := range loaded.signatures {
			if _, excluded := currentSignature.excludedExtensions[extension]; excluded {
				continue
			}
			for _, match := range currentSignature.expression.FindAllIndex(content, -1) {
				exact := string(content[match[0]:match[1]])
				if _, allowed := loaded.allowed[strings.ToLower(exact)]; allowed {
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
