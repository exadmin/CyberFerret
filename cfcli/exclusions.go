package main

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"os"
	"path/filepath"
)

// fullPathExclusionHash stands where a text hash would stand in grand-report.json and excludes the
// paired path outright: the file itself, or every file beneath it when the path is a directory. It
// is a literal marker rather than the SHA-256 of anything.
const fullPathExclusionHash = "00000000"

// exclusionSet holds the exclusions loaded from grand-report.json. The outer key is the SHA-256 hex
// of a path normalized by [normalizeRelativePath], the inner key the SHA-256 hex of an exact match
// or [fullPathExclusionHash]; hashing the match is what makes text exclusions case-sensitive.
type exclusionSet struct {
	textHashesByFileHash map[string]map[string]struct{}
}

type grandReport struct {
	Exclusions []grandReportExclusion `json:"exclusions"`
}

type grandReportExclusion struct {
	TextHash string `json:"t-hash"`
	FileHash string `json:"f-hash"`
}

// loadExclusions reads the exclusions from root/.qubership/grand-report.json. A missing file yields
// an empty set silently; an unreadable or malformed one yields an empty set after a warning naming
// the absolute report path, so a broken report never stops a scan.
func loadExclusions(root string, warnings *lineOutput) exclusionSet {
	loaded := exclusionSet{textHashesByFileHash: make(map[string]map[string]struct{})}
	reportPath, err := filepath.Abs(filepath.Join(root, ".qubership", "grand-report.json"))
	if err != nil {
		_ = warnings.text("Cannot resolve exclusions file \"%s\": %v. Continuing without exclusions.", reportPath, err)
		return loaded
	}
	content, err := os.ReadFile(reportPath)
	if os.IsNotExist(err) {
		return loaded
	}
	if err != nil {
		_ = warnings.text("Cannot read exclusions file \"%s\": %v. Continuing without exclusions.", reportPath, err)
		return loaded
	}
	var report grandReport
	if err := json.Unmarshal(content, &report); err != nil {
		_ = warnings.text("Cannot parse exclusions file \"%s\": %v. Continuing without exclusions.", reportPath, err)
		return loaded
	}
	for _, item := range report.Exclusions {
		textHashes := loaded.textHashesByFileHash[item.FileHash]
		if textHashes == nil {
			textHashes = make(map[string]struct{})
			loaded.textHashesByFileHash[item.FileHash] = textHashes
		}
		textHashes[item.TextHash] = struct{}{}
	}
	return loaded
}

func (e exclusionSet) excludesPath(relativePath string) bool {
	return len(e.excludedPaths(relativePath)) > 0
}

// excludedPaths returns the whole-path exclusions that cover relativePath: the path itself and each
// of its ancestors up to the repository root, outermost first. The result is empty when no
// exclusion covers the path.
func (e exclusionSet) excludedPaths(relativePath string) []string {
	candidates := []string{normalizeRelativePath(relativePath)}
	for candidates[len(candidates)-1] != "" {
		parent := filepath.ToSlash(filepath.Dir(filepath.FromSlash(candidates[len(candidates)-1])))
		if parent == "." {
			parent = ""
		}
		candidates = append(candidates, parent)
	}

	matched := make([]string, 0, len(candidates))
	for index := len(candidates) - 1; index >= 0; index-- {
		candidate := candidates[index]
		if e.contains(fullPathExclusionHash, sha256Hex(candidate)) {
			matched = append(matched, candidate)
		}
	}
	return matched
}

func (e exclusionSet) excludesMatch(relativePath, exact string) bool {
	return e.contains(sha256Hex(exact), sha256Hex(normalizeRelativePath(relativePath)))
}

func (e exclusionSet) contains(textHash, fileHash string) bool {
	_, found := e.textHashesByFileHash[fileHash][textHash]
	return found
}

// normalizeRelativePath returns path in the cleaned, slash-separated form the exclusion hashes are
// computed over. The repository root becomes the empty string.
func normalizeRelativePath(path string) string {
	cleaned := filepath.ToSlash(filepath.Clean(filepath.FromSlash(path)))
	if cleaned == "." {
		return ""
	}
	return cleaned
}

func sha256Hex(value string) string {
	digest := sha256.Sum256([]byte(value))
	return hex.EncodeToString(digest[:])
}
