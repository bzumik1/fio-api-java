package io.github.bzumik1.fio;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * An answer of the bank kept as it was sent: a statement in one of the raw formats, such as CSV or PDF.
 *
 * @param mediaType the {@code Content-Type} of the answer, such as {@code application/pdf}
 * @param content the body of the answer
 */
public record FioDocument(String mediaType, byte[] content) {

    private static final Pattern CHARSET = Pattern.compile("charset=\"?([^\";\\s]+)\"?", Pattern.CASE_INSENSITIVE);

    /**
     * Validates and copies the components, so the document cannot be changed through the array.
     *
     * @param mediaType the media type
     * @param content the body
     */
    public FioDocument {
        requireNonNull(mediaType, "mediaType");
        content = requireNonNull(content, "content").clone();
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
     * Returns the body as text, decoded with the charset the media type names, or UTF-8 when it names none.
     *
     * @return the body as text
     */
    public String asText() {
        return new String(content, charset());
    }

    /**
     * Returns the charset the media type names, or UTF-8 when it names none or an unknown one.
     *
     * @return the charset of the body
     */
    public Charset charset() {
        Matcher matcher = CHARSET.matcher(mediaType);
        if (matcher.find()) {
            try {
                return Charset.forName(matcher.group(1));
            } catch (IllegalArgumentException unknown) {
                return StandardCharsets.UTF_8;
            }
        }
        return StandardCharsets.UTF_8;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FioDocument that
                && that.mediaType.equals(mediaType)
                && Arrays.equals(that.content, content);
    }

    @Override
    public int hashCode() {
        return 31 * mediaType.hashCode() + Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return "FioDocument[" + mediaType + ", " + content.length + " bytes]";
    }
}
