package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.AnalyticsProperties;
import adliya.uz.functioncatalogservice.entity.DocumentDetail;
import adliya.uz.functioncatalogservice.entity.InstructionField;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.service.InterfaceLabels.Labels;
import adliya.uz.functioncatalogservice.service.LocalizedContent.Instruction;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Printable PDF guide for one published service in the visitor's language, built only from what this site publishes:
 * the card, its instructions (translated where a translation exists, otherwise the original text with a note) and the
 * organization's public contact details. Empty sections are left out instead of being filled with placeholders.
 * Available to everyone without signing in, like the public card itself.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceGuideService {

    private static final float MIN_SPACE_BELOW_HEADING = 90;

    private final OrgFunctionService functions;
    private final PublicChangeService publicChanges;
    private final IdentityOrganizationClient organizations;
    private final InterfaceLabels interfaceLabels;
    private final AnalyticsProperties properties;

    @Value("${catalog.public-base-url:}")
    private String publicBaseUrl;

    public FileDownload guide(Long functionId, String language) {
        OrgFunction function = functions.getById(functionId);
        Labels labels = interfaceLabels.forLanguage(language == null ? function.getSourceLanguage() : language);
        String code = labels.language();
        var organization = organizations.findPublic(function.getOrganizationId());
        Instant lastUpdated = publicChanges.lastUpdatedAt(List.of(function.getId())).get(function.getId());

        Guide guide = new Guide(
                LocalizedContent.text(function.getName(), function.getNameTranslations(), code),
                organization.map(found -> LocalizedContent.text(found.name(), found.nameTranslations(), code)).orElse(null),
                function.getFunctionCategory() == null ? null : LocalizedContent.text(function.getFunctionCategory().getName(),
                        function.getFunctionCategory().getNameTranslations(), code),
                TextValues.blankToNull(LocalizedContent.text(function.getDescription(), function.getDescriptionTranslations(), code)),
                instruction(function, InstructionField.WHO_CAN_USE, code),
                instruction(function, InstructionField.STEPS, code),
                instruction(function, InstructionField.REQUIRED_DOCUMENTS, code),
                LocalizedContent.lines(function.getRequirements()),
                instruction(function, InstructionField.WHERE_HOW_TO_APPLY, code),
                instruction(function, InstructionField.PROCESSING_TIME, code),
                instruction(function, InstructionField.FEE, code),
                organization.map(IdentityOrganizationClient.PublicOrganization::contact).orElse(null),
                lastUpdated, function.getLastVerifiedAt(), function.getId(), function.getRequiredDocuments(),
                function.documentDetailsOrEmpty());
        String filename = "service-guide-" + function.getId() + "-" + code + ".pdf";
        return new FileDownload(filename, FileDownload.PDF, render(guide, labels), false);
    }

    private static Instruction instruction(OrgFunction function, InstructionField field, String language) {
        return LocalizedContent.instruction(function, field, language);
    }

    private byte[] render(Guide guide, Labels labels) {
        Locale locale = labels.locale();
        DateTimeFormatter dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).withZone(properties.getZone());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document pdf = new Document(PageSize.A4, 48, 48, 48, 56);
        try {
            PdfWriter writer = PdfWriter.getInstance(pdf, out);
            String generated = labels.get("guide.generatedOn", "Downloaded on") + " " + format(dates, Instant.now());
            String site = PublicSiteUrl.page(publicBaseUrl, "/functions/" + guide.functionId());
            PdfSupport.prepare(pdf, writer, guide.name(), locale, new PdfSupport.Footer(site == null ? generated : generated + " · " + site,
                    labels.get("export.page", "Page"), 26, 7.5f));
            pdf.open();

            pdf.add(PdfSupport.paragraph(labels.get("app.name", "ReestrGov") + " · " + labels.get("guide.title", "Service guide"),
                    PdfFonts.regular(9, PdfFonts.MUTED), 6));
            pdf.add(PdfSupport.paragraph(guide.name(), PdfFonts.bold(20), 4));
            if (guide.organization() != null) {
                pdf.add(PdfSupport.paragraph(guide.organization(), PdfFonts.regular(12, PdfFonts.ACCENT), 6));
            }
            List<String[]> meta = new ArrayList<>();
            if (guide.category() != null) {
                meta.add(new String[]{labels.get("field.category", "Category"), guide.category()});
            }
            if (guide.lastUpdated() != null) {
                meta.add(new String[]{labels.get("history.lastUpdated", "Last updated"), format(dates, guide.lastUpdated())});
            }
            if (guide.lastVerified() != null) {
                meta.add(new String[]{labels.get("verification.lastVerified", "Last verified"), format(dates, guide.lastVerified())});
            }
            if (!meta.isEmpty()) {
                pdf.add(facts(meta));
            }
            boolean machine = Stream.of(guide.whoCanUse(), guide.steps(), guide.documents(), guide.whereHow(), guide.time(), guide.fee())
                    .anyMatch(value -> value != null && value.machine());
            boolean fallback = Stream.of(guide.whoCanUse(), guide.steps(), guide.documents(), guide.whereHow(), guide.time(), guide.fee())
                    .anyMatch(value -> value != null && value.fallback());
            if (machine) {
                pdf.add(PdfSupport.paragraph(labels.get("fn.machineTranslated",
                        "Parts of this page were translated automatically and may contain mistakes."), PdfFonts.regular(9, PdfFonts.MUTED), 2));
            }
            if (fallback) {
                pdf.add(PdfSupport.paragraph(labels.get("fn.originalLanguageShown",
                        "Some details are shown in the original language because a translation is not available yet."),
                        PdfFonts.regular(9, PdfFonts.MUTED), 2));
            }

            if (guide.description() != null) {
                heading(pdf, writer, labels.get("fn.aboutTitle", "About this service"));
                pdf.add(body(guide.description()));
            }
            if (guide.whoCanUse() != null) {
                heading(pdf, writer, labels.get("instructions.whoCanUse", "Who can use this service"));
                pdf.add(body(guide.whoCanUse().text()));
            }
            List<String> steps = guide.steps() == null ? List.of() : LocalizedContent.lines(guide.steps().text());
            if (!steps.isEmpty()) {
                heading(pdf, writer, labels.get("instructions.steps", "Step-by-step procedure"));
                pdf.add(list(steps, true));
            }
            List<String> documents = guide.documents() == null ? List.of() : LocalizedContent.lines(guide.documents().text());
            if (!documents.isEmpty()) {
                heading(pdf, writer, labels.get("instructions.requiredDocuments", "Required documents"));
                pdf.add(list(DocumentDetails.describeAll(documents, LocalizedContent.lines(guide.sourceDocuments()),
                        guide.documentDetails(), labels), false));
            }
            if (!guide.requirements().isEmpty()) {
                heading(pdf, writer, documents.isEmpty() ? labels.get("field.requirements", "Requirements")
                        : labels.get("instructions.additionalRequirements", "Additional requirements"));
                pdf.add(list(guide.requirements(), false));
            }
            List<String[]> practical = new ArrayList<>();
            if (guide.fee() != null) {
                practical.add(new String[]{labels.get("instructions.fee", "Official fee"), guide.fee().text()});
            }
            if (guide.time() != null) {
                practical.add(new String[]{labels.get("instructions.processingTime", "Processing time"), guide.time().text()});
            }
            if (!practical.isEmpty()) {
                heading(pdf, writer, labels.get("guide.costAndTime", "Cost and time"));
                pdf.add(facts(practical));
            }
            if (guide.whereHow() != null) {
                heading(pdf, writer, labels.get("instructions.whereHowToApply", "Where and how to apply"));
                pdf.add(body(guide.whereHow().text()));
            }
            List<String[]> contacts = new ArrayList<>();
            if (guide.contact() != null) {
                addIfPresent(contacts, labels.get("contact.address", "Address"), guide.contact().address());
                addIfPresent(contacts, labels.get("contact.phone", "Phone"), guide.contact().phone());
                addIfPresent(contacts, labels.get("contact.workingHours", "Working hours"), guide.contact().workingHours());
            }
            if (!contacts.isEmpty()) {
                heading(pdf, writer, labels.get("guide.contacts", "Contacts of the organization"));
                pdf.add(facts(contacts));
            }

            List<String> checklist = new ArrayList<>();
            checklist.addAll(documents);
            checklist.addAll(guide.requirements());
            checklist.addAll(steps);
            if (!checklist.isEmpty()) {
                heading(pdf, writer, labels.get("checklist.title", "Your preparation checklist"));
                pdf.add(checklist(checklist));
                pdf.add(PdfSupport.paragraph(labels.get("checklist.disclaimer", "This checklist is a preparation aid. It is not an application "
                        + "and not an official confirmation that you are eligible."), PdfFonts.regular(9, PdfFonts.MUTED), 2));
            }
            Paragraph source = PdfSupport.paragraph(labels.get("guide.sourceNote", "This guide contains the information published on this "
                    + "site at the time of download. Confirm details with the organization before you apply."),
                    PdfFonts.regular(8.5f, PdfFonts.MUTED), 0);
            source.setSpacingBefore(16);
            pdf.add(source);
            pdf.close();
        } catch (DocumentException exception) {
            throw new IllegalStateException("Could not render the service guide", exception);
        }
        return out.toByteArray();
    }

    /** Starts a new page instead of leaving a heading alone at the bottom of the current one. */
    private static void heading(Document pdf, PdfWriter writer, String text) throws DocumentException {
        if (writer.getVerticalPosition(true) - pdf.bottomMargin() < MIN_SPACE_BELOW_HEADING) {
            pdf.newPage();
        }
        Paragraph heading = new Paragraph(text, PdfFonts.bold(12.5f, PdfFonts.ACCENT));
        heading.setSpacingBefore(12);
        heading.setSpacingAfter(4);
        heading.setKeepTogether(true);
        pdf.add(heading);
    }

    private static Paragraph body(String text) {
        Paragraph paragraph = new Paragraph(text, PdfFonts.regular(10.5f));
        paragraph.setLeading(15);
        return paragraph;
    }

    private static PdfPTable list(List<String> items, boolean numbered) {
        PdfPTable table = new PdfPTable(new float[]{0.6f, 12f});
        table.setWidthPercentage(100);
        for (int index = 0; index < items.size(); index++) {
            table.addCell(plain(numbered ? (index + 1) + "." : "•", PdfFonts.bold(10.5f), Element.ALIGN_RIGHT));
            table.addCell(plain(items.get(index), PdfFonts.regular(10.5f), Element.ALIGN_LEFT));
        }
        return table;
    }

    /** Empty bordered squares to tick on paper, one per document, requirement and step. */
    private static PdfPTable checklist(List<String> items) {
        PdfPTable table = new PdfPTable(new float[]{0.55f, 12f});
        table.setWidthPercentage(100);
        for (String item : items) {
            PdfPTable box = new PdfPTable(1);
            box.setTotalWidth(10);
            box.setLockedWidth(true);
            PdfPCell square = new PdfPCell(new Phrase(" ", PdfFonts.regular(6)));
            square.setFixedHeight(10);
            square.setBorderColor(PdfFonts.TEXT);
            square.setBorderWidth(0.8f);
            box.addCell(square);
            PdfPCell holder = new PdfPCell(box);
            holder.setBorder(Rectangle.NO_BORDER);
            holder.setPaddingTop(4);
            holder.setPaddingBottom(4);
            table.addCell(holder);
            table.addCell(plain(item, PdfFonts.regular(10.5f), Element.ALIGN_LEFT));
        }
        return table;
    }

    private static PdfPTable facts(List<String[]> rows) {
        PdfPTable table = new PdfPTable(new float[]{1.3f, 3.7f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(2);
        for (String[] row : rows) {
            PdfPCell label = plain(row[0], PdfFonts.bold(10), Element.ALIGN_LEFT);
            label.setBackgroundColor(PdfFonts.SHADE);
            label.setBorder(Rectangle.BOTTOM);
            label.setBorderColor(PdfFonts.LINE);
            PdfPCell value = plain(row[1], PdfFonts.regular(10.5f), Element.ALIGN_LEFT);
            value.setBorder(Rectangle.BOTTOM);
            value.setBorderColor(PdfFonts.LINE);
            table.addCell(label);
            table.addCell(value);
        }
        return table;
    }

    private static PdfPCell plain(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(new Chunk(text, font)));
        cell.setLeading(0, 1.3f);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(4);
        cell.setHorizontalAlignment(alignment);
        return cell;
    }

    private static void addIfPresent(List<String[]> rows, String label, String value) {
        if (value != null && !value.isBlank()) {
            rows.add(new String[]{label, value.strip()});
        }
    }

    private static String format(DateTimeFormatter formatter, Instant instant) {
        return PdfSupport.spaces(formatter.format(instant));
    }

    private record Guide(String name, String organization, String category, String description, Instruction whoCanUse,
                         Instruction steps, Instruction documents, List<String> requirements, Instruction whereHow,
                         Instruction time, Instruction fee, IdentityOrganizationClient.Contact contact,
                         Instant lastUpdated, Instant lastVerified, Long functionId, String sourceDocuments,
                         List<DocumentDetail> documentDetails) {}
}
