# FX CLI settings design

## Goal

Replace the dictionary selection controls with the runtime settings needed by the CF CLI scanner. Keep the online
dictionary and repository sections visible at all times.

## User interface

Remove the `Offline Dictionary` pane from the analyzer tab. Display `Online Dictionary` and `Repository` as separate,
expanded `TitledPane` controls with collapsing disabled.

The `Online Dictionary` pane contains two rows:

1. `Password` is a non-editable `PasswordField`. Its value comes only from the `CYBER_FERRET_PASSWORD` environment
   variable. The application does not save the password in `app.properties`.
2. `CF CLI executable` is an editable path field with a file chooser. The chooser accepts an executable file. The
   value is stored as `cfcli.path` in `app.properties` when the application closes and restored at startup.

An empty CF CLI path means `cfcli`, allowing the operating system to resolve the executable through `PATH`. An
explicit path must identify an existing regular file before scanning starts.

## Scanner integration

`CfCliScanner` receives the configured executable as a constructor argument and uses it as the first process command
element. The remaining command stays unchanged:

```text
<executable> --mode=json --verbose=true <repository>
```

The UI displays a validation error and does not start scanning when an explicit executable path is invalid. Process
startup errors, including a missing `cfcli` on `PATH`, continue through the existing asynchronous error channel.

## Persistence and compatibility

Add the string property `cfcli.path` to `PersistentPropertiesManager`, with an empty default. Existing application
property files remain valid.

Remove the persistent password property. Unknown legacy `dictionary.password` entries are ignored by the existing
loader with a warning. Dictionary download and decryption code remains present, but the removed pane no longer exposes
its previous controls.

## Tests

Add tests that verify:

- `cfcli.path` loads from and saves to the application properties file;
- the environment password is not registered or written to the properties file;
- `CfCliScanner` uses the configured executable as the first command element;
- the default executable is `cfcli` when the configured path is blank;
- explicit executable validation accepts regular files and rejects missing paths and directories.

Run the focused FX tests followed by `mvn clean package assembly:single`.
