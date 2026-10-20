package adliya.uz.referenceservice.dto;

public record TranslationCoverageResponse(
        String languageCode,
        long requiredKeys,
        long translatedKeys,
        long missingKeys,
        double percentage,
        boolean readyToPublish
) {
}
