package io.github.bzumik1.fio;

import java.time.LocalDate;

final class HttpMerchant implements FioClient.Merchant {

    private final FioHttp http;

    HttpMerchant(FioHttp http) {
        this.http = http;
    }

    @Override
    public FioClient.MerchantRequest between(LocalDate from, LocalDate to) {
        HttpFioClient.requirePeriod(from, to);
        throw new UnsupportedOperationException("Merchant downloads are not implemented yet: " + http.token());
    }
}
