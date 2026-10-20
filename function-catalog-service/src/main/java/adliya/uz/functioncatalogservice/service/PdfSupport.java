package adliya.uz.functioncatalogservice.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfString;
import com.lowagie.text.pdf.PdfWriter;

import java.util.Locale;

/** Pieces shared by the generated PDFs: document metadata, the page footer and simple paragraphs. */
final class PdfSupport {

    private PdfSupport() {}

    /** Title, creator and document language, plus a footer on every page; called before the document is opened. */
    static void prepare(Document pdf, PdfWriter writer, String title, Locale locale, Footer footer) {
        writer.setPageEvent(footer);
        writer.getExtraCatalog().put(PdfName.LANG, new PdfString(locale.toLanguageTag()));
        pdf.addTitle(title);
        pdf.addCreator("ReestrGov");
    }

    static Paragraph paragraph(String text, Font font, float after) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setSpacingAfter(after);
        return paragraph;
    }

    /** Locale formats may use narrow/no-break spaces; plain spaces always exist in the embedded font. */
    static String spaces(String text) {
        return text.replace(' ', ' ').replace(' ', ' ');
    }

    /** Muted text on the left and the page number on the right, {@code offset} points below the bottom margin. */
    static final class Footer extends PdfPageEventHelper {
        private final String text;
        private final String page;
        private final float offset;
        private final float size;

        Footer(String text, String page, float offset, float size) {
            this.text = text;
            this.page = page;
            this.offset = offset;
            this.size = size;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            float y = document.bottomMargin() - offset;
            ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT, new Phrase(text, PdfFonts.regular(size, PdfFonts.MUTED)),
                    document.leftMargin(), y, 0);
            ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT,
                    new Phrase(page + " " + writer.getPageNumber(), PdfFonts.regular(size, PdfFonts.MUTED)),
                    document.getPageSize().getWidth() - document.rightMargin(), y, 0);
        }
    }
}
