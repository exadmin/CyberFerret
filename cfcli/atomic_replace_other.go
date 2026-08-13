//go:build !windows

package main

import "os"

// replaceFile renames source onto destination, replacing an existing destination in one step.
// Both paths must live on the same filesystem, because [os.Rename] has no copy fallback. Windows
// builds get the MoveFileExW implementation instead.
func replaceFile(source, destination string) error {
	return os.Rename(source, destination)
}
