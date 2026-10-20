package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.HelpConversationStatus;
import jakarta.validation.constraints.NotNull;

public record HelpStatusRequest(@NotNull HelpConversationStatus status) {}
