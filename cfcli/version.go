package main

import (
	"fmt"
	"io"
)

const appVersion = "2.1.2"

func writeAppVersion(output io.Writer) error {
	_, err := fmt.Fprintf(output, "TEXT: CyberFerret CLI, version %s\n", appVersion)
	return err
}
