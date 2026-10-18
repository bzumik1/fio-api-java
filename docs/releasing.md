# Releasing

A release is a signed git tag `vX.Y.Z` on `main`. The `Release` workflow checks the tag, runs the full build on
Java 17, 21 and 25, builds and signs the artifacts, keeps a copy of them, publishes
`io.github.bzumik1:fio-api:X.Y.Z` to Maven Central and creates the GitHub release with the version's changelog
section as notes and the jars, their signatures and a build provenance attestation attached. Nothing else has
to be done by hand.

```
git switch main && git pull
git tag -s v1.0.0 -m "fio-api 1.0.0"
git push origin v1.0.0
```

The tag is signed with the same GPG key that signs the artifacts (see the one-time setup below); `git tag -s`
uses the key `user.signingkey` names in the git config.

Before tagging, in [CHANGELOG.md](../CHANGELOG.md):

1. Rename the `## [Unreleased]` section to the version with the date, `## [1.0.0] - 2026-10-20`, and start a
   new empty `## [Unreleased]` section above it. The tag check fails when there is no `## [X.Y.Z]` heading for
   the tag, and the GitHub release takes everything between that heading and the next `## [` heading, or the
   link references at the bottom of the file, whichever comes first, as its notes.
2. Add the link references at the bottom of the file, as Keep a Changelog prescribes:
   ```
   [Unreleased]: https://github.com/bzumik1/fio-api-java/compare/v1.0.0...HEAD
   [1.0.0]: https://github.com/bzumik1/fio-api-java/releases/tag/v1.0.0
   ```
   Every later version compares with its predecessor:
   `[1.1.0]: https://github.com/bzumik1/fio-api-java/compare/v1.0.0...v1.1.0`.

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
   gpg --quick-generate-key "Jakub Znamenáček <znamenacekjakub@gmail.com>" rsa4096 sign 0
   gpg --list-keys --keyid-format long            # note the key id
   gpg --keyserver keyserver.ubuntu.com --send-keys <key id>
   gpg --armor --export-secret-keys <key id>      # the whole block goes into the SIGNING_KEY secret
   git config --global user.signingkey <key id>   # so that git tag -s uses the same key
   ```
   RSA 4096 is the key type every validator on Central's side accepts; an EdDSA key signs just as well but its
   acceptance by the portal's validation has not been confirmed. The `0` makes the key never expire; a release
   signed with an expired key would be refused by Central. Should the key ever leak, revoke it
   (`gpg --generate-revocation <key id>`, import the certificate, send the key to the key server again),
   generate a new one and replace the `SIGNING_KEY` and `SIGNING_KEY_PASSWORD` secrets.
4. **Repository settings**, described in the next section; the secrets from steps 2 and 3 go into the
   `maven-central` environment.

## Repository settings

These are set once by the owner in the repository's Settings on GitHub; the workflows assume them.

- **Environment `maven-central`** (Settings > Environments). The publish job of the release workflow runs in
  it (`environment: maven-central` in `release.yml`), and the secrets it sees are the environment's, so they
  are defined there and not as repository secrets. GitHub creates the environment on the first run that names
  it, but the secrets have to be added by hand before that run:

  | Secret | Value |
  |---|---|
  | `MAVEN_CENTRAL_USERNAME` | the token username from step 2 |
  | `MAVEN_CENTRAL_PASSWORD` | the token password from step 2 |
  | `SIGNING_KEY` | the ASCII-armoured secret key from step 3 |
  | `SIGNING_KEY_PASSWORD` | the passphrase of that key |

  The workflow hands them to the build as `ORG_GRADLE_PROJECT_mavenCentralUsername`,
  `ORG_GRADLE_PROJECT_mavenCentralPassword`, `ORG_GRADLE_PROJECT_signingInMemoryKey` and
  `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword`, which the
  [gradle-maven-publish-plugin](https://github.com/vanniktech/gradle-maven-publish-plugin) reads. An
  environment can also require a reviewer or restrict which branches and tags may deploy to it; restricting it
  to tags `v*` keeps a pull request from ever seeing the secrets.
- **Tag ruleset** (Settings > Rules > Rulesets, target tags, pattern `v*`): restrict creation, update and
  deletion to the repository owner, so only `bzumik1` can create a tag that starts a release. Until the ruleset
  exists, anyone with push access can start a release by pushing a tag.
- **Visibility: public.** The build provenance attestation (`actions/attest-build-provenance` in the release
  job) works only on public repositories; on a private one the release job fails after the version is already
  on Central, and the release has to be created by hand as "Publication failed" below describes. Make the
  repository public before the first tag.
- **Branch protection on `main`** (a ruleset or the classic branch protection): require a pull request before
  merging, require the status checks "Build and test on Java 17", "Build and test on Java 21" and "Build and
  test on Java 25" to pass, block force pushes and deletions, and include administrators (do not add a bypass
  for the owner), so that `main` only ever holds code the checks have seen.
- **Workflow permissions** (Settings > Actions > General): "Read repository contents and packages permissions".
  Each job that needs more declares it in its `permissions` block (the release job writes the release and the
  attestation).
- **Code security** (Settings > Code security): CodeQL default setup, Dependabot alerts (the Dependabot
  updates come from `.github/dependabot.yml`), secret scanning with push protection, and private vulnerability
  reporting, so that the report link in [SECURITY.md](../SECURITY.md) works.
- `.github/CODEOWNERS` names the owner as the reviewer of everything; with "require review from Code Owners"
  on `main`, every pull request waits for him.

## When a release fails

The workflow has four jobs: the tag check, the build matrix, the publication and the GitHub release. Which one
failed decides what to do.

- **Tag check or build failed.** Nothing was published. A failed tag check usually means the changelog has no
  section for the version. Fix `main`, delete the tag (`git push --delete origin vX.Y.Z` and `git tag -d
  vX.Y.Z`) and tag again once the fix is in.
- **Publication failed.** The job builds, then signs in a separate step (the only one, with the publishing
  step, that sees the key), uploads the jars and their `.asc` signatures as the workflow artifact `jars`, and
  only then publishes. Read the job log: a missing or expired secret, a key not found on the key server, or a
  validation error from Central (the POM, the signature, a missing javadoc jar).
  Nothing is on Central until the plugin has uploaded and released the deployment. Before anything else, open
  Deployments at https://central.sonatype.com: a deployment that is publishing or published means the version
  is out (the job may have been killed by its timeout while Central was still validating), so the tag must
  stay, and the GitHub release is created by hand as the next point describes; a deployment marked failed is
  dropped there first. Then, a cause outside the repository (a secret, the key server, Central itself) is fixed
  in place and the job re-run ("Re-run failed jobs"; it builds and signs again and replaces the `jars`
  artifact); a cause in the repository is fixed on `main`, after which the tag is deleted and recreated as
  above.
- **GitHub release failed** after a successful publication. The version is on Central and cannot be changed, so
  do not delete the tag. Re-run the failed job ("Re-run failed jobs" in the Actions tab): the release job
  creates the GitHub release if it does not exist yet and skips it if it does. If the job cannot be re-run,
  create the release by hand from the `jars` artifact of the run (downloadable from the run's summary page for
  90 days) and the changelog section:
  ```
  gh release create vX.Y.Z --verify-tag --title "fio-api X.Y.Z" --notes-file notes.md jars/*.jar jars/*.asc
  ```
  where `notes.md` holds the `## [X.Y.Z]` section of the changelog. A release created by hand has no
  provenance attestation; say so in the notes.

A version on Maven Central is permanent: it cannot be deleted or replaced. A broken `1.0.0` is followed by a
`1.0.1` that fixes it, and the changelog says why.

## Checking a release locally

```
./gradlew build publishToMavenLocal
ls ~/.m2/repository/io/github/bzumik1/fio-api/
```

`build` produces the jar, the sources jar and the javadoc jar in `build/libs`, each with `META-INF/LICENSE`.
`publishToMavenLocal` adds the POM and the Gradle module metadata and publishes the unsigned snapshot (the
plugin signs only release versions) into the local Maven repository, so a project that depends on
`io.github.bzumik1:fio-api:1.0.0-SNAPSHOT` with `mavenLocal()` can try it before a release. The build
workflow runs the same and checks that the four files are there, so a change that breaks the publication is
caught on the pull request and not on release day. `./gradlew build` also runs a consumer of the built jar on
the module path (`src/moduleSmoke`), which catches a wrong `module-info.java` before a release does.

To see the signing work locally, pass a key as the workflow does:

```
ORG_GRADLE_PROJECT_signingInMemoryKey="$(gpg --armor --export-secret-keys <key id>)" \
ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=... ./gradlew -Pversion=1.0.0 build signMavenPublication
```

The signatures of the jars land next to them in `build/libs/*.asc`, those of the POM and the module metadata
in `build/publications/maven/`.

## Versioning

Semantic versioning. The public API is what `module-info.java` exports; a change that breaks it is a new major
version. After 1.0.0 the build is meant to compare each change against the last release with japicmp, so that
a breaking change cannot slip into a minor version; it is not wired yet.
