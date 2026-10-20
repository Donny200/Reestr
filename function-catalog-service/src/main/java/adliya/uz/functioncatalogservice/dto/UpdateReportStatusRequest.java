package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.ReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateReportStatusRequest(@NotNull ReportStatus status, @Size(max = 2000) String note) {}
