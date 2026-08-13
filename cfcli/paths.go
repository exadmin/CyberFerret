package main

import (
	"bufio"
	"context"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
)

// maxListedPathLength is the longest line [readListedPaths] accepts from a list
// file, in bytes. A longer line fails the read instead of being truncated.
const maxListedPathLength = 1024 * 1024

// selectFiles returns the sorted absolute paths of the files to scan under
// rootArg, which must name a directory. [enumerateGitFiles] decides which paths
// are eligible: everything Git tracks, plus the untracked files no ignore rule
// covers. A non-nil listArg names a file of paths relative to rootArg that
// narrows the selection further: an entry Git does not report is skipped, while
// an absolute entry or one escaping rootArg fails the call. Only regular files
// survive, so a symbolic link to a directory is neither returned nor traversed.
func selectFiles(ctx context.Context, rootArg string, listArg *string) ([]string, error) {
	root, err := filepath.Abs(rootArg)
	if err != nil {
		return nil, fmt.Errorf("resolve FOLDER_PATH %q: %w", rootArg, err)
	}
	root = filepath.Clean(root)
	info, err := os.Stat(root)
	if err != nil {
		return nil, fmt.Errorf("inspect FOLDER_PATH %q: %w", root, err)
	}
	if !info.IsDir() {
		return nil, fmt.Errorf("FOLDER_PATH %q is not a directory", root)
	}

	gitFiles, err := enumerateGitFiles(ctx, root)
	if err != nil {
		return nil, fmt.Errorf("list Git files in %q: %w", root, err)
	}

	candidates := gitFiles
	if listArg != nil {
		listedPaths, err := readListedPaths(*listArg)
		if err != nil {
			return nil, fmt.Errorf("read file list %q: %w", *listArg, err)
		}
		candidates = make(map[string]struct{}, len(listedPaths))
		for _, path := range listedPaths {
			if err := validateRelativeGitPath(path); err != nil {
				return nil, fmt.Errorf("invalid listed path %q: %w", path, err)
			}
			nativePath := filepath.Clean(filepath.FromSlash(path))
			if _, selected := gitFiles[nativePath]; selected {
				candidates[nativePath] = struct{}{}
			}
		}
	}

	selected := make(map[string]struct{}, len(candidates))
	for relativePath := range candidates {
		absolutePath := filepath.Join(root, relativePath)
		fileInfo, err := os.Stat(absolutePath)
		if err != nil || !fileInfo.Mode().IsRegular() {
			continue
		}
		selected[absolutePath] = struct{}{}
	}

	result := make([]string, 0, len(selected))
	for path := range selected {
		result = append(result, path)
	}
	sort.Strings(result)
	return result, nil
}

// readListedPaths returns the non-empty lines of the file at path, each with a
// trailing carriage return removed so a CRLF list reads like an LF one. A line
// longer than [maxListedPathLength] fails the read.
func readListedPaths(path string) ([]string, error) {
	file, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer file.Close()

	var paths []string
	scanner := bufio.NewScanner(file)
	scanner.Buffer(make([]byte, 64*1024), maxListedPathLength)
	for scanner.Scan() {
		path := strings.TrimSuffix(scanner.Text(), "\r")
		if path != "" {
			paths = append(paths, path)
		}
	}
	if err := scanner.Err(); err != nil {
		return nil, err
	}
	return paths, nil
}

// validateRelativeGitPath rejects a listed path that cannot name a file inside
// FOLDER_PATH: an absolute path, one that resolves to the folder itself, and one
// that escapes it through "..". The error names the rule that was broken and
// reaches the user, wrapped by [selectFiles].
func validateRelativeGitPath(path string) error {
	nativePath := filepath.FromSlash(path)
	if filepath.IsAbs(nativePath) {
		return fmt.Errorf("path must be relative")
	}

	cleaned := filepath.Clean(nativePath)
	if cleaned == "." {
		return fmt.Errorf("path must identify a file")
	}
	if cleaned == ".." || strings.HasPrefix(cleaned, ".."+string(filepath.Separator)) {
		return fmt.Errorf("path escapes FOLDER_PATH")
	}
	return nil
}
