package main

// exitStatus is the process status one cfcli invocation ends with. [run] returns it and main
// passes it to os.Exit, so the values below are a published interface: they are what a script
// calling cfcli branches on, and README.md documents the same four.
type exitStatus int

const (
	// exitClean reports that the scan found nothing, or that the exclude subcommand succeeded.
	exitClean exitStatus = 0
	// exitFailure reports any failure the other three do not name: bad arguments, the dictionary,
	// decryption, scanning, an exclusion update, or a write.
	exitFailure exitStatus = 1
	// exitFindings reports at least one finding that no allow rule or exclusion covered.
	exitFindings exitStatus = 2
	// exitBadExpression reports a dictionary expression that does not compile under Go's RE2 engine.
	exitBadExpression exitStatus = 3
)
