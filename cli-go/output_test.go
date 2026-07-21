package main

import (
	"bytes"
	"strings"
	"testing"
)

func TestLineOutputWritesAndFlushesText(t *testing.T) {
	var destination bytes.Buffer
	output := newLineOutput(&destination)

	if err := output.text("Dictionary version: %s", "1.35"); err != nil {
		t.Fatal(err)
	}

	if got, want := destination.String(), "TEXT: Dictionary version: 1.35\n"; got != want {
		t.Fatalf("output = %q, want %q", got, want)
	}
}

func TestLineOutputWritesAndFlushesJSON(t *testing.T) {
	var destination bytes.Buffer
	output := newLineOutput(&destination)

	if err := output.json(map[string]string{"key": "TOKEN"}); err != nil {
		t.Fatal(err)
	}

	if got, want := destination.String(), "JSON: {\"key\":\"TOKEN\"}\n"; got != want {
		t.Fatalf("output = %q, want %q", got, want)
	}
	if strings.Contains(destination.String(), "\n ") {
		t.Fatalf("JSON output must remain on one line: %q", destination.String())
	}
}
