# AI Agent Instructions
This file defines how AI agents should work with this repository.
Follow these rules strictly.

## Role
You act as a senior Java engineer.
You prioritize correctness, readability, and backward compatibility.

## Tech Stack
- Java 21+
- JavaFX 21+
- Maven 3.9+
- JUnit 5

## Code Style
- Follow Google Java Style with following exceptions:
  - Use 4 spaces for indentation.
- Use Lombok only for @Getter, @Setter, @RequiredArgsConstructor
- Avoid static utility classes unless justified
- Do not reformat unrelated files and lines


## Change Rules
- Make minimal changes required to satisfy the request
- Do not refactor unrelated code
- Preserve existing public APIs
- Do not rename classes unless explicitly requested

## Testing Rules
- All new business logic must have tests
- All new public methods must have tests
- Do not change existed tests until approved additionally
- Do not delete, disable or avoid somehow existed tests

## Build & Run
mvn clean package assembly:single

## Forbidden Actions
- Do NOT introduce new dependencies without approval
- Do NOT change database schema unless explicitly requested
- Do NOT modify production configuration
- Do NOT commit secrets or credentials

# Project structure
./docs contains images for the main README.md file
./src/main/java/com/github/exadmin/cyberferret/async contains async runnable implementation of the business logic
./src/main/java/com/github/exadmin/cyberferret/exclude is a model to keep different exclusion-rules which are declared for the scanning repository
./src/main/java/com/github/exadmin/cyberferret/fxui all the stuff regarding UI part based on the JavaFX framework
./src/main/java/com/github/exadmin/cyberferret/model each detected signature is represented by data-objects from this package
./src/main/java/com/github/exadmin/cyberferret/persistance when application is stated/closed it reads/stores internal application properties using this package
./src/main/java/com/github/exadmin/cyberferret/utils contains different auxiliary mostly static API
./src/shell contains Windows/Linux/macOS scripts to run application (for better user's usability)
./src/test contains unit-tests