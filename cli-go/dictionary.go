package main

import (
	"bufio"
	"bytes"
	"fmt"
	"regexp"
	"strconv"
	"strings"
	"unicode/utf16"
)

type signature struct {
	key                string
	expression         *regexp.Regexp
	excludedExtensions map[string]struct{}
}

type dictionary struct {
	version         string
	signatures      []signature
	allowed         map[string]struct{}
	allowedPatterns []*regexp.Regexp
}

type regexpCompileError struct {
	key        string
	expression string
	cause      error
}

func (e *regexpCompileError) Error() string {
	return fmt.Sprintf("compile dictionary regexp %q for key %q: %v", e.expression, e.key, e.cause)
}

func (e *regexpCompileError) Unwrap() error {
	return e.cause
}

type dictionaryEntry struct {
	key   string
	value string
	live  bool
}

type signatureSpec struct {
	key        string
	expression string
}

func loadDictionary(plaintext []byte, output *lineOutput) (dictionary, error) {
	entries, err := parseDictionaryEntries(plaintext, output)
	if err != nil {
		return dictionary{}, err
	}

	result := dictionary{allowed: make(map[string]struct{})}
	exclusions := make(map[string]map[string]struct{})
	var signatures []signatureSpec
	for _, entry := range entries {
		if !entry.live {
			continue
		}
		switch {
		case strings.EqualFold(entry.key, "VERSION"):
			result.version = entry.value
		case strings.HasSuffix(entry.key, "(allowed)"):
			if strings.Contains(entry.value, "*") {
				compiled, err := compileAllowedPattern(entry.value)
				if err != nil {
					return dictionary{}, fmt.Errorf("compile allowed pattern %q: %w", entry.value, err)
				}
				result.allowedPatterns = append(result.allowedPatterns, compiled)
			} else {
				result.allowed[strings.ToLower(entry.value)] = struct{}{}
			}
		case strings.HasSuffix(entry.key, "(exclude-ext)"):
			key := strings.TrimSuffix(entry.key, "(exclude-ext)")
			if key == "" {
				return dictionary{}, fmt.Errorf("dictionary exclusion key is empty")
			}
			extensions := make(map[string]struct{})
			for _, item := range strings.Split(entry.value, ",") {
				extension := strings.ToLower(strings.TrimPrefix(strings.TrimSpace(item), "."))
				if extension != "" {
					extensions[extension] = struct{}{}
				}
			}
			exclusions[key] = extensions
		case strings.HasSuffix(entry.key, "(regexp)"):
			key := strings.TrimSuffix(entry.key, "(regexp)")
			if key == "" {
				return dictionary{}, fmt.Errorf("dictionary regexp key is empty")
			}
			signatures = append(signatures, signatureSpec{key: key, expression: entry.value})
		case strings.ContainsAny(entry.key, "()"):
			return dictionary{}, fmt.Errorf("unsupported dictionary key suffix in %q", entry.key)
		default:
			signatures = append(signatures, signatureSpec{key: entry.key, expression: literalExpression(entry.value)})
		}
	}

	for _, spec := range signatures {
		compiled, err := regexp.Compile("(?is)" + spec.expression)
		if err != nil {
			return dictionary{}, &regexpCompileError{key: spec.key, expression: spec.expression, cause: err}
		}
		result.signatures = append(result.signatures, signature{
			key:                spec.key,
			expression:         compiled,
			excludedExtensions: exclusions[spec.key],
		})
	}
	return result, nil
}

func compileAllowedPattern(value string) (*regexp.Regexp, error) {
	parts := strings.Split(value, "*")
	for index := range parts {
		parts[index] = regexp.QuoteMeta(parts[index])
	}
	return regexp.Compile(`(?i)^` + strings.Join(parts, `\S+`) + `\z`)
}

func (d dictionary) isAllowed(exact string) bool {
	if _, allowed := d.allowed[strings.ToLower(exact)]; allowed {
		return true
	}
	for _, pattern := range d.allowedPatterns {
		if pattern.MatchString(exact) {
			return true
		}
	}
	return false
}

func parseDictionaryEntries(plaintext []byte, output *lineOutput) ([]dictionaryEntry, error) {
	entries := make([]dictionaryEntry, 0)
	positions := make(map[string]int)
	scanner := bufio.NewScanner(bytes.NewReader(plaintext))
	scanner.Buffer(make([]byte, 64*1024), 1024*1024)
	lineNumber := 0
	for scanner.Scan() {
		lineNumber++
		line := strings.TrimSuffix(scanner.Text(), "\r")
		trimmed := strings.TrimSpace(line)
		if trimmed == "" || strings.HasPrefix(trimmed, "#") {
			continue
		}
		separator := strings.IndexByte(line, '=')
		if separator < 0 {
			return nil, fmt.Errorf("dictionary line %d has no '=' separator", lineNumber)
		}
		key, err := unescapeProperty(strings.TrimSpace(line[:separator]))
		if err != nil {
			return nil, fmt.Errorf("decode dictionary key on line %d: %w", lineNumber, err)
		}
		if key == "" {
			return nil, fmt.Errorf("dictionary line %d has an empty key", lineNumber)
		}
		value, err := unescapeProperty(line[separator+1:])
		if err != nil {
			return nil, fmt.Errorf("decode dictionary value on line %d: %w", lineNumber, err)
		}
		if previous, duplicate := positions[key]; duplicate {
			entries[previous].live = false
			if err := output.text("Duplicate dictionary key %q; using last value", key); err != nil {
				return nil, fmt.Errorf("write duplicate-key message: %w", err)
			}
		}
		positions[key] = len(entries)
		entries = append(entries, dictionaryEntry{key: key, value: value, live: true})
	}
	if err := scanner.Err(); err != nil {
		return nil, fmt.Errorf("read dictionary: %w", err)
	}
	return entries, nil
}

func unescapeProperty(value string) (string, error) {
	var result strings.Builder
	for index := 0; index < len(value); index++ {
		if value[index] != '\\' {
			result.WriteByte(value[index])
			continue
		}
		index++
		if index >= len(value) {
			result.WriteByte('\\')
			break
		}
		switch value[index] {
		case 't':
			result.WriteByte('\t')
		case 'n':
			result.WriteByte('\n')
		case 'r':
			result.WriteByte('\r')
		case 'f':
			result.WriteByte('\f')
		case 'u':
			first, next, err := parseUnicodePropertyEscape(value, index)
			if err != nil {
				return "", err
			}
			index = next
			if utf16.IsSurrogate(first) && first >= 0xD800 && first <= 0xDBFF && index+6 < len(value) && value[index+1:index+3] == `\u` {
				second, secondNext, secondErr := parseUnicodePropertyEscape(value, index+2)
				if secondErr == nil && second >= 0xDC00 && second <= 0xDFFF {
					result.WriteRune(utf16.DecodeRune(first, second))
					index = secondNext
					continue
				}
			}
			result.WriteRune(first)
		default:
			result.WriteByte(value[index])
		}
	}
	return result.String(), nil
}

func parseUnicodePropertyEscape(value string, markerIndex int) (rune, int, error) {
	if markerIndex+4 >= len(value) {
		return 0, markerIndex, fmt.Errorf("incomplete Unicode escape")
	}
	encoded := value[markerIndex+1 : markerIndex+5]
	parsed, err := strconv.ParseUint(encoded, 16, 16)
	if err != nil {
		return 0, markerIndex, fmt.Errorf("invalid Unicode escape \\u%s", encoded)
	}
	return rune(parsed), markerIndex + 4, nil
}

func literalExpression(value string) string {
	parts := strings.Split(value, " ")
	for index := range parts {
		parts[index] = regexp.QuoteMeta(parts[index])
	}
	return `\b` + strings.Join(parts, `\s+`) + `\b`
}
