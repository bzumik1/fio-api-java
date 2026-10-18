package io.github.bzumik1.fio;

import java.time.LocalDate;

import static java.util.Objects.requireNonNull;

final class HttpMerchant implements FioClient.Merchant {

    private final FioHttp http;
    private final MerchantStatementParser parser = new MerchantStatementParser();

    HttpMerchant(FioHttp http) {
        this.http = http;
    }

    @Override
    public FioClient.MerchantRequest between(LocalDate from, LocalDate to) {
        HttpFioClient.requirePeriod(from, to);
        return new HttpMerchantRequest(HttpFioClient.iso(from), HttpFioClient.iso(to));
    }

    private final class HttpMerchantRequest implements FioClient.MerchantRequest {

        private final String from;
        private final String to;

        private HttpMerchantRequest(String from, String to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public MerchantStatement fetch() {
            return http.parsed(get(MerchantFormat.XML), parser::parse);
        }

        @Override
        public Download download(MerchantFormat format) {
            requireNonNull(format, "format");
            return get(format).document(format.charset());
        }

        private FioHttp.FioResponse get(MerchantFormat format) {
            return http.get(format.mediaType(), "merchant", http.token().value(), from, to, "transactions." + format.extension());
        }
    }
}
