/**
 * A client of the Fio banka REST API; see {@link io.github.bzumik1.fio.FioClient}.
 */
module io.github.bzumik1.fio {
    requires transitive java.net.http;
    requires transitive org.jspecify;
    requires java.xml;
    requires tools.jackson.databind;

    exports io.github.bzumik1.fio;
}
