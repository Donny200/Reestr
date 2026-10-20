package adliya.uz.task1.dto;

public record LanguageSearchResult(
        String code,
        String name,
        String nativeName,
        boolean alreadyAdded
) {
}
