package adliya.uz.functioncatalogservice.exception;

import org.springframework.http.HttpStatus;

public class TranslationUnavailableException extends RuntimeException {
    private final HttpStatus status;
    public TranslationUnavailableException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus status() { return status; }
}
