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
	root     string
	listPath *string
}

func parseOptions(args []string) (options, error) {
	parsed := options{mode: modeJSON}
	if len(args) > 0 && strings.HasPrefix(args[0], "--mode=") {
		parsed.mode = scanMode(strings.TrimPrefix(args[0], "--mode="))
		if parsed.mode != modeQuick && parsed.mode != modeJSON {
			return options{}, fmt.Errorf("invalid mode %q; %s", parsed.mode, usage)
		}
		args = args[1:]
	}

	if len(args) < 1 || len(args) > 2 {
		return options{}, fmt.Errorf("%s", usage)
	}
	for _, arg := range args {
		if strings.HasPrefix(arg, "--mode=") {
			return options{}, fmt.Errorf("--mode must precede positional arguments; %s", usage)
		}
	}
	parsed.root = args[0]
	if len(args) == 2 {
		parsed.listPath = &args[1]
	}
	return parsed, nil
}
