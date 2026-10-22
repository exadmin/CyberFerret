package main

import (
	"bytes"
	"context"
	"strings"
	"testing"
)

func TestSilentScan(t *testing.T) {
	for _, mode := range []string{"json", "quick"} {
		for _, scenario := range []string{"clean", "allowed", "excluded", "found", "warning", "error"} {
			t.Run(mode+"/"+scenario, func(t *testing.T) {
				root := initRepository(t)
				content := "safe"
				if scenario == "allowed" || scenario == "excluded" || scenario == "found" {
					content = "SECRET"
				}
				writeTestFile(t, root, "sample.txt", content)
				dictionary := "VERSION=1.0\nSECRET=SECRET\n"
				if scenario == "allowed" {
					dictionary += "SECRET(allowed)=SECRET\n"
				}
				if scenario == "excluded" {
					writeGrandReport(t, root, `{"exclusions":[{"t-hash":"`+testSHA256("SECRET")+
						`","f-hash":"`+testSHA256("sample.txt")+`"}]}`)
				}
				if scenario == "warning" {
					writeGrandReport(t, root, `{invalid`)
				}
				dependencies := testAppDependencies(t, dictionary, "test-password")
				dependencies.getenv = func(name string) string {
					if name == "CYBER_FERRET_SILENT_MODE" {
						return "true"
					}
					if scenario == "error" {
						return ""
					}
					return "test-password"
				}
				var stdout, stderr bytes.Buffer
				code := runWithDependencies(context.Background(), []string{"--mode=" + mode, "--verbose=true", root},
					&stdout, &stderr, dependencies)
				switch scenario {
				case "found":
					if code != exitFindings || !strings.Contains(stdout.String(), `"type":"found"`) {
						t.Fatalf("code = %d, stdout = %q, stderr = %q", code, stdout.String(), stderr.String())
					}
				case "warning", "error":
					if stderr.Len() == 0 || stdout.Len() != 0 || (scenario == "error" && code != exitFailure) {
						t.Fatalf("code = %d, stdout = %q, stderr = %q", code, stdout.String(), stderr.String())
					}
				default:
					if code != exitClean || stdout.Len() != 0 || stderr.Len() != 0 {
						t.Fatalf("code = %d, stdout = %q, stderr = %q", code, stdout.String(), stderr.String())
					}
				}
			})
		}
	}
}

func TestSilentModeRequiresExactTrue(t *testing.T) {
	for _, value := range []string{"", "false", "TRUE", "1"} {
		t.Run(value, func(t *testing.T) {
			dependencies := testAppDependencies(t, "VERSION=1.0\n", "test-password")
			getenv := dependencies.getenv
			dependencies.getenv = func(name string) string {
				if name == "CYBER_FERRET_SILENT_MODE" {
					return value
				}
				return getenv(name)
			}
			var stdout, stderr bytes.Buffer
			code := runWithDependencies(context.Background(), []string{initRepository(t)}, &stdout, &stderr, dependencies)
			if code != exitClean || !strings.Contains(stdout.String(), "Scanning is finished") {
				t.Fatalf("code = %d, stdout = %q, stderr = %q", code, stdout.String(), stderr.String())
			}
		})
	}
}
