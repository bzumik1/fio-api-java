package io.github.bzumik1.fio;

import java.nio.charset.Charset;

/**
 * A raw format a download can be asked for: what names it in the request path, what media type the bank serves
 * it as and which charset Fio documents for it. The three families of formats implement it, so code that saves a
 * {@link Download} under the right extension can take any of them.
 */
public sealed interface DownloadFormat permits TransactionFormat, StatementFormat, MerchantFormat {

    /**
     * Returns the extension that names the format in the request path.
     *
     * @return the extension
     */
    String extension();

    /**
     * Returns the media type the format is usually served as.
     *
     * @return the media type
     */
    String mediaType();

    /**
     * Returns the charset Fio documents for the format, used when the bank's answer names none. A binary format
     * such as PDF has no meaningful charset and reports UTF-8.
     *
     * @return the documented charset
     */
    Charset charset();
}
