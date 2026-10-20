package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.service.ReportDocument.Chart;
import adliya.uz.functioncatalogservice.service.ReportDocument.Column;
import adliya.uz.functioncatalogservice.service.ReportDocument.Section;
import adliya.uz.functioncatalogservice.service.ReportDocument.Table;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;

/**
 * Renders a {@link ReportDocument} as a printable A4 landscape PDF: title, filters, notes, then every section with its
 * optional bar chart and tables. Long tables repeat their header on each page and are cut at {@link #ROW_LIMIT} rows
 * with a note pointing to the Excel file, which always holds every row.
 */
public final class PdfReportWriter {

    static final int ROW_LIMIT = 1_000;

    private PdfReportWriter() {}

    public static byte[] write(ReportDocument document) {
        Formats formats = new Formats(document);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document pdf = new Document(PageSize.A4.rotate(), 36, 36, 40, 48);
        try {
            PdfWriter writer = PdfWriter.getInstance(pdf, out);
            PdfSupport.prepare(pdf, writer, document.title(), document.locale(), new PdfSupport.Footer(document.title() + " · "
                    + document.labels().generated() + " " + formats.dateTime(document.generatedAt()), document.labels().page(), 22, 7));
            pdf.open();

            Paragraph title = new Paragraph(document.title(), PdfFonts.bold(18));
            title.setSpacingAfter(8);
            pdf.add(title);
            pdf.add(facts(document));
            for (String note : document.notes()) {
                pdf.add(PdfSupport.paragraph(note, PdfFonts.regular(8.5f, PdfFonts.MUTED), 4));
            }
            for (Section section : document.sections()) {
                section(pdf, writer, section, document, formats);
            }
            pdf.close();
        } catch (DocumentException exception) {
            throw new IllegalStateException("Could not render the PDF report", exception);
        }
        return out.toByteArray();
    }

    private static PdfPTable facts(ReportDocument document) {
        PdfPTable table = new PdfPTable(new float[]{1.2f, 4f});
        table.setWidthPercentage(60);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setSpacingAfter(6);
        for (ReportDocument.Fact fact : document.facts()) {
            table.addCell(plain(fact.label(), PdfFonts.bold(9)));
            table.addCell(plain(fact.value(), PdfFonts.regular(9)));
        }
        return table;
    }

    private static void section(Document pdf, PdfWriter writer, Section section, ReportDocument document, Formats formats)
            throws DocumentException {
        keepWithNext(pdf, writer, section.chart() != null ? 220 : 90);
        Paragraph heading = new Paragraph(section.title(), PdfFonts.bold(13, PdfFonts.ACCENT));
        heading.setSpacingBefore(14);
        heading.setSpacingAfter(4);
        pdf.add(heading);
        if (section.note() != null) {
            pdf.add(PdfSupport.paragraph(section.note(), PdfFonts.regular(8.5f, PdfFonts.MUTED), 4));
        }
        if (section.empty()) {
            pdf.add(PdfSupport.paragraph(document.labels().empty(), PdfFonts.regular(9.5f, PdfFonts.MUTED), 4));
            return;
        }
        if (section.chart() != null && section.chart().values().stream().anyMatch(value -> value > 0)) {
            pdf.add(chart(writer, section.chart(), pdf.getPageSize().getWidth() - pdf.leftMargin() - pdf.rightMargin()));
        }
        for (Table table : section.tables()) {
            if (table.title() != null) {
                keepWithNext(pdf, writer, 70);
                Paragraph subtitle = new Paragraph(table.title(), PdfFonts.bold(10));
                subtitle.setSpacingBefore(6);
                subtitle.setSpacingAfter(3);
                pdf.add(subtitle);
            }
            if (table.rows().isEmpty()) {
                pdf.add(PdfSupport.paragraph(document.labels().empty(), PdfFonts.regular(9, PdfFonts.MUTED), 2));
                continue;
            }
            pdf.add(table(table, formats));
            if (table.rows().size() > ROW_LIMIT) {
                pdf.add(PdfSupport.paragraph(document.labels().rowLimit().replace("{count}", formats.integer(ROW_LIMIT)),
                        PdfFonts.regular(8.5f, PdfFonts.MUTED), 2));
            }
        }
    }

    /** Starts a new page instead of leaving a heading without its first rows at the bottom of the current one. */
    private static void keepWithNext(Document pdf, PdfWriter writer, float space) {
        if (writer.getVerticalPosition(true) - pdf.bottomMargin() < space) {
            pdf.newPage();
        }
    }

    private static PdfPTable table(Table table, Formats formats) {
        float[] widths = new float[table.columns().size()];
        for (int index = 0; index < widths.length; index++) {
            widths[index] = table.columns().get(index).width();
        }
        PdfPTable pdfTable = new PdfPTable(widths);
        pdfTable.setWidthPercentage(100);
        pdfTable.setHeaderRows(1);
        pdfTable.setSpacingAfter(4);
        pdfTable.setSplitLate(false);
        for (Column column : table.columns()) {
            PdfPCell cell = new PdfPCell(new Phrase(column.header(), PdfFonts.bold(7.5f)));
            cell.setLeading(0, 1.25f);
            cell.setBackgroundColor(PdfFonts.SHADE);
            cell.setBorderColor(PdfFonts.LINE);
            cell.setPadding(3.5f);
            cell.setHorizontalAlignment(numeric(column) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            pdfTable.addCell(cell);
        }
        int count = 0;
        for (List<Object> row : table.rows()) {
            if (count++ >= ROW_LIMIT) {
                break;
            }
            for (int index = 0; index < table.columns().size(); index++) {
                Column column = table.columns().get(index);
                Object value = index < row.size() ? row.get(index) : null;
                PdfPCell cell = new PdfPCell(new Phrase(formats.cell(value, column), PdfFonts.regular(7.5f)));
                cell.setLeading(0, 1.3f);
                cell.setBorderColor(PdfFonts.LINE);
                cell.setPadding(3.5f);
                cell.setHorizontalAlignment(numeric(column) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                pdfTable.addCell(cell);
            }
        }
        return pdfTable;
    }

    /** Simple bar chart drawn as vector graphics; at most ~20 axis labels are printed so they never overlap. */
    private static Image chart(PdfWriter writer, Chart chart, float width) throws DocumentException {
        float height = 150;
        float left = 34;
        float bottom = 22;
        float top = 18;
        PdfTemplate template = writer.getDirectContent().createTemplate(width, height);
        long max = chart.values().stream().mapToLong(Long::longValue).max().orElse(1);
        float plotWidth = width - left - 6;
        float plotHeight = height - bottom - top;
        int count = chart.values().size();
        float slot = plotWidth / Math.max(1, count);
        float bar = Math.max(1f, slot * 0.7f);

        template.setColorStroke(PdfFonts.LINE);
        template.setLineWidth(0.5f);
        template.moveTo(left, bottom);
        template.lineTo(left + plotWidth, bottom);
        template.moveTo(left, bottom + plotHeight);
        template.lineTo(left + plotWidth, bottom + plotHeight);
        template.stroke();

        template.setColorFill(PdfFonts.ACCENT);
        for (int index = 0; index < count; index++) {
            float value = chart.values().get(index);
            float barHeight = max == 0 ? 0 : plotHeight * value / max;
            if (barHeight > 0) {
                template.rectangle(left + index * slot + (slot - bar) / 2, bottom, bar, barHeight);
            }
        }
        template.fill();

        template.beginText();
        template.setColorFill(PdfFonts.MUTED);
        template.setFontAndSize(PdfFonts.regularBase(), 6.5f);
        template.showTextAligned(PdfContentByte.ALIGN_RIGHT, Long.toString(max), left - 4, bottom + plotHeight - 2, 0);
        template.showTextAligned(PdfContentByte.ALIGN_RIGHT, "0", left - 4, bottom - 2, 0);
        int step = Math.max(1, (int) Math.ceil(count / 20.0));
        for (int index = 0; index < count; index += step) {
            template.showTextAligned(PdfContentByte.ALIGN_CENTER, chart.labels().get(index), left + index * slot + slot / 2, bottom - 10, 0);
        }
        template.setFontAndSize(PdfFonts.regularBase(), 8);
        template.setColorFill(PdfFonts.TEXT);
        template.showTextAligned(PdfContentByte.ALIGN_LEFT, chart.title(), left, height - 10, 0);
        template.endText();

        Image image = Image.getInstance(template);
        image.setSpacingAfter(6);
        image.setAlt(chart.title());
        return image;
    }

    private static boolean numeric(Column column) {
        return column.kind() == ReportDocument.Kind.INTEGER || column.kind() == ReportDocument.Kind.PERCENT;
    }

    private static PdfPCell plain(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(2);
        return cell;
    }

    /** Locale-aware cell text for the PDF (Excel keeps typed values instead). */
    static final class Formats {
        private final ReportDocument document;
        private final NumberFormat integers;
        private final NumberFormat percents;
        private final DateTimeFormatter dates;
        private final DateTimeFormatter dateTimes;

        Formats(ReportDocument document) {
            this.document = document;
            Locale locale = document.locale();
            integers = NumberFormat.getIntegerInstance(locale);
            percents = NumberFormat.getNumberInstance(locale);
            percents.setMaximumFractionDigits(1);
            percents.setMinimumFractionDigits(1);
            dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
            dateTimes = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale);
        }

        String cell(Object value, Column column) {
            if (value == null) {
                return "—";
            }
            if (value instanceof Number number) {
                if (column.kind() == ReportDocument.Kind.PERCENT) {
                    double percent = number.doubleValue() * 100;
                    return (percent > 0 ? "+" : "") + PdfSupport.spaces(percents.format(percent)) + "%";
                }
                return integer(number.longValue());
            }
            if (value instanceof LocalDate date) {
                return PdfSupport.spaces(dates.format(date));
            }
            if (value instanceof Instant instant) {
                return dateTime(instant);
            }
            return value.toString();
        }

        String integer(long value) {
            return PdfSupport.spaces(integers.format(value));
        }

        String dateTime(Instant instant) {
            return PdfSupport.spaces(dateTimes.format(instant.atZone(document.zone())));
        }
    }
}
