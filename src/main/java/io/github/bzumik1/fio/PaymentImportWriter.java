package io.github.bzumik1.fio;

import org.jspecify.annotations.Nullable;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Objects.requireNonNull;

final class PaymentImportWriter {

    static final String SCHEMA_LOCATION = "http://www.fio.cz/schema/importIB.xsd";

    private static final String XSI = "xsi";
    private static final String UTF_8 = StandardCharsets.UTF_8.name();

    byte[] write(List<PaymentOrder> orders) {
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        write(orders, file);
        return file.toByteArray();
    }

    void write(List<PaymentOrder> orders, OutputStream file) {
        try {
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(file, UTF_8);
            xml.writeStartDocument(UTF_8, "1.0");
            xml.writeStartElement("Import");
            xml.writeNamespace(XSI, XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI);
            xml.writeAttribute(XSI, XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "noNamespaceSchemaLocation", SCHEMA_LOCATION);
            xml.writeStartElement("Orders");
            for (int index : fileOrder(orders)) {
                order(xml, orders.get(index));
            }
            xml.writeEndElement();
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.flush();
            xml.close();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("The payment orders could not be written as Fio XML", e);
        }
    }

    static List<Integer> fileOrder(List<PaymentOrder> orders) {
        return IntStream.range(0, orders.size())
                .boxed()
                .sorted(Comparator.comparingInt(index -> rank(orders.get(index))))
                .collect(Collectors.toList());
    }

    private static int rank(PaymentOrder order) {
        if (order instanceof DomesticPayment) {
            return 0;
        }
        return order instanceof EuroPayment ? 1 : 2;
    }

    private static void order(XMLStreamWriter xml, PaymentOrder order) throws XMLStreamException {
        if (order instanceof DomesticPayment domestic) {
            domestic(xml, domestic);
        } else if (order instanceof EuroPayment euro) {
            euro(xml, euro);
        } else {
            foreign(xml, (ForeignPayment) order);
        }
    }

    private static void domestic(XMLStreamWriter xml, DomesticPayment payment) throws XMLStreamException {
        xml.writeStartElement("DomesticTransaction");
        payer(xml, payment);
        element(xml, "accountTo", payment.to().numberWithPrefix());
        element(xml, "bankCode", requireNonNull(payment.to().bankCode()));
        symbols(xml, payment.symbols());
        date(xml, payment);
        optional(xml, "messageForRecipient", payment.messageForRecipient());
        optional(xml, "comment", payment.comment());
        optional(xml, "paymentReason", payment.paymentReason());
        element(xml, "paymentType", payment.type().code());
        xml.writeEndElement();
    }

    private static void euro(XMLStreamWriter xml, EuroPayment payment) throws XMLStreamException {
        xml.writeStartElement("T2Transaction");
        payer(xml, payment);
        element(xml, "accountTo", payment.to().identifier());
        symbols(xml, payment.symbols());
        optional(xml, "bic", payment.bic());
        date(xml, payment);
        optional(xml, "comment", payment.comment());
        beneficiary(xml, payment.beneficiary());
        remittanceInfo(xml, payment.remittanceInfo());
        optional(xml, "paymentReason", payment.paymentReason());
        element(xml, "paymentType", payment.type().code());
        xml.writeEndElement();
    }

    private static void foreign(XMLStreamWriter xml, ForeignPayment payment) throws XMLStreamException {
        xml.writeStartElement("ForeignTransaction");
        payer(xml, payment);
        element(xml, "accountTo", payment.to().identifier());
        element(xml, "bic", payment.bic());
        date(xml, payment);
        optional(xml, "comment", payment.comment());
        beneficiary(xml, payment.beneficiary());
        remittanceInfo(xml, payment.remittanceInfo());
        element(xml, "detailsOfCharges", payment.charges().code());
        element(xml, "paymentReason", payment.paymentReason());
        xml.writeEndElement();
    }

    private static void payer(XMLStreamWriter xml, PaymentOrder order) throws XMLStreamException {
        element(xml, "accountFrom", order.from().number());
        element(xml, "currency", order.amount().currency().getCurrencyCode());
        element(xml, "amount", order.amount().amount().setScale(2, RoundingMode.UNNECESSARY).toPlainString());
    }

    private static void symbols(XMLStreamWriter xml, PaymentSymbols symbols) throws XMLStreamException {
        optional(xml, "ks", symbols.constant());
        optional(xml, "vs", symbols.variable());
        optional(xml, "ss", symbols.specific());
    }

    private static void date(XMLStreamWriter xml, PaymentOrder order) throws XMLStreamException {
        element(xml, "date", DateTimeFormatter.ISO_LOCAL_DATE.format(order.date()));
    }

    private static void beneficiary(XMLStreamWriter xml, Beneficiary beneficiary) throws XMLStreamException {
        element(xml, "benefName", beneficiary.name());
        optional(xml, "benefStreet", beneficiary.street());
        optional(xml, "benefCity", beneficiary.city());
        optional(xml, "benefCountry", beneficiary.country());
    }

    private static void remittanceInfo(XMLStreamWriter xml, List<String> lines) throws XMLStreamException {
        for (int line = 0; line < lines.size(); line++) {
            element(xml, "remittanceInfo" + (line + 1), lines.get(line));
        }
    }

    private static void optional(XMLStreamWriter xml, String name, @Nullable String value) throws XMLStreamException {
        if (value != null) {
            element(xml, name, value);
        }
    }

    private static void element(XMLStreamWriter xml, String name, String value) throws XMLStreamException {
        xml.writeStartElement(name);
        xml.writeCharacters(value);
        xml.writeEndElement();
    }
}
