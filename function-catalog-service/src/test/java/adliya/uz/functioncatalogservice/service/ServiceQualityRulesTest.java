package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.dto.QualityIssue;
import adliya.uz.functioncatalogservice.entity.FunctionStatus;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.QualityIssueType;
import adliya.uz.functioncatalogservice.entity.TranslatedText;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceQualityRulesTest {

    private static final Instant NOW = Instant.parse("2026-06-01T08:00:00Z");
    private static final Optional<Set<String>> LANGUAGES = Optional.of(Set.of("en", "ru", "uz"));

    @Test void completeVerifiedCardHasNoIssues() {
        assertThat(ServiceQualityRules.evaluate(complete(), LANGUAGES, NOW)).isEmpty();
    }

    @Test void verificationIsOverdueOnlyForPublishedCardsUsingTheExistingRecheckWindow() {
        OrgFunction card = complete();
        card.setLastVerifiedAt(NOW.minus(Duration.ofDays(180)));
        assertThat(ServiceQualityRules.evaluate(card, LANGUAGES, NOW)).isEmpty();

        card.setLastVerifiedAt(NOW.minus(Duration.ofDays(181)));
        assertThat(issue(card, QualityIssueType.VERIFICATION_OVERDUE).reason()).isEqualTo(QualityIssue.RECHECK_DUE);

        card.setLastVerifiedAt(NOW.minus(Duration.ofDays(3)));
        card.setVerificationOutdated(true);
        assertThat(issue(card, QualityIssueType.VERIFICATION_OVERDUE).reason()).isEqualTo(QualityIssue.CHANGED_SINCE_VERIFICATION);

        card.setLastVerifiedAt(null);
        card.setVerificationOutdated(false);
        assertThat(issue(card, QualityIssueType.VERIFICATION_OVERDUE).reason()).isEqualTo(QualityIssue.NEVER_VERIFIED);

        card.setStatus(FunctionStatus.DRAFT);
        assertThat(ServiceQualityRules.evaluate(card, LANGUAGES, NOW)).isEmpty();
    }

    @Test void externalSourceLinkIsOptionalButMustBeAWebLinkWhenPresent() {
        OrgFunction card = complete();
        card.setOfficialSourceUrl(null);
        assertThat(ServiceQualityRules.evaluate(card, LANGUAGES, NOW)).isEmpty();
        card.setOfficialSourceUrl("  ");
        assertThat(ServiceQualityRules.evaluate(card, LANGUAGES, NOW)).isEmpty();
        card.setOfficialSourceUrl("www.gov.uz/passport");
        QualityIssue unusable = issue(card, QualityIssueType.SOURCE_MISSING);
        assertThat(unusable.reason()).isEqualTo(QualityIssue.UNUSABLE);
        assertThat(unusable.details()).containsExactly("www.gov.uz/passport");
        card.setOfficialSourceUrl("javascript:alert(1)");
        assertThat(issue(card, QualityIssueType.SOURCE_MISSING).reason()).isEqualTo(QualityIssue.UNUSABLE);
        card.setOfficialSourceUrl(" HTTPS://my.gov.uz/service ");
        assertThat(ServiceQualityRules.evaluate(card, LANGUAGES, NOW)).isEmpty();
    }

    @Test void onlyFieldsRequiredByTheEditorAreReportedAsIncomplete() {
        OrgFunction card = complete();
        card.setRequirements(null);
        card.setFunctionCategory(null);
        card.setSteps(null);
        card.setFee(null);
        card.setInstructionTranslations(new HashMap<>());
        assertThat(ServiceQualityRules.evaluate(card, LANGUAGES, NOW)).isEmpty();

        card.setDescription(" ");
        card.setOrganizationId(null);
        QualityIssue incomplete = issue(card, QualityIssueType.INFORMATION_INCOMPLETE);
        assertThat(incomplete.reason()).isEqualTo(QualityIssue.REQUIRED_FIELDS);
        assertThat(incomplete.details()).containsExactly("description", "organization");
    }

    @Test void translationsAreCheckedForEveryActiveLanguageExceptTheSourceLanguage() {
        OrgFunction card = complete();
        card.getNameTranslations().remove("uz");
        assertThat(issue(card, QualityIssueType.TRANSLATIONS_MISSING).details()).containsExactly("uz");

        card = complete();
        card.getDescriptionTranslations().put("ru", new TranslatedText(" ", TranslatedText.HUMAN));
        assertThat(issue(card, QualityIssueType.TRANSLATIONS_MISSING).details()).containsExactly("ru");

        card = complete();
        card.setSteps("Apply online");
        assertThat(issue(card, QualityIssueType.TRANSLATIONS_MISSING).details()).containsExactly("ru", "uz");
        card.setInstructionTranslations(new HashMap<>(Map.of("steps", Map.of(
                "ru", new TranslatedText("Подайте заявку", TranslatedText.MACHINE),
                "uz", new TranslatedText("Ariza bering", TranslatedText.HUMAN)))));
        assertThat(ServiceQualityRules.evaluate(card, LANGUAGES, NOW)).isEmpty();

        card = complete();
        card.setSourceLanguage("ru");
        card.getNameTranslations().remove("ru");
        card.getDescriptionTranslations().remove("ru");
        assertThat(issue(card, QualityIssueType.TRANSLATIONS_MISSING).details()).containsExactly("en");
    }

    @Test void translationsAreNotJudgedWhenTheLanguageListIsUnavailable() {
        OrgFunction card = complete();
        card.getNameTranslations().clear();
        assertThat(ServiceQualityRules.evaluate(card, Optional.empty(), NOW)).isEmpty();
    }

    @Test void deactivatedCardsNeverNeedAttention() {
        OrgFunction card = complete();
        card.setStatus(FunctionStatus.DEACTIVATED);
        card.setOfficialSourceUrl(null);
        card.setLastVerifiedAt(null);
        card.setDescription(null);
        assertThat(ServiceQualityRules.evaluate(card, LANGUAGES, NOW)).isEmpty();
    }

    @Test void issuesAreReportedInAStableOrder() {
        OrgFunction card = complete();
        card.setLastVerifiedAt(null);
        card.setOfficialSourceUrl("not a link");
        card.setDescription(null);
        card.getNameTranslations().clear();
        List<QualityIssueType> types = ServiceQualityRules.evaluate(card, LANGUAGES, NOW).stream().map(QualityIssue::type).toList();
        assertThat(types).containsExactly(QualityIssueType.VERIFICATION_OVERDUE, QualityIssueType.SOURCE_MISSING,
                QualityIssueType.INFORMATION_INCOMPLETE, QualityIssueType.TRANSLATIONS_MISSING);
    }

    private static QualityIssue issue(OrgFunction card, QualityIssueType type) {
        return ServiceQualityRules.evaluate(card, LANGUAGES, NOW).stream().filter(found -> found.type() == type)
                .findFirst().orElseThrow(() -> new AssertionError("Expected issue " + type));
    }

    private static OrgFunction complete() {
        return OrgFunction.builder().id(1L).name("Passport").description("Issue a passport").organizationId(10L)
                .status(FunctionStatus.PUBLISHED).sourceLanguage("en").officialSourceUrl("https://gov.uz/passport")
                .lastVerifiedAt(NOW.minus(Duration.ofDays(10)))
                .nameTranslations(new HashMap<>(Map.of("ru", human("Паспорт"), "uz", human("Pasport"))))
                .descriptionTranslations(new HashMap<>(Map.of("ru", human("Выдача паспорта"), "uz", human("Pasport berish"))))
                .instructionTranslations(new HashMap<>())
                .build();
    }

    private static TranslatedText human(String text) {
        return new TranslatedText(text, TranslatedText.HUMAN);
    }
}
