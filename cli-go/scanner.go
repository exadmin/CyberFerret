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

func scanFiles(
	root string,
	files []string,
	loaded dictionary,
	mode scanMode,
	output, errors *lineOutput,
) (bool, error) {
	absoluteRoot, err := filepath.Abs(root)
	if err != nil {
		return false, fmt.Errorf("resolve scan root %q: %w", root, err)
	}
	absoluteRoot = filepath.Clean(absoluteRoot)
	foundAny := false
	for _, path := range files {
		content, err := os.ReadFile(path)
		if err != nil {
			if writeErr := errors.text("Cannot read file %q: %v", path, err); writeErr != nil {
				return false, fmt.Errorf("write file-read warning: %w", writeErr)
			}
			continue
		}
		relativePath, err := filepath.Rel(absoluteRoot, path)
		if err != nil {
			return false, fmt.Errorf("make path %q relative to %q: %w", path, root, err)
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
						return false, fmt.Errorf("write quick finding: %w", err)
					}
					return true, nil
				}
				if err := output.json(finding{
					Key:      currentSignature.key,
					Found:    truncateRunes(exact, 16),
					Position: match[0],
					File:     relativePath,
				}); err != nil {
					return false, fmt.Errorf("write JSON finding: %w", err)
				}
			}
		}
	}

	if mode == modeJSON {
		for _, path := range files {
			if err := output.text("%s", path); err != nil {
				return false, fmt.Errorf("write selected file path: %w", err)
			}
		}
	}
	return foundAny, nil
}

func truncateRunes(value string, maximum int) string {
	runes := []rune(value)
	if len(runes) <= maximum {
		return value
	}
	return string(runes[:maximum])
}
