package io.github.bzumik1.fio;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

final class ImportSchema {

    static final Schema IMPORT = schema("importIB.xsd");
    static final Schema RESPONSE = schema("responseImportIB.xsd");

    private ImportSchema() {
    }

    static void validate(Schema schema, byte[] xml) throws SAXException {
        Validator validator = schema.newValidator();
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        try {
            validator.validate(new StreamSource(new ByteArrayInputStream(xml)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<String> enumeration(String simpleType) {
        Document types = document("fio_xml_type.xsd");
        NodeList simpleTypes = types.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "simpleType");
        for (int index = 0; index < simpleTypes.getLength(); index++) {
            Element type = (Element) simpleTypes.item(index);
            if (type.getAttribute("name").equals(simpleType)) {
                NodeList values = type.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "enumeration");
                List<String> enumeration = new ArrayList<>();
                for (int value = 0; value < values.getLength(); value++) {
                    enumeration.add(((Element) values.item(value)).getAttribute("value"));
                }
                return enumeration;
            }
        }
        throw new IllegalArgumentException("No simple type " + simpleType);
    }

    static Document parsed(byte[] xml) {
        try {
            return builder().parse(new ByteArrayInputStream(xml));
        } catch (SAXException | IOException e) {
            throw new IllegalStateException("Not well-formed XML", e);
        }
    }

    static List<String> nonEmptyLeaves(byte[] xml) {
        Element root = parsed(xml).getDocumentElement();
        List<String> leaves = new ArrayList<>();
        for (int index = 0; index < root.getAttributes().getLength(); index++) {
            leaves.add(root.getTagName() + "@" + root.getAttributes().item(index).getNodeName() + "="
                    + root.getAttributes().item(index).getNodeValue());
        }
        collectLeaves(root, root.getTagName(), leaves);
        return leaves;
    }

    private static void collectLeaves(Element element, String path, List<String> leaves) {
        boolean hasChildElements = false;
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element) {
                hasChildElements = true;
                collectLeaves((Element) child, path + "/" + ((Element) child).getTagName(), leaves);
            }
        }
        if (!hasChildElements && !element.getTextContent().isEmpty()) {
            leaves.add(path + "=" + element.getTextContent());
        }
    }

    private static Document document(String name) {
        try (InputStream stream = ImportSchema.class.getResourceAsStream("/schema/" + name)) {
            return builder().parse(stream);
        } catch (SAXException | IOException e) {
            throw new IllegalStateException("The vendored schema " + name + " does not load", e);
        }
    }

    private static DocumentBuilder builder() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Schema schema(String name) {
        URL location = ImportSchema.class.getResource("/schema/" + name);
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            return factory.newSchema(location);
        } catch (SAXException e) {
            throw new IllegalStateException("The vendored schema " + name + " does not load", e);
        }
    }
}
