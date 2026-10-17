package io.github.bzumik1.fio;

/**
 * The raw formats Fio offers for a download of account movements.
 */
public enum TransactionFormat {

    /** Semicolon-separated values. */
    CSV("csv", "text/csv"),
    /** The Czech GPC format. */
    GPC("gpc", "text/plain"),
    /** An HTML page. */
    HTML("html", "text/html"),
    /** The JSON format the typed downloads read. */
    JSON("json", "application/json"),
    /** Open Financial Exchange. */
    OFX("ofx", "application/x-ofx"),
    /** Fio's own XML. */
    XML("xml", "application/xml");

    private final String extension;
    private final String mediaType;

    TransactionFormat(String extension, String mediaType) {
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
