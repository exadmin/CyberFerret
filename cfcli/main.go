package main

import (
	"context"
	"os"
)

func main() {
	os.Exit(int(run(context.Background(), os.Args[1:], os.Stdout, os.Stderr)))
}
