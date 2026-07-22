package main

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"encoding/binary"
	"fmt"
	"hash"
	"strings"
	"unicode/utf8"
)

var dictionaryIV = []byte{0, 2, 3, 4, 5, 4, 3, 2, 1, 0, 1, 2, 3, 4, 5, 0}

func decryptDictionary(ciphertext []byte, password string) ([]byte, error) {
	if password == "" {
		return nil, fmt.Errorf("Dictionary password environment variable CYBER_FERRET_PASSWORD is not set")
	}
	decoded, err := base64.StdEncoding.DecodeString(strings.TrimSpace(string(ciphertext)))
	if err != nil {
		return nil, fmt.Errorf("decode dictionary Base64: %w", err)
	}
	if len(decoded) == 0 || len(decoded)%aes.BlockSize != 0 {
		return nil, fmt.Errorf("encrypted dictionary length must be a positive multiple of AES block size")
	}

	key := deriveKey([]byte(password), []byte("bsd87918hediu"), 65536, 32)
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, fmt.Errorf("create AES cipher: %w", err)
	}
	plaintext := make([]byte, len(decoded))
	cipher.NewCBCDecrypter(block, dictionaryIV).CryptBlocks(plaintext, decoded)
	plaintext, err = removePKCS7Padding(plaintext, aes.BlockSize)
	if err != nil {
		return nil, err
	}
	if !utf8.Valid(plaintext) {
		return nil, fmt.Errorf("decrypted dictionary is not valid UTF-8")
	}
	return append([]byte(nil), plaintext...), nil
}

func deriveKey(password, salt []byte, iterations, keyLength int) []byte {
	return pbkdf2(password, salt, iterations, keyLength, sha256.New)
}

func pbkdf2(password, salt []byte, iterations, keyLength int, newHash func() hash.Hash) []byte {
	hashLength := newHash().Size()
	blocks := (keyLength + hashLength - 1) / hashLength
	derived := make([]byte, 0, blocks*hashLength)
	counter := make([]byte, 4)

	for blockIndex := 1; blockIndex <= blocks; blockIndex++ {
		binary.BigEndian.PutUint32(counter, uint32(blockIndex))
		mac := hmac.New(newHash, password)
		_, _ = mac.Write(salt)
		_, _ = mac.Write(counter)
		previous := mac.Sum(nil)
		combined := append([]byte(nil), previous...)
		for iteration := 1; iteration < iterations; iteration++ {
			mac = hmac.New(newHash, password)
			_, _ = mac.Write(previous)
			previous = mac.Sum(nil)
			for index := range combined {
				combined[index] ^= previous[index]
			}
		}
		derived = append(derived, combined...)
	}
	return derived[:keyLength]
}

func removePKCS7Padding(plaintext []byte, blockSize int) ([]byte, error) {
	if len(plaintext) == 0 {
		return nil, fmt.Errorf("invalid PKCS padding: plaintext is empty")
	}
	paddingLength := int(plaintext[len(plaintext)-1])
	if paddingLength == 0 || paddingLength > blockSize || paddingLength > len(plaintext) {
		return nil, fmt.Errorf("invalid PKCS padding length %d", paddingLength)
	}
	for _, value := range plaintext[len(plaintext)-paddingLength:] {
		if int(value) != paddingLength {
			return nil, fmt.Errorf("invalid PKCS padding bytes")
		}
	}
	return plaintext[:len(plaintext)-paddingLength], nil
}
