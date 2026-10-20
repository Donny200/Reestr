package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.dto.*;
import adliya.uz.functioncatalogservice.dto.FunctionImportResponse.RowError;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class FunctionImportService {
    private static final Set<String> COLUMNS = Set.of("name", "description", "organizationId", "requirements", "category", "categoryId");
    private final Validator validator;
    private final FunctionImportRowService rows;
    private final AuditWriter audit;

    public FunctionImportResponse importFile(MultipartFile file) {
        var actor = audit.actor();
        var records = parse(file);
        Long auditId = audit.startImport(actor);
        List<Long> ids = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        try {
            for (var record : records) {
                try {
                    if (!record.values().isConsistent()) throw new IllegalArgumentException("Column count does not match the header");
                    var csv = record.values();
                    var request = new CreateOrgFunctionRequest(value(csv, "name"), value(csv, "description"),
                            positiveId(value(csv, "organizationId"), "organizationId"), value(csv, "requirements"),
                            value(csv, "category"), positiveId(value(csv, "categoryId"), "categoryId"));
                    var violations = validator.validate(request);
                    if (!violations.isEmpty()) throw new IllegalArgumentException(violations.stream()
                            .map(v -> v.getPropertyPath() + ": " + v.getMessage()).sorted()
                            .reduce((a,b) -> a + "; " + b).orElse("Invalid row"));
                    ids.add(rows.persist(request, auditId));
                } catch (IllegalArgumentException | NoSuchElementException | AccessDeniedException exception) {
                    errors.add(new RowError(record.line(), exception.getMessage()));
                } catch (DataIntegrityViolationException exception) {
                    errors.add(new RowError(record.line(), "Database uniqueness or reference constraint"));
                } catch (ResponseStatusException exception) {
                    errors.add(new RowError(record.line(), exception.getReason()));
                }
            }
        } finally {
            audit.finishImport(auditId, errors.size());
        }
        return new FunctionImportResponse(auditId, ids.size(), errors.size(), List.copyOf(ids), List.copyOf(errors));
    }

    private List<SourceRecord> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("A nonempty CSV file is required");
        if (file.getSize() > 1_048_576) throw new IllegalArgumentException("CSV must not exceed 1 MiB");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(file.getBytes())).toString();
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            var format = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true)
                    .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW).setIgnoreEmptyLines(false).get();
            try (var parser = format.parse(new StringReader(text))) {
                var headers = parser.getHeaderNames();
                if (!headers.contains("name")) throw new IllegalArgumentException("CSV header requires name; organizationId may be empty only for global editors");
                if (!COLUMNS.containsAll(headers)) throw new IllegalArgumentException("Supported columns: " + COLUMNS);
                List<SourceRecord> result = new ArrayList<>();
                long line = 1;
                int cursor = 0;
                for (var record : parser) {
                    if (result.size() >= 10_000) throw new IllegalArgumentException("CSV must not exceed 10000 records");
                    for (int i = cursor; i < record.getCharacterPosition(); i++) {
                        if (text.charAt(i) == '\n' || (text.charAt(i) == '\r' && (i + 1 == text.length() || text.charAt(i + 1) != '\n'))) line++;
                    }
                    cursor = (int) record.getCharacterPosition();
                    result.add(new SourceRecord(line, record));
                }
                return result;
            }
        } catch (IOException | UncheckedIOException exception) {
            throw new IllegalArgumentException("Invalid UTF-8 or malformed CSV: " + exception.getMessage());
        }
    }

    private String value(CSVRecord row, String column) {
        if (!row.isMapped(column)) return null;
        String value = row.get(column);
        return value.isEmpty() ? null : value;
    }
    private Long positiveId(String value, String column) {
        if (value == null || value.isBlank()) return null;
        try {
            long id = Long.parseLong(value.trim());
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) { throw new IllegalArgumentException(column + " must be a positive integer"); }
    }
    private record SourceRecord(long line, CSVRecord values) {}
}
