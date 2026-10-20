package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.entity.AuditAction;
import adliya.uz.functioncatalogservice.entity.AuditLog;
import adliya.uz.functioncatalogservice.entity.FunctionPublicChange;
import adliya.uz.functioncatalogservice.entity.FunctionStatus;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.PublicChangeType;
import adliya.uz.functioncatalogservice.repository.AuditLogRepository;
import adliya.uz.functioncatalogservice.repository.FunctionPublicChangeRepository;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Public change history of published cards. Entries are written only when a reviewer publishes; draft edits,
 * submissions, rejections and discarded revisions never reach it, and no draft text, note or user is stored.
 */
@Service
@RequiredArgsConstructor
public class PublicChangeService {

    private final FunctionPublicChangeRepository changes;
    private final OrgFunctionRepository functions;
    private final AuditLogRepository auditLogs;

    /** Records the publication of {@code published} (already holding the new public content). Returns null when nothing public changed. */
    @Transactional(propagation = Propagation.MANDATORY)
    public FunctionPublicChange recordPublication(OrgFunction published, FunctionStatus previousStatus, Instant now) {
        Map<String, String> hashes = PublicContentHash.of(published);
        Optional<FunctionPublicChange> last = changes.findFirstByFunctionIdOrderByPublishedAtDescIdDesc(published.getId());
        PublicChangeType type;
        List<String> sections;
        if (last.isEmpty()) {
            type = PublicChangeType.FIRST_PUBLICATION;
            sections = List.of();
        } else {
            type = previousStatus == FunctionStatus.DEACTIVATED ? PublicChangeType.REPUBLISHED : PublicChangeType.UPDATE;
            sections = PublicContentHash.changedSections(last.get().getSectionHashes(), hashes);
            if (type == PublicChangeType.UPDATE && sections.isEmpty() && last.get().getSectionHashes() != null) {
                return null;
            }
        }
        return changes.save(FunctionPublicChange.builder().functionId(published.getId()).publishedAt(now)
                .changeType(type).sections(sections).sectionHashes(hashes).build());
    }

    /** Newest first; only published cards have a public history (anything else is 404 like the public card itself). */
    @Transactional(readOnly = true)
    public List<FunctionPublicChange> publicHistory(Long functionId) {
        functions.findByIdAndStatus(functionId, FunctionStatus.PUBLISHED)
                .orElseThrow(() -> new NoSuchElementException("Function not found, ID: " + functionId));
        return changes.findTop20ByFunctionIdOrderByPublishedAtDescIdDesc(functionId);
    }

    @Transactional(readOnly = true)
    public Map<Long, Instant> lastUpdatedAt(Collection<Long> functionIds) {
        Map<Long, Instant> result = new HashMap<>();
        if (functionIds.isEmpty()) {
            return result;
        }
        for (Object[] row : changes.latestPublishedAt(functionIds)) {
            result.put((Long) row[0], (Instant) row[1]);
        }
        return result;
    }

    /**
     * Gives cards that were published before this history existed a first entry, so their next publication can report
     * which sections changed. The entry is dated by the last recorded publication in the internal audit log when known.
     */
    @Transactional
    public int backfill(Instant now) {
        int created = 0;
        for (OrgFunction function : functions.findAllByStatus(FunctionStatus.PUBLISHED)) {
            if (function.isRevision() || changes.existsByFunctionId(function.getId())) {
                continue;
            }
            Instant publishedAt = auditLogs.findFunctionHistory(function.getId()).stream()
                    .filter(log -> log.getAction() == AuditAction.PUBLISH)
                    .map(AuditLog::getPerformedAt)
                    .reduce((first, second) -> second)
                    .orElse(now);
            changes.save(FunctionPublicChange.builder().functionId(function.getId()).publishedAt(publishedAt)
                    .changeType(PublicChangeType.FIRST_PUBLICATION).sections(List.of())
                    .sectionHashes(PublicContentHash.of(function)).build());
            created++;
        }
        return created;
    }
}
