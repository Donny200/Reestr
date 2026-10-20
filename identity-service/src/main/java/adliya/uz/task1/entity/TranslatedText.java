package adliya.uz.task1.entity;

public record TranslatedText(String text, String source) {

    public static final String HUMAN = "human";
    public static final String MACHINE = "machine";
}
