package adliya.uz.functioncatalogservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A reviewer's correction request for one field or section, identified by its stable path. */
public record ReviewCommentRequest(
        @NotBlank @Size(max = 200) @Pattern(regexp = "[A-Za-z0-9._-]+") String fieldPath,
        @NotBlank @Size(max = 2000) @Pattern(regexp = ".*\\S.*", flags = Pattern.Flag.DOTALL) String comment
) {}
