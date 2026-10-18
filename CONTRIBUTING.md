# Contributing

Pull requests are welcome. Before opening one, please read this page; it is short.

## How the code is written

- **Tests first.** Every behaviour is pinned by a test, written before the code that makes it pass. The build
  enforces 100 % line and branch coverage (`jacocoTestCoverageVerification`); a line nothing exercises fails the
  build, which is the signal to either test it or remove it.
- **No comments in code.** Names, types and tests say what the code does. A reason that must be written down
  goes into `docs/` or into this file, not into a comment. The build scripts follow the same rule.
- **Warnings are errors.** `javac` runs with `-Xlint:all,-module -Werror`, and javadoc with `-Xdoclint:all
  -Werror`. The `module` lint category is the only one switched off: javac warns that the module name component
  `bzumik1` ends in digits ("module name component bzumik1 should avoid terminal digits"), and with `-Werror`
  that warning fails the build. The module name is fixed by the Maven namespace `io.github.bzumik1`, so the
  category stays off; do not re-enable it.
- **The public API is what `module-info.java` exports.** Anything else may change in a patch release. A new
  public type needs javadoc that passes doclint.
- **Dependencies** are declared in `gradle/libs.versions.toml`. Jackson and JSpecify are the only runtime
  dependencies; adding another one needs a strong reason.

## Before a pull request

```
./gradlew build
```

This compiles with the lints above, runs the tests on Java 17, checks the coverage, builds the javadoc and runs
a consumer of the jar on the module path. The pull request check runs the same on Java 17, 21 and 25 and also
publishes the artifacts to the runner's local Maven repository, so a change that breaks the publication is
caught before a release. `./gradlew build -PtestJdk=25` runs the tests on another JDK locally.

## Commits

One change per commit, in English, in the imperative mood, saying what changes and why when the diff does not:
"Reject a base URI with user info" rather than "fix" or "changes". The pull request title follows the same
style; the description says what a reviewer should look at.

Add a line to the `Unreleased` section of [CHANGELOG.md](CHANGELOG.md) for anything a user of the library
would notice.

## Tokens and secrets

Never commit a Fio token, a signing key or a Maven Central token, not even in a test: the tests run against an
in-process stub of the bank and use a token the build sets in `FIO_API_TEST_TOKEN`. If a token has been
committed, revoke it in Fio internet banking first and rewrite the history second. Security issues are reported
privately as described in [SECURITY.md](SECURITY.md).
