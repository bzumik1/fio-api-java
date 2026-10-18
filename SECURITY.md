# Security policy

## Reporting a vulnerability

Please report a vulnerability privately through GitHub:
[Report a vulnerability](https://github.com/bzumik1/fio-api-java/security/advisories/new). Do not open a public
issue for it. You will get an answer within a week, and a fix, when one is needed, as a patch release of the
latest version together with a security advisory that credits you, unless you prefer not to be named.

## Supported versions

The latest release receives fixes. Older versions do not; update to the latest minor of the same major.

## What the library does with the token

The Fio API token is the only credential. The library sends it to the bank in the URL path of each request (or
in the body of a payment import), as the API requires, and nowhere else: it is never logged, and every message,
URL and response body carried by a `FioException` has it replaced by `***`. The client refuses a base URI other
than `https` unless it points at a loopback address, so a token cannot leave the machine unencrypted. If a token
has leaked anyway, revoke it in Fio internet banking (Nastavení, API) and create a new one; the library has no
way to do that for you.

## Dependencies

The only runtime dependency is Jackson. Dependabot opens a pull request for every dependency and GitHub Actions
update, and the actions in the workflows are pinned to commits.
