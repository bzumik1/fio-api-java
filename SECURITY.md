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
URL and response body carried by a `FioException` has it replaced by `***`. If a token has leaked anyway, revoke
it in Fio internet banking (Nastavení, API) and create a new one; the library has no way to do that for you.

The JDK's own diagnostics are outside the library's control. `java.net.http.HttpClient` logs the full request
line, token included, when the JVM runs with `-Djdk.httpclient.HttpClient.log=requests`, and
`-Djavax.net.debug` dumps the TLS plaintext; neither belongs on a production JVM.

## Where the token may be sent

The base URI must be `https`, with a host and without a query, a fragment or user info. The only exception is
`http` to the literal loopback addresses `127.0.0.1` and `[::1]`, meant for an in-process stub of the bank
(the builder also has an explicit opt-out for plain `http`, for a stub on another address). A token therefore
cannot leave the machine unencrypted by accident.

A custom `HttpClient` passed to the builder is accepted only if its redirect policy is `NEVER`: a client that
follows redirects would re-send the request, token included, to whatever host the redirect names.

## Limits on what is read

A response body is read up to 256 MiB by default (`maximumResponseSize` on the builder lowers it); a larger
answer fails with a `FioException` instead of exhausting the heap. The limit is on the bytes received, not on the heap the parsed answer takes: a body made of millions of
tiny elements or objects still costs many times its size in objects, so the limit guards against a runaway
download, not against a hostile bank host. The body stored in an exception is cut to its first 16 Ki characters.

## Dependencies

The runtime dependencies are Jackson (`tools.jackson.core:jackson-databind`) and JSpecify
(`org.jspecify:jspecify`, an `api` dependency, so it is on the consumer's module path). Dependabot opens a pull
request for every dependency and GitHub Actions update, and the actions in the workflows are pinned to commits.
