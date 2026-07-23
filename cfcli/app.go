package main

import (
	"context"
	"errors"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"
)

type appDependencies struct {
	refresher cacheRefresher
	getenv    func(string) string
	now       func() time.Time
}

func run(ctx context.Context, args []string, stdout, stderr io.Writer) int {
	return runWithDependencies(ctx, args, stdout, stderr, appDependencies{
		refresher: cacheRefresher{
			client:  &http.Client{},
			now:     time.Now,
			homeDir: os.UserHomeDir,
			url:     dictionaryURL,
			timeout: refreshTimeout,
		},
		getenv: os.Getenv,
		now:    time.Now,
	})
}

func runWithDependencies(
	ctx context.Context,
	args []string,
	stdout, stderr io.Writer,
	dependencies appDependencies,
) int {
	if isExcludeCommand(args) {
		return runExcludeCommand(args, stdout, stderr)
	}

	output := newLineOutput(stdout)
	errorOutput := newLineOutput(stderr)
	parsed, err := parseOptions(args)
	if err != nil {
		var argumentCountError *usageError
		if !errors.As(err, &argumentCountError) {
			writeFatal(errorOutput, "%v", err)
		}
		_ = writeHelp(errorOutput)
		return 1
	}
	if parsed.printDetails && parsed.mode != modeQuick {
		if err := output.text(
			"Warning: --print=details applies only to --mode=quick and will be ignored.",
		); err != nil {
			writeFatal(errorOutput, "Cannot write print option warning: %v", err)
			return 1
		}
	}

	cache, err := dependencies.refresher.refresh(ctx, errorOutput)
	if err != nil {
		writeFatal(errorOutput, "Cannot prepare dictionary cache: %v", err)
		return 1
	}
	cachePath := cache.path
	if err := output.text("%s", dictionaryStatusMessage(cache.state)); err != nil {
		writeFatal(errorOutput, "Cannot write dictionary status: %v", err)
		return 1
	}
	if err := output.text("Dictionary path: %s", dictionaryDisplayPath(cache.path, cache.home)); err != nil {
		writeFatal(errorOutput, "Cannot write dictionary path: %v", err)
		return 1
	}
	encrypted, err := os.ReadFile(cachePath)
	if err != nil {
		writeFatal(errorOutput, "Cannot read dictionary cache %q: %v", cachePath, err)
		return 1
	}
	password := ""
	if dependencies.getenv != nil {
		password = dependencies.getenv("CYBER_FERRET_PASSWORD")
	}
	plaintext, err := decryptDictionary(encrypted, password)
	if err != nil {
		writeFatal(errorOutput, "%v", err)
		return 1
	}

	loaded, err := loadDictionary(plaintext, errorOutput)
	if err != nil {
		var compileError *regexpCompileError
		if errors.As(err, &compileError) {
			dictionaryPath := cachePath
			if absolutePath, pathErr := filepath.Abs(cachePath); pathErr == nil {
				dictionaryPath = absolutePath
			}
			writeFatal(errorOutput, "Cannot compile dictionary regexp from \"%s\": %v", dictionaryPath, compileError)
			return 3
		}
		writeFatal(errorOutput, "Cannot load dictionary: %v", err)
		return 1
	}
	if err := output.text("Dictionary version: %s", loaded.version); err != nil {
		writeFatal(errorOutput, "Cannot write dictionary version: %v", err)
		return 1
	}

	now := dependencies.now
	if now == nil {
		now = time.Now
	}
	startedAt := now()
	if err := output.text("Scanning is in progress. Please wait."); err != nil {
		writeFatal(errorOutput, "Cannot write scanning progress: %v", err)
		return 1
	}

	exclusions := loadExclusions(parsed.root, errorOutput)
	files, err := selectFiles(ctx, parsed.root, parsed.listPath)
	if err != nil {
		writeFatal(errorOutput, "%v", err)
		return 1
	}
	result, err := scanFilesConfigured(
		parsed.root,
		files,
		loaded,
		parsed.mode,
		exclusions,
		parsed.verbose,
		parsed.printDetails,
		output,
		errorOutput,
	)
	if err != nil {
		writeFatal(errorOutput, "Cannot scan files: %v", err)
		return 1
	}
	if err := output.text("Total files scanned %d", result.scannedCount); err != nil {
		writeFatal(errorOutput, "Cannot write scanned file count: %v", err)
		return 1
	}
	elapsed := now().Sub(startedAt).Seconds()
	if err := output.text("Scanning is finished in %.3f seconds.", elapsed); err != nil {
		writeFatal(errorOutput, "Cannot write scanning duration: %v", err)
		return 1
	}
	if result.found {
		return 2
	}
	return 0
}

func writeHelp(output *lineOutput) error {
	lines := []string{
		"Usage:",
		"  cfcli [--mode=quick|--mode=json] [--print=details] [--verbose=true|--verbose=false] " +
			"FOLDER_PATH [PATH_TO_LIST_OF_FILES]",
		"  cfcli exclude FOLDER_PATH JSON_OBJECT",
		"",
		"The exclude command adds a found event to FOLDER_PATH/.qubership/grand-report.json.",
		"JSON_OBJECT may start with \"JSON:\" and must have type \"found\", found, and file fields.",
	}
	for _, line := range lines {
		if err := output.text("%s", line); err != nil {
			return err
		}
	}
	return nil
}

func writeFatal(output *lineOutput, format string, args ...any) {
	_ = output.text(format, args...)
}

func dictionaryStatusMessage(state cacheState) string {
	return map[cacheState]string{
		cacheCurrent:  "Dictionary is up to date.",
		cacheUpdated:  "Dictionary was updated.",
		cacheFallback: "Dictionary was not updated due to network issues; using the existing dictionary.",
	}[state]
}

func dictionaryDisplayPath(path, home string) string {
	relative, err := filepath.Rel(home, path)
	outsideHome := relative == ".." || strings.HasPrefix(relative, ".."+string(filepath.Separator))
	if err == nil && !filepath.IsAbs(relative) && !outsideHome {
		return "~/" + filepath.ToSlash(relative)
	}
	return filepath.ToSlash(filepath.Clean(path))
}
