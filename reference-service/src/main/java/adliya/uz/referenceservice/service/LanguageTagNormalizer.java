package adliya.uz.referenceservice.service;

import org.springframework.stereotype.Component;

import java.util.IllformedLocaleException;
import java.util.Locale;

@Component
public class LanguageTagNormalizer {

    public String normalize(String rawLanguageCode) {
        if (rawLanguageCode == null || rawLanguageCode.isBlank()) {
            throw new IllegalArgumentException("Language code must not be blank");
        }

        String candidate = rawLanguageCode.trim().replace('_', '-');
        try {
            Locale locale = new Locale.Builder().setLanguageTag(candidate).build();
            String tag = locale.toLanguageTag();
            if (locale.getLanguage().isBlank() || "und".equalsIgnoreCase(tag)) {
                throw new IllegalArgumentException("Invalid language tag: " + rawLanguageCode);
            }
            if (tag.length() > 64) {
                throw new IllegalArgumentException("Language tag must be at most 64 characters: " + rawLanguageCode);
            }
            return tag;
        } catch (IllformedLocaleException exception) {
            throw new IllegalArgumentException("Invalid language tag: " + rawLanguageCode, exception);
        }
    }
}
