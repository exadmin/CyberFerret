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

func parseOptions(args []string) (options, error) {
	parsed := options{mode: modeJSON}

parseLeadingOptions:
	for len(args) > 0 {
		switch {
		case strings.HasPrefix(args[0], "--mode="):
			parsed.mode = scanMode(strings.TrimPrefix(args[0], "--mode="))
			if parsed.mode != modeQuick && parsed.mode != modeJSON {
				return options{}, fmt.Errorf("invalid mode %q; %s", parsed.mode, usage)
			}
		case strings.HasPrefix(args[0], "--verbose="):
			value := strings.TrimPrefix(args[0], "--verbose=")
			switch value {
			case "true":
				parsed.verbose = true
			case "false":
				parsed.verbose = false
			default:
				return options{}, fmt.Errorf("invalid verbose value %q; %s", value, usage)
			}
		default:
			break parseLeadingOptions
		}
		args = args[1:]
	}

	if len(args) < 1 || len(args) > 2 {
		return options{}, fmt.Errorf("%s", usage)
	}
	for _, arg := range args {
		if strings.HasPrefix(arg, "--mode=") || strings.HasPrefix(arg, "--verbose=") {
			return options{}, fmt.Errorf("options must precede positional arguments; %s", usage)
		}
	}
	parsed.root = args[0]
	if len(args) == 2 {
		parsed.listPath = &args[1]
	}
	return parsed, nil
}
