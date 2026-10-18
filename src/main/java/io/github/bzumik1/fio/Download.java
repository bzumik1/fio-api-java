package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * An answer of the bank kept as it was sent: a statement in one of the raw formats, such as CSV or PDF.
 *
 * @param mediaType the {@code Content-Type} of the answer, such as {@code application/pdf}
 * @param charset the charset of the body: the one the media type names, or the one the format is documented with
 *        when it names none
 * @param content the body of the answer
 */
public record Download(String mediaType, Charset charset, byte[] content) {

    private static final Pattern CHARSET = Pattern.compile("charset=\"?([^\";\\s]+)\"?", Pattern.CASE_INSENSITIVE);

    /**
     * Validates and copies the components, so the document cannot be changed through the array.
     *
     * @param mediaType the media type
     * @param charset the charset of the body
     * @param content the body
     */
    public Download {
        requireNonNull(mediaType, "mediaType");
        requireNonNull(charset, "charset");
        content = requireNonNull(content, "content").clone();
    }

    static Download of(String mediaType, Charset documented, byte[] content) {
        Matcher matcher = CHARSET.matcher(mediaType);
        if (matcher.find()) {
            try {
                return new Download(mediaType, Charset.forName(matcher.group(1)), content);
            } catch (IllegalArgumentException unknown) {
                return new Download(mediaType, documented, content);
            }
        }
        return new Download(mediaType, documented, content);
    }

    /**
     * Returns a copy of the body.
     *
     * @return the body
     */
    @Override
    public byte[] content() {
        return content.clone();
    }

    /**
     * Returns the body decoded with {@link #charset()}.
     *
     * @return the body as text
     */
    public String asText() {
        return new String(content, charset);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof Download that
                && that.mediaType.equals(mediaType)
                && that.charset.equals(charset)
                && Arrays.equals(that.content, content);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * mediaType.hashCode() + charset.hashCode()) + Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return "Download[" + mediaType + ", " + charset + ", " + content.length + " bytes]";
    }
}
