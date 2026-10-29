package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.dto.OfficialSourceUrl;
import adliya.uz.functioncatalogservice.entity.DocumentDetail;
import adliya.uz.functioncatalogservice.exception.InvalidFieldException;

import java.util.ArrayList;
import java.util.List;

/** Normalization of structured document details: trimmed text, empty text as null, absolute http(s) form links only. */
public final class DocumentDetails {

    private DocumentDetails() {}

    public static DocumentDetail normalize(DocumentDetail detail) {
        String document = detail.document() == null ? "" : detail.document().strip();
        if (document.isEmpty()) {
            throw new InvalidFieldException("documentDetails", "Each document detail must name its document");
        }
        String formUrl = text(detail.formUrl());
        if (formUrl != null && !OfficialSourceUrl.isWebLink(formUrl)) {
            throw new InvalidFieldException("documentDetails", "The form link " + OfficialSourceUrl.MESSAGE);
        }
        Integer copies = detail.copies();
        if (copies != null && (copies < 1 || copies > 20)) {
            throw new InvalidFieldException("documentDetails", "The number of copies must be between 1 and 20");
        }
        String id = detail.id() == null || !detail.id().matches(DocumentDetail.ID_PATTERN) ? null : detail.id();
        return new DocumentDetail(document, detail.form(), copies, text(detail.validity()), text(detail.notes()), formUrl, id);
    }

    /**
     * Document lines (in the reader's language) with what the organization said about each: form, copies, validity,
     * notes and the approved form link. Details are matched by the source-language line; translated lines are paired
     * by position only when both lists have the same length.
     */
    public static List<String> describeAll(List<String> shown, List<String> source, List<DocumentDetail> details,
                                           InterfaceLabels.Labels labels) {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < shown.size(); index++) {
            String sourceLine = source.size() == shown.size() ? source.get(index) : shown.get(index);
            DocumentDetail detail = details.stream().filter(item -> item.document().equals(sourceLine)).findFirst().orElse(null);
            String suffix = detail == null ? null : describe(detail, labels);
            result.add(suffix == null ? shown.get(index) : shown.get(index) + " — " + suffix);
        }
        return result;
    }

    static String describe(DocumentDetail detail, InterfaceLabels.Labels labels) {
        List<String> parts = new ArrayList<>();
        if (detail.form() != null) {
            parts.add(labels.get("documents.form." + detail.form().name(), switch (detail.form()) {
                case ORIGINAL -> "Original";
                case COPY -> "Copy";
                case ORIGINAL_AND_COPY -> "Original and copy";
                case FILLED_FORM -> "Filled-in form";
                case OTHER -> "Other item";
            }));
        }
        if (detail.copies() != null) {
            parts.add(labels.get("documents.copies", "Copies") + ": " + detail.copies());
        }
        if (detail.validity() != null) {
            parts.add(labels.get("documents.validity", "Validity") + ": " + detail.validity());
        }
        if (detail.notes() != null) {
            parts.add(detail.notes());
        }
        if (detail.formUrl() != null) {
            parts.add(labels.get("documents.formLink", "Approved form") + ": " + detail.formUrl());
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    private static String text(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip().replaceAll("[\\p{Cntrl}&&[^\\n]]", "");
        return stripped.isEmpty() ? null : stripped;
    }
}
