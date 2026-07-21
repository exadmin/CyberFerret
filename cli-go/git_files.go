package main

import (
	"bytes"
	"context"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

func enumerateGitFiles(ctx context.Context, root string) (map[string]struct{}, error) {
	selected := make(map[string]struct{})
	untrackedArgs := []string{"ls-files", "--others", "--exclude-standard", "-z"}
	if home, err := os.UserHomeDir(); err == nil {
		globalIgnore := filepath.Join(home, ".gitignore_global")
		if info, statErr := os.Stat(globalIgnore); statErr == nil && info.Mode().IsRegular() {
			untrackedArgs = append(untrackedArgs, "--exclude-from="+globalIgnore)
		}
	}
	commands := [][]string{
		{"ls-files", "--cached", "-z"},
		untrackedArgs,
	}

	for _, args := range commands {
		output, err := runGitLsFiles(ctx, root, args...)
		if err != nil {
			return nil, err
		}
		for _, path := range bytes.Split(output, []byte{0}) {
			if len(path) == 0 {
				continue
			}
			selected[filepath.FromSlash(string(path))] = struct{}{}
		}
	}

	return selected, nil
}

func runGitLsFiles(ctx context.Context, root string, args ...string) ([]byte, error) {
	command := exec.CommandContext(ctx, "git", args...)
	command.Dir = root
	var stderr bytes.Buffer
	command.Stderr = &stderr

	output, err := command.Output()
	if err != nil {
		message := strings.TrimSpace(stderr.String())
		if message == "" {
			message = err.Error()
		}
		return nil, fmt.Errorf("git %s: %s", strings.Join(args, " "), message)
	}

	return output, nil
}
