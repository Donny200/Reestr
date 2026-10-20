package adliya.uz.functioncatalogservice.service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * Format-neutral content of an exported report: the same document is rendered as an Excel workbook (one sheet per
 * section) and as a printable PDF, so both formats always carry the same filters, figures and rows.
 *
 * <p>Cell values are {@link String}, {@link Number}, {@link java.time.LocalDate}, {@link Instant} (shown in
 * {@link #zone()}) or null for "no value".</p>
 */
public record ReportDocument(String title, Instant generatedAt, ZoneId zone, Locale locale, List<Fact> facts,
                             List<String> notes, List<Section> sections, Labels labels) {

    public ReportDocument {
        facts = List.copyOf(facts);
        notes = List.copyOf(notes);
        sections = List.copyOf(sections);
    }

    /** One filter or property of the whole report, e.g. "Period: 2026-09-01 – 2026-09-30". */
    public record Fact(String label, String value) {}

    public enum Kind { TEXT, INTEGER, DATE, DATETIME, PERCENT }

    /** {@code width} is the relative PDF column width. */
    public record Column(String header, Kind kind, float width) {
        public static Column text(String header, float width) { return new Column(header, Kind.TEXT, width); }
        public static Column integer(String header) { return new Column(header, Kind.INTEGER, 1f); }
        public static Column date(String header) { return new Column(header, Kind.DATE, 1.2f); }
        public static Column dateTime(String header) { return new Column(header, Kind.DATETIME, 1.6f); }
        public static Column percent(String header) { return new Column(header, Kind.PERCENT, 1f); }
    }

    public record Table(String title, List<Column> columns, List<List<Object>> rows) {
        public Table {
            columns = List.copyOf(columns);
            rows = List.copyOf(rows);
        }
    }

    /** Bar chart drawn in the PDF above the section's tables; Excel users build their own from the rows. */
    public record Chart(String title, List<String> labels, List<Long> values) {
        public Chart {
            labels = List.copyOf(labels);
            values = List.copyOf(values);
        }
    }

    /** One worksheet in Excel and one titled part in the PDF. */
    public record Section(String sheetName, String title, String note, List<Table> tables, Chart chart) {
        public Section {
            tables = List.copyOf(tables);
        }

        public boolean empty() {
            return tables.stream().allMatch(table -> table.rows().isEmpty());
        }
    }

    /** Words the writers themselves need, already in the report language. */
    public record Labels(String empty, String generated, String page, String rowLimit) {}
}
