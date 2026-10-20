package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.entity.FunctionCategory;
import adliya.uz.functioncatalogservice.entity.FunctionStatus;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.TranslatedText;
import adliya.uz.functioncatalogservice.entity.TranslationTarget;
import adliya.uz.functioncatalogservice.repository.FunctionCategoryRepository;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Creates the editorial sample drafts once. Translations are not requested here: the draft is put on the background
 * translation queue, which fills every active language later and never calls the provider during start-up.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EditorialSeedService {

    private static final String SEED_LANGUAGE = "ru";

    private final OrgFunctionRepository functions;
    private final FunctionCategoryRepository categories;
    private final MachineTranslationQueue translations;
    private final AuditWriter audit;

    @Transactional
    public void seed(Seed seed) {
        OrgFunction function = functions.findBySeedKey(seed.key()).orElse(null);
        if (function == null) {
            function = createDraft(seed);
            translations.enqueue(TranslationTarget.FUNCTION, function.getId());
            return;
        }
        if (function.getStatus() != FunctionStatus.DRAFT
                || !Objects.equals(seed.name(), function.getName())
                || !Objects.equals(seed.description(), function.getDescription())) {
            return;
        }
        // Untouched draft from an earlier start-up: queue it once so missing languages get filled, never twice.
        translations.enqueueIfAbsent(TranslationTarget.FUNCTION, function.getId());
    }

    private OrgFunction createDraft(Seed seed) {
        FunctionCategory category = categories.findByName(seed.category()).orElseGet(() -> {
            FunctionCategory created = categories.save(FunctionCategory.builder()
                    .name(seed.category())
                    .nameTranslations(human(seed.category()))
                    .sourceLanguage(SEED_LANGUAGE)
                    .build());
            translations.enqueue(TranslationTarget.CATEGORY, created.getId());
            return created;
        });
        OrgFunction function = OrgFunction.builder()
                .seedKey(seed.key())
                .name(seed.name())
                .description(seed.description())
                .sourceLanguage(SEED_LANGUAGE)
                .requirements(seed.requirements())
                .functionCategory(category)
                .status(FunctionStatus.DRAFT)
                .nameTranslations(human(seed.name()))
                .descriptionTranslations(human(seed.description()))
                .build();
        functions.saveAndFlush(function);
        audit.seeded(function);
        return function;
    }

    private static Map<String, TranslatedText> human(String text) {
        Map<String, TranslatedText> values = new LinkedHashMap<>();
        values.put(SEED_LANGUAGE, new TranslatedText(text, TranslatedText.HUMAN));
        return values;
    }

    public record Seed(String key, String name, String description, String requirements, String category) {}
}
