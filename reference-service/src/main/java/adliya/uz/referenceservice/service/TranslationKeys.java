package adliya.uz.referenceservice.service;

final class TranslationKeys {

    private static final int MAX_LENGTH = 150;

    private TranslationKeys() {
    }

    static String normalize(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new IllegalArgumentException("Translation key must not be blank");
        }
        String key = rawKey.trim();
        if (key.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Translation key is too long: " + key);
        }
        return key;
    }
}
