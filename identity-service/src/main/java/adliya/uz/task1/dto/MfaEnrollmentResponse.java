package adliya.uz.task1.dto;

/** Returned exactly once when enrollment starts; the secret is shown to the user to add it to an authenticator app. */
public record MfaEnrollmentResponse(String secret, String otpauthUri, String issuer, String account) {}
