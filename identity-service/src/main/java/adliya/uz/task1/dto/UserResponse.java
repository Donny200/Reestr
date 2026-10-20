package adliya.uz.task1.dto;

import adliya.uz.task1.entity.Organization;
import adliya.uz.task1.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.List;
import java.util.Comparator;
import adliya.uz.task1.entity.Permission;
import java.util.stream.Collectors;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String role;
    private Boolean enabled;
    private Boolean mustChangePassword;
    private LocalDateTime createdAt;
    private Set<Long> organizationIds;

    private List<String> permissions;
    private List<OrganizationSummary> organizations;

    /** Second-factor state; absent on responses that did not look it up. */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private MfaSummary mfa;

    public record OrganizationSummary(Long id, String name) {}

    public record MfaSummary(boolean required, boolean enrolled, java.time.Instant enrollmentDeadline, boolean enrollmentOverdue) {}

    public UserResponse withMfa(MfaSummary summary) {
        return toBuilder().mfa(summary).build();
    }

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole().getName())
                .enabled(user.getEnabled())
                .mustChangePassword(user.getMustChangePassword())
                .createdAt(user.getCreatedAt())
                .permissions(user.getRole().getPermissions().stream().map(Permission::getCode).sorted().toList())
                .organizations(user.getOrganizations().stream()
                        .sorted(Comparator.comparing(Organization::getId))
                        .map(org -> new OrganizationSummary(org.getId(), org.getName())).toList())
                .organizationIds(user.getOrganizations().stream()
                        .map(Organization::getId)
                        .collect(Collectors.toSet()))
                .build();
    }
}
