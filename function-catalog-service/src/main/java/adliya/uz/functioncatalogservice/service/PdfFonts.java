package adliya.uz.functioncatalogservice.service;

import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Embedded Liberation Sans (bundled by openpdf-fonts-extra), which covers Latin including Uzbek letters, Cyrillic and
 * French accents. The PDF standard fonts would silently drop every Cyrillic character, so they are never used.
 */
public final class PdfFonts {

    public static final Color TEXT = new Color(0x1f, 0x23, 0x28);
    public static final Color MUTED = new Color(0x5b, 0x63, 0x6e);
    public static final Color LINE = new Color(0xd0, 0xd5, 0xdb);
    public static final Color SHADE = new Color(0xf2, 0xf4, 0xf6);
    public static final Color ACCENT = new Color(0x2f, 0x5d, 0x8a);

    private static final BaseFont REGULAR = load("liberation/LiberationSans-Regular.ttf");
    private static final BaseFont BOLD = load("liberation/LiberationSans-Bold.ttf");

    private PdfFonts() {}

    public static BaseFont regularBase() {
        return REGULAR;
    }

    public static Font regular(float size) {
        return new Font(REGULAR, size, Font.NORMAL, TEXT);
    }

    public static Font regular(float size, Color color) {
        return new Font(REGULAR, size, Font.NORMAL, color);
    }

    public static Font bold(float size) {
        return new Font(BOLD, size, Font.NORMAL, TEXT);
    }

    public static Font bold(float size, Color color) {
        return new Font(BOLD, size, Font.NORMAL, color);
    }

    private static BaseFont load(String resource) {
        try (InputStream in = PdfFonts.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Font resource missing from the classpath: " + resource);
            }
            return BaseFont.createFont(resource, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
