package adliya.uz.functioncatalogservice.dto;

/** Outcome of one bounded backfill request: how many records were looked at and how many were queued. */
public record TranslationBackfillResponse(int examined, int queued, int alreadyQueued, int limit, boolean limitReached) {}
