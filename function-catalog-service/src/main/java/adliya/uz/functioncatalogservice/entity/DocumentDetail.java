package adliya.uz.functioncatalogservice.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import adliya.uz.functioncatalogservice.dto.OfficialSourceUrl;

/**
 * What the organization says about one line of "Required documents": which form of the document to bring, how many
 * copies, how long it stays valid or other preparation notes, and a link to its approved blank form. Every part is
 * optional; nothing is assumed when the organization did not provide it.
 *
 * @param document the line of {@code requiredDocuments} (source language) this detail belongs to
 * @param form     original, copy, both, a filled form or something else; null when not provided
 * @param copies   number of copies to bring, when the organization specifies it
 * @param validity validity period (e.g. "issued within the last 30 days"), when specified
 * @param notes    other preparation notes
 * @param formUrl  link to the officially approved blank form published by the organization; only when one exists
 */
public record DocumentDetail(
        @NotBlank @Size(max = 4000) String document,
        Form form,
        @Min(1) @Max(20) Integer copies,
        @Size(max = 200) String validity,
        @Size(max = 300) String notes,
        @Size(max = 500) @Pattern(regexp = OfficialSourceUrl.PATTERN, message = OfficialSourceUrl.MESSAGE) String formUrl
) {
    public static final int MAX_ITEMS = InstructionField.MAX_LIST_ITEMS;

    public enum Form { ORIGINAL, COPY, ORIGINAL_AND_COPY, FILLED_FORM, OTHER }

    /** True when the organization provided nothing beyond the document name. */
    public boolean empty() {
        return form == null && copies == null && blank(validity) && blank(notes) && blank(formUrl);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
