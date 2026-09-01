package main

import (
	"fmt"
	"io"
)

const appVersion = "2.1.1"

func writeAppVersion(output io.Writer) error {
	_, err := fmt.Fprintf(output, "TXT: CyberFerret CLI, version %s\n", appVersion)
	return err
}
