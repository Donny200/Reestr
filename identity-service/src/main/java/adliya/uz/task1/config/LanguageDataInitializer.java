package adliya.uz.task1.config;

import adliya.uz.task1.entity.Language;
import adliya.uz.task1.repository.LanguageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class LanguageDataInitializer implements CommandLineRunner {

    private static final List<String> DEFAULT_CODES = List.of("en", "ru", "uz");
    private static final String DEFAULT_LANGUAGE = "en";

    private final LanguageRepository languageRepository;

    @Override
    public void run(String... args) {
        DEFAULT_CODES.forEach(this::ensureActive);
        if (languageRepository.findByIsDefaultTrueAndActiveTrue().isEmpty()) {
            languageRepository.findByCode(DEFAULT_LANGUAGE).ifPresent(language -> {
                language.setDefault(true);
                languageRepository.save(language);
            });
        }
    }

    private void ensureActive(String code) {
        Language language = languageRepository.findByCode(code).orElseGet(() -> {
            Locale locale = Locale.forLanguageTag(code);
            return Language.builder()
                    .code(code)
                    .nameNative(locale.getDisplayLanguage(locale))
                    .build();
        });
        language.setActive(true);
        languageRepository.save(language);
    }
}
