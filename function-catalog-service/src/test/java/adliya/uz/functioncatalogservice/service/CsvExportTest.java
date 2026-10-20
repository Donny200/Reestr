package adliya.uz.functioncatalogservice.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvExportTest {

    @Test void outputStartsWithAByteOrderMarkAndUsesCrlfRecords() {
        String csv = text(CsvExport.write(CsvExport.Delimiter.COMMA, List.of("a", "b"), List.of(List.of(1L, "x"))));
        assertThat(csv).startsWith("﻿").isEqualTo("﻿a,b\r\n1,x\r\n");
    }

    @Test void delimitersQuotesAndLineBreaksAreEscaped() throws IOException {
        List<Object> row = Arrays.asList("comma, inside", "quote \"here\"", "line\nbreak", "semi;colon", null, "Ўзбекча ‘matn’");
        String csv = text(CsvExport.write(CsvExport.Delimiter.COMMA, List.of("1", "2", "3", "4", "5", "6"), List.of(row)));
        assertThat(csv).contains("\"comma, inside\"", "\"quote \"\"here\"\"\"", "\"line\nbreak\"", ",semi;colon,");
        CSVRecord parsed = parse(csv, ',').get(1);
        assertThat(parsed.values()).containsExactly("comma, inside", "quote \"here\"", "line\nbreak", "semi;colon", "", "Ўзбекча ‘matn’");

        String semicolon = text(CsvExport.write(CsvExport.Delimiter.SEMICOLON, List.of("a", "b"), List.of(List.of("x;y", "p,q"))));
        assertThat(semicolon).endsWith("\"x;y\";p,q\r\n");
        assertThat(parse(semicolon, ';').get(1).values()).containsExactly("x;y", "p,q");
    }

    @Test void cellsThatSpreadsheetsWouldEvaluateAreNeutralized() {
        for (String dangerous : List.of("=HYPERLINK(\"http://evil\")", "+1+1", "-2+3", "@SUM(A1)", "\tcmd", "\rcmd", "＝1+1", "＋1", "－1", "＠A1")) {
            assertThat(CsvExport.cell(dangerous)).isEqualTo("'" + dangerous);
        }
        assertThat(CsvExport.cell("Normal text = fine")).isEqualTo("Normal text = fine");
        assertThat(CsvExport.cell(-5L)).isEqualTo("-5");
        assertThat(CsvExport.cell(null)).isEmpty();
        assertThat(CsvExport.cell("")).isEmpty();
        String csv = text(CsvExport.write(CsvExport.Delimiter.COMMA, List.of("=header"), List.of(List.of("=1+2"))));
        assertThat(csv).isEqualTo("﻿'=header\r\n'=1+2\r\n");
    }

    @Test void timestampsUseTheReportingTimeZone() {
        assertThat(CsvExport.timestamp(Instant.parse("2026-03-01T19:30:00Z"), ZoneId.of("Asia/Tashkent"))).isEqualTo("2026-03-02 00:30:00");
        assertThat(CsvExport.timestamp(null, ZoneId.of("Asia/Tashkent"))).isNull();
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static List<CSVRecord> parse(String csv, char delimiter) throws IOException {
        return CSVFormat.RFC4180.builder().setDelimiter(delimiter).get().parse(new StringReader(csv.substring(1))).getRecords();
    }
}
