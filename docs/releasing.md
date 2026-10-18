# Releasing

A release is a git tag `vX.Y.Z` on `main`. The `Release` workflow builds that commit, signs the artifacts and
publishes `io.github.bzumik1:fio-api:X.Y.Z` to Maven Central, then creates the GitHub release with generated
notes and the jars attached. Nothing else has to be done by hand.

```
git tag v1.0.0
git push origin v1.0.0
```

The version in `gradle.properties` stays `-SNAPSHOT`; the workflow passes the tag's version with `-Pversion`.
After a release, bump `gradle.properties` to the next snapshot.

## One-time setup

1. **Maven Central account.** Sign up at https://central.sonatype.com with the GitHub account `bzumik1`. Add the
   namespace `io.github.bzumik1`; Central verifies a `io.github.<user>` namespace by asking for a public repository
   named after a verification key it shows, which can be deleted afterwards.
2. **Publishing token.** In Central, under the account, "Generate User Token". It gives a username and a password.
3. **Signing key.** Maven Central requires every artifact to be signed with a key published on a key server:
   ```
   gpg --quick-generate-key "Jakub Znamenáček <znamenacekjakub@gmail.com>" ed25519 sign 3y
   gpg --list-keys --keyid-format long            # note the key id
   gpg --keyserver keyserver.ubuntu.com --send-keys <key id>
   gpg --armor --export-secret-keys <key id>      # the whole block goes into the SIGNING_KEY secret
   ```
4. **Repository secrets** (Settings > Secrets and variables > Actions):

   | Secret | Value |
   |---|---|
   | `MAVEN_CENTRAL_USERNAME` | the token username from step 2 |
   | `MAVEN_CENTRAL_PASSWORD` | the token password from step 2 |
   | `SIGNING_KEY` | the ASCII-armoured secret key from step 3 |
   | `SIGNING_KEY_PASSWORD` | the passphrase of that key |

The workflow hands them to the build as `ORG_GRADLE_PROJECT_mavenCentralUsername`,
`ORG_GRADLE_PROJECT_mavenCentralPassword`, `ORG_GRADLE_PROJECT_signingInMemoryKey` and
`ORG_GRADLE_PROJECT_signingInMemoryKeyPassword`, which the
[gradle-maven-publish-plugin](https://github.com/vanniktech/gradle-maven-publish-plugin) reads.

## Checking a release locally

```
./gradlew publishToMavenLocal
ls ~/.m2/repository/io/github/bzumik1/fio-api/
```

This publishes the unsigned snapshot into the local Maven repository, so a project that depends on
`io.github.bzumik1:fio-api:1.0.0-SNAPSHOT` with `mavenLocal()` can try it before a release.

## Versioning

Semantic versioning. The public API is what `module-info.java` exports; a change that breaks it is a new major
version. Once 1.0.0 is out, the build will compare each change against the last release with japicmp.
