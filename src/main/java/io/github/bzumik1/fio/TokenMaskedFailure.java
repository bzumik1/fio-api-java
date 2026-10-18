package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

final class TokenMaskedFailure extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    private TokenMaskedFailure(String maskedDescription, StackTraceElement[] stackTrace) {
        super(maskedDescription);
        setStackTrace(stackTrace);
    }

    static Throwable withoutToken(Throwable failure, FioToken token) {
        return mentions(failure, token, identitySet()) ? copy(failure, token, identitySet()) : failure;
    }

    private static boolean mentions(@Nullable Throwable failure, FioToken token, Set<Throwable> visited) {
        if (failure == null || !visited.add(failure)) {
            return false;
        }
        String described = failure.toString();
        if (described.length() > FioException.RESPONSE_BODY_LIMIT || token.occursIn(described)) {
            return true;
        }
        for (Throwable suppressed : failure.getSuppressed()) {
            if (mentions(suppressed, token, visited)) {
                return true;
            }
        }
        return mentions(failure.getCause(), token, visited);
    }

    private static TokenMaskedFailure copy(Throwable failure, FioToken token, Set<Throwable> copied) {
        copied.add(failure);
        TokenMaskedFailure masked = new TokenMaskedFailure(
                token.maskExcerpt(MalformedResponseException.excerpt(failure.toString(), FioException.RESPONSE_BODY_LIMIT)),
                failure.getStackTrace());
        for (Throwable suppressed : failure.getSuppressed()) {
            if (!copied.contains(suppressed)) {
                masked.addSuppressed(copy(suppressed, token, copied));
            }
        }
        Throwable cause = failure.getCause();
        if (cause != null && !copied.contains(cause)) {
            masked.initCause(copy(cause, token, copied));
        }
        return masked;
    }

    private static Set<Throwable> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
