package adliya.uz.functioncatalogservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** One anonymous answer to "Was this information helpful?" for a published card. */
public record FeedbackVoteRequest(@NotNull @Positive Long serviceId, @NotNull Boolean helpful) {}
