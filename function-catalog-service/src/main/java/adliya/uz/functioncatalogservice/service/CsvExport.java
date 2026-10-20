package adliya.uz.functioncatalogservice.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class CsvExport {

    public static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);
    public static final String TRUNCATED_HEADER = "X-Export-Truncated";
    private static final String BYTE_ORDER_MARK = "﻿";
    private static final String FORMULA_TRIGGERS = "=+-@\t\r＝＋－＠";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private CsvExport() {}

    public enum Delimiter {
        COMMA(','), SEMICOLON(';');

        private final char character;

        Delimiter(char character) {
            this.character = character;
        }
    }

    public record File(String filename, byte[] content, boolean truncated) {}

    public static byte[] write(Delimiter delimiter, List<String> header, List<? extends List<?>> rows) {
        CSVFormat format = CSVFormat.RFC4180.builder()
                .setDelimiter((delimiter == null ? Delimiter.COMMA : delimiter).character)
                .setRecordSeparator("\r\n")
                .get();
        StringBuilder out = new StringBuilder(BYTE_ORDER_MARK);
        try (CSVPrinter printer = new CSVPrinter(out, format)) {
            printer.printRecord(header.stream().map(CsvExport::cell).toList());
            for (List<?> row : rows) {
                printer.printRecord(row.stream().map(CsvExport::cell).toList());
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        String text = value.toString();
        return !text.isEmpty() && FORMULA_TRIGGERS.indexOf(text.charAt(0)) >= 0 ? "'" + text : text;
    }

    public static String timestamp(Instant instant, ZoneId zone) {
        return instant == null ? null : TIMESTAMP.format(instant.atZone(zone));
    }

    public static ResponseEntity<byte[]> attachment(File file) {
        return new FileDownload(file.filename(), TEXT_CSV, file.content(), file.truncated()).attachment();
    }
}
