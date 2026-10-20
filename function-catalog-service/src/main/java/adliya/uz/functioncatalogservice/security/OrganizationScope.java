package adliya.uz.functioncatalogservice.security;

import java.util.Set;

public record OrganizationScope(boolean allOrganizations, Set<Long> organizationIds) {

    public OrganizationScope {
        organizationIds = organizationIds == null ? Set.of() : Set.copyOf(organizationIds);
    }

    public static OrganizationScope all() {
        return new OrganizationScope(true, Set.of());
    }

    public static OrganizationScope of(Set<Long> organizationIds) {
        return new OrganizationScope(false, organizationIds);
    }

    public boolean empty() {
        return !allOrganizations && organizationIds.isEmpty();
    }

    public boolean includes(Long organizationId) {
        return allOrganizations || (organizationId != null && organizationIds.contains(organizationId));
    }
}
