# Releasing

A release is a git tag `vX.Y.Z` on `main`. The `Release` workflow checks the tag, runs the full build on Java 17,
21 and 25, signs the artifacts, publishes `io.github.bzumik1:fio-api:X.Y.Z` to Maven Central and creates the
GitHub release with generated notes and the jars attached. Nothing else has to be done by hand.

```
git switch main && git pull
git tag v1.0.0
git push origin v1.0.0
```

Before tagging, move the `Unreleased` section of [CHANGELOG.md](../CHANGELOG.md) under the new version.

The version in `gradle.properties` stays `-SNAPSHOT`; the workflow passes the tag's version with `-Pversion`.
After a release, bump `gradle.properties` to the next snapshot.

## One-time setup

1. **Maven Central account.** Sign in at https://central.sonatype.com with the GitHub account `bzumik1`. Central
   then verifies the namespace `io.github.bzumik1` by itself, because the GitHub login proves the account; the
   namespace appears under "Namespaces" as verified. (Only a namespace that does not match the login, such as a
   custom domain, needs the manual verification with a DNS record or a repository.)
2. **Publishing token.** In Central, under the account, "Generate User Token". It gives a username and a password.
3. **Signing key.** Maven Central requires every artifact to be signed with a key published on a key server:
   ```
   gpg --quick-generate-key "Jakub Znamenáček <znamenacekjakub@gmail.com>" ed25519 sign 3y
   gpg --list-keys --keyid-format long            # note the key id
   gpg --keyserver keyserver.ubuntu.com --send-keys <key id>
   gpg --armor --export-secret-keys <key id>      # the whole block goes into the SIGNING_KEY secret
   ```
   The key expires after three years. Before that, extend it (`gpg --quick-set-expire <fingerprint> 3y`), send
   it to the key server again and replace the `SIGNING_KEY` secret; a release signed with an expired key is
   refused by Central.
4. **Repository secrets** (Settings > Secrets and variables > Actions):

   | Secret | Value |
   |---|---|
   | `MAVEN_CENTRAL_USERNAME` | the token username from step 2 |
   | `MAVEN_CENTRAL_PASSWORD` | the token password from step 2 |
   | `SIGNING_KEY` | the ASCII-armoured secret key from step 3 |
   | `SIGNING_KEY_PASSWORD` | the passphrase of that key |

5. **Private vulnerability reporting** (Settings > Code security): switch it on, so that the report link in
   [SECURITY.md](../SECURITY.md) works.

The workflow hands the secrets to the build as `ORG_GRADLE_PROJECT_mavenCentralUsername`,
`ORG_GRADLE_PROJECT_mavenCentralPassword`, `ORG_GRADLE_PROJECT_signingInMemoryKey` and
`ORG_GRADLE_PROJECT_signingInMemoryKeyPassword`, which the
[gradle-maven-publish-plugin](https://github.com/vanniktech/gradle-maven-publish-plugin) reads.

## When a release fails

The workflow has four jobs: the tag check, the build matrix, the publication and the GitHub release. Which one
failed decides what to do.

- **Tag check or build failed.** Nothing was published. Fix `main`, delete the tag (`git push --delete origin
  vX.Y.Z` and `git tag -d vX.Y.Z`) and tag again once the fix is in.
- **Publication failed.** Read the job log: a missing or expired secret, a key not found on the key server, or a
  validation error from Central (the POM, the signature, a missing javadoc jar). Nothing is on Central until the
  plugin has uploaded and released the deployment, so after fixing the cause the tag can be deleted and recreated
  as above. If the log shows the deployment was released, the version is out and the tag must stay.
- **GitHub release failed** after a successful publication. The version is on Central and cannot be changed, so
  do not delete the tag. Re-run the failed job ("Re-run failed jobs" in the Actions tab): the release job creates
  the GitHub release if it does not exist yet and skips it if it does.

A version on Maven Central is permanent: it cannot be deleted or replaced. A broken `1.0.0` is followed by a
`1.0.1` that fixes it, and the changelog says why.

## Checking a release locally

```
./gradlew publishToMavenLocal
ls ~/.m2/repository/io/github/bzumik1/fio-api/
```

This publishes the unsigned snapshot into the local Maven repository, so a project that depends on
`io.github.bzumik1:fio-api:1.0.0-SNAPSHOT` with `mavenLocal()` can try it before a release. `./gradlew build`
also runs a consumer of the built jar on the module path (`src/moduleSmoke`), which catches a wrong
`module-info.java` before a release does.

## Versioning

Semantic versioning. The public API is what `module-info.java` exports; a change that breaks it is a new major
version. Once 1.0.0 is out, the build will compare each change against the last release with japicmp.
