package adliya.uz.functioncatalogservice.dto;

import java.util.List;
public record FunctionImportResponse(Long auditId, int imported, int failed, List<Long> functionIds, List<RowError> errors) {
    public record RowError(long line, String reason) {}
}
