package adliya.uz.task1.dto;

/** Tells the login page whether the self-service "Forgot password?" flow can deliver links. */
public record PasswordResetOptionsResponse(boolean selfServiceAvailable) {}
