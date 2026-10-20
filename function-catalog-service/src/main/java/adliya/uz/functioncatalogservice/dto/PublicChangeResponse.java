package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.FunctionPublicChange;
import adliya.uz.functioncatalogservice.entity.PublicChangeType;

import java.time.Instant;
import java.util.List;

/** Public history entry: when a reviewed version went live and which sections differ from the previous public version. */
public record PublicChangeResponse(Instant publishedAt, PublicChangeType changeType, List<String> sections) {
    public static PublicChangeResponse from(FunctionPublicChange change) {
        return new PublicChangeResponse(change.getPublishedAt(), change.getChangeType(), change.getSections());
    }
}
