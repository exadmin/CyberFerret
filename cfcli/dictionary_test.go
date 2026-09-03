package main

import (
	"bytes"
	"errors"
	"regexp"
	"strings"
	"testing"
)

func TestLoadDictionaryParsesSupportedEntries(t *testing.T) {
	plaintext := []byte(`
        # comment
VERSION=1.35
LITERAL=alpha&beta two
MULTILINE(regexp)=BEGIN.*END
SAFE(allowed)=Token123
LITERAL(exclude-ext)= .EXE, bin ,
`)
	var messages bytes.Buffer

	got, err := loadDictionary(plaintext, newLineOutput(&messages))

	if err != nil {
		t.Fatal(err)
	}
	if got.version != "1.35" {
		t.Fatalf("version = %q, want 1.35", got.version)
	}
	if len(got.signatures) != 2 || got.signatures[0].key != "LITERAL" || got.signatures[1].key != "MULTILINE" {
		t.Fatalf("signatures = %#v, want ordered LITERAL and MULTILINE", got.signatures)
	}
	if !got.signatures[0].expression.MatchString("ALPHA&BETA\tTWO") {
		t.Fatal("literal signature must escape punctuation, allow whitespace, and ignore case")
	}
	if !got.signatures[1].expression.MatchString("begin\nanything\nend") {
		t.Fatal("regexp signature must use case-insensitive dot-all matching")
	}
	if _, ok := got.allowed["token123"]; !ok {
		t.Fatalf("allowed = %#v, want lowercase token123", got.allowed)
	}
	for _, extension := range []string{"exe", "bin"} {
		if _, ok := got.signatures[0].excludedExtensions[extension]; !ok {
			t.Fatalf("excluded extensions = %#v, want %q", got.signatures[0].excludedExtensions, extension)
		}
	}
	if messages.Len() != 0 {
		t.Fatalf("messages = %q, want empty", messages.String())
	}
}

func TestLoadDictionaryReportsDuplicateAndUsesLastValue(t *testing.T) {
	plaintext := []byte("KEY=first\nOTHER=value\nKEY=last\n")
	var messages bytes.Buffer

	got, err := loadDictionary(plaintext, newLineOutput(&messages))

	if err != nil {
		t.Fatal(err)
	}
	if len(got.signatures) != 2 || got.signatures[0].key != "OTHER" || got.signatures[1].key != "KEY" {
		t.Fatalf("signature order = %#v, want OTHER then final KEY", got.signatures)
	}
	if !got.signatures[1].expression.MatchString("last") || got.signatures[1].expression.MatchString("first") {
		t.Fatal("duplicate key must retain only its last value")
	}
	if want := `TEXT: Duplicate dictionary key "KEY"; using last value` + "\n"; messages.String() != want {
		t.Fatalf("messages = %q, want %q", messages.String(), want)
	}
}

func TestLoadDictionaryRejectsMalformedEntries(t *testing.T) {
	tests := []string{
		"missing separator",
		"=empty-key",
		"KEY(unknown)=value",
	}
	for _, plaintext := range tests {
		t.Run(plaintext, func(t *testing.T) {
			_, err := loadDictionary([]byte(plaintext), newLineOutput(&bytes.Buffer{}))
			if err == nil {
				t.Fatal("loadDictionary() error = nil, want error")
			}
		})
	}
}

func TestLoadDictionaryRejectsEmptySignatureValues(t *testing.T) {
	tests := []struct {
		name        string
		plaintext   string
		wantMessage string
	}{
		{
			name:        "regexp",
			plaintext:   "EMPTY(regexp)=\n",
			wantMessage: `dictionary regexp value for key "EMPTY" is empty`,
		},
		{
			name:        "literal",
			plaintext:   "EMPTY=\n",
			wantMessage: `dictionary literal value for key "EMPTY" is empty`,
		},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			_, err := loadDictionary([]byte(test.plaintext), newLineOutput(&bytes.Buffer{}))

			if err == nil || err.Error() != test.wantMessage {
				t.Fatalf("loadDictionary() error = %v, want %q", err, test.wantMessage)
			}
		})
	}
}

func TestLoadDictionaryReturnsTypedRegexpError(t *testing.T) {
	_, err := loadDictionary([]byte("LOOKBEHIND(regexp)=(?<=token)value\n"), newLineOutput(&bytes.Buffer{}))

	var compileError *regexpCompileError
	if !errors.As(err, &compileError) {
		t.Fatalf("loadDictionary() error = %v, want *regexpCompileError", err)
	}
	if compileError.key != "LOOKBEHIND" || !strings.Contains(compileError.Error(), "(?<=token)value") {
		t.Fatalf("compile error = %#v", compileError)
	}
}

func TestLoadDictionaryDecodesJavaPropertyEscapes(t *testing.T) {
	plaintext := []byte(`BOUNDARY(regexp)=\\bword\\b
UNICODE=prefix\u0020value
`)

	got, err := loadDictionary(plaintext, newLineOutput(&bytes.Buffer{}))

	if err != nil {
		t.Fatal(err)
	}
	if !got.signatures[0].expression.MatchString("word") || got.signatures[0].expression.MatchString(`\bword\b`) {
		t.Fatal("regexp value did not decode doubled Java property backslashes")
	}
	if !got.signatures[1].expression.MatchString("PREFIX\tVALUE") {
		t.Fatal("literal value did not decode Java Unicode escape")
	}
}

func TestLoadDictionaryMatchesAllowedWildcard(t *testing.T) {
	domain := "example" + ".com"
	loaded, err := loadDictionary(
		[]byte("EMAIL(allowed)=*@example.com\nEXACT(allowed)=Token123\n"),
		newLineOutput(&bytes.Buffer{}),
	)
	if err != nil {
		t.Fatal(err)
	}

	for _, value := range []string{
		"john.doe@" + domain,
		"user-name@" + strings.ToUpper(domain),
		"user+tag@" + domain,
		"TOKEN123",
	} {
		if !loaded.isAllowed(value) {
			t.Errorf("isAllowed(%q) = false, want true", value)
		}
	}
	for _, value := range []string{
		"@" + domain,
		"first last@" + domain,
		"user@" + domain + " trailing",
		"user@example" + "Xcom",
	} {
		if loaded.isAllowed(value) {
			t.Errorf("isAllowed(%q) = true, want false", value)
		}
	}
}

func TestLiteralExpressionEscapesEveryLiteralSpace(t *testing.T) {
	expression := literalExpression("a  b+c")

	compiled, err := regexp.Compile("(?is)" + expression)
	if err != nil {
		t.Fatal(err)
	}
	if !compiled.MatchString("A\t  B+C") {
		t.Fatalf("expression %q does not match flexible whitespace and literal plus", expression)
	}
}
