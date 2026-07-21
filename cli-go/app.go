package main

import (
	"context"
	"fmt"
	"io"
)

const usage = "usage: cli-go FOLDER_PATH [PATH_TO_LIST_OF_FILES]"

func run(ctx context.Context, args []string, stdout, stderr io.Writer) int {
	if len(args) < 1 || len(args) > 2 {
		fmt.Fprintln(stderr, usage)
		return 2
	}

	var listArg *string
	if len(args) == 2 {
		listArg = &args[1]
	}
	files, err := selectFiles(ctx, args[0], listArg)
	if err != nil {
		fmt.Fprintf(stderr, "error: %v\n", err)
		return 1
	}
	for _, path := range files {
		fmt.Fprintln(stdout, path)
	}
	return 0
}
