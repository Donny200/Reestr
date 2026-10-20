package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.FunctionStatus;
import adliya.uz.functioncatalogservice.entity.TranslatedText;
import adliya.uz.functioncatalogservice.entity.VerificationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record QualityQueue(boolean translationsChecked, List<String> activeLanguages, List<Item> items) {

    public record Item(Long functionId, String name, Map<String, TranslatedText> nameTranslations, FunctionStatus status,
                       Long organizationId, Long categoryId, String category, VerificationStatus verificationStatus,
                       Instant lastVerifiedAt, Instant verificationDueAt, String officialSourceUrl, List<QualityIssue> issues) {}
}
