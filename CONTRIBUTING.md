# Contributing

Thanks for helping improve `redisson-streams-starter`. Follow the checklist below to wire local credentials and publish artifacts safely.

## Local setup

1. Create (or update) a **local-only** `gradle.properties` in the project root.
2. Copy the required property keys and fill in your private values locally (do not commit secrets).
   ```properties
   mavenCentralUsername=
   mavenCentralPassword=
   signing.keyId=
   signing.password=
   signingKey=
   gpr.user=
   gpr.token=
   ```

## Publishing

- **Maven Central**  
  Bump the project `version` in `build.gradle.kts`, then run:
  ```bash
  ./gradlew publishToMavenCentral
  ```

- **GitHub Packages**  
  Use the GitHub Packages publication task:
  ```bash
  ./gradlew publishGithubPublicationToGitHubPackagesRepository
  ```
