package adliya.uz.referenceservice.translation;

/**
 * The provider could not translate. {@code status} is the HTTP status Azure answered with (0 for a timeout or
 * connection failure). Messages never contain credentials.
 */
public class ProviderException extends RuntimeException {

    private final int status;

    public ProviderException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public boolean rateLimited() {
        return status == 429;
    }

    /** 400: the request itself was refused (e.g. a language the provider no longer offers). */
    public boolean rejected() {
        return status == 400;
    }
}
