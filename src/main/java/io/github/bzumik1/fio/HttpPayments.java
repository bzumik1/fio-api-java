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
                numberedInBatch(result.messages(), fileOrder));
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

    private static List<Message> numberedInBatch(List<Message> messages, List<Integer> fileOrder) {
        boolean allPositionsFit = messages.stream()
                .map(Message::orderNumber)
                .allMatch(position -> position == null || (position >= 1 && position <= fileOrder.size()));
        List<Message> numbered = new ArrayList<>(messages.size());
        for (Message message : messages) {
            Integer positionInFile = message.orderNumber();
            Integer positionInBatch = positionInFile == null || !allPositionsFit
                    ? null
                    : fileOrder.get(positionInFile - 1) + 1;
            numbered.add(new Message(positionInBatch, message.status(), message.text()));
        }
        return numbered;
    }

    private final class Batch implements FioClient.BatchRequest {

        private final List<PaymentOrder> orders = new ArrayList<>();
        private boolean submitted;

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
            if (submitted) {
                throw new IllegalStateException("The batch was already submitted; build a new one to submit again");
            }
            if (orders.isEmpty()) {
                throw new IllegalStateException("The batch holds no payment order to submit");
            }
            submitted = true;
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
