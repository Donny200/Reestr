package adliya.uz.functioncatalogservice.repository;

import adliya.uz.functioncatalogservice.security.OrganizationScope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.time.LocalDate;

public record StatisticsFilter(OrganizationScope scope, Long categoryId) {

    static MapSqlParameterSource parameters(LocalDate from, LocalDate to) {
        return new MapSqlParameterSource().addValue("from", from).addValue("to", to);
    }

    /** Period, scope and category conditions on the table aliased {@code alias}, joined to org_functions as {@code f}. */
    String where(String alias, MapSqlParameterSource parameters) {
        StringBuilder sql = new StringBuilder(" WHERE " + alias + ".activity_date BETWEEN :from AND :to");
        if (!scope.allOrganizations()) {
            sql.append(" AND ").append(alias).append(".organization_id IN (:organizations)");
            parameters.addValue("organizations", scope.organizationIds());
        }
        if (categoryId != null) {
            sql.append(" AND f.category_id = :category");
            parameters.addValue("category", categoryId);
        }
        return sql.toString();
    }
}
