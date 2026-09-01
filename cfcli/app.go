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

// appDependencies holds the process-level services runWithDependencies would otherwise reach for
// directly, so a test can supply a temporary home, a fixed clock, and a known password. A nil now
// falls back to [time.Now]. A nil getenv leaves the dictionary password empty, which decryption
// then rejects.
type appDependencies struct {
	refresher cacheRefresher
	getenv    func(string) string
	now       func() time.Time
}

// run performs one cfcli invocation with the live dictionary endpoint, the process environment,
// and the wall clock. It returns the exit status documented on [runWithDependencies].
func run(ctx context.Context, args []string, stdout, stderr io.Writer) exitStatus {
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

// runWithDependencies performs one cfcli invocation against the given dependencies and returns
// [exitClean], [exitFailure], [exitFindings], or [exitBadExpression], each of which documents the
// condition it reports.
//
// Scan output goes to stdout. The usage help, every fatal message, and the dictionary warnings go
// to stderr; the --print=details warning is the one warning written to stdout. An "exclude" first
// argument is dispatched to [runExcludeCommand] before any option is parsed.
func runWithDependencies(
	ctx context.Context,
	args []string,
	stdout, stderr io.Writer,
	dependencies appDependencies,
) exitStatus {
	if err := writeAppVersion(stdout); err != nil {
		return exitFailure
	}
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
		return exitFailure
	}
	if parsed.printDetails && parsed.mode != modeQuick {
		if err := output.text(
			"Warning: --print=details applies only to --mode=quick and will be ignored.",
		); err != nil {
			writeFatal(errorOutput, "Cannot write print option warning: %v", err)
			return exitFailure
		}
	}

	cache, err := dependencies.refresher.refresh(ctx, errorOutput)
	if err != nil {
		writeFatal(errorOutput, "Cannot prepare dictionary cache: %v", err)
		return exitFailure
	}
	cachePath := cache.path
	if err := output.text("%s", dictionaryStatusMessage(cache.state)); err != nil {
		writeFatal(errorOutput, "Cannot write dictionary status: %v", err)
		return exitFailure
	}
	if err := output.text("Dictionary path: %s", dictionaryDisplayPath(cache.path, cache.home)); err != nil {
		writeFatal(errorOutput, "Cannot write dictionary path: %v", err)
		return exitFailure
	}
	encrypted, err := os.ReadFile(cachePath)
	if err != nil {
		writeFatal(errorOutput, "Cannot read dictionary cache %q: %v", cachePath, err)
		return exitFailure
	}
	password := ""
	if dependencies.getenv != nil {
		password = dependencies.getenv("CYBER_FERRET_PASSWORD")
	}
	plaintext, err := decryptDictionary(encrypted, password)
	if err != nil {
		writeFatal(errorOutput, "%v", err)
		return exitFailure
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
			return exitBadExpression
		}
		writeFatal(errorOutput, "Cannot load dictionary: %v", err)
		return exitFailure
	}
	if err := output.text("Dictionary version: %s", loaded.version); err != nil {
		writeFatal(errorOutput, "Cannot write dictionary version: %v", err)
		return exitFailure
	}

	now := dependencies.now
	if now == nil {
		now = time.Now
	}
	startedAt := now()
	if err := output.text("Scanning is in progress. Please wait."); err != nil {
		writeFatal(errorOutput, "Cannot write scanning progress: %v", err)
		return exitFailure
	}

	exclusions := loadExclusions(parsed.root, errorOutput)
	files, err := selectFiles(ctx, parsed.root, parsed.listPath)
	if err != nil {
		writeFatal(errorOutput, "%v", err)
		return exitFailure
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
		return exitFailure
	}
	if err := output.text("Total files scanned %d", result.scannedCount); err != nil {
		writeFatal(errorOutput, "Cannot write scanned file count: %v", err)
		return exitFailure
	}
	elapsed := now().Sub(startedAt).Seconds()
	if err := output.text("Scanning is finished in %.3f seconds.", elapsed); err != nil {
		writeFatal(errorOutput, "Cannot write scanning duration: %v", err)
		return exitFailure
	}
	if result.found {
		return exitFindings
	}
	return exitClean
}

func writeHelp(output *lineOutput) error {
	lines := []string{
		"Usage:",
		"  cfcli [--mode=quick|--mode=json] [--print=details] [--verbose=true|--verbose=false] " +
			"FOLDER_PATH [PATH_TO_LIST_OF_FILES]",
		"  cfcli exclude <add|remove> FOLDER_PATH JSON_OBJECT",
		"  cfcli exclude FOLDER_PATH JSON_OBJECT",
		"",
		"cfcli scans a Git repository for sensitive values using the CyberFerret dictionary.",
		"It reports non-allowed findings and can add reviewed findings to " +
			".qubership/grand-report.json.",
		"",
		"Before scanning:",
		"  Set CYBER_FERRET_PASSWORD to the dictionary decryption password.",
		"  Git must be available on PATH.",
		"",
		"Arguments and options:",
		"  FOLDER_PATH              Git repository root to scan or update.",
		"  PATH_TO_LIST_OF_FILES    Optional file with Git-relative paths to scan, one path per line.",
		"  --mode=json              Scan all selected files and print findings as JSON. " +
			"This is the default.",
		"  --mode=quick             Stop after the first non-allowed finding.",
		"  --print=details          In quick mode, print copy-ready exclude commands for the finding.",
		"  --verbose=true           Print each folder and file before it is scanned.",
		"  --verbose=false          Do not print per-file progress. This is the default.",
		"  add                      Ensure that the specified exclusion exists.",
		"  remove                   Ensure that the specified exclusion does not exist.",
		"  JSON_OBJECT              Exclusion target for FOLDER_PATH/.qubership/grand-report.json.",
		"                           Use type \"found\" with found and file, type \"file\" with file,",
		"                           or type \"folder\" with folder. Paths must be relative to FOLDER_PATH.",
		"                           The legacy exclude form adds a \"found\" target and remains supported.",
		"",
		"Use exclude only when you consider the detected value acceptable for that repository file.",
		"The exclude command does not refresh or decrypt the dictionary and does not require " +
			"CYBER_FERRET_PASSWORD.",
	}
	for _, line := range lines {
		if err := output.text("%s", line); err != nil {
			return err
		}
	}
	return nil
}

// writeFatal writes a message for a failure the caller exits on. It discards the write error:
// every caller already returns a nonzero exit code, and the only stream left to report a failed
// write on is the one that just failed.
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

// dictionaryDisplayPath renders path for a human reader: a path inside home becomes "~/" followed
// by the remainder, and anything else is cleaned and left as it is. The result always uses "/"
// separators, including on Windows.
func dictionaryDisplayPath(path, home string) string {
	relative, err := filepath.Rel(home, path)
	outsideHome := relative == ".." || strings.HasPrefix(relative, ".."+string(filepath.Separator))
	if err == nil && !filepath.IsAbs(relative) && !outsideHome {
		return "~/" + filepath.ToSlash(relative)
	}
	return filepath.ToSlash(filepath.Clean(path))
}
