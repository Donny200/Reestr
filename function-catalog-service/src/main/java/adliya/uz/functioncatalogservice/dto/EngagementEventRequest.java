package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.EngagementEventType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EngagementEventRequest(@NotNull EngagementEventType type, @Positive Long serviceId, @Positive Long organizationId) {}
