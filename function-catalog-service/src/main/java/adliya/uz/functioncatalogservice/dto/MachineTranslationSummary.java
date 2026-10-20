package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.TranslationJob;
import adliya.uz.functioncatalogservice.entity.TranslationJobStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/** Staff-facing state of the background translation of one card: never the provider response or credentials. */
public record MachineTranslationSummary(
        /** True when a provider is configured and the worker is enabled. */
        boolean automatic,
        @JsonInclude(JsonInclude.Include.NON_NULL) TranslationJobStatus status,
        int attempts,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant requestedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant nextAttemptAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant finishedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String lastError,
        /** Active languages that still lack a complete translation (source language excluded). */
        List<String> missingLanguages,
        /** Active languages (source excluded) the provider cannot translate this card into; they need a manual translation. */
        List<String> unsupportedLanguages
) {
    public static MachineTranslationSummary of(boolean automatic, TranslationJob job, List<String> missingLanguages) {
        return of(automatic, job, missingLanguages, List.of());
    }

    public static MachineTranslationSummary of(boolean automatic, TranslationJob job, List<String> missingLanguages,
                                               List<String> unsupportedLanguages) {
        if (job == null) {
            return new MachineTranslationSummary(automatic, null, 0, null, null, null, null, missingLanguages, unsupportedLanguages);
        }
        return new MachineTranslationSummary(automatic, job.getStatus(), job.getAttempts(), job.getRequestedAt(),
                job.isOpen() ? job.getNextAttemptAt() : null, job.getFinishedAt(), job.getLastError(), missingLanguages,
                unsupportedLanguages);
    }
}
