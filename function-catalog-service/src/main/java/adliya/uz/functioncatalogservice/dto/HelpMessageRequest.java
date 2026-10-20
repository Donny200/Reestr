package adliya.uz.functioncatalogservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A staff reply; {@code clientMessageId} makes retries return the stored reply instead of posting it twice. */
public record HelpMessageRequest(@NotBlank @Size(max = 2000) String message,
                                 @Pattern(regexp = SupportDtos.CLIENT_ID_PATTERN) String clientMessageId) {}
