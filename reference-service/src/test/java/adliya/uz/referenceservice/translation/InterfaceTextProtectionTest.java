package adliya.uz.referenceservice.translation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InterfaceTextProtectionTest {

    @Test
    void wrapsPlaceholdersProductNamesAndLinksAndEscapesMarkup() {
        String html = InterfaceTextProtection.protect("Checking every {seconds} s on ReestrGov <b> & https://example.uz/a?b=1");
        assertThat(html).isEqualTo("Checking every <span class=\"notranslate\">{seconds}</span> s on "
                + "<span class=\"notranslate\">ReestrGov</span> &lt;b&gt; &amp; "
                + "<span class=\"notranslate\">https://example.uz/a?b=1</span>");
    }

    @Test
    void restoresTheProviderAnswerToPlainText() {
        String original = "Checking every {seconds} seconds & more";
        String answer = "Vérification toutes les <span class=\"notranslate\">{seconds}</span> secondes &amp; plus";
        assertThat(InterfaceTextProtection.restore(original, answer)).contains("Vérification toutes les {seconds} secondes & plus");
    }

    @Test
    void rejectsAnswersThatLostOrRenamedAPlaceholder() {
        String original = "{count} results for {query}";
        assertThat(InterfaceTextProtection.restore(original, "<span class=\"notranslate\">{count}</span> résultats")).isEmpty();
        assertThat(InterfaceTextProtection.restore(original,
                "<span class=\"notranslate\">{count}</span> résultats pour {requête}")).isEmpty();
    }

    @Test
    void rejectsStrayMarkupAndEmptyAnswers() {
        assertThat(InterfaceTextProtection.restore("Save", "<b>Enregistrer</b>")).isEmpty();
        assertThat(InterfaceTextProtection.restore("Save", "  ")).isEmpty();
        assertThat(InterfaceTextProtection.restore("Save", null)).isEmpty();
    }

    @Test
    void toleratesProviderQuotingOfTheProtectionSpan() {
        assertThat(InterfaceTextProtection.restore("{n} items", "<span class=notranslate>{n}</span> éléments"))
                .contains("{n} éléments");
    }
}
