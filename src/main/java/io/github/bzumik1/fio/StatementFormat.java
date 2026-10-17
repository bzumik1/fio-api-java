package io.github.bzumik1.fio;

/**
 * The raw formats Fio offers for a download of an official statement.
 */
public enum StatementFormat {

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
    XML("xml", "application/xml"),
    /** A PDF document. */
    PDF("pdf", "application/pdf"),
    /** SWIFT MT940, which Fio serves with the extension {@code sta}. */
    MT940("sta", "text/plain"),
    /** The Czech banking association's camt.053 XML. */
    CBA_XML("cba_xml", "application/xml"),
    /** The Slovak banking association's camt.053 XML. */
    SBA_XML("sba_xml", "application/xml");

    private final String extension;
    private final String mediaType;

    StatementFormat(String extension, String mediaType) {
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
