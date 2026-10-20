package adliya.uz.functioncatalogservice.entity;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum ReportStatus {
    NEW, IN_PROGRESS, RESOLVED, REJECTED;

    public static final Set<ReportStatus> OPEN = Collections.unmodifiableSet(EnumSet.of(NEW, IN_PROGRESS));

    public boolean closed() {
        return this == RESOLVED || this == REJECTED;
    }

    public boolean canMoveTo(ReportStatus target) {
        if (target == this) {
            return false;
        }
        return closed() ? target == IN_PROGRESS : target != NEW;
    }
}
