# Cyber Ferret, v2.x.x
[<img src="./docs/cyber-ferret.jpg">]()

Scans locally cloned Git-repository for different pre-defined signatures (supporting RegExp and other rules).
The main difference to other popular opensource similar scanners are:
* Ability to have centralized signatures dictionary
* Dictionary is published in encrypted state and ecrypted during use
* A native Go CLI and a JavaFX GUI
* Ability to manage exclusions per exact signature and file in secured manner

Other base features are supported:
* Ability to customize signatures to be found
* Ability to define signatures using RegExp
* Ability to define exclusions including wild-card expressions
* Windows, Linux, MacOS is supported


# High level usage flow
[<img src="./docs/highlevel-diag.png">]()

# Roles
* The signatures dictionary owner manages and encrypts the dictionary for distribution through public networks and
  services.
* Teams use CyberFerret and know the dictionary password without having to manage dictionary distribution or updates.

# Pre-requisites
* Install [JDK 21 or newer](https://jdk.java.net/21/)
* Install [JavaFX 21 or newer](https://gluonhq.com/products/javafx/)
* Install [Apache Maven 3.9](https://maven.apache.org/download.cgi)
* Install [Git](https://git-scm.com/downloads) and ensure `git` is available on `PATH`
* Setup M2_HOME, JAVA_HOME and PATH (add maven and java) System Variables as recommended for Java and Maven usage

The native `cfcli` command uses Git to select files for scanning. The JavaFX application delegates scanning and
dictionary management to this command.

# Scanning procedure
CyberFerret scans tracked files and untracked files that Git does not ignore. This includes newly created source files
that have not been added to Git yet.

Cyber Ferret skips files excluded by standard Git rules, including repository `.gitignore` files,
`.git/info/exclude`, and the user's global Git excludes file. Git must be available on `PATH` to scan a Git
repository. When you scan a directory that is not a Git repository, Cyber Ferret scans regular files without following
symbolic links to directories or entering `.git` directories.

Checked-out Git submodules are scanned recursively. Cyber Ferret does not follow symbolic links to directories, so a
tracked link cannot make a scan traverse an external repository. Symbolic links to regular files remain scannable.
On Unix systems, the scanner also preserves and scans file names that contain byte sequences that are not valid UTF-8.

## Example
[<img src="./docs/run-example.gif">]()

## Build from the command line

### JavaFX application

Run Maven from the repository root:

```shell
mvn -pl fx -am clean package
```

The application JAR is written to `fx/target/cyberferret-fx.jar`.

### Native CLI

Run Go from the `cfcli` directory:

```shell
go build -o cfcli .
```

On Windows, the output file is `cfcli.exe`. See the [cfcli reference](cfcli/README.md) for usage and runtime
requirements.

## Release new versions

Release the CLI and JavaFX application independently. CLI tags use the `cfcli-v<version>` format, and JavaFX tags use
the `fx-v<version>` format. A release tag must not already exist.

### Release the native CLI

1. Check out the commit to release and verify the CLI:

   ```shell
   cd cfcli
   go test -count=1 ./...
   go vet ./...
   cd ..
   ```

2. Create and push a tag such as `cfcli-v1.2.0`:

   ```shell
   git tag cfcli-v1.2.0
   git push origin cfcli-v1.2.0
   ```

The `Release CF CLI` workflow builds binaries for Windows, Linux, and macOS, then publishes them in a GitHub Release.
You can also run the workflow from the Actions tab and provide the tag that it should create.

### Release the JavaFX application

1. Set the release version in the root `pom.xml`. The `<revision>` value must match the tag without the `fx-v` prefix:

   ```xml
   <revision>2.2.0</revision>
   ```

2. Commit and push the version change, then verify the build from the repository root:

   ```shell
   mvn --batch-mode --no-transfer-progress clean package
   ```

3. Create and push the matching tag:

   ```shell
   git tag fx-v2.2.0
   git push origin fx-v2.2.0
   ```

The `Release CyberFerret FX` workflow builds platform-specific archives for Windows, Linux, and macOS. It publishes
the archives in a GitHub Release. Creating that release also starts the `Publish package to GitHub Packages` workflow.
You can run the release workflow manually from the Actions tab; the supplied tag must still match `<revision>`.

## Manage exclusions with the native CLI

Use `add` to ensure that an exclusion exists and `remove` to ensure that it does not exist:

```text
cfcli exclude <add|remove> FOLDER_PATH JSON_OBJECT
```

Both operations are idempotent. Repeating an operation succeeds without adding duplicates or failing when the target
is already absent. The original `cfcli exclude FOLDER_PATH JSON_OBJECT` form remains an alias for adding a `found`
target.

Exclude or restore one exact finding in one file:

```shell
cfcli exclude add /path/to/repository \
  '{"type":"found","found":"ci.noreply@example.com","file":"docs/notifications.md"}'
cfcli exclude remove /path/to/repository \
  '{"type":"found","found":"ci.noreply@example.com","file":"docs/notifications.md"}'
```

Exclude or restore a complete file:

```shell
cfcli exclude add /path/to/repository \
  '{"type":"file","file":"src/generated/App.java"}'
cfcli exclude remove /path/to/repository \
  '{"type":"file","file":"src/generated/App.java"}'
```

Exclude or restore a directory subtree:

```shell
cfcli exclude add /path/to/repository \
  '{"type":"folder","folder":"src/generated"}'
cfcli exclude remove /path/to/repository \
  '{"type":"folder","folder":"src/generated"}'
```

All target paths are relative to `FOLDER_PATH`. The command rejects absolute paths and paths that escape the repository.
It updates `.qubership/grand-report.json` atomically and leaves the file unchanged after any validation or parsing
error. See the [cfcli exclusion reference](cfcli/README.md#manage-exclusions) for hashing, normalization, output, and
exit-code details.

## Run the JavaFX application on Windows

Replace `${PATH_TO_JAVA_FX_SDK}` with the path to the JavaFX SDK:

```shell
java --module-path "${PATH_TO_JAVA_FX_SDK}\lib" --add-modules javafx.controls,javafx.web,javafx.graphics --enable-native-access=javafx.graphics -jar .\fx\target\cyberferret-fx.jar
```

You can also use `fx/src/shell/run-app.cmd`.

## Run the JavaFX application on Linux or macOS

Replace `$PATH_TO_JAVAFX_SDK` with the path to the JavaFX SDK:

```shell
java --module-path "$PATH_TO_JAVAFX_SDK/lib" --add-modules javafx.controls,javafx.web,javafx.graphics --enable-native-access=javafx.graphics -jar ./fx/target/cyberferret-fx.jar
```

You can also use `fx/src/shell/run-app.sh`.

## Run the JavaFX application in IntelliJ IDEA

Install JDK 21 and JavaFX 21 or newer. Create an Application run configuration and set these VM options:

```shell
--module-path "...\JDKs\javafx-sdk-21\lib" --add-modules javafx.controls,javafx.web,javafx.graphics --enable-native-access=javafx.graphics
```


# Dictionary format
## Dictionary example file
```properties
# It is just a java properties file with a key=value pairs per line
# Reserved key 'VERSION' is used for users notifications only, may be skipped
VERSION=1.1

# All key names may be in 3 formats
# KEY_NAME=VALUE - means the ferret will search for VALUE-string case-insensitive, the VALUE-string will be converted to RegExp pattern '\bVALUE\b'. Note: all spaces inside will be replaced with '\\s+', all special chars (&, -, +) will be escaped by '\\'
# KEY_NAME(regexp)=VALUE - means you have finally defined RegExp pattern, and it will be used as is
# KEY_NAME(allowed)=VALUE - means you have defined an exact string or a value with '*' wildcards that may be found during scanning, but must be treated as allowed. Each '*' matches one or more nonwhitespace characters. Actually no matter what key name will be used - the value is a global string.
# KEY_NAME(exclude-ext)=VALUE1,VALUE2,etc.. - list of file extentions to be ignored for the "KEY_NAME" signature
# Notes: all key names must be unique

Examples
SUB-DOMAIN(regexp)=\\w+\\.example-domain\\.com
SUB-DOMAIN-1(allowed)=test1.example-domain.com
SUB-DOMAIN-2(allowed)=test2.example-domain.com

# Emails
EMAIL(regexp)=([a-zA-Z0-9._-]+@[a-zA-Z]+\\.(?:com|ru|net|org|edu|gov|mil|int|us|uk|de|jp|in|test|localhost|invalid|example|arab|cn|ua)+)
EMAIL-1(allowed)=example@example.com
EMAIL-2(allowed)=test@mail.org
EMAIL-3(allowed)=*@example.com
EMAIL(exclude-ext)=exe,bin,ttf,zip

# Passwords or credentials
PASSW-001=password
PASSW-002=1234567890
PASSW-003=qwerty123

# IP Address
IP-ADDR(regexp)=((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)
IP-ADDR-1(allowed)=0.0.0.0
IP-ADDR-2(allowed)=127.0.0.1
```
