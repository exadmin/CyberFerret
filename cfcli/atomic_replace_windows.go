//go:build windows

package main

import (
	"fmt"
	"syscall"
	"unsafe"
)

// Values of the Win32 MOVEFILE_* flags that MoveFileExW takes in dwFlags.
const (
	moveFileReplaceExisting = 0x1
	moveFileWriteThrough    = 0x8
)

var moveFileEx = syscall.NewLazyDLL("kernel32.dll").NewProc("MoveFileExW")

// replaceFile moves source onto destination with MoveFileExW, replacing an existing destination
// and returning once the change has reached the disk rather than the cache. Both paths must live
// on the same volume, because the call omits MOVEFILE_COPY_ALLOWED.
func replaceFile(source, destination string) error {
	sourcePointer, err := syscall.UTF16PtrFromString(source)
	if err != nil {
		return err
	}
	destinationPointer, err := syscall.UTF16PtrFromString(destination)
	if err != nil {
		return err
	}
	result, _, callErr := moveFileEx.Call(
		uintptr(unsafe.Pointer(sourcePointer)),
		uintptr(unsafe.Pointer(destinationPointer)),
		moveFileReplaceExisting|moveFileWriteThrough,
	)
	if result == 0 {
		return fmt.Errorf("MoveFileExW: %w", callErr)
	}
	return nil
}
