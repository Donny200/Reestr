package adliya.uz.functioncatalogservice.dto;

import jakarta.validation.constraints.Size;

/** Optional overall note sent with a request for changes; the field-level notes carry the details. */
public record RequestChangesRequest(@Size(max = 2000) String summary) {}
