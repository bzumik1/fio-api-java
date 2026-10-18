package io.github.bzumik1.fio;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LargeStatementTest {

    private static final int FIO_MAXIMUM_MOVEMENTS = 50_000;

    @Test
    void should_parse_within_the_test_heap_when_parsed_given_statement_with_the_most_movements_fio_returns() {
        byte[] json = statementWith(FIO_MAXIMUM_MOVEMENTS);

        AccountStatement statement = new AccountStatementParser().parse(json);

        assertThat(statement.transactions()).hasSize(FIO_MAXIMUM_MOVEMENTS);
        assertThat(statement.transactions().get(FIO_MAXIMUM_MOVEMENTS - 1).id()).isEqualTo(1_000_000_000L + FIO_MAXIMUM_MOVEMENTS - 1);
    }

    private static byte[] statementWith(int movements) {
        StringBuilder json = new StringBuilder(movements * 600);
        json.append("{\"accountStatement\":{\"info\":{\"accountId\":\"2400222222\",\"bankId\":\"2010\",\"currency\":\"CZK\",")
                .append("\"iban\":\"CZ7920100000002400222222\",\"bic\":\"FIOBCZPPXXX\",\"openingBalance\":0.00,\"closingBalance\":50000.00,")
                .append("\"dateStart\":\"2026-01-01+0100\",\"dateEnd\":\"2026-03-31+0200\",\"yearList\":null,\"idList\":null,")
                .append("\"idFrom\":1000000000,\"idTo\":1000049999,\"idLastDownload\":null},\"transactionList\":{\"transaction\":[");
        for (int i = 0; i < movements; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"column22\":{\"value\":").append(1_000_000_000L + i).append(",\"name\":\"ID pohybu\",\"id\":22},")
                    .append("\"column0\":{\"value\":\"2026-02-14+0100\",\"name\":\"Datum\",\"id\":0},")
                    .append("\"column1\":{\"value\":1.00,\"name\":\"Objem\",\"id\":1},")
                    .append("\"column14\":{\"value\":\"CZK\",\"name\":\"Měna\",\"id\":14},")
                    .append("\"column2\":{\"value\":\"2900233333\",\"name\":\"Protiúčet\",\"id\":2},")
                    .append("\"column10\":{\"value\":\"Pavel Novák\",\"name\":\"Název protiúčtu\",\"id\":10},")
                    .append("\"column3\":{\"value\":\"2010\",\"name\":\"Kód banky\",\"id\":3},")
                    .append("\"column12\":{\"value\":\"Fio banka, a.s.\",\"name\":\"Název banky\",\"id\":12},")
                    .append("\"column5\":{\"value\":\"").append(i % 10_000).append("\",\"name\":\"VS\",\"id\":5},")
                    .append("\"column7\":{\"value\":\"Dar na provoz\",\"name\":\"Uživatelská identifikace\",\"id\":7},")
                    .append("\"column16\":{\"value\":\"Zpráva pro příjemce\",\"name\":\"Zpráva pro příjemce\",\"id\":16},")
                    .append("\"column8\":{\"value\":\"Příjem převodem uvnitř banky\",\"name\":\"Typ\",\"id\":8},")
                    .append("\"column9\":null,\"column18\":null,\"column25\":{\"value\":\"Pavel Novák\",\"name\":\"Komentář\",\"id\":25},")
                    .append("\"column26\":null,\"column17\":{\"value\":").append(2_000_000_000L + i).append(",\"name\":\"ID pokynu\",\"id\":17}}");
        }
        json.append("]}}}");
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }
}
