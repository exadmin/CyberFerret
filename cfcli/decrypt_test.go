package main

import (
	"encoding/base64"
	"strings"
	"testing"
)

func TestDecryptDictionaryMatchesJavaVector(t *testing.T) {
	const ciphertext = "nzn8A/OYyg6yMGSNTqYRzsRQ4G/x0BkR2x9W7Wqq8Kw="

	got, err := decryptDictionary([]byte(ciphertext), "test-password")

	if err != nil {
		t.Fatal(err)
	}
	if want := "VERSION=1.0\nTOKEN=secret\n"; string(got) != want {
		t.Fatalf("plaintext = %q, want %q", got, want)
	}
}

func TestDecryptDictionaryRejectsInvalidInputs(t *testing.T) {
	tests := []struct {
		name       string
		ciphertext []byte
		password   string
		want       string
	}{
		{name: "missing password", ciphertext: []byte("AA=="), want: "password"},
		{name: "invalid Base64", ciphertext: []byte("not base64"), password: "password", want: "Base64"},
		{name: "invalid block size", ciphertext: []byte(base64.StdEncoding.EncodeToString([]byte("short"))), password: "password", want: "block size"},
		{name: "invalid padding", ciphertext: []byte(base64.StdEncoding.EncodeToString(make([]byte, 16))), password: "password", want: "padding"},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			_, err := decryptDictionary(test.ciphertext, test.password)
			if err == nil || !strings.Contains(err.Error(), test.want) {
				t.Fatalf("decryptDictionary() error = %v, want message %q", err, test.want)
			}
		})
	}
}

func TestRemovePKCS7Padding(t *testing.T) {
	got, err := removePKCS7Padding([]byte{'d', 'a', 't', 'a', 4, 4, 4, 4}, 8)
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != "data" {
		t.Fatalf("unpadded = %q, want data", got)
	}
}

func TestDecryptDictionaryRejectsInvalidUTF8(t *testing.T) {
	ciphertext := encryptDictionaryForTest(t, []byte{0xff, 0xfe}, "test-password")

	_, err := decryptDictionary([]byte(ciphertext), "test-password")

	if err == nil || !strings.Contains(err.Error(), "UTF-8") {
		t.Fatalf("decryptDictionary() error = %v, want UTF-8 error", err)
	}
}
