package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.dto.AssigneeSummary;
import adliya.uz.functioncatalogservice.dto.AssignmentOverview;
import adliya.uz.functioncatalogservice.dto.StaffMember;
import adliya.uz.functioncatalogservice.entity.AuditAction;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.exception.WorkflowConflictException;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import adliya.uz.functioncatalogservice.security.CatalogAccess;
import adliya.uz.functioncatalogservice.security.OrganizationScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Responsible staff member per card. The assignee must be an active user of the card's organization as reported by
 * identity-service at assignment time; later deactivation or loss of access is surfaced by the overview, never fixed
 * silently by assigning somebody else.
 */
@Service
@RequiredArgsConstructor
public class FunctionAssignmentService {

    private static final Comparator<AssignmentOverview.Item> ORDER = Comparator
            .comparingInt((AssignmentOverview.Item item) -> item.state().ordinal()).reversed()
            .thenComparing(AssignmentOverview.Item::functionId);

    private final OrgFunctionRepository functions;
    private final IdentityOrganizationClient organizations;
    private final CatalogAccess access;
    private final AuditWriter audit;

    @Transactional
    public OrgFunction assign(Long functionId, Long userId) {
        OrgFunction function = assignable(functionId);
        StaffMember member = organizations.activeMembers(function.getOrganizationId()).stream()
                .filter(candidate -> userId.equals(candidate.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "User " + userId + " is not an active member of organization " + function.getOrganizationId()));
        if (Objects.equals(function.getAssignedUserId(), member.id())) {
            return function;
        }
        String previous = describe(function);
        function.setAssignedUserId(member.id());
        function.setAssignedUserEmail(member.email());
        function.setAssignedUserName(TextValues.truncate(member.displayName(), 150));
        function.setAssignedAt(Instant.now());
        functions.saveAndFlush(function);
        audit.function(function, AuditAction.ASSIGNEE_CHANGE, "Responsible staff: " + previous + " -> " + describe(function));
        return function;
    }

    @Transactional
    public OrgFunction unassign(Long functionId) {
        OrgFunction function = assignable(functionId);
        if (function.getAssignedUserId() == null) {
            return function;
        }
        String previous = describe(function);
        function.setAssignedUserId(null);
        function.setAssignedUserEmail(null);
        function.setAssignedUserName(null);
        function.setAssignedAt(null);
        functions.saveAndFlush(function);
        audit.function(function, AuditAction.ASSIGNEE_CHANGE, "Responsible staff: " + previous + " -> nobody");
        return function;
    }

    /** Active users of the card's organization; the caller's own session is forwarded to identity-service. */
    @Transactional(readOnly = true)
    public List<StaffMember> candidates(Long functionId) {
        OrgFunction function = assignable(functionId);
        return organizations.activeMembers(function.getOrganizationId()).stream()
                .sorted(Comparator.comparing(StaffMember::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public AssignmentOverview overview(Long organizationId, AssignmentOverview.State state) {
        OrganizationScope scope = access.scope(organizationId);
        if (scope.empty()) {
            return new AssignmentOverview(new AssignmentOverview.Counts(0, 0, 0), true, List.of());
        }
        List<OrgFunction> cards = (scope.allOrganizations() ? functions.findAll() : functions.findAllByOrganizationIdIn(scope.organizationIds()))
                .stream().filter(card -> !card.isRevision() && card.getOrganizationId() != null).toList();
        Map<Long, Optional<Map<Long, StaffMember>>> membersByOrganization = new HashMap<>();
        boolean checked = true;
        long assigned = 0;
        long unassigned = 0;
        long needsReassignment = 0;
        List<AssignmentOverview.Item> items = new java.util.ArrayList<>();
        for (OrgFunction card : cards) {
            AssignmentOverview.State current;
            if (card.getAssignedUserId() == null) {
                current = AssignmentOverview.State.UNASSIGNED;
                unassigned++;
            } else {
                Optional<Map<Long, StaffMember>> members = membersByOrganization.computeIfAbsent(card.getOrganizationId(), this::membersOf);
                if (members.isEmpty()) {
                    current = AssignmentOverview.State.UNKNOWN;
                    checked = false;
                } else if (members.get().containsKey(card.getAssignedUserId())) {
                    current = AssignmentOverview.State.ASSIGNED;
                    assigned++;
                } else {
                    current = AssignmentOverview.State.NEEDS_REASSIGNMENT;
                    needsReassignment++;
                }
            }
            if (state == null || state == current) {
                items.add(new AssignmentOverview.Item(card.getId(), card.getName(), card.getNameTranslations(), card.getStatus(),
                        card.getOrganizationId(), card.getFunctionCategory() == null ? null : card.getFunctionCategory().getId(),
                        card.getCategory(), AssigneeSummary.of(card), current));
            }
        }
        items.sort(ORDER);
        return new AssignmentOverview(new AssignmentOverview.Counts(assigned, unassigned, needsReassignment), checked, items);
    }

    private Optional<Map<Long, StaffMember>> membersOf(Long organizationId) {
        try {
            Map<Long, StaffMember> members = new HashMap<>();
            organizations.activeMembers(organizationId).forEach(member -> members.put(member.id(), member));
            return Optional.of(members);
        } catch (ResponseStatusException exception) {
            return Optional.empty();
        }
    }

    private OrgFunction assignable(Long functionId) {
        OrgFunction function = functions.findById(functionId)
                .orElseThrow(() -> new NoSuchElementException("Function not found, ID: " + functionId));
        access.requireOrganization(function.getOrganizationId());
        if (function.isRevision()) {
            throw new WorkflowConflictException("Responsibility is assigned on the published card, not on its draft revision");
        }
        if (function.getOrganizationId() == null) {
            throw new IllegalArgumentException("Assign an organization to the card before choosing a responsible staff member");
        }
        return function;
    }

    private static String describe(OrgFunction function) {
        return function.getAssignedUserId() == null ? "nobody" : "#" + function.getAssignedUserId() + " (" + function.getAssignedUserEmail() + ")";
    }
}
