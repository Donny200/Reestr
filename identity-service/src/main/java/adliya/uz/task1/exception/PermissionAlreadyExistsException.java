package adliya.uz.task1.exception;

public class PermissionAlreadyExistsException extends RuntimeException {
    public PermissionAlreadyExistsException(String message) {
        super(message);
    }
}