package adliya.uz.functioncatalogservice.exception;

public class InvalidFieldException extends RuntimeException {
    private final String field;
    public InvalidFieldException(String field, String message) {
        super(message);
        this.field = field;
    }
    public String field() { return field; }
}
