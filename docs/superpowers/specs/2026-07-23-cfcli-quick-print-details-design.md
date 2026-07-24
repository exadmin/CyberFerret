# cfcli quick print details design

## Goal

Reduce the default quick-mode output to one exclusion hint while preserving access to the existing shell-specific
exclusion commands through `--print=details`.

## CLI behavior

`cfcli --mode=quick FOLDER_PATH` stops after the first found signature. It prints the JSON finding followed by:

```text
TEXT: To print exclusion commands, run cfcli with --mode=quick --print=details.
```

`cfcli --mode=quick --print=details FOLDER_PATH` also stops after the first found signature. It prints the JSON finding
followed by the existing POSIX, PowerShell, and cmd.exe exclusion commands in that order.

`cfcli --print=details FOLDER_PATH` uses the default JSON mode. Before scanning, it prints:

```text
TEXT: Warning: --print=details applies only to --mode=quick and will be ignored.
```

The scan then proceeds normally without printing exclusion commands. Unknown `--print` values are argument errors and
cause the application to print the error and usage help.

## Implementation

The option parser gains a print-detail setting that accepts only `--print=details`. The application emits the
non-quick warning after option parsing and before scan setup. The scanner receives the setting explicitly and chooses
between the short hint and the three existing exclusion commands when it handles the first quick-mode finding.

The help output and README document `--print=details` and its quick-mode restriction.

## Testing

Parser tests cover the valid option, an unknown value, and an option placed after positional arguments. Scanner tests
verify the exact hint and detailed command output while preserving immediate quick-mode termination. Pipeline tests
verify the non-quick warning, the absence of exclusion commands in JSON mode, and updated help output.
