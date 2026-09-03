package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"io"
	"strings"
	"sync"
)

// lineOutput writes cfcli's output protocol: one complete line per call,
// prefixed with TEXT: or JSON: and flushed before the call returns. Safe for
// concurrent use by multiple goroutines.
type lineOutput struct {
	writer *bufio.Writer
	mu     sync.Mutex
}

func newLineOutput(writer io.Writer) *lineOutput {
	return &lineOutput{writer: bufio.NewWriter(writer)}
}

// text writes one TEXT: line. format is a [fmt.Printf] format string, so data
// such as a file path or a matched value goes in an argument. Carriage returns
// and line feeds are escaped to preserve the line-oriented protocol.
func (o *lineOutput) text(format string, args ...any) error {
	message := fmt.Sprintf(format, args...)
	message = strings.NewReplacer("\r", `\r`, "\n", `\n`).Replace(message)

	o.mu.Lock()
	defer o.mu.Unlock()

	if _, err := fmt.Fprintf(o.writer, "TEXT: %s\n", message); err != nil {
		return err
	}
	return o.writer.Flush()
}

// json writes value as one JSON: line, encoded by [json.Marshal]. Nothing
// reaches the writer when value cannot be encoded.
func (o *lineOutput) json(value any) error {
	encoded, err := json.Marshal(value)
	if err != nil {
		return err
	}

	o.mu.Lock()
	defer o.mu.Unlock()
	if _, err := fmt.Fprintf(o.writer, "JSON: %s\n", encoded); err != nil {
		return err
	}
	return o.writer.Flush()
}
