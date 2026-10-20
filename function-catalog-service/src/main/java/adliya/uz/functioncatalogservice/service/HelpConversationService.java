package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.HelpProperties;
import adliya.uz.functioncatalogservice.dto.HelpConversationSummary;
import adliya.uz.functioncatalogservice.dto.StaffMember;
import adliya.uz.functioncatalogservice.dto.SupportDtos;
import adliya.uz.functioncatalogservice.entity.AuditAction;
import adliya.uz.functioncatalogservice.entity.HelpConversation;
import adliya.uz.functioncatalogservice.entity.HelpConversationStatus;
import adliya.uz.functioncatalogservice.entity.HelpGuest;
import adliya.uz.functioncatalogservice.entity.HelpMessage;
import adliya.uz.functioncatalogservice.entity.InformationReport;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.ReportEntityType;
import adliya.uz.functioncatalogservice.exception.InvalidFieldException;
import adliya.uz.functioncatalogservice.exception.WorkflowConflictException;
import adliya.uz.functioncatalogservice.repository.HelpConversationRepository;
import adliya.uz.functioncatalogservice.repository.HelpGuestRepository;
import adliya.uz.functioncatalogservice.repository.HelpMessageRepository;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import adliya.uz.functioncatalogservice.security.CatalogAccess;
import adliya.uz.functioncatalogservice.security.JwtPrincipal;
import adliya.uz.functioncatalogservice.security.OrganizationScope;
import adliya.uz.functioncatalogservice.service.SupportIdentityService.Identity;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The support chat: one private thread per visitor and the staff inbox that answers it.
 *
 * <p>The visitor writes in a single thread. Each message is routed by its context: a message about a service (or an
 * organization) goes to the conversation of the responsible organization, a message without context to central
 * support. A visitor therefore has at most one conversation per organization plus one central one, all shown to the
 * visitor as one timeline, while organization staff only ever see their own organization's conversation. Access is
 * always derived from the caller's support identity or staff scope on the server, never from a conversation id.</p>
 *
 * <p>Open support pages and staff inboxes are told about changes through {@link SupportEventBroker} (server-sent
 * events carrying ids only) and then reload through these methods; light polling ({@code updates}, {@code unread})
 * remains the fallback when a stream is unavailable.</p>
 */
@Service
@RequiredArgsConstructor
public class HelpConversationService {

    public static final int LIST_LIMIT = 500;
    private static final int LABEL_MAX_LENGTH = 150;
    /** Polls also return messages created this recently, so a message committed out of id order is not missed. */
    private static final Duration POLL_OVERLAP = Duration.ofSeconds(60);
    private static final String NOT_FOUND = "Conversation not found";

    private final HelpConversationRepository conversations;
    private final HelpMessageRepository messages;
    private final HelpGuestRepository guests;
    private final OrgFunctionRepository functions;
    private final HelpRateLimiter rateLimiter;
    private final SupportRateLimiters limits;
    private final HelpProperties properties;
    private final HelpNotificationMailer mailer;
    private final IdentityOrganizationClient organizations;
    private final ReportSubmissionService reports;
    private final CatalogAccess access;
    private final AuditWriter audit;
    private final TransactionTemplate transaction;
    private final SupportEventBroker events;

    public boolean notificationsAvailable() {
        return mailer.available();
    }

    // ---------------------------------------------------------------- visitor side

    public SupportDtos.Thread thread(Optional<Identity> identity) {
        if (identity.isEmpty()) {
            return new SupportDtos.Thread(SupportDtos.Identity.NONE, List.of(), 0, null, mailer.available(), false);
        }
        HelpGuest guest = identity.get().guest();
        Map<Long, HelpConversation> owned = owned(guest.getId());
        List<SupportDtos.Message> thread = messages.findAllByConversationIdInOrderByIdAsc(owned.keySet()).stream()
                .map(message -> SupportDtos.Message.of(message, owned.get(message.getConversationId()).getOrganizationId())).toList();
        return new SupportDtos.Thread(identity.get().account() ? SupportDtos.Identity.ACCOUNT : SupportDtos.Identity.BROWSER,
                thread, unread(owned.keySet()), new SupportDtos.Contact(guest.getContactEmail(), guest.isNotifyByEmail()),
                mailer.available(), guest.getRecoveryCodeHash() != null);
    }

    /** New messages after {@code afterId}, plus anything created in the last minute (the client de-duplicates by id). */
    public SupportDtos.Updates updates(Optional<Identity> identity, Long afterId) {
        if (identity.isEmpty()) {
            return new SupportDtos.Updates(List.of(), 0, null);
        }
        Map<Long, HelpConversation> owned = owned(identity.get().guest().getId());
        if (owned.isEmpty()) {
            return new SupportDtos.Updates(List.of(), 0, null);
        }
        Instant recent = Instant.now().minus(POLL_OVERLAP);
        List<HelpMessage> found = messages.findAllByConversationIdInAndIdGreaterThanOrderByIdAsc(owned.keySet(),
                afterId == null ? 0L : afterId);
        List<HelpMessage> overlap = messages.findAllByConversationIdInOrderByIdAsc(owned.keySet()).stream()
                .filter(message -> message.getCreatedAt().isAfter(recent) && (afterId == null || message.getId() <= afterId)).toList();
        List<SupportDtos.Message> result = new ArrayList<>();
        overlap.forEach(message -> result.add(SupportDtos.Message.of(message, owned.get(message.getConversationId()).getOrganizationId())));
        found.forEach(message -> result.add(SupportDtos.Message.of(message, owned.get(message.getConversationId()).getOrganizationId())));
        result.sort(Comparator.comparing(SupportDtos.Message::id));
        return new SupportDtos.Updates(result, unread(owned.keySet()), latestReply(owned.keySet()));
    }

    public SupportDtos.Unread unreadFor(Optional<Identity> identity) {
        if (identity.isEmpty()) {
            return new SupportDtos.Unread(0, null);
        }
        Set<Long> owned = owned(identity.get().guest().getId()).keySet();
        return new SupportDtos.Unread(unread(owned), latestReply(owned));
    }

    /**
     * Stores one visitor message (idempotent by {@code clientMessageId}) in the conversation its context routes to,
     * creating that conversation on first use. A report category additionally files an information report for the
     * editorial workflow; the visitor still sees it as a message in the same thread.
     */
    public SupportDtos.Message send(Identity identity, SupportDtos.SendRequest request, String clientAddress) {
        HelpGuest guest = identity.guest();
        Optional<HelpMessage> repeated = repeated(guest.getId(), request.clientMessageId());
        if (repeated.isPresent()) {
            return visitorMessage(repeated.get());
        }
        rateLimiter.acquire(clientAddress);
        limits.guest(guest);
        if (request.website() != null && !request.website().isBlank()) {
            throw new InvalidFieldException("body", "This message could not be sent");
        }
        String body = visitorText(request.body(), request.reportCategory() != null);
        Route route = route(request);
        if (request.reportCategory() != null && route.entityType() == null) {
            throw new InvalidFieldException("reportCategory", "Choose the service or organization the report is about");
        }
        HelpMessage saved;
        try {
            saved = transaction.execute(status -> store(guest, request, route, body, clientAddress));
        } catch (DataIntegrityViolationException exception) {
            // Two first messages to the same organization raced, or the same client id was retried concurrently.
            Optional<HelpMessage> stored = repeated(guest.getId(), request.clientMessageId());
            if (stored.isPresent()) {
                return visitorMessage(stored.get());
            }
            saved = transaction.execute(status -> store(guest, request, route, body, clientAddress));
        }
        HelpConversation conversation = conversations.findById(saved.getConversationId()).orElseThrow();
        events.visitorEvent(guest.getId(), SupportEventBroker.Type.MESSAGE, saved.getId());
        events.staffEvent(conversation.getOrganizationId(), conversation.getId(), SupportEventBroker.Type.MESSAGE);
        return SupportDtos.Message.of(saved, conversation.getOrganizationId());
    }

    /** The visitor viewed the thread: staff replies up to {@code upTo} are read (persisted, so the badge survives reloads). */
    public SupportDtos.Unread markRead(Identity identity, Long upTo) {
        Map<Long, HelpConversation> ownedConversations = owned(identity.guest().getId());
        Set<Long> owned = ownedConversations.keySet();
        if (!owned.isEmpty() && upTo != null) {
            Integer changed = transaction.execute(status -> messages.markRead(owned, HelpMessage.Author.STAFF, upTo, Instant.now()));
            if (changed != null && changed > 0) {
                events.visitorEvent(identity.guest().getId(), SupportEventBroker.Type.READ, upTo);
                ownedConversations.values().forEach(conversation ->
                        events.staffEvent(conversation.getOrganizationId(), conversation.getId(), SupportEventBroker.Type.READ));
            }
        }
        return new SupportDtos.Unread(unread(owned), latestReply(owned));
    }

    /** Optional contact for support follow-up; an empty address removes it. Never required. */
    public SupportDtos.Contact updateContact(Identity identity, SupportDtos.ContactRequest request) {
        String email = request.email() == null || request.email().isBlank() ? null : request.email().trim();
        if (request.notifyByEmail() && email == null) {
            throw new InvalidFieldException("email", "Enter an e-mail address to receive notices, or turn notices off");
        }
        if (request.notifyByEmail() && !mailer.available()) {
            throw new InvalidFieldException("notifyByEmail", "E-mail notices are not available on this site");
        }
        return transaction.execute(status -> {
            HelpGuest guest = guests.findById(identity.guest().getId()).orElseThrow();
            guest.setContactEmail(email);
            guest.setNotifyByEmail(email != null && request.notifyByEmail());
            guests.saveAndFlush(guest);
            return new SupportDtos.Contact(guest.getContactEmail(), guest.isNotifyByEmail());
        });
    }

    private HelpMessage store(HelpGuest guest, SupportDtos.SendRequest request, Route route, String body, String clientAddress) {
        Instant now = Instant.now();
        HelpConversation conversation = conversations.findRoute(guest.getId(), route.organizationId())
                .orElseGet(() -> conversations.saveAndFlush(HelpConversation.builder()
                        .guestId(guest.getId()).organizationId(route.organizationId())
                        .status(HelpConversationStatus.NEW).language(TextValues.language(request.language()))
                        .createdAt(now).updatedAt(now).lastVisitorMessageAt(now).lastMessageAt(now).build()));
        List<HelpMessage> thread = messages.findAllByConversationIdOrderByIdAsc(conversation.getId());
        if (thread.size() >= properties.getMaxMessages()) {
            throw new WorkflowConflictException("This conversation reached its message limit");
        }
        int unanswered = 0;
        for (int index = thread.size() - 1; index >= 0 && thread.get(index).getAuthor() == HelpMessage.Author.VISITOR; index--) {
            unanswered++;
        }
        if (unanswered >= properties.getMaxUnansweredVisitorMessages()) {
            throw new WorkflowConflictException("Please wait for a reply before sending more messages");
        }
        InformationReport report = request.reportCategory() == null ? null
                : reports.fileFromSupport(route.entityType(), route.entityId(), request.reportCategory(), body,
                request.language(), clientAddress);
        HelpMessage message = messages.save(HelpMessage.builder().conversationId(conversation.getId())
                .author(HelpMessage.Author.VISITOR).body(body).createdAt(now).clientMessageId(request.clientMessageId())
                .functionId(route.functionId()).contextLabel(route.label())
                .category(request.reportCategory() == null ? null : request.reportCategory().name())
                .reportId(report == null ? null : report.getId()).build());
        if (conversation.getStatus() == HelpConversationStatus.CLOSED) {
            conversation.setStatus(HelpConversationStatus.NEW);
            conversation.setClosedAt(null);
        } else if (conversation.getStatus() == HelpConversationStatus.ANSWERED) {
            conversation.setStatus(HelpConversationStatus.IN_PROGRESS);
        }
        if (route.functionId() != null) {
            conversation.setFunctionId(route.functionId());
            conversation.setSubjectLabel(route.label());
        }
        conversation.setLastVisitorMessageAt(now);
        conversation.setLastMessageAt(now);
        conversation.setUpdatedAt(now);
        conversations.saveAndFlush(conversation);
        messages.flush();
        return message;
    }

    /** Organization responsible for the message's context; central support when there is none. */
    private Route route(SupportDtos.SendRequest request) {
        if (request.serviceId() != null) {
            OrgFunction service = functions.requirePublished(request.serviceId());
            return new Route(service.getOrganizationId(), service.getId(), TextValues.truncate(service.getName(), LABEL_MAX_LENGTH),
                    ReportEntityType.FUNCTION, service.getId());
        }
        if (request.organizationId() != null) {
            IdentityOrganizationClient.PublicOrganization organization = organizations.requirePublic(request.organizationId());
            return new Route(organization.id(), null, TextValues.truncate(organization.name(), LABEL_MAX_LENGTH),
                    ReportEntityType.ORGANIZATION, organization.id());
        }
        return new Route(null, null, null, null, null);
    }

    private record Route(Long organizationId, Long functionId, String label, ReportEntityType entityType, Long entityId) {}

    private Optional<HelpMessage> repeated(Long guestId, String clientMessageId) {
        Set<Long> owned = owned(guestId).keySet();
        return owned.isEmpty() ? Optional.empty()
                : messages.findFirstByConversationIdInAndAuthorAndClientMessageId(owned, HelpMessage.Author.VISITOR, clientMessageId);
    }

    private SupportDtos.Message visitorMessage(HelpMessage message) {
        Long organizationId = conversations.findById(message.getConversationId()).map(HelpConversation::getOrganizationId).orElse(null);
        return SupportDtos.Message.of(message, organizationId);
    }

    private Map<Long, HelpConversation> owned(Long guestId) {
        return conversations.findAllByGuestId(guestId).stream().collect(Collectors.toMap(HelpConversation::getId, Function.identity()));
    }

    private long unread(Collection<Long> owned) {
        return owned.isEmpty() ? 0 : messages.countByConversationIdInAndAuthorAndReadAtIsNull(owned, HelpMessage.Author.STAFF);
    }

    private Long latestReply(Collection<Long> owned) {
        if (owned.isEmpty()) {
            return null;
        }
        return messages.findLatest(owned).stream().filter(message -> message.getAuthor() == HelpMessage.Author.STAFF)
                .map(HelpMessage::getId).max(Long::compare).orElse(null);
    }

    // ---------------------------------------------------------------- staff side

    public enum InboxSort { LAST_MESSAGE, UNREAD_FIRST }

    public record InboxQuery(Set<HelpConversationStatus> statuses, Long organizationId, Long serviceId, boolean generalOnly,
                             boolean assignedToMe, boolean unreadOnly, InboxSort sort) {}

    public List<HelpConversationSummary> inbox(InboxQuery query) {
        OrganizationScope scope = access.scope(query.organizationId());
        if (query.generalOnly() && !scope.allOrganizations()) {
            throw new AccessDeniedException("Only central support staff can view general conversations");
        }
        if (scope.empty()) {
            return List.of();
        }
        Specification<HelpConversation> specification = ScopeSpecifications.<HelpConversation>inScope(scope)
                .and((root, criteria, builder) -> root.get("status").in(query.statuses()));
        if (query.generalOnly()) {
            specification = specification.and((root, criteria, builder) -> builder.isNull(root.get("organizationId")));
        }
        if (query.serviceId() != null) {
            specification = specification.and((root, criteria, builder) -> builder.equal(root.get("functionId"), query.serviceId()));
        }
        if (query.assignedToMe()) {
            Long me = access.principal().userId();
            specification = specification.and((root, criteria, builder) -> builder.equal(root.get("assignedUserId"), me == null ? -1L : me));
        }
        if (query.unreadOnly()) {
            specification = specification.and(hasUnreadVisitorMessages());
        }
        List<HelpConversation> found = conversations.findAll(specification, PageRequest.of(0, LIST_LIMIT,
                Sort.by(Sort.Order.desc("lastMessageAt"), Sort.Order.desc("id")))).getContent();
        List<HelpConversationSummary> summaries = summaries(found);
        if (query.sort() == InboxSort.UNREAD_FIRST) {
            summaries = summaries.stream().sorted(Comparator.comparing((HelpConversationSummary item) -> item.unreadCount() == 0)).toList();
        }
        return summaries;
    }

    /** Badge for the admin navigation: conversations and visitor messages in scope that no staff member has read. */
    public HelpConversationSummary.Unread staffUnread() {
        OrganizationScope scope = access.scope(null);
        if (scope.empty()) {
            return new HelpConversationSummary.Unread(0, 0);
        }
        List<Long> ids = conversations.findAll(ScopeSpecifications.<HelpConversation>inScope(scope).and(hasUnreadVisitorMessages()))
                .stream().map(HelpConversation::getId).toList();
        long count = ids.isEmpty() ? 0 : messages.countByConversationIdInAndAuthorAndReadAtIsNull(ids, HelpMessage.Author.VISITOR);
        return new HelpConversationSummary.Unread(ids.size(), count);
    }

    public HelpConversationSummary.Detail detail(Long id) {
        HelpConversation conversation = visibleToStaff(id);
        List<HelpMessage> thread = messages.findAllByConversationIdOrderByIdAsc(id);
        HelpGuest guest = conversation.getGuestId() == null ? null : guests.findById(conversation.getGuestId()).orElse(null);
        boolean contact = guest != null && guest.getContactEmail() != null;
        SupportDtos.Contact visibleContact = contact && access.hasPermission("VISITOR_QUESTIONS_REPLY")
                ? new SupportDtos.Contact(guest.getContactEmail(), guest.isNotifyByEmail()) : null;
        long unread = thread.stream().filter(message -> message.getAuthor() == HelpMessage.Author.VISITOR && message.getReadAt() == null).count();
        return new HelpConversationSummary.Detail(
                HelpConversationSummary.of(conversation, thread.size(), unread, thread.isEmpty() ? null : thread.get(thread.size() - 1), contact),
                thread.stream().map(HelpConversationSummary.Message::of).toList(), visibleContact);
    }

    /** Staff viewed the conversation: visitor messages up to {@code upTo} are read for the whole team. */
    public HelpConversationSummary.Detail markReadByStaff(Long id, Long upTo) {
        HelpConversation conversation = visibleToStaff(id);
        if (upTo != null) {
            Integer changed = transaction.execute(status -> messages.markRead(List.of(id), HelpMessage.Author.VISITOR, upTo, Instant.now()));
            if (changed != null && changed > 0) {
                events.visitorEvent(conversation.getGuestId(), SupportEventBroker.Type.READ, upTo);
                events.staffEvent(conversation.getOrganizationId(), id, SupportEventBroker.Type.READ);
            }
        }
        return detail(id);
    }

    public HelpConversationSummary.Detail reply(Long id, String text, String clientMessageId) {
        String body = staffText(text);
        HelpGuest[] notify = new HelpGuest[1];
        transaction.executeWithoutResult(status -> {
            HelpConversation conversation = visibleToStaff(id);
            if (clientMessageId != null && messages.findFirstByConversationIdInAndAuthorAndClientMessageId(List.of(id),
                    HelpMessage.Author.STAFF, clientMessageId).isPresent()) {
                return;
            }
            if (conversation.getStatus() == HelpConversationStatus.CLOSED) {
                throw new WorkflowConflictException("Reopen the conversation before replying");
            }
            if (messages.countByConversationId(id) >= properties.getMaxMessages()) {
                throw new WorkflowConflictException("This conversation reached its message limit");
            }
            AuditWriter.Actor actor = audit.actor();
            Instant now = Instant.now();
            HelpMessage saved = messages.save(HelpMessage.builder().conversationId(id).author(HelpMessage.Author.STAFF).staffUserId(actor.id())
                    .staffLabel(actor.email()).body(body).createdAt(now).clientMessageId(clientMessageId).build());
            // Answering means the staff member has seen what the visitor wrote so far.
            messages.markRead(List.of(id), HelpMessage.Author.VISITOR, Long.MAX_VALUE, now);
            HelpConversationStatus previous = conversation.getStatus();
            conversation.setStatus(HelpConversationStatus.ANSWERED);
            conversation.setLastStaffReplyAt(now);
            conversation.setLastMessageAt(now);
            conversation.setUpdatedAt(now);
            if (conversation.getAssignedUserId() == null) {
                conversation.setAssignedUserId(actor.id());
                conversation.setAssignedUserName(actor.email());
            }
            conversations.saveAndFlush(conversation);
            audit.helpConversation(conversation, AuditAction.HELP_REPLY,
                    "Conversation #" + id + ": reply sent; " + previous + " -> " + HelpConversationStatus.ANSWERED);
            notify[0] = conversation.getGuestId() == null ? null : guests.findById(conversation.getGuestId()).orElse(null);
            messages.flush();
            events.visitorEvent(conversation.getGuestId(), SupportEventBroker.Type.MESSAGE, saved.getId());
            events.staffEvent(conversation.getOrganizationId(), id, SupportEventBroker.Type.MESSAGE);
        });
        HelpGuest guest = notify[0];
        if (guest != null && guest.isNotifyByEmail() && guest.getContactEmail() != null) {
            mailer.sendReplyNotice(guest.getContactEmail());
        }
        return detail(id);
    }

    public HelpConversationSummary.Detail changeStatus(Long id, HelpConversationStatus target) {
        transaction.executeWithoutResult(status -> {
            HelpConversation conversation = visibleToStaff(id);
            HelpConversationStatus previous = conversation.getStatus();
            if (!previous.canMoveTo(target)) {
                throw new WorkflowConflictException("A conversation cannot move from " + previous + " to " + target
                        + (target == HelpConversationStatus.ANSWERED ? "; send a reply instead" : ""));
            }
            Instant now = Instant.now();
            conversation.setStatus(target);
            conversation.setUpdatedAt(now);
            conversation.setClosedAt(target == HelpConversationStatus.CLOSED ? now : null);
            conversations.saveAndFlush(conversation);
            audit.helpConversation(conversation, AuditAction.HELP_STATUS_CHANGE, "Conversation #" + id + ": " + previous + " -> " + target);
            events.staffEvent(conversation.getOrganizationId(), id, SupportEventBroker.Type.STATUS);
        });
        return detail(id);
    }

    /** Staff who may be assigned: active members of the conversation's organization; for central support the caller only. */
    public List<StaffMember> candidates(Long id) {
        HelpConversation conversation = visibleToStaff(id);
        if (conversation.getOrganizationId() == null) {
            JwtPrincipal me = access.principal();
            return me.userId() == null ? List.of() : List.of(new StaffMember(me.userId(), null, null, me.email(), me.role()));
        }
        return organizations.activeMembers(conversation.getOrganizationId());
    }

    public HelpConversationSummary.Detail assign(Long id, Long userId) {
        HelpConversation current = visibleToStaff(id);
        StaffMember member = userId == null ? null : candidates(id).stream()
                .filter(candidate -> userId.equals(candidate.id())).findFirst()
                .orElseThrow(() -> new InvalidFieldException("userId", "Choose an active staff member of the responsible organization"));
        transaction.executeWithoutResult(status -> {
            HelpConversation conversation = conversations.findById(current.getId()).orElseThrow(() -> new NoSuchElementException(NOT_FOUND));
            Long previous = conversation.getAssignedUserId();
            conversation.setAssignedUserId(member == null ? null : member.id());
            conversation.setAssignedUserName(member == null ? null : member.displayName());
            conversation.setUpdatedAt(Instant.now());
            conversations.saveAndFlush(conversation);
            audit.helpConversation(conversation, AuditAction.HELP_ASSIGNEE_CHANGE, "Conversation #" + id + ": assignee "
                    + (previous == null ? "nobody" : "#" + previous) + " -> " + (member == null ? "nobody" : "#" + member.id()));
            events.staffEvent(conversation.getOrganizationId(), id, SupportEventBroker.Type.STATUS);
        });
        return detail(id);
    }

    // ---------------------------------------------------------------- helpers

    /** Organization staff see their organizations' conversations; central (general) ones only global staff. */
    private HelpConversation visibleToStaff(Long id) {
        HelpConversation conversation = conversations.findById(id).orElseThrow(() -> new NoSuchElementException(NOT_FOUND));
        if (conversation.getOrganizationId() == null) {
            if (!access.global()) {
                throw new AccessDeniedException("Only central support staff can handle general conversations");
            }
        } else {
            access.requireOrganization(conversation.getOrganizationId());
        }
        return conversation;
    }

    private List<HelpConversationSummary> summaries(List<HelpConversation> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        List<Long> ids = found.stream().map(HelpConversation::getId).toList();
        Map<Long, Long> counts = grouped(messages.countByConversationIds(ids));
        Map<Long, Long> unread = grouped(messages.countUnread(ids, HelpMessage.Author.VISITOR));
        Map<Long, HelpMessage> latest = messages.findLatest(ids).stream()
                .collect(Collectors.toMap(HelpMessage::getConversationId, Function.identity()));
        Set<Long> guestIds = found.stream().map(HelpConversation::getGuestId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> withContact = guests.findAllById(guestIds).stream().filter(guest -> guest.getContactEmail() != null)
                .map(HelpGuest::getId).collect(Collectors.toSet());
        return found.stream().map(item -> HelpConversationSummary.of(item, counts.getOrDefault(item.getId(), 0L),
                unread.getOrDefault(item.getId(), 0L), latest.get(item.getId()),
                item.getGuestId() != null && withContact.contains(item.getGuestId()))).toList();
    }

    private static Map<Long, Long> grouped(List<Object[]> rows) {
        Map<Long, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }

    private static Specification<HelpConversation> hasUnreadVisitorMessages() {
        return (root, criteria, builder) -> {
            Subquery<Long> unread = criteria.subquery(Long.class);
            Root<HelpMessage> message = unread.from(HelpMessage.class);
            unread.select(message.get("id")).where(builder.equal(message.get("conversationId"), root.get("id")),
                    builder.equal(message.get("author"), HelpMessage.Author.VISITOR), builder.isNull(message.get("readAt")));
            return builder.exists(unread);
        };
    }

    private static String visitorText(String raw, boolean report) {
        String value = VisitorText.normalize(raw);
        if (!VisitorText.meaningful(value, report ? 10 : 1)) {
            throw new InvalidFieldException("body", report ? "Describe the problem in at least 10 characters" : "Write a message");
        }
        if (value.length() > 2000) {
            throw new InvalidFieldException("body", "A message may contain at most 2000 characters");
        }
        if (VisitorText.links(value) > VisitorText.MAX_LINKS) {
            throw new InvalidFieldException("body", "A message may contain at most " + VisitorText.MAX_LINKS + " links");
        }
        if (VisitorText.containsLongNumber(value)) {
            throw new InvalidFieldException("body",
                    "Remove identification, card or document numbers: they are not needed to answer a question");
        }
        return value;
    }

    private static String staffText(String raw) {
        String value = VisitorText.normalize(raw);
        if (!VisitorText.meaningful(value, 1)) {
            throw new InvalidFieldException("message", "Write a reply");
        }
        if (value.length() > 2000) {
            throw new InvalidFieldException("message", "A reply may contain at most 2000 characters");
        }
        return value;
    }

    public static Set<HelpConversationStatus> statuses(String value) {
        String normalized = value == null || value.isBlank() ? "ACTIVE" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ACTIVE" -> HelpConversationStatus.ACTIVE;
            case "OPEN" -> HelpConversationStatus.AWAITING_STAFF;
            case "ALL" -> EnumSet.allOf(HelpConversationStatus.class);
            default -> {
                try {
                    yield EnumSet.of(HelpConversationStatus.valueOf(normalized));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("Unknown conversation status: " + value);
                }
            }
        };
    }
}
