package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.dto.FunctionCategoryRequest;
import adliya.uz.functioncatalogservice.entity.AuditAction;
import adliya.uz.functioncatalogservice.entity.FunctionCategory;
import adliya.uz.functioncatalogservice.entity.TranslatedText;
import adliya.uz.functioncatalogservice.entity.TranslationTarget;
import adliya.uz.functioncatalogservice.exception.WorkflowConflictException;
import adliya.uz.functioncatalogservice.repository.FunctionCategoryRepository;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FunctionCategoryService {

    private final FunctionCategoryRepository repository;
    private final OrgFunctionRepository functions;
    private final AuditWriter audit;
    private final MachineTranslationQueue translations;

    public List<FunctionCategory> getAll() {
        return repository.findAll();
    }

    public FunctionCategory get(Long id) {
        return repository.findById(id).orElseThrow(() -> new NoSuchElementException("Category not found: " + id));
    }

    public FunctionCategory resolve(Long id, String legacyName) {
        if (id != null) {
            FunctionCategory category = get(id);
            if (StringUtils.hasText(legacyName) && !legacyName.equals(category.getName())) {
                throw new IllegalArgumentException("category and categoryId refer to different categories");
            }
            return category;
        }
        if (!StringUtils.hasText(legacyName)) {
            return null;
        }
        return repository.findByName(legacyName).orElseThrow(
                () -> new IllegalArgumentException("Unknown category; create it with FUNCTION_CATEGORIES_MANAGE first"));
    }

    @Transactional
    public FunctionCategory create(FunctionCategoryRequest request) {
        checkUnique(request.name(), null);
        FunctionCategory category = FunctionCategory.builder()
                .name(request.name())
                .nameTranslations(TranslatedText.humanEdits(request.nameTranslations()))
                .sourceLanguage(sourceLanguage(request))
                .build();
        repository.saveAndFlush(category);
        audit.category(category.getId(), AuditAction.CREATE, "Category created: " + category.getName());
        translations.enqueue(TranslationTarget.CATEGORY, category.getId());
        return category;
    }

    @Transactional
    public FunctionCategory update(Long id, FunctionCategoryRequest request) {
        FunctionCategory category = get(id);
        checkUnique(request.name(), id);
        String previousName = category.getName();
        category.setName(request.name());
        if (request.nameTranslations() != null) {
            category.setNameTranslations(TranslatedText.humanEdits(request.nameTranslations()));
        } else if (!previousName.equals(request.name())) {
            category.setNameTranslations(OrgFunctionTranslationService.withoutMachineTranslations(category.getNameTranslations()));
        }
        if (request.sourceLanguage() != null) {
            category.setSourceLanguage(sourceLanguage(request));
        }
        repository.saveAndFlush(category);
        audit.category(id, AuditAction.UPDATE, "Category updated: " + previousName + " -> " + category.getName());
        translations.enqueue(TranslationTarget.CATEGORY, category.getId());
        return category;
    }

    @Transactional
    public void delete(Long id) {
        FunctionCategory category = get(id);
        if (functions.existsByFunctionCategory_Id(id)) {
            throw new WorkflowConflictException("Category is referenced by a function");
        }
        audit.category(id, AuditAction.DELETE, "Category deleted: " + category.getName());
        translations.remove(TranslationTarget.CATEGORY, id);
        repository.delete(category);
        repository.flush();
    }

    private static String sourceLanguage(FunctionCategoryRequest request) {
        return request.sourceLanguage() == null || request.sourceLanguage().isBlank()
                ? MissingTranslations.DEFAULT_CATEGORY_LANGUAGE
                : request.sourceLanguage().trim().toLowerCase(Locale.ROOT);
    }

    private void checkUnique(String name, Long exceptId) {
        repository.findByName(name)
                .filter(existing -> !Objects.equals(existing.getId(), exceptId))
                .ifPresent(existing -> {
                    throw new WorkflowConflictException("Category name already exists");
                });
    }
}
