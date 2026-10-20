package adliya.uz.functioncatalogservice.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The canonical public address of the site (catalog.public-base-url) as it may appear in printed or downloaded
 * material. Development and private addresses (localhost, loopback, private networks, *.local) are never printed:
 * they mean nothing to a visitor holding the paper, so such a configuration counts as "not configured".
 */
public final class PublicSiteUrl {

    private static final Pattern PRIVATE_IPV4 = Pattern.compile(
            "^(127\\.|10\\.|0\\.|192\\.168\\.|169\\.254\\.|172\\.(1[6-9]|2\\d|3[01])\\.).*");

    private PublicSiteUrl() {}

    /** The configured origin without trailing slashes, or null when unset, malformed or a development address. */
    public static String canonical(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        String value = configured.trim().replaceAll("/+$", "");
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("https") || scheme.equals("http")) || host.isEmpty() || development(host)) {
                return null;
            }
            return value;
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    /** Address of a page of the site, e.g. {@code /functions/3}; null when no canonical address is configured. */
    public static String page(String configured, String path) {
        String base = canonical(configured);
        return base == null ? null : base + (path.startsWith("/") ? path : "/" + path);
    }

    static boolean development(String host) {
        String bare = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        return bare.equals("localhost") || bare.endsWith(".localhost") || bare.endsWith(".local") || bare.endsWith(".internal")
                || bare.equals("::1") || bare.startsWith("fc") || bare.startsWith("fd") || bare.startsWith("fe80")
                || bare.equals("0.0.0.0") || PRIVATE_IPV4.matcher(bare).matches() || !bare.contains(".") && !bare.contains(":");
    }
}
