package io.github.bzumik1.fio;

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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class MerchantStatementParser {

    private static final String ROOT = "MerchantStatement";
    private static final int MAXIMUM_DEPTH = 16;

    MerchantStatement parse(String xml) {
        return parse(xml.getBytes(StandardCharsets.UTF_8));
    }

    MerchantStatement parse(byte[] xml) {
        Element root = read(xml);
        if (!root.name().equals(ROOT)) {
            throw new MalformedResponseException(
                    "The root element is " + MalformedResponseException.excerpt(root.name()) + ", not " + ROOT);
        }
        Element info = root.child(ROOT, "Info");
        if (info == null) {
            throw new MalformedResponseException(ROOT + "/Info is missing");
        }
        Element transactionList = root.child(ROOT, "TransactionList");
        return statement(info, transactionList == null ? List.of() : transactionList.children("Transaction"));
    }

    private static MerchantStatement statement(Element info, List<Element> transactions) {
        Fields field = new Fields("Info", info);
        Currency currency = field.value("currency").requiredCurrency();
        List<CardTransaction> parsed = new ArrayList<>();
        for (int index = 0; index < transactions.size(); index++) {
            parsed.add(transaction(new Fields("Transaction[" + (index + 1) + "]", transactions.get(index)), currency));
        }
        return new MerchantStatement(
                account(field.value("accountId"), field.value("bankId")),
                field.value("iban").requiredText(),
                field.value("bic").requiredText(),
                currency,
                field.value("dateStart").optionalDate(),
                field.value("dateEnd").optionalDate(),
                parsed);
    }

    private static DomesticAccount account(RawValue accountId, RawValue bankId) {
        String account = accountId.requiredText() + "/" + bankId.requiredText();
        try {
            return DomesticAccount.parse(account);
        } catch (IllegalArgumentException e) {
            throw new MalformedResponseException(
                    accountId.label() + " and " + bankId.label() + " are not an account number: '"
                            + MalformedResponseException.excerpt(account) + "'", e);
        }
    }

    private static CardTransaction transaction(Fields field, Currency currency) {
        String typeLabel = field.value("type").optionalText();
        return new CardTransaction(
                field.value("operationId").requiredLong(),
                field.value("orderId").requiredLong(),
                field.value("date").requiredDate(),
                new Money(field.value("amount").requiredDecimal(), currency),
                field.value("note").optionalText(),
                field.value("branchName").optionalText(),
                field.value("transactionId").optionalLong(),
                field.value("deviceId").optionalText(),
                field.value("transactionDateTime").optionalDateTime(),
                field.value("autorizationNumber").optionalText(),
                field.value("cardNumber").optionalText(),
                transactionAmount(field.value("transactionAmount"), field.value("transactionCurrency")),
                CardTransactionType.fromOptionalLabel(typeLabel),
                typeLabel,
                field.value("cardIssuer").optionalText(),
                money(field.value("totalFees"), currency),
                money(field.value("fioFee"), currency),
                money(field.value("interchangeFee"), currency),
                money(field.value("cardAssociationFee", "cardAsosiationFee"), currency),
                money(field.value("flexibleCommission"), currency),
                field.value("settlement").optionalBoolean(),
                field.value("settlementDate").optionalDate(),
                field.value("vs", "VS").optionalText());
    }

    private static @Nullable Money money(RawValue value, Currency currency) {
        BigDecimal amount = value.optionalDecimal();
        return amount == null ? null : new Money(amount, currency);
    }

    private static @Nullable Money transactionAmount(RawValue amount, RawValue currency) {
        BigDecimal value = amount.optionalDecimal();
        return value == null ? null : new Money(value, currency.requiredCurrency());
    }

    private static Element read(byte[] xml) {
        try {
            XMLStreamReader reader = secureFactory().createXMLStreamReader(new ByteArrayInputStream(xml));
            int event = reader.next();
            while (event != XMLStreamConstants.START_ELEMENT) {
                if (event == XMLStreamConstants.DTD) {
                    throw new MalformedResponseException("The body declares a document type, which this client does not read");
                }
                event = reader.next();
            }
            return element(reader, 1);
        } catch (XMLStreamException e) {
            throw new MalformedResponseException("The body is not well-formed XML", e);
        }
    }

    private static XMLInputFactory secureFactory() {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        return factory;
    }

    private static Element element(XMLStreamReader reader, int depth) throws XMLStreamException {
        if (depth > MAXIMUM_DEPTH) {
            throw new MalformedResponseException("The body nests elements deeper than a merchant statement does");
        }
        String name = reader.getLocalName();
        StringBuilder text = new StringBuilder();
        List<Element> children = new ArrayList<>();
        int event = reader.next();
        while (event != XMLStreamConstants.END_ELEMENT) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                children.add(element(reader, depth + 1));
            } else if (event == XMLStreamConstants.CHARACTERS) {
                text.append(reader.getText());
            }
            event = reader.next();
        }
        return new Element(name, text.toString().strip(), children);
    }

    private record Element(String name, String text, List<Element> children) {

        List<Element> children(String childName) {
            return children(Set.of(childName));
        }

        List<Element> children(Set<String> names) {
            return children.stream().filter(child -> names.contains(child.name())).collect(Collectors.toList());
        }

        @Nullable Element child(String label, String... names) {
            List<Element> matching = children(Set.of(names));
            if (matching.size() > 1) {
                throw new MalformedResponseException(label + " has more than one " + String.join(" or ", names));
            }
            return matching.isEmpty() ? null : matching.get(0);
        }
    }

    private record Fields(String label, Element element) {

        RawValue value(String... names) {
            Element child = element.child(label, names);
            return child == null
                    ? new RawValue(label + "/" + names[0], null)
                    : new RawValue(label + "/" + child.name(), child.text());
        }
    }
}
