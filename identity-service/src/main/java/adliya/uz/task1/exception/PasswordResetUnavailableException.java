package adliya.uz.task1.exception;

/** Self-service reset needs a configured, verified e-mail delivery; without it no fallback is offered. */
public class PasswordResetUnavailableException extends RuntimeException {
    public PasswordResetUnavailableException(String message) {
        super(message);
    }
}
