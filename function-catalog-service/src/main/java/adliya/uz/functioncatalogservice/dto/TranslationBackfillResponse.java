package adliya.uz.functioncatalogservice.dto;

/**
 * Outcome of one bounded backfill request: how many records were looked at and how many were queued. The revision
 * counters are only non-zero when published cards were included: those are never changed in place, so their gaps are
 * filled on a draft revision ({@code revisionsCreated} new ones, {@code revisionsQueued} existing drafts) that then
 * goes through review; {@code skippedUnderReview} cards have a revision waiting for review and were left alone.
 */
public record TranslationBackfillResponse(int examined, int queued, int alreadyQueued, int limit, boolean limitReached,
                                          int revisionsCreated, int revisionsQueued, int skippedUnderReview) {

    public TranslationBackfillResponse(int examined, int queued, int alreadyQueued, int limit, boolean limitReached) {
        this(examined, queued, alreadyQueued, limit, limitReached, 0, 0, 0);
    }
}
