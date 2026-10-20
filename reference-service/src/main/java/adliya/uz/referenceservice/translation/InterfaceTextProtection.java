package adliya.uz.referenceservice.translation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prepares an interface string for Azure Translator ({@code textType=html}) and checks the answer. Placeholders such
 * as {@code {count}}, product names, URLs, e-mail addresses and API paths are wrapped in
 * {@code <span class="notranslate">} so the provider leaves them alone; the text itself is HTML-escaped so markup-like
 * characters in labels survive. A translation that lost or changed any protected part is rejected.
 */
public final class InterfaceTextProtection {

    private static final String OPEN = "<span class=\"notranslate\">";
    private static final String CLOSE = "</span>";
    private static final Pattern PROTECTED = Pattern.compile(String.join("|",
            "\\{[A-Za-z0-9_.]+}",                 // interpolation placeholders
            "https?://[^\\s<>\"]+",               // URLs
            "[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+",     // e-mail addresses
            "/api/[\\w/.{}-]*",                   // API paths shown to administrators
            "\\bReestrGov\\b", "\\bReestr Task\\b", "\\bAzure Translator\\b", "\\bBCP[ -]47\\b"));
    private static final Pattern RESTORE = Pattern.compile("<span\\s+class\\s*=\\s*[\"']?notranslate[\"']?\\s*>(.*?)</span>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ANY_TAG = Pattern.compile("<[^>]*>");

    private InterfaceTextProtection() {}

    public static String protect(String text) {
        StringBuilder result = new StringBuilder();
        Matcher matcher = PROTECTED.matcher(text);
        int last = 0;
        while (matcher.find()) {
            result.append(escape(text.substring(last, matcher.start())))
                    .append(OPEN).append(escape(matcher.group())).append(CLOSE);
            last = matcher.end();
        }
        result.append(escape(text.substring(last)));
        return result.toString();
    }

    /** Plain text of the provider's answer, or empty when it cannot be trusted (protected parts altered, stray markup). */
    public static Optional<String> restore(String original, String translatedHtml) {
        if (translatedHtml == null) {
            return Optional.empty();
        }
        Matcher matcher = RESTORE.matcher(translatedHtml);
        StringBuilder plain = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            String between = translatedHtml.substring(last, matcher.start());
            if (ANY_TAG.matcher(between).find()) {
                return Optional.empty();
            }
            plain.append(unescape(between)).append(unescape(matcher.group(1)));
            last = matcher.end();
        }
        String tail = translatedHtml.substring(last);
        if (ANY_TAG.matcher(tail).find()) {
            return Optional.empty();
        }
        plain.append(unescape(tail));
        String value = plain.toString().strip();
        if (value.isEmpty() || !protectedParts(original).equals(protectedParts(value))) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    static List<String> protectedParts(String text) {
        List<String> parts = new ArrayList<>();
        Matcher matcher = PROTECTED.matcher(text);
        while (matcher.find()) {
            parts.add(matcher.group());
        }
        parts.sort(String::compareTo);
        return parts;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String unescape(String value) {
        return value.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&#x27;", "'").replace("&nbsp;", "\u00a0").replace("&amp;", "&");
    }
}
