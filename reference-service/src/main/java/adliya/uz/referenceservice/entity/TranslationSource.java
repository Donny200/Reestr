package adliya.uz.referenceservice.entity;

/**
 * Who wrote an interface translation. Precedence when values compete: HUMAN is never replaced automatically; SEED
 * (the curated catalog shipped with the service) replaces MACHINE; MACHINE only fills keys that have no value.
 */
public enum TranslationSource {
    SEED,
    HUMAN,
    MACHINE
}
