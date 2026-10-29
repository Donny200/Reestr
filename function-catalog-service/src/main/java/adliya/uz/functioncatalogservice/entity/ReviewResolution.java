package adliya.uz.functioncatalogservice.entity;

/**
 * How a review note was closed: the author changed the field and resubmitted, a reviewer resolved it explicitly, or
 * the draft revision it belonged to was discarded.
 */
public enum ReviewResolution { ADDRESSED, REVIEWER_RESOLVED, REVISION_DISCARDED }
