package adliya.uz.task1.dto;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class OfficialLink {

    public static final String PATTERN = "^\\s*$|^\\s*(?i:https?)://[^\\s/?#]+[^\\s]*\\s*$";
    public static final String MESSAGE = "Link must be an absolute http(s) address";

    private OfficialLink() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (!isWebLink(value)) {
            throw new IllegalArgumentException(MESSAGE);
        }
        return value;
    }

    private static boolean isWebLink(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return (scheme.equals("https") || scheme.equals("http")) && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
