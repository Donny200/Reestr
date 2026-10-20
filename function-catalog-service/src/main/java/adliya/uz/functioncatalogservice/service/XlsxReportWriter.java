package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.service.ReportDocument.Column;
import adliya.uz.functioncatalogservice.service.ReportDocument.Section;
import adliya.uz.functioncatalogservice.service.ReportDocument.Table;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Renders a {@link ReportDocument} as an .xlsx workbook: one sheet per section, typed cells (numbers stay numbers,
 * dates are real dates in the report time zone), a frozen header row and an auto-filter on every table. Text is always
 * written as a string cell, never as a formula, so values starting with "=" cannot execute in a spreadsheet.
 */
public final class XlsxReportWriter {

    private static final int MAX_COLUMN_WIDTH = 60;
    private static final int MAX_CELL_LENGTH = 32_767;

    private XlsxReportWriter() {}

    public static byte[] write(ReportDocument document) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            Set<String> names = new HashSet<>();
            for (int index = 0; index < document.sections().size(); index++) {
                Section section = document.sections().get(index);
                Sheet sheet = workbook.createSheet(uniqueName(section.sheetName(), names));
                int row = 0;
                row = title(sheet, row, index == 0 ? document.title() : section.title(), styles.title);
                if (index == 0) {
                    row = facts(sheet, row, document, styles);
                    for (String note : document.notes()) {
                        row = note(sheet, row, note, styles.note);
                    }
                }
                if (section.note() != null) {
                    row = note(sheet, row, section.note(), styles.note);
                }
                row++;
                int widest = 2;
                for (Table table : section.tables()) {
                    if (table.title() != null) {
                        row = title(sheet, row, table.title(), styles.subtitle);
                    }
                    int header = row;
                    row = table(sheet, row, table, document, styles);
                    widest = Math.max(widest, table.columns().size());
                    if (section.tables().size() == 1 && !table.rows().isEmpty()) {
                        sheet.createFreezePane(0, header + 1);
                        sheet.setAutoFilter(new CellRangeAddress(header, row - 1, 0, table.columns().size() - 1));
                    }
                    row++;
                }
                if (section.empty()) {
                    note(sheet, row, document.labels().empty(), styles.note);
                }
                for (int column = 0; column < widest; column++) {
                    sheet.setColumnWidth(column, width(section, column, index == 0 ? document : null) * 256);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static int title(Sheet sheet, int row, String text, CellStyle style) {
        Cell cell = sheet.createRow(row).createCell(0);
        cell.setCellValue(text);
        cell.setCellStyle(style);
        return row + 1;
    }

    private static int note(Sheet sheet, int row, String text, CellStyle style) {
        Cell cell = sheet.createRow(row).createCell(0);
        cell.setCellValue(clip(text));
        cell.setCellStyle(style);
        return row + 1;
    }

    private static int facts(Sheet sheet, int row, ReportDocument document, Styles styles) {
        for (ReportDocument.Fact fact : document.facts()) {
            Row line = sheet.createRow(row++);
            Cell label = line.createCell(0);
            label.setCellValue(fact.label());
            label.setCellStyle(styles.factLabel);
            line.createCell(1).setCellValue(clip(fact.value()));
        }
        return row;
    }

    private static int table(Sheet sheet, int row, Table table, ReportDocument document, Styles styles) {
        Row header = sheet.createRow(row++);
        for (int column = 0; column < table.columns().size(); column++) {
            Cell cell = header.createCell(column);
            cell.setCellValue(table.columns().get(column).header());
            cell.setCellStyle(styles.header);
        }
        for (List<Object> values : table.rows()) {
            Row line = sheet.createRow(row++);
            for (int column = 0; column < table.columns().size() && column < values.size(); column++) {
                cell(line.createCell(column), values.get(column), table.columns().get(column), document, styles);
            }
        }
        return row;
    }

    private static void cell(Cell cell, Object value, Column column, ReportDocument document, Styles styles) {
        if (value == null) {
            cell.setBlank();
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            cell.setCellStyle(column.kind() == ReportDocument.Kind.PERCENT ? styles.percent : styles.integer);
        } else if (value instanceof LocalDate date) {
            cell.setCellValue(date);
            cell.setCellStyle(styles.date);
        } else if (value instanceof Instant instant) {
            cell.setCellValue(instant.atZone(document.zone()).toLocalDateTime());
            cell.setCellStyle(styles.dateTime);
        } else {
            cell.setCellValue(clip(value.toString()));
            cell.setCellStyle(styles.text);
        }
    }

    private static int width(Section section, int column, ReportDocument withFacts) {
        int widest = 8;
        if (withFacts != null) {
            for (ReportDocument.Fact fact : withFacts.facts()) {
                widest = Math.max(widest, (column == 0 ? fact.label() : column == 1 ? fact.value() : "").length() + 2);
            }
        }
        for (Table table : section.tables()) {
            if (column >= table.columns().size()) {
                continue;
            }
            Column definition = table.columns().get(column);
            widest = Math.max(widest, definition.header().length() + 2);
            switch (definition.kind()) {
                case DATE -> widest = Math.max(widest, 12);
                case DATETIME -> widest = Math.max(widest, 18);
                default -> {
                    for (List<Object> row : table.rows()) {
                        Object value = column < row.size() ? row.get(column) : null;
                        if (value != null) {
                            widest = Math.max(widest, Math.min(MAX_COLUMN_WIDTH, value.toString().length() + 2));
                        }
                    }
                }
            }
        }
        return Math.min(widest, MAX_COLUMN_WIDTH);
    }

    private static String uniqueName(String requested, Set<String> used) {
        String base = WorkbookUtil.createSafeSheetName(requested == null || requested.isBlank() ? "Sheet" : requested.strip());
        String name = base;
        for (int suffix = 2; used.contains(name.toLowerCase(Locale.ROOT)); suffix++) {
            String tail = " (" + suffix + ")";
            name = base.substring(0, Math.min(base.length(), 31 - tail.length())) + tail;
        }
        used.add(name.toLowerCase(Locale.ROOT));
        return name;
    }

    private static String clip(String value) {
        return value == null || value.length() <= MAX_CELL_LENGTH ? value : value.substring(0, MAX_CELL_LENGTH);
    }

    private static final class Styles {
        final CellStyle title;
        final CellStyle subtitle;
        final CellStyle note;
        final CellStyle factLabel;
        final CellStyle header;
        final CellStyle text;
        final CellStyle integer;
        final CellStyle percent;
        final CellStyle date;
        final CellStyle dateTime;

        Styles(XSSFWorkbook workbook) {
            Font bold = workbook.createFont();
            bold.setBold(true);
            Font large = workbook.createFont();
            large.setBold(true);
            large.setFontHeightInPoints((short) 14);
            Font muted = workbook.createFont();
            muted.setItalic(true);
            muted.setColor(IndexedColors.GREY_50_PERCENT.getIndex());

            title = workbook.createCellStyle();
            title.setFont(large);
            subtitle = workbook.createCellStyle();
            subtitle.setFont(bold);
            note = workbook.createCellStyle();
            note.setFont(muted);
            factLabel = workbook.createCellStyle();
            factLabel.setFont(bold);

            header = workbook.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);
            header.setWrapText(true);
            header.setVerticalAlignment(VerticalAlignment.TOP);

            text = workbook.createCellStyle();
            text.setVerticalAlignment(VerticalAlignment.TOP);
            integer = workbook.createCellStyle();
            integer.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            percent = workbook.createCellStyle();
            percent.setDataFormat(workbook.createDataFormat().getFormat("+0.0%;-0.0%;0.0%"));
            date = workbook.createCellStyle();
            date.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            dateTime = workbook.createCellStyle();
            dateTime.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
        }
    }
}
