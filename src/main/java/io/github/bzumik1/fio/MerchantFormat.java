package io.github.bzumik1.fio;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * The raw formats Fio offers for a download of a merchant's card transactions.
 *
 * <p>Constants may be added in a minor release when Fio offers a new format, so a {@code switch} over this type
 * needs a {@code default} branch.
 */
public enum MerchantFormat {

    /** Fio's own XML, which the typed download reads. */
    XML("xml", "application/xml", StandardCharsets.UTF_8),
    /** Semicolon-separated values. */
    CSV("csv", "text/csv", StandardCharsets.UTF_8);

    private final String extension;
    private final String mediaType;
    private final Charset charset;

    MerchantFormat(String extension, String mediaType, Charset charset) {
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
}
