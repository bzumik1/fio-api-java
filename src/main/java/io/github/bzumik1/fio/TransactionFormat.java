package io.github.bzumik1.fio;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * The raw formats Fio offers for a download of account movements.
 *
 * <p>Constants may be added in a minor release when Fio offers a new format, so a {@code switch} over this type
 * needs a {@code default} branch.
 */
public enum TransactionFormat {

    /** Semicolon-separated values. */
    CSV("csv", "text/csv", StandardCharsets.UTF_8),
    /** The Czech GPC format. */
    GPC("gpc", "text/plain", Windows1250.CHARSET),
    /** An HTML page. */
    HTML("html", "text/html", StandardCharsets.UTF_8),
    /** The JSON format the typed downloads read. */
    JSON("json", "application/json", StandardCharsets.UTF_8),
    /** Open Financial Exchange. */
    OFX("ofx", "application/x-ofx", StandardCharsets.UTF_8),
    /** Fio's own XML. */
    XML("xml", "application/xml", StandardCharsets.UTF_8);

    private final String extension;
    private final String mediaType;
    private final Charset charset;

    TransactionFormat(String extension, String mediaType, Charset charset) {
        this.extension = extension;
        this.mediaType = mediaType;
        this.charset = charset;
    }

    /**
     * Returns the extension that names the format in the request path.
     *
     * @return the extension
     */
    public String extension() {
        return extension;
    }

    /**
     * Returns the media type the format is usually served as.
     *
     * @return the media type
     */
    public String mediaType() {
        return mediaType;
    }

    /**
     * Returns the charset Fio documents for the format, used when the bank's answer names none.
     *
     * @return the documented charset
     */
    public Charset charset() {
        return charset;
    }

    private static final class Windows1250 {
        private static final Charset CHARSET = Charset.forName("windows-1250");
    }
}
