package adliya.uz.task1.exception;

/** The second factor is missing or wrong; the message never reveals which. */
public class MfaRequiredException extends RuntimeException {
    public MfaRequiredException(String message) {
        super(message);
    }
}
