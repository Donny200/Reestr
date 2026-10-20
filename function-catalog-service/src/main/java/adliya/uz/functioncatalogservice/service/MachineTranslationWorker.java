package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.MachineTranslationProperties;
import adliya.uz.functioncatalogservice.entity.FunctionCategory;
import adliya.uz.functioncatalogservice.entity.InstructionField;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.TranslatedText;
import adliya.uz.functioncatalogservice.entity.TranslationJob;
import adliya.uz.functioncatalogservice.entity.TranslationJobStatus;
import adliya.uz.functioncatalogservice.exception.TranslationUnavailableException;
import adliya.uz.functioncatalogservice.repository.FunctionCategoryRepository;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import adliya.uz.functioncatalogservice.repository.TranslationJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Processes {@link TranslationJob}s in small batches: reads the record, asks the provider only for the texts and
 * languages that are still missing, and stores the answers without ever replacing an existing translation.
 * Provider calls happen outside database transactions; a record that changed meanwhile is simply queued again.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MachineTranslationWorker {

    static final int NAME_MAX_LENGTH = 150;
    static final int DESCRIPTION_MAX_LENGTH = 500;
    static final int CATEGORY_MAX_LENGTH = 100;
    private static final List<Duration> BACKOFF = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5),
            Duration.ofMinutes(15), Duration.ofHours(1), Duration.ofHours(6));
    private static final Duration RATE_LIMIT_BACKOFF = Duration.ofMinutes(5);

    private final TranslationJobRepository jobs;
    private final OrgFunctionRepository functions;
    private final FunctionCategoryRepository categories;
    private final ActiveLanguageClient languages;
    private final TranslationClient translator;
    private final MachineTranslationProperties properties;
    private final TransactionTemplate transaction;
    private final AuditWriter audit;

    @Scheduled(initialDelayString = "${catalog.translation.initial-delay:PT30S}",
            fixedDelayString = "${catalog.translation.interval:PT20S}")
    public void tick() {
        if (!properties.isEnabled() || !translator.isAvailable()) {
            return;
        }
        try {
            RunResult result = runDue(Instant.now());
            if (result.claimed() > 0) {
                log.info("Machine translation: {} job(s) handled, {} done, {} queued again, {} skipped",
                        result.claimed(), result.done(), result.retried(), result.skipped());
            }
        } catch (DataAccessException exception) {
            log.warn("Machine translation tick skipped: {}", exception.getMessage());
        }
    }

    /** Handles up to {@code batch-size} due jobs; safe to call from tests with a fixed clock. */
    public RunResult runDue(Instant now) {
        List<TranslationJob> claimed = Objects.requireNonNull(transaction.execute(status -> claim(now)));
        int done = 0;
        int retried = 0;
        int skipped = 0;
        for (TranslationJob job : claimed) {
            switch (process(job, now)) {
                case DONE -> done++;
                case RETRY -> retried++;
                case SKIPPED -> skipped++;
            }
        }
        return new RunResult(claimed.size(), done, retried, skipped);
    }

    private List<TranslationJob> claim(Instant now) {
        List<TranslationJob> due = jobs.findDue(now, PageRequest.of(0, Math.max(1, properties.getBatchSize())));
        for (TranslationJob job : due) {
            job.setStatus(TranslationJobStatus.RUNNING);
            job.setAttempts(job.getAttempts() + 1);
            job.setNextAttemptAt(now.plus(properties.getLease()));
        }
        jobs.saveAllAndFlush(due);
        return due;
    }

    Outcome process(TranslationJob job, Instant now) {
        try {
            return switch (job.getTargetType()) {
                case FUNCTION -> translateFunction(job, now);
                case CATEGORY -> translateCategory(job, now);
            };
        } catch (TranslationUnavailableException exception) {
            return fail(job, now, exception.getMessage(), exception.status());
        } catch (RuntimeException exception) {
            log.warn("Translation job {} failed ({})", job.getId(), exception.getClass().getSimpleName());
            return fail(job, now, "Unexpected error while translating; retry later", null);
        }
    }

    private Outcome translateFunction(TranslationJob job, Instant now) {
        Optional<Plan> planned = Objects.requireNonNull(transaction.execute(status -> planFunction(job, now)));
        if (planned.isEmpty()) {
            return jobOutcome(job);
        }
        Plan plan = planned.get();
        Map<Text, Map<String, String>> translated = translate(plan);
        return Objects.requireNonNull(transaction.execute(status -> applyFunction(job, plan, translated, now)));
    }

    private Optional<Plan> planFunction(TranslationJob job, Instant now) {
        OrgFunction function = functions.findById(job.getTargetId()).orElse(null);
        if (function == null) {
            finish(job, TranslationJobStatus.SKIPPED, now, "Card no longer exists");
            return Optional.empty();
        }
        if (!function.isEditable()) {
            finish(job, TranslationJobStatus.SKIPPED, now, "Card is " + function.getStatus()
                    + "; machine translation applies to drafts (published cards are translated through a revision)");
            return Optional.empty();
        }
        String source = MissingTranslations.normalize(function.getSourceLanguage());
        if (translator.sourceUnsupported(source)) {
            finish(job, TranslationJobStatus.DONE, now, sourceUnsupportedNote(source));
            return Optional.empty();
        }
        List<String> all = MissingTranslations.targets(activeLanguages(), source);
        String unsupportedNote = unsupportedNote(source, all);
        List<String> targets = supportedOnly(source, all);
        List<Text> texts = new ArrayList<>();
        addText(texts, "name", function.getName(), NAME_MAX_LENGTH, null, function.getNameTranslations(), targets);
        addText(texts, "description", function.getDescription(), DESCRIPTION_MAX_LENGTH, null, function.getDescriptionTranslations(), targets);
        for (InstructionField field : InstructionField.values()) {
            addText(texts, field.key(), field.get(function), field.maxLength(), field, function.instructionTranslationsOf(field), targets);
        }
        if (texts.isEmpty()) {
            finish(job, TranslationJobStatus.DONE, now, unsupportedNote);
            return Optional.empty();
        }
        return Optional.of(new Plan(function.getVersion(), source, texts, unsupportedNote));
    }

    private Outcome applyFunction(TranslationJob job, Plan plan, Map<Text, Map<String, String>> translated, Instant now) {
        OrgFunction function = functions.findById(job.getTargetId()).orElse(null);
        if (function == null) {
            finish(job, TranslationJobStatus.SKIPPED, now, "Card no longer exists");
            return Outcome.SKIPPED;
        }
        if (!Objects.equals(function.getVersion(), plan.version())) {
            requeue(job, now, "Card changed while translating; queued again");
            return Outcome.RETRY;
        }
        if (!function.isEditable()) {
            finish(job, TranslationJobStatus.SKIPPED, now, "Card is " + function.getStatus() + "; translations were not changed");
            return Outcome.SKIPPED;
        }
        Set<String> filled = new TreeSet<>();
        List<String> notes = plan.initialNotes();
        Map<String, TranslatedText> names = new LinkedHashMap<>(function.getNameTranslations());
        Map<String, TranslatedText> descriptions = new LinkedHashMap<>(function.getDescriptionTranslations());
        Map<String, Map<String, TranslatedText>> instructions = new LinkedHashMap<>();
        if (function.getInstructionTranslations() != null) {
            function.getInstructionTranslations().forEach((key, values) -> instructions.put(key, new LinkedHashMap<>(values)));
        }
        for (Text text : plan.texts()) {
            Map<String, TranslatedText> target = switch (text.key()) {
                case "name" -> names;
                case "description" -> descriptions;
                default -> instructions.computeIfAbsent(text.key(), ignored -> new LinkedHashMap<>());
            };
            merge(text, translated.getOrDefault(text, Map.of()), target, filled, notes);
        }
        instructions.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        if (!filled.isEmpty()) {
            function.setNameTranslations(names);
            function.setDescriptionTranslations(descriptions);
            function.setInstructionTranslations(instructions);
            functions.saveAndFlush(function);
            audit.machineTranslation(function, "Automatic translation: " + String.join(", ", filled));
        }
        finish(job, TranslationJobStatus.DONE, now, notes.isEmpty() ? null : String.join("; ", notes));
        return Outcome.DONE;
    }

    private Outcome translateCategory(TranslationJob job, Instant now) {
        Optional<Plan> planned = Objects.requireNonNull(transaction.execute(status -> planCategory(job, now)));
        if (planned.isEmpty()) {
            return jobOutcome(job);
        }
        Plan plan = planned.get();
        Map<Text, Map<String, String>> translated = translate(plan);
        return Objects.requireNonNull(transaction.execute(status -> applyCategory(job, plan, translated, now)));
    }

    private Optional<Plan> planCategory(TranslationJob job, Instant now) {
        FunctionCategory category = categories.findById(job.getTargetId()).orElse(null);
        if (category == null) {
            finish(job, TranslationJobStatus.SKIPPED, now, "Category no longer exists");
            return Optional.empty();
        }
        String source = MissingTranslations.sourceLanguageOf(category);
        if (translator.sourceUnsupported(source)) {
            finish(job, TranslationJobStatus.DONE, now, sourceUnsupportedNote(source));
            return Optional.empty();
        }
        List<String> all = MissingTranslations.targets(activeLanguages(), source);
        String unsupportedNote = unsupportedNote(source, all);
        List<Text> texts = new ArrayList<>();
        addText(texts, "name", category.getName(), CATEGORY_MAX_LENGTH, null, category.getNameTranslations(),
                supportedOnly(source, all));
        if (texts.isEmpty()) {
            finish(job, TranslationJobStatus.DONE, now, unsupportedNote);
            return Optional.empty();
        }
        return Optional.of(new Plan(category.getVersion(), source, texts, unsupportedNote));
    }

    private Outcome applyCategory(TranslationJob job, Plan plan, Map<Text, Map<String, String>> translated, Instant now) {
        FunctionCategory category = categories.findById(job.getTargetId()).orElse(null);
        if (category == null) {
            finish(job, TranslationJobStatus.SKIPPED, now, "Category no longer exists");
            return Outcome.SKIPPED;
        }
        if (!Objects.equals(category.getVersion(), plan.version())) {
            requeue(job, now, "Category changed while translating; queued again");
            return Outcome.RETRY;
        }
        Set<String> filled = new TreeSet<>();
        List<String> notes = plan.initialNotes();
        Map<String, TranslatedText> names = new LinkedHashMap<>(category.getNameTranslations());
        merge(plan.texts().get(0), translated.getOrDefault(plan.texts().get(0), Map.of()), names, filled, notes);
        if (!filled.isEmpty()) {
            category.setNameTranslations(names);
            categories.saveAndFlush(category);
            audit.categoryMachineTranslation(category.getId(), "Automatic translation of the category name: " + String.join(", ", filled));
        }
        finish(job, TranslationJobStatus.DONE, now, notes.isEmpty() ? null : String.join("; ", notes));
        return Outcome.DONE;
    }

    /** One provider request per distinct set of missing languages; texts sharing the same gaps travel together. */
    private Map<Text, Map<String, String>> translate(Plan plan) {
        Map<List<String>, List<Text>> groups = new LinkedHashMap<>();
        for (Text text : plan.texts()) {
            groups.computeIfAbsent(text.needed(), ignored -> new ArrayList<>()).add(text);
        }
        Map<Text, Map<String, String>> result = new LinkedHashMap<>();
        groups.forEach((needed, texts) -> {
            List<Map<String, String>> translated = translator.translateBatch(texts.stream().map(Text::text).toList(), plan.source(), needed);
            for (int i = 0; i < texts.size(); i++) {
                result.put(texts.get(i), i < translated.size() && translated.get(i) != null ? translated.get(i) : Map.of());
            }
        });
        return result;
    }

    private static void merge(Text text, Map<String, String> translations, Map<String, TranslatedText> target,
                              Set<String> filled, List<String> notes) {
        for (String code : text.needed()) {
            if (MissingTranslations.present(target, code)) {
                continue; // a translation appeared meanwhile (human or machine): never replace it
            }
            String value = translations.get(code);
            if (value == null || value.isBlank()) {
                notes.add(text.key() + "/" + code + ": provider returned no text");
                continue;
            }
            String normalized;
            try {
                normalized = text.field() == null ? value.strip() : InstructionText.normalize(text.field(), value);
            } catch (IllegalArgumentException exception) {
                normalized = null;
            }
            if (normalized == null || normalized.isEmpty() || normalized.length() > text.maxLength()) {
                notes.add(text.key() + "/" + code + ": translation rejected (empty or too long); enter it manually");
                continue;
            }
            target.put(code, new TranslatedText(normalized, TranslatedText.MACHINE));
            filled.add(code);
        }
    }

    private static void addText(List<Text> texts, String key, String value, int maxLength, InstructionField field,
                                Map<String, TranslatedText> existing, List<String> targets) {
        if (value == null || value.isBlank()) {
            return;
        }
        List<String> needed = targets.stream().filter(code -> !MissingTranslations.present(existing, code)).toList();
        if (!needed.isEmpty()) {
            texts.add(new Text(key, value, maxLength, field, needed));
        }
    }

    private List<String> supportedOnly(String source, List<String> targets) {
        Set<String> unsupported = translator.unsupportedTargets(source, targets);
        return targets.stream().filter(code -> !unsupported.contains(code)).toList();
    }

    private String unsupportedNote(String source, List<String> targets) {
        Set<String> unsupported = new TreeSet<>(translator.unsupportedTargets(source, targets));
        return unsupported.isEmpty() ? null : "Not supported by Azure Translator: " + String.join(", ", unsupported)
                + "; enter these translations manually";
    }

    private static String sourceUnsupportedNote(String source) {
        return "Source language " + source + " is not supported by Azure Translator; enter translations manually";
    }

    private Set<String> activeLanguages() {
        return languages.activeLanguageCodes().orElseThrow(() -> new TranslationUnavailableException(
                HttpStatus.SERVICE_UNAVAILABLE, "Active languages are unavailable (identity-service); retry later"));
    }

    private Outcome fail(TranslationJob job, Instant now, String message, HttpStatus status) {
        TranslationJob current = jobs.findById(job.getId()).orElse(job);
        if (current.getAttempts() >= properties.getMaxAttempts()) {
            current.setStatus(TranslationJobStatus.FAILED);
            current.setFinishedAt(now);
            current.setLastError(shorten("Gave up after " + current.getAttempts() + " attempts: " + message));
        } else {
            Duration delay = BACKOFF.get(Math.min(Math.max(current.getAttempts() - 1, 0), BACKOFF.size() - 1));
            if (status == HttpStatus.TOO_MANY_REQUESTS && delay.compareTo(RATE_LIMIT_BACKOFF) < 0) {
                delay = RATE_LIMIT_BACKOFF;
            }
            current.setStatus(TranslationJobStatus.PENDING);
            current.setNextAttemptAt(now.plus(delay));
            current.setLastError(shorten(message));
        }
        jobs.saveAndFlush(current);
        return Outcome.RETRY;
    }

    private void requeue(TranslationJob job, Instant now, String note) {
        TranslationJob current = jobs.findById(job.getId()).orElse(job);
        current.setStatus(TranslationJobStatus.PENDING);
        current.setAttempts(Math.max(0, current.getAttempts() - 1));
        current.setNextAttemptAt(now.plus(properties.getInterval()));
        current.setLastError(shorten(note));
        jobs.saveAndFlush(current);
    }

    private void finish(TranslationJob job, TranslationJobStatus status, Instant now, String note) {
        TranslationJob current = jobs.findById(job.getId()).orElse(job);
        current.setStatus(status);
        current.setFinishedAt(now);
        current.setLastError(shorten(note));
        jobs.saveAndFlush(current);
        job.setStatus(status);
    }

    private Outcome jobOutcome(TranslationJob job) {
        return job.getStatus() == TranslationJobStatus.DONE ? Outcome.DONE : Outcome.SKIPPED;
    }

    private static String shorten(String value) {
        return value == null || value.length() <= 500 ? value : value.substring(0, 497) + "...";
    }

    enum Outcome { DONE, RETRY, SKIPPED }

    public record RunResult(int claimed, int done, int retried, int skipped) {}

    private record Plan(Long version, String source, List<Text> texts, String note) {
        List<String> initialNotes() {
            List<String> notes = new ArrayList<>();
            if (note != null) {
                notes.add(note);
            }
            return notes;
        }
    }

    private record Text(String key, String text, int maxLength, InstructionField field, List<String> needed) {}
}
