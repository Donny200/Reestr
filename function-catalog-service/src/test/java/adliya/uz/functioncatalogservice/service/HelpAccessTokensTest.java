package adliya.uz.functioncatalogservice.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HelpAccessTokensTest {

    @Test void tokensAreUrlSafeHighEntropyAndOnlyTheirHashIsComparable() {
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < 1_000; index++) {
            String token = HelpAccessTokens.generate();
            assertThat(HelpAccessTokens.wellFormed(token)).isTrue();
            assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
            assertThat(seen.add(token)).isTrue();
        }
        String token = HelpAccessTokens.generate();
        assertThat(HelpAccessTokens.hash(token)).hasSize(64).isEqualTo(HelpAccessTokens.hash(token)).doesNotContain(token);
        assertThat(HelpAccessTokens.hash(token)).isNotEqualTo(HelpAccessTokens.hash(HelpAccessTokens.generate()));
    }

    @Test void malformedTokensAreRejectedBeforeAnyLookup() {
        assertThat(HelpAccessTokens.wellFormed(null)).isFalse();
        assertThat(HelpAccessTokens.wellFormed("")).isFalse();
        assertThat(HelpAccessTokens.wellFormed("1")).isFalse();
        assertThat(HelpAccessTokens.wellFormed("a".repeat(42))).isFalse();
        assertThat(HelpAccessTokens.wellFormed("a".repeat(44))).isFalse();
        assertThat(HelpAccessTokens.wellFormed("a".repeat(42) + "=")).isFalse();
    }

    @Test void visitorTextFlagsIdentityAndCardNumbersButNotPhoneNumbersOrDates() {
        assertThat(VisitorText.containsLongNumber("PINFL 31210890123456")).isTrue();
        assertThat(VisitorText.containsLongNumber("card 8600 1234 5678 9012")).isTrue();
        assertThat(VisitorText.containsLongNumber("call +998 71 200-00-00 on 2026-10-16")).isFalse();
        assertThat(VisitorText.normalize("  Line one \r\n\r\n\r\n line\ttwo  ")).isEqualTo("Line one\n\nline two");
    }
}
