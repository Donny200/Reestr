package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.dto.AutoTranslateRequest;
import adliya.uz.functioncatalogservice.entity.AuditAction;
import adliya.uz.functioncatalogservice.entity.InstructionField;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.TranslatedText;
import adliya.uz.functioncatalogservice.exception.TranslationUnavailableException;
import adliya.uz.functioncatalogservice.exception.WorkflowConflictException;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import adliya.uz.functioncatalogservice.security.CatalogAccess;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FunctionAutoTranslationService {

    private final OrgFunctionRepository functions;
    private final CatalogAccess access;
    private final TranslationClient translator;
    private final AuditWriter audit;
    private final TransactionTemplate transaction;

    @PersistenceContext
    private EntityManager entityManager;

    public boolean available() {
        return translator.isAvailable();
    }

    public OrgFunction translate(Long id, AutoTranslateRequest request) {
        access.requireTranslationEditor();

        Snapshot snapshot = Objects.requireNonNull(transaction.execute(status -> snapshotOf(requireTranslatable(access, findFunction(id)))));
        if (!available()) {
            throw new TranslationUnavailableException(HttpStatus.SERVICE_UNAVAILABLE, "Automatic translation is not configured");
        }

        List<String> targets = request.languages().stream()
                .map(code -> code.toLowerCase(Locale.ROOT))
                .distinct()
                .filter(code -> !code.equals(snapshot.language()))
                .toList();
        Map<String, String> names = generate(snapshot.name(), snapshot.names(), targets,
                snapshot.language(), request.overwriteMachine(), MachineTranslationWorker.NAME_MAX_LENGTH);
        Map<String, String> descriptions = generate(snapshot.description(), snapshot.descriptions(), targets,
                snapshot.language(), request.overwriteMachine(), MachineTranslationWorker.DESCRIPTION_MAX_LENGTH);
        Map<InstructionField, Map<String, String>> instructions = new EnumMap<>(InstructionField.class);
        for (InstructionField field : InstructionField.values()) {
            Map<String, String> generated = generate(snapshot.instructions().get(field),
                    snapshot.instructionTranslations().getOrDefault(field, Map.of()), targets,
                    snapshot.language(), request.overwriteMachine(), field.maxLength());
            Map<String, String> normalized = new LinkedHashMap<>();
            generated.forEach((code, text) -> normalized.put(code, normalizedTranslation(field, text)));
            if (!normalized.isEmpty()) {
                instructions.put(field, normalized);
            }
        }

        return Objects.requireNonNull(transaction.execute(status -> apply(id, snapshot, targets, names, descriptions, instructions)));
    }

    private OrgFunction apply(Long id, Snapshot snapshot, List<String> targets, Map<String, String> names,
                              Map<String, String> descriptions, Map<InstructionField, Map<String, String>> instructions) {
        OrgFunction function = findFunction(id);
        entityManager.refresh(function);
        access.requireOrganization(function.getOrganizationId());
        if (!Objects.equals(snapshot.version(), function.getVersion()) || !function.isEditable()) {
            throw new WorkflowConflictException("Card changed while translating; reload and retry");
        }
        if (names.isEmpty() && descriptions.isEmpty() && instructions.isEmpty()) {
            return function;
        }
        function.setNameTranslations(merged(function.getNameTranslations(), names));
        function.setDescriptionTranslations(merged(function.getDescriptionTranslations(), descriptions));
        Map<String, Map<String, TranslatedText>> instructionTranslations = new LinkedHashMap<>();
        for (InstructionField field : InstructionField.values()) {
            Map<String, TranslatedText> values = merged(function.instructionTranslationsOf(field), instructions.getOrDefault(field, Map.of()));
            if (!values.isEmpty()) {
                instructionTranslations.put(field.key(), values);
            }
        }
        function.setInstructionTranslations(instructionTranslations);
        functions.saveAndFlush(function);
        audit.function(function, AuditAction.TRANSLATION_EDIT, "Machine translation: " + String.join(", ", targets));
        return function;
    }

    /** Machine translation writes into the caller's organization's editable cards only, never into a published one. */
    static OrgFunction requireTranslatable(CatalogAccess access, OrgFunction function) {
        access.requireOrganization(function.getOrganizationId());
        if (!function.isEditable()) {
            throw new WorkflowConflictException("Translation requires DRAFT or DEACTIVATED; published cards are translated through a draft revision");
        }
        return function;
    }

    private OrgFunction findFunction(Long id) {
        return functions.findById(id).orElseThrow(() -> new NoSuchElementException("Function not found"));
    }

    private Map<String, String> generate(String text, Map<String, TranslatedText> existing, List<String> targets,
                                         String source, boolean overwrite, int maxLength) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        List<String> needed = targets.stream().filter(code -> {
            TranslatedText value = existing.get(code);
            return value == null || value.text() == null || value.text().isBlank()
                    || (overwrite && TranslatedText.MACHINE.equals(value.source()));
        }).toList();
        if (needed.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = translator.translateRequired(text, source, needed);
        Map<String, String> checked = new LinkedHashMap<>();
        for (String code : needed) {
            String translated = result.get(code);
            if (translated == null || translated.isBlank() || translated.length() > maxLength) {
                throw new TranslationUnavailableException(HttpStatus.BAD_GATEWAY,
                        "Translation is incomplete or too long; try a manual translation");
            }
            checked.put(code, translated);
        }
        return checked;
    }

    private static String normalizedTranslation(InstructionField field, String text) {
        try {
            String value = InstructionText.normalize(field, text);
            if (value == null) {
                throw new IllegalArgumentException(field.key() + ": empty translation");
            }
            return value;
        } catch (IllegalArgumentException exception) {
            throw new TranslationUnavailableException(HttpStatus.BAD_GATEWAY,
                    "Translation is incomplete or too long; try a manual translation");
        }
    }

    private static Map<String, TranslatedText> merged(Map<String, TranslatedText> existing, Map<String, String> generated) {
        Map<String, TranslatedText> result = new LinkedHashMap<>(existing);
        generated.forEach((code, text) -> result.put(code, new TranslatedText(text, TranslatedText.MACHINE)));
        return result;
    }

    private static Snapshot snapshotOf(OrgFunction function) {
        Map<InstructionField, String> instructions = new EnumMap<>(InstructionField.class);
        Map<InstructionField, Map<String, TranslatedText>> instructionTranslations = new EnumMap<>(InstructionField.class);
        for (InstructionField field : InstructionField.values()) {
            if (field.get(function) != null) {
                instructions.put(field, field.get(function));
            }
            instructionTranslations.put(field, new LinkedHashMap<>(function.instructionTranslationsOf(field)));
        }
        return new Snapshot(function.getVersion(), function.getName(), function.getDescription(), function.getSourceLanguage(),
                new LinkedHashMap<>(function.getNameTranslations()), new LinkedHashMap<>(function.getDescriptionTranslations()),
                instructions, instructionTranslations);
    }

    private record Snapshot(Long version, String name, String description, String language,
                            Map<String, TranslatedText> names, Map<String, TranslatedText> descriptions,
                            Map<InstructionField, String> instructions,
                            Map<InstructionField, Map<String, TranslatedText>> instructionTranslations) {}
}
