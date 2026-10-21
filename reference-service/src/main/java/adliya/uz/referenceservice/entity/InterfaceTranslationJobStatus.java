package adliya.uz.referenceservice.entity;

public enum InterfaceTranslationJobStatus {
    /** Waiting for the worker (also after a failed attempt; {@code nextAttemptAt} says when). */
    PENDING,
    /** Claimed by a worker; the lease in {@code nextAttemptAt} lets another tick take over after a crash. */
    RUNNING,
    DONE,
    /** Gave up after the configured attempts; only an explicit retry queues it again. */
    FAILED,
    /** Azure Translator offers no code for this language; re-checked when the provider list changes or on retry. */
    UNSUPPORTED
}
