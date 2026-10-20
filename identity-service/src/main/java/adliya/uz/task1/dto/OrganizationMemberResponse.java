package adliya.uz.task1.dto;

import adliya.uz.task1.entity.User;

/** Active user with access to an organization, for choosing responsible staff. */
public record OrganizationMemberResponse(Long id, String firstName, String lastName, String email, String role) {
    public static OrganizationMemberResponse from(User user) {
        return new OrganizationMemberResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
                user.getRole() == null ? null : user.getRole().getName());
    }
}
