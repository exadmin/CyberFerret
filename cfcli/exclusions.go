package main

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"os"
	"path/filepath"
)

const fullPathExclusionHash = "00000000"

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
