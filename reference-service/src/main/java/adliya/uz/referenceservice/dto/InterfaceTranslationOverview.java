package adliya.uz.referenceservice.dto;

import adliya.uz.referenceservice.entity.InterfaceTranslationJob;
import adliya.uz.referenceservice.entity.InterfaceTranslationJobStatus;
import adliya.uz.referenceservice.service.InterfaceCoverageService.LanguageCoverage;

import java.time.Instant;
import java.util.List;

/**
 * Coverage of every active interface language and the state of its machine translation job. Contains no provider
 * credentials: {@code providerConfigured} only says whether all three AZURE_TRANSLATOR_* values are set.
 */
public record InterfaceTranslationOverview(
        boolean automatic,
        boolean providerConfigured,
        boolean workerEnabled,
        boolean activeLanguagesAvailable,
        List<LanguageStatus> languages
) {
    private static final int SAMPLE_SIZE = 25;

    public record LanguageStatus(
            String languageCode,
            String providerCode,
            boolean supported,
            boolean englishBase,
            long requiredKeys,
            long translatedKeys,
            long missingKeys,
            double percentage,
            long seedKeys,
            long humanKeys,
            long machineKeys,
            long staleMachineKeys,
            long outdatedHumanKeys,
            List<String> missingSample,
            Job job
    ) {
        public static LanguageStatus of(LanguageCoverage coverage, String providerCode, InterfaceTranslationJob job) {
            return new LanguageStatus(coverage.languageCode(), providerCode, providerCode != null, coverage.englishBase(),
                    coverage.requiredKeys(), coverage.translatedKeys(), coverage.missingKeys().size(), coverage.percentage(),
                    coverage.seedKeys(), coverage.humanKeys(), coverage.machineKeys(), coverage.staleMachineKeys().size(),
                    coverage.outdatedHumanKeys().size(),
                    coverage.missingKeys().stream().limit(SAMPLE_SIZE).toList(), Job.of(job));
        }
    }

    public record Job(InterfaceTranslationJobStatus status, String providerCode, int attempts, int missingKeys,
                      int translatedKeys, Instant requestedAt, Instant nextAttemptAt, Instant finishedAt, String lastError) {
        public static Job of(InterfaceTranslationJob job) {
            return job == null ? null : new Job(job.getStatus(), job.getProviderCode(), job.getAttempts(), job.getMissingKeys(),
                    job.getTranslatedKeys(), job.getRequestedAt(), job.getNextAttemptAt(), job.getFinishedAt(), job.getLastError());
        }
    }
}
