package io.github.bzumik1.fio;

final class HttpPayments implements FioClient.Payments {

    private final FioHttp http;

    HttpPayments(FioHttp http) {
        this.http = http;
    }

    @Override
    public FioClient.BatchRequest batch() {
        throw new UnsupportedOperationException("Payments are not implemented yet: " + http.token());
    }

    @Override
    public ImportResult submit(PaymentBatch batch) {
        throw new UnsupportedOperationException("Payments are not implemented yet: " + http.token());
    }

    @Override
    public ImportResult upload(PaymentFile file) {
        throw new UnsupportedOperationException("Payments are not implemented yet: " + http.token());
    }
}
