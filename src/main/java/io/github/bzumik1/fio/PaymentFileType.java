package io.github.bzumik1.fio;

/**
 * The formats of payment orders Fio imports.
 *
 * <p>Constants may be added in a minor release when Fio imports a new format, so a {@code switch} over this type
 * needs a {@code default} branch.
 */
public enum PaymentFileType {

    /** The Czech ABO format, for payments in CZK within the Czech Republic. */
    ABO("abo", "abo"),
    /** Fio's own XML, which {@link FioClient.Payments#submit(PaymentBatch)} writes. */
    FIO_XML("xml", "xml"),
    /** ISO 20022 pain.001 credit transfers. */
    PAIN_001("pain001_xml", "xml"),
    /** ISO 20022 pain.008 direct debits. */
    PAIN_008("pain008_xml", "xml");

    private final String code;
    private final String extension;

    PaymentFileType(String code, String extension) {
        this.code = code;
        this.extension = extension;
    }

    /**
     * Returns the value of the {@code type} field Fio expects with the upload.
     *
     * @return the code
     */
    public String code() {
        return code;
    }

    /**
     * Returns the usual extension of such a file.
     *
     * @return the extension
     */
    public String extension() {
        return extension;
    }
}
