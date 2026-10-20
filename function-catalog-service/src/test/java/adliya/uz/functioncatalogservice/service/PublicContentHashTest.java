package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.TranslatedText;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PublicContentHashTest {

    @Test
    void onlyPublicSectionsThatDifferAreReported() {
        OrgFunction before = OrgFunction.builder().name("Passport").description("Renewal").requirements("Old passport")
                .fee("50 000").sourceLanguage("en").organizationId(10L).build();
        OrgFunction after = OrgFunction.builder().name("Passport").description("Renewal").requirements("Old passport\nPhoto")
                .fee("50 000").sourceLanguage("en").organizationId(10L).lastVerifiedAt(java.time.Instant.now()).verifiedByUserId(5L).build();
        after.getNameTranslations().put("ru", new TranslatedText("Паспорт", "human"));
        Map<String, String> previous = PublicContentHash.of(before);
        assertThat(PublicContentHash.changedSections(previous, PublicContentHash.of(after))).containsExactly("documents", "translations");
        assertThat(PublicContentHash.changedSections(previous, previous)).isEmpty();
        assertThat(PublicContentHash.changedSections(null, previous)).as("unknown previous state is never guessed").isEmpty();
        assertThat(previous.values()).allMatch(hash -> hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void sourceLanguageMirrorAndMachineHumanFlagsDoNotCountAsTranslationChanges() {
        OrgFunction before = OrgFunction.builder().name("Passport").sourceLanguage("en").build();
        before.getNameTranslations().put("en", new TranslatedText("Passport", "human"));
        before.getNameTranslations().put("uz", new TranslatedText("Pasport", "machine"));
        OrgFunction after = OrgFunction.builder().name("Passport").sourceLanguage("en").build();
        after.getNameTranslations().put("uz", new TranslatedText("Pasport", "human"));
        assertThat(PublicContentHash.changedSections(PublicContentHash.of(before), PublicContentHash.of(after))).isEmpty();
    }
}
