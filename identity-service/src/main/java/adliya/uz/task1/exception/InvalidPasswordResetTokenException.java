package adliya.uz.task1.exception;

/** The reset token is unknown, expired, already used or superseded; the response never says which. */
public class InvalidPasswordResetTokenException extends RuntimeException {
    public InvalidPasswordResetTokenException(String message) {
        super(message);
    }
}
