package adliya.uz.functioncatalogservice.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class InvalidFieldException extends RuntimeException {
    private final Map<String, String> fields;
    public InvalidFieldException(String field, String message) {
        super(message);
        this.fields = Map.of(field, message);
    }
    /** Several invalid fields at once (e.g. everything a submission for review still needs), in display order. */
    public InvalidFieldException(Map<String, String> fields) {
        super(fields.entrySet().stream().map(entry -> entry.getKey() + ": " + entry.getValue()).collect(Collectors.joining("; ")));
        this.fields = new LinkedHashMap<>(fields);
    }
    public String field() { return fields.keySet().iterator().next(); }
    public Map<String, String> fields() { return fields; }
}
