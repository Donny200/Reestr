package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.security.OrganizationScope;
import org.springframework.data.jpa.domain.Specification;

/** Staff queries limited to the caller's organizations through an {@code organizationId} attribute. */
final class ScopeSpecifications {

    private ScopeSpecifications() {}

    static <T> Specification<T> inScope(OrganizationScope scope) {
        return scope.allOrganizations()
                ? (root, criteria, builder) -> builder.conjunction()
                : (root, criteria, builder) -> root.get("organizationId").in(scope.organizationIds());
    }
}
