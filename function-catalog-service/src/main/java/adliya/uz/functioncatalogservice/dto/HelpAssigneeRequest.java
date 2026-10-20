package adliya.uz.functioncatalogservice.dto;

import jakarta.validation.constraints.Positive;

/** {@code userId} null removes the assignment. */
public record HelpAssigneeRequest(@Positive Long userId) {}
