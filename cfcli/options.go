package main

import (
	"fmt"
	"strings"
)

type scanMode string

const (
	modeQuick scanMode = "quick"
	modeJSON  scanMode = "json"
)

type options struct {
	mode     scanMode
	verbose  bool
	root     string
	listPath *string
}

type usageError struct{}

func (e *usageError) Error() string {
	return "invalid argument count"
}

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
		if strings.HasPrefix(arg, "--mode=") || strings.HasPrefix(arg, "--verbose=") {
			return options{}, fmt.Errorf("options must precede positional arguments")
		}
	}
	parsed.root = args[0]
	if len(args) == 2 {
		parsed.listPath = &args[1]
	}
	return parsed, nil
}
