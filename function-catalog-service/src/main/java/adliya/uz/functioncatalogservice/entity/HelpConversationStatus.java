package adliya.uz.functioncatalogservice.entity;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * NEW ("open"): a visitor message waits and nobody has taken it. IN_PROGRESS: staff is on it, or the visitor wrote
 * again after a reply. ANSWERED: staff replied last. CLOSED: handled; a new visitor message reopens it as NEW.
 */
public enum HelpConversationStatus {
    NEW, IN_PROGRESS, ANSWERED, CLOSED;

    /** Conversations that still need a reply from staff. */
    public static final Set<HelpConversationStatus> AWAITING_STAFF = Collections.unmodifiableSet(EnumSet.of(NEW, IN_PROGRESS));
    public static final Set<HelpConversationStatus> ACTIVE = Collections.unmodifiableSet(EnumSet.of(NEW, IN_PROGRESS, ANSWERED));

    /** Status changes staff may make by hand (open, in progress, closed, reopen); ANSWERED is set by replying. */
    public boolean canMoveTo(HelpConversationStatus target) {
        return target != this && target != ANSWERED;
    }
}
