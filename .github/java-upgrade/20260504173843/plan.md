# Upgrade Plan: roadworks-v2x (20260504173843)

- **Generated**: 2026-05-04 17:43:00
- **HEAD Branch**: main
- **HEAD Commit ID**: N/A

## Available Tools

**JDKs**
- JDK 17.0.17: C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot\bin (current project JDK, used by step 2)
- JDK 25.0.1: C:\Program Files\Java\jdk-25\bin (target LTS, used by steps 1, 3, 4)
- JDK 25: C:\Users\Luis\.jdks\openjdk-25\bin (alternate target JDK)

**Build Tools**
- Maven 3.9.12: C:\ProgramData\chocolatey\lib\maven\apache-maven-3.9.12\bin
- Maven 4.0+: **<TO_BE_INSTALLED>** (required by step 1 for Java 25 compatibility)
- Maven Wrapper: not present

## Guidelines

> Note: You can add any specific guidelines or constraints for the upgrade process here if needed, bullet points are preferred.

## Options

- Working branch: appmod/java-upgrade-20260504173843
- Run tests before and after the upgrade: true

## Upgrade Goals

- Upgrade Java runtime to the latest LTS version: Java 25

## Technology Stack

| Technology/Dependency        | Current          | Min Compatible      | Why Incompatible                                    |
| --------------------------- | ---------------- | ------------------- | --------------------------------------------------- |
| Java                        | 17               | 25                  | User requested latest LTS; target compatibility     |
| Maven                       | 3.9.12           | 4.0.0               | Maven 3.9.x does not support Java 25 builds safely  |
| maven-compiler-plugin       | default/unknown  | 3.12.0              | Explicit plugin version required for JDK 25        |
| javax.annotation            | bundled w/ old JDK| javax.annotation-api:1.3.2 | Removed from JDK 11+; code uses `@Nonnull`    |
| org.eclipse.mosaic libraries| 25.2             | 25.2                | Current dependency version is likely compatible     |

## Derived Upgrades

- Upgrade Java from 17 to 25 because the user requested the latest LTS runtime.
- Upgrade Maven to 4.0+ because Java 25 requires a build tool that supports the new JDK.
- Add explicit `maven-compiler-plugin` 3.12.0 to ensure the project can compile cleanly with Java 25.
- Add `javax.annotation-api` because `javax.annotation.Nonnull` is used in source code and is no longer bundled with modern JDKs.
- Add `maven-surefire-plugin` 3.1.2 as a modern test plugin recommendation.

## Upgrade Steps

- Step 1: Setup Environment
  - **Rationale**: Java 25 is available, but the current Maven installation is 3.9.12 and is incompatible with Java 25.
  - **Changes to Make**:
    - Install Maven 4.0+ using `#appmod-install-maven`.
    - Keep JDK 17 available for baseline verification.
    - Verify `mvn -v` reports Maven 4.x and Java 25.
  - **Verification**: `mvn -version` with Java 25 path, expected Maven 4.0+ and JDK 25 output.

- Step 2: Setup Baseline
  - **Rationale**: Capture the current compile/test state on Java 17 before making upgrade changes.
  - **Changes to Make**:
    - Run baseline compile with the current project configuration on JDK 17.
    - Run baseline test execution for the module, even if no tests exist.
  - **Verification**: `mvn -q clean test-compile && mvn -q clean test` with JDK 17.

- Step 3: Upgrade Java configuration and compatibility dependencies
  - **Rationale**: Update project build settings to Java 25 and add the missing annotation API dependency.
  - **Changes to Make**:
    - Set `maven.compiler.source` and `maven.compiler.target` to `25`.
    - Add explicit `maven.compiler.release` property set to `25`.
    - Add explicit `maven-compiler-plugin` 3.12.0 and `maven-surefire-plugin` 3.1.2.
    - Add `javax.annotation-api:1.3.2` dependency to restore `javax.annotation.Nonnull`.
  - **Verification**: `mvn -q clean test-compile` with Java 25.

- Step 4: Final Validation
  - **Rationale**: Confirm the module compiles and the full Maven test lifecycle passes under Java 25.
  - **Changes to Make**:
    - Run a clean build and full test execution with Java 25.
    - Resolve any compatibility issues found during the final run.
  - **Verification**: `mvn -q clean test` with Java 25.

## Key Challenges

- **`javax.annotation.Nonnull` removal**
  - **Challenge**: `scenarios/Roadworks/application/src/main/java/pt/uminho/v2x/AvisoObraMessage.java:3` imports `javax.annotation.Nonnull`, which is no longer bundled with JDK 11+.
  - **Strategy**: Add `javax.annotation-api:1.3.2` and keep the existing annotation usage.

- **Build tool compatibility for Java 25**
  - **Challenge**: The current Maven installation is 3.9.12; Java 25 requires Maven 4.0+.
  - **Strategy**: Install Maven 4.0+ as part of environment setup.

- **No local tests in module**
  - **Challenge**: `scenarios/Roadworks/application` has no `src/test/java`, so validation is compile-centric.
  - **Strategy**: Use `mvn clean test` to verify plugin lifecycle and compile success; document the lack of test coverage.
