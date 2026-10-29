package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.entity.DocumentDetail;
import adliya.uz.functioncatalogservice.entity.InstructionField;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.ReviewSection;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Stable paths of the reviewable parts of a card, the editor step each belongs to and the fingerprint of its current
 * value. The same paths are used by the frontend editor:
 * <ul>
 *     <li>{@code section.<step>} — a whole step (e.g. {@code section.mechanism});</li>
 *     <li>a field, e.g. {@code name}, {@code instructions.fee}, {@code profile.riskLevel};</li>
 *     <li>{@code documentDetails.<id>} — one structured document item, by its stable id.</li>
 * </ul>
 */
public final class ReviewFields {

    public static final String SECTION_PREFIX = "section.";
    public static final String DOCUMENT_PREFIX = "documentDetails.";
    static final String REMOVED = "\u0000removed";

    private static final Map<String, Field> FIELDS = new LinkedHashMap<>();

    static {
        add("name", ReviewSection.GENERAL, OrgFunction::getName);
        add("description", ReviewSection.GENERAL, OrgFunction::getDescription);
        add("organizationId", ReviewSection.GENERAL, function -> text(function.getOrganizationId()));
        add("categoryId", ReviewSection.GENERAL,
                function -> function.getFunctionCategory() == null ? null : text(function.getFunctionCategory().getId()));
        add("sourceLanguage", ReviewSection.GENERAL, OrgFunction::getSourceLanguage);
        for (InstructionField field : List.of(InstructionField.WHO_CAN_USE, InstructionField.STEPS, InstructionField.REQUIRED_DOCUMENTS,
                InstructionField.PROCESSING_TIME, InstructionField.FEE)) {
            add("instructions." + field.key(), ReviewSection.MECHANISM, field::get);
        }
        add("profile.deliveryChannel", ReviewSection.DIGITALIZATION, function -> text(function.getDeliveryChannel()));
        add("profile.onlineServiceUrl", ReviewSection.DIGITALIZATION, OrgFunction::getOnlineServiceUrl);
        add("instructions." + InstructionField.WHERE_HOW_TO_APPLY.key(), ReviewSection.DIGITALIZATION, InstructionField.WHERE_HOW_TO_APPLY::get);
        add("profile.provisionLevel", ReviewSection.DECENTRALIZATION, function -> text(function.getProvisionLevel()));
        add("profile.riskLevel", ReviewSection.DECENTRALIZATION, function -> text(function.getRiskLevel()));
        add("profile.riskNotes", ReviewSection.DECENTRALIZATION, OrgFunction::getRiskNotes);
        add("requirements", ReviewSection.OTHER, OrgFunction::getRequirements);
        add("officialSourceUrl", ReviewSection.OTHER, OrgFunction::getOfficialSourceUrl);
    }

    private ReviewFields() {}

    private record Field(ReviewSection section, Function<OrgFunction, String> value) {}

    private static void add(String path, ReviewSection section, Function<OrgFunction, String> value) {
        FIELDS.put(path, new Field(section, value));
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * The section a path belongs to, when the path names a section, a known field or an existing document item of
     * {@code function}; empty for anything else.
     */
    public static Optional<ReviewSection> sectionOf(String path, OrgFunction function) {
        if (path.startsWith(SECTION_PREFIX)) {
            return ReviewSection.byKey(path.substring(SECTION_PREFIX.length()));
        }
        if (path.startsWith(DOCUMENT_PREFIX)) {
            return document(function, path.substring(DOCUMENT_PREFIX.length())).map(ignored -> ReviewSection.MECHANISM);
        }
        return Optional.ofNullable(FIELDS.get(path)).map(Field::section);
    }

    /** SHA-256 of the current value at {@code path}; a removed document item has its own constant fingerprint. */
    public static String fingerprint(OrgFunction function, String path) {
        return Sha256.hex(canonical(function, path).getBytes(StandardCharsets.UTF_8));
    }

    private static String canonical(OrgFunction function, String path) {
        if (path.startsWith(SECTION_PREFIX)) {
            ReviewSection section = ReviewSection.byKey(path.substring(SECTION_PREFIX.length())).orElseThrow();
            StringBuilder text = new StringBuilder();
            FIELDS.forEach((field, meta) -> {
                if (meta.section() == section) {
                    text.append(field).append('=').append(normalize(meta.value().apply(function))).append('\n');
                }
            });
            if (section == ReviewSection.MECHANISM) {
                function.documentDetailsOrEmpty().forEach(detail -> text.append(DOCUMENT_PREFIX).append(detail.id()).append('=')
                        .append(describe(detail)).append('\n'));
            }
            return text.toString();
        }
        if (path.startsWith(DOCUMENT_PREFIX)) {
            return document(function, path.substring(DOCUMENT_PREFIX.length())).map(ReviewFields::describe).orElse(REMOVED);
        }
        Field field = FIELDS.get(path);
        return field == null ? REMOVED : normalize(field.value().apply(function));
    }

    private static Optional<DocumentDetail> document(OrgFunction function, String id) {
        return function.documentDetailsOrEmpty().stream().filter(detail -> Objects.equals(detail.id(), id)).findFirst();
    }

    private static String describe(DocumentDetail detail) {
        return String.join("|", normalize(detail.document()), String.valueOf(detail.form()), String.valueOf(detail.copies()),
                normalize(detail.validity()), normalize(detail.notes()), normalize(detail.formUrl()));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
