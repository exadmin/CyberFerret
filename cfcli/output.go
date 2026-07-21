package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"io"
	"sync"
)

type lineOutput struct {
	writer *bufio.Writer
	mu     sync.Mutex
}

func newLineOutput(writer io.Writer) *lineOutput {
	return &lineOutput{writer: bufio.NewWriter(writer)}
}

func (o *lineOutput) text(format string, args ...any) error {
	o.mu.Lock()
	defer o.mu.Unlock()

	if _, err := fmt.Fprintf(o.writer, "TEXT: "+format+"\n", args...); err != nil {
		return err
	}
	return o.writer.Flush()
}

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
