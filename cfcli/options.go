package main

import (
	"fmt"
	"strings"
)

// scanMode selects how far a scan runs and what it reports. modeQuick stops at
// the first finding that is neither allowed nor excluded; modeJSON scans every
// file and reports allowed and excluded matches alongside the findings.
// parseOptions accepts no other value.
type scanMode string

const (
	modeQuick scanMode = "quick"
	modeJSON  scanMode = "json"
)

// options holds a parsed command line. listPath is nil unless a second
// positional argument named a file listing the paths to scan. printDetails
// takes effect only in modeQuick; [runWithDependencies] warns when another mode
// is in force.
type options struct {
	mode         scanMode
	printDetails bool
	verbose      bool
	root         string
	listPath     *string
}

// usageError reports a positional argument count other than the one or two
// parseOptions accepts. [runWithDependencies] matches it with errors.As and
// answers with the help text alone, so its message never reaches the user.
type usageError struct{}

func (e *usageError) Error() string {
	return "invalid argument count"
}

// parseOptions reads the leading --mode, --print, and --verbose flags, then the
// positional arguments: the root to scan and, optionally, a file listing the
// paths to scan. The mode defaults to modeJSON.
//
// The flags may appear in any order but not after a positional argument, and an
// unrecognized flag value is rejected. A positional count other than one or two
// returns a [usageError].
func parseOptions(args []string) (options, error) {
	parsed := options{mode: modeJSON}

parseLeadingOptions:
	for len(args) > 0 {
		switch {
		case strings.HasPrefix(args[0], "--mode="):
			parsed.mode = scanMode(strings.TrimPrefix(args[0], "--mode="))
			if parsed.mode != modeQuick && parsed.mode != modeJSON {
				return options{}, fmt.Errorf("invalid mode %q", parsed.mode)
			}
		case strings.HasPrefix(args[0], "--print="):
			value := strings.TrimPrefix(args[0], "--print=")
			if value != "details" {
				return options{}, fmt.Errorf("invalid print value %q", value)
			}
			parsed.printDetails = true
		case strings.HasPrefix(args[0], "--verbose="):
			value := strings.TrimPrefix(args[0], "--verbose=")
			switch value {
			case "true":
				parsed.verbose = true
			case "false":
				parsed.verbose = false
			default:
				return options{}, fmt.Errorf("invalid verbose value %q", value)
			}
		default:
			break parseLeadingOptions
		}
		args = args[1:]
	}

	if len(args) < 1 || len(args) > 2 {
		return options{}, &usageError{}
	}
	for _, arg := range args {
		if strings.HasPrefix(arg, "--mode=") ||
			strings.HasPrefix(arg, "--print=") ||
			strings.HasPrefix(arg, "--verbose=") {
			return options{}, fmt.Errorf("options must precede positional arguments")
		}
	}
	parsed.root = args[0]
	if len(args) == 2 {
		parsed.listPath = &args[1]
	}
	return parsed, nil
}
