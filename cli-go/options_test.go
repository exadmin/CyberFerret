package main

import "testing"

func TestParseOptions(t *testing.T) {
	tests := []struct {
		name     string
		args     []string
		wantMode scanMode
		wantRoot string
		wantList string
		wantErr  bool
	}{
		{name: "default JSON", args: []string{"root"}, wantMode: modeJSON, wantRoot: "root"},
		{name: "default JSON with list", args: []string{"root", "list"}, wantMode: modeJSON, wantRoot: "root", wantList: "list"},
		{name: "quick", args: []string{"--mode=quick", "root"}, wantMode: modeQuick, wantRoot: "root"},
		{name: "explicit JSON", args: []string{"--mode=json", "root", "list"}, wantMode: modeJSON, wantRoot: "root", wantList: "list"},
		{name: "invalid mode", args: []string{"--mode=bad", "root"}, wantErr: true},
		{name: "missing root", args: []string{"--mode=quick"}, wantErr: true},
		{name: "too many positional arguments", args: []string{"root", "list", "extra"}, wantErr: true},
		{name: "mode after root", args: []string{"root", "--mode=quick"}, wantErr: true},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			got, err := parseOptions(test.args)
			if test.wantErr {
				if err == nil {
					t.Fatal("parseOptions() error = nil, want error")
				}
				return
			}
			if err != nil {
				t.Fatal(err)
			}
			if got.mode != test.wantMode || got.root != test.wantRoot {
				t.Fatalf("parseOptions() = %#v, want mode %q and root %q", got, test.wantMode, test.wantRoot)
			}
			if test.wantList == "" {
				if got.listPath != nil {
					t.Fatalf("listPath = %q, want nil", *got.listPath)
				}
			} else if got.listPath == nil || *got.listPath != test.wantList {
				t.Fatalf("listPath = %v, want %q", got.listPath, test.wantList)
			}
		})
	}
}
