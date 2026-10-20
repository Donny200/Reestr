package adliya.uz.functioncatalogservice.entity;

public enum TranslationJobStatus {
    /** Waiting for the worker (possibly after a failed attempt; see nextAttemptAt). */
    PENDING,
    /** Claimed by the worker; the lease expires at nextAttemptAt if the process dies mid-run. */
    RUNNING,
    /** The provider was reached; every missing translation it returned has been stored. */
    DONE,
    /** Gave up after the configured number of attempts; staff can retry explicitly. */
    FAILED,
    /** Nothing to do: the record is gone, published or under review (drafts are translated through a revision). */
    SKIPPED
}
