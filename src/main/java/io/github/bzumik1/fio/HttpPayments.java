package io.github.bzumik1.fio;

import io.github.bzumik1.fio.ImportResult.Message;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

final class HttpPayments implements FioClient.Payments {

    private static final String IMPORT = "import";
    private static final String FILE_NAME = "payments.";

    private final FioHttp http;
    private final PaymentImportWriter writer = new PaymentImportWriter();
    private final ImportResponseParser parser = new ImportResponseParser();

    HttpPayments(FioHttp http) {
        this.http = http;
    }

    @Override
    public FioClient.BatchRequest batch() {
        return new Batch();
    }

    @Override
    public ImportResult submit(PaymentBatch batch) {
        requireNonNull(batch, "batch");
        List<Integer> fileOrder = PaymentImportWriter.fileOrder(batch.orders());
        ImportResult result = upload(new PaymentFile(PaymentFileType.FIO_XML, writer.write(batch.orders())));
        return new ImportResult(
                result.errorCode(),
                result.status(),
                result.instructionId(),
                result.sums(),
                result.messages().stream().map(message -> numberedInBatch(message, fileOrder)).toList());
    }

    @Override
    public ImportResult upload(PaymentFile file) {
        requireNonNull(file, "file");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("type", file.type().code());
        fields.put("token", http.token().value());
        fields.put("lng", http.messageLanguage());
        FioHttp.FioResponse response = http.postMultipart(IMPORT, fields, FILE_NAME + file.type().extension(), file.content());
        return http.parsed(response, parser::parse);
    }

    private static Message numberedInBatch(Message message, List<Integer> fileOrder) {
        Integer positionInFile = message.orderNumber();
        if (positionInFile == null || positionInFile < 1 || positionInFile > fileOrder.size()) {
            return message;
        }
        return new Message(fileOrder.get(positionInFile - 1) + 1, message.status(), message.text());
    }

    private final class Batch implements FioClient.BatchRequest {

        private final List<PaymentOrder> orders = new ArrayList<>();

        @Override
        public FioClient.BatchRequest domestic(Consumer<DomesticPayment.Builder> order) {
            return built(DomesticPayment::builder, order, DomesticPayment.Builder::build);
        }

        @Override
        public FioClient.BatchRequest euro(Consumer<EuroPayment.Builder> order) {
            return built(EuroPayment::builder, order, EuroPayment.Builder::build);
        }

        @Override
        public FioClient.BatchRequest foreign(Consumer<ForeignPayment.Builder> order) {
            return built(ForeignPayment::builder, order, ForeignPayment.Builder::build);
        }

        @Override
        public FioClient.BatchRequest add(PaymentOrder order) {
            orders.add(requireNonNull(order, "order"));
            return this;
        }

        @Override
        public ImportResult submit() {
            if (orders.isEmpty()) {
                throw new IllegalStateException("The batch holds no payment order to submit");
            }
            return HttpPayments.this.submit(new PaymentBatch(orders));
        }

        private <B> FioClient.BatchRequest built(
                Supplier<B> builder, Consumer<B> order, Function<B, PaymentOrder> build) {
            requireNonNull(order, "order");
            B filled = builder.get();
            order.accept(filled);
            return add(build.apply(filled));
        }
    }
}
