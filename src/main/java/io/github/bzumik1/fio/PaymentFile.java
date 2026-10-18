package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;

import static java.util.Objects.requireNonNull;

/**
 * A file of payment orders ready for Fio's import, in one of the formats the bank accepts.
 *
 * @param type the format of the file
 * @param content the file, up to {@link #MAXIMUM_SIZE} bytes (2 MiB)
 */
public record PaymentFile(PaymentFileType type, byte[] content) {

    /** The largest file Fio imports. */
    public static final int MAXIMUM_SIZE = 2 * 1024 * 1024;

    /**
     * Validates and copies the components.
     *
     * @param type the format
     * @param content the file
     * @throws IllegalArgumentException when the file is empty or larger than {@link #MAXIMUM_SIZE}
     */
    public PaymentFile {
        requireNonNull(type, "type");
        content = requireNonNull(content, "content").clone();
        if (content.length == 0) {
            throw new IllegalArgumentException("A payment file is not empty");
        }
        if (content.length > MAXIMUM_SIZE) {
            throw new IllegalArgumentException(
                    "A payment file is at most " + MAXIMUM_SIZE + " bytes, not " + content.length);
        }
    }

    /**
     * Returns a copy of the file.
     *
     * @return the file
     */
    @Override
    public byte[] content() {
        return content.clone();
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof PaymentFile that && that.type == type && Arrays.equals(that.content, content);
    }

    @Override
    public int hashCode() {
        return 31 * type.hashCode() + Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return "PaymentFile[" + type + ", " + content.length + " bytes]";
    }
}
