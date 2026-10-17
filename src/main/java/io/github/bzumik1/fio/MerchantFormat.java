package io.github.bzumik1.fio;

/**
 * The raw formats Fio offers for a download of a merchant's card transactions.
 */
public enum MerchantFormat {

    /** Fio's own XML, which the typed download reads. */
    XML("xml", "application/xml"),
    /** Semicolon-separated values. */
    CSV("csv", "text/csv");

    private final String extension;
    private final String mediaType;

    MerchantFormat(String extension, String mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
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
}
