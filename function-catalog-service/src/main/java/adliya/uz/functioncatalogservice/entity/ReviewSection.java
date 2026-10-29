package adliya.uz.functioncatalogservice.entity;

import java.util.Arrays;
import java.util.Optional;

/** The steps of the service editor, in order; the reviewer inspects a submission in the same sections. */
public enum ReviewSection {
    GENERAL("general"), MECHANISM("mechanism"), DIGITALIZATION("digitalization"), DECENTRALIZATION("decentralization"), OTHER("other");

    private final String key;

    ReviewSection(String key) {
        this.key = key;
    }

    public String key() { return key; }

    public static Optional<ReviewSection> byKey(String key) {
        return Arrays.stream(values()).filter(section -> section.key.equals(key)).findFirst();
    }
}
