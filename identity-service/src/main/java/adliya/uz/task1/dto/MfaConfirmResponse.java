package adliya.uz.task1.dto;

import java.util.List;

/** Recovery codes are shown exactly once; only their hashes are stored. {@code user} is present when a session was started. */
public record MfaConfirmResponse(List<String> recoveryCodes, UserResponse user) {}
