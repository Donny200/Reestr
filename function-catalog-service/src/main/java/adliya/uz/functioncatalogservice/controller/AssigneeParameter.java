package adliya.uz.functioncatalogservice.controller;

/**
 * The {@code assignee} filter of staff lists. Only "me" is accepted: follow-up is routed to the caller's own
 * responsibilities, never looked up for other users.
 */
final class AssigneeParameter {

    private AssigneeParameter() {}

    /** True for "me", false when the filter is absent. */
    static boolean me(String assignee) {
        if (assignee == null || assignee.isBlank()) {
            return false;
        }
        if (!"me".equalsIgnoreCase(assignee.trim())) {
            throw new IllegalArgumentException("assignee accepts only \"me\"");
        }
        return true;
    }
}
