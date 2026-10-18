package io.github.bzumik1.fio;

import io.github.bzumik1.fio.ImportResult.Message;
import io.github.bzumik1.fio.ImportResult.Status;
import io.github.bzumik1.fio.ImportResult.Sum;
import org.jspecify.annotations.Nullable;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class ImportResponseParser {

    private static final String ROOT = "responseImport";

    ImportResult parse(byte[] xml) {
        return parse(new String(xml, StandardCharsets.UTF_8));
    }

    ImportResult parse(String xml) {
        Element root = read(xml);
        if (!root.name().equals(ROOT)) {
            throw new MalformedResponseException("The body is a <" + root.name() + ">, not a <" + ROOT + ">");
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
            parsed.add(new Sum(
                    sum.attribute("id"),
                    optionalNumber(sum, "sumDebet", BigDecimal::new),
                    optionalNumber(sum, "sumCredit", BigDecimal::new)));
        }
        return parsed;
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
        throw new MalformedResponseException("The " + what + " is not ok, warning, error or fatal: '" + text + "'");
    }

    private static <T> @Nullable T optionalNumber(Element parent, String name, Function<String, T> parse) {
        Element child = parent.first(name);
        return child == null ? null : number(name, child.text(), parse);
    }

    private static <T> T number(String what, String text, Function<String, T> parse) {
        try {
            return parse.apply(text);
        } catch (NumberFormatException e) {
            throw new MalformedResponseException("The " + what + " is not a number: '" + text + "'", e);
        }
    }

    private static Element read(String xml) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));
            reader.nextTag();
            return element(reader);
        } catch (XMLStreamException e) {
            throw new MalformedResponseException("The body is not well-formed XML without a DTD", e);
        }
    }

    private static Element element(XMLStreamReader reader) throws XMLStreamException {
        String name = reader.getLocalName();
        Map<String, String> attributes = new HashMap<>();
        for (int index = 0; index < reader.getAttributeCount(); index++) {
            attributes.put(reader.getAttributeLocalName(index), reader.getAttributeValue(index));
        }
        StringBuilder text = new StringBuilder();
        List<Element> children = new ArrayList<>();
        for (int event = reader.next(); event != XMLStreamConstants.END_ELEMENT; event = reader.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                children.add(element(reader));
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
