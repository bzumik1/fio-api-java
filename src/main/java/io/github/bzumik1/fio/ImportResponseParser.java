package io.github.bzumik1.fio;

import io.github.bzumik1.fio.ImportResult.Message;
import io.github.bzumik1.fio.ImportResult.Status;
import io.github.bzumik1.fio.ImportResult.Sum;
import org.jspecify.annotations.Nullable;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class ImportResponseParser {

    private static final String ROOT = "responseImport";
    private static final int MAXIMUM_DEPTH = 16;

    ImportResult parse(String xml) {
        return parse(xml.getBytes(StandardCharsets.UTF_8));
    }

    ImportResult parse(byte[] xml) {
        Element root = read(xml);
        if (!root.name().equals(ROOT)) {
            throw new MalformedResponseException(
                    "The body is a <" + MalformedResponseException.excerpt(root.name()) + ">, not a <" + ROOT + ">");
        }
        Element result = root.required("result");
        return new ImportResult(
                number("errorCode", result.required("errorCode").text(), Integer::valueOf),
                status("status", result.required("status").text()),
                optionalNumber(result, "idInstruction", Long::valueOf),
                sums(result.firstOrEmpty("sums")),
                messages(result, root.firstOrEmpty("ordersDetails")));
    }

    private static List<Sum> sums(Element sums) {
        List<Sum> parsed = new ArrayList<>();
        for (Element sum : sums.all("sum")) {
            Currency currency = new RawValue("The sum id", sum.attribute("id")).requiredCurrency();
            parsed.add(new Sum(currency, money(sum, "sumDebet", currency), money(sum, "sumCredit", currency)));
        }
        return parsed;
    }

    private static @Nullable Money money(Element sum, String name, Currency currency) {
        BigDecimal amount = optionalNumber(sum, name, RawValue::toDecimal);
        return amount == null ? null : new Money(amount, currency);
    }

    private static List<Message> messages(Element result, Element ordersDetails) {
        List<Message> parsed = new ArrayList<>();
        for (Element message : result.all("message")) {
            parsed.add(new Message(null, null, message.text()));
        }
        for (Element detail : ordersDetails.all("detail")) {
            String id = detail.attribute("id");
            Integer orderNumber = id == null ? null : number("detail id", id, Integer::valueOf);
            for (Element message : detail.firstOrEmpty("messages").all("message")) {
                String status = message.attribute("status");
                parsed.add(new Message(orderNumber, status == null ? null : status("message status", status), message.text()));
            }
        }
        return parsed;
    }

    private static Status status(String what, String text) {
        for (Status status : Status.values()) {
            if (status.name().equalsIgnoreCase(text)) {
                return status;
            }
        }
        throw new MalformedResponseException(
                "The " + what + " is not ok, warning, error or fatal: '" + MalformedResponseException.excerpt(text) + "'");
    }

    private static <T> @Nullable T optionalNumber(Element parent, String name, Function<String, T> parse) {
        Element child = parent.first(name);
        return child == null ? null : number(name, child.text(), parse);
    }

    private static <T> T number(String what, String text, Function<String, T> parse) {
        try {
            return parse.apply(text);
        } catch (NumberFormatException e) {
            throw new MalformedResponseException(
                    "The " + what + " is not a number: '" + MalformedResponseException.excerpt(text) + "'", e);
        }
    }

    private static Element read(byte[] xml) {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
            reader.nextTag();
            return element(reader, 1);
        } catch (XMLStreamException e) {
            throw new MalformedResponseException("The body is not well-formed XML without a DTD", e);
        }
    }

    private static Element element(XMLStreamReader reader, int depth) throws XMLStreamException {
        if (depth > MAXIMUM_DEPTH) {
            throw new MalformedResponseException("The body nests elements deeper than an import answer does");
        }
        String name = reader.getLocalName();
        Map<String, String> attributes = new HashMap<>();
        for (int index = 0; index < reader.getAttributeCount(); index++) {
            attributes.put(reader.getAttributeLocalName(index), reader.getAttributeValue(index));
        }
        StringBuilder text = new StringBuilder();
        List<Element> children = new ArrayList<>();
        for (int event = reader.next(); event != XMLStreamConstants.END_ELEMENT; event = reader.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                children.add(element(reader, depth + 1));
            } else if (event == XMLStreamConstants.CHARACTERS) {
                text.append(reader.getText());
            }
        }
        return new Element(name, attributes, text.toString().strip(), children);
    }

    private record Element(String name, Map<String, String> attributes, String text, List<Element> children) {

        private static final Element NONE = new Element("", Map.of(), "", List.of());

        List<Element> all(String childName) {
            List<Element> found = new ArrayList<>();
            for (Element child : children) {
                if (child.name.equals(childName)) {
                    found.add(child);
                }
            }
            return found;
        }

        @Nullable Element first(String childName) {
            List<Element> found = all(childName);
            return found.isEmpty() ? null : found.get(0);
        }

        Element firstOrEmpty(String childName) {
            Element found = first(childName);
            return found == null ? NONE : found;
        }

        Element required(String childName) {
            Element found = first(childName);
            if (found == null) {
                throw new MalformedResponseException("The <" + name + "> has no <" + childName + ">");
            }
            return found;
        }

        @Nullable String attribute(String attributeName) {
            return attributes.get(attributeName);
        }
    }
}
