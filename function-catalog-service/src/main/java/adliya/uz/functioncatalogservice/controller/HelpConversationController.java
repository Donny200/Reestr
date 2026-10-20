package adliya.uz.functioncatalogservice.controller;

import adliya.uz.functioncatalogservice.dto.HelpAssigneeRequest;
import adliya.uz.functioncatalogservice.dto.HelpConversationSummary;
import adliya.uz.functioncatalogservice.dto.HelpMessageRequest;
import adliya.uz.functioncatalogservice.dto.HelpStatusRequest;
import adliya.uz.functioncatalogservice.dto.StaffMember;
import adliya.uz.functioncatalogservice.dto.SupportDtos;
import adliya.uz.functioncatalogservice.exception.RateLimitExceededException;
import adliya.uz.functioncatalogservice.security.CatalogAccess;
import adliya.uz.functioncatalogservice.security.ChatOriginGuard;
import adliya.uz.functioncatalogservice.security.OrganizationScope;
import adliya.uz.functioncatalogservice.service.ClientAddressResolver;
import adliya.uz.functioncatalogservice.service.HelpConversationService;
import adliya.uz.functioncatalogservice.service.SupportEventBroker;
import adliya.uz.functioncatalogservice.service.SupportIdentityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/help")
@RequiredArgsConstructor
@Tag(name = "Support chat", description = "One private support thread per visitor (anonymous browser or signed-in account), "
        + "routed per responsible organization, and the staff inbox that answers it. Open pages are told about changes "
        + "through server-sent events (ids only) and reload through the REST endpoints; polling remains the fallback. "
        + "Visitor endpoints are identified by an HttpOnly guest cookie or the account; never by a conversation id.")
public class HelpConversationController {

    static final String VIEW = "hasAuthority('VISITOR_QUESTIONS_VIEW')";
    static final String REPLY = "hasAuthority('VISITOR_QUESTIONS_VIEW') and hasAuthority('VISITOR_QUESTIONS_REPLY')";

    private final HelpConversationService help;
    private final SupportIdentityService identities;
    private final ClientAddressResolver clientAddresses;
    private final ChatOriginGuard origins;
    private final SupportEventBroker events;
    private final CatalogAccess access;

    @GetMapping("/options")
    @Operation(summary = "What the support page can offer", description = "Whether e-mail reply notices are configured, and length limits.")
    public Map<String, Object> options() {
        return Map.of("notificationsAvailable", help.notificationsAvailable(), "maxLength", 2000);
    }

    // ---------------------------------------------------------------- visitor

    @GetMapping("/support")
    @Operation(summary = "My support thread", description = "Empty (identity NONE) until the first message; nothing is created by reading.")
    public ResponseEntity<SupportDtos.Thread> thread(HttpServletRequest request) {
        return noStore(help.thread(identities.current(request)));
    }

    @GetMapping("/support/updates")
    @Operation(summary = "Poll for new messages", description = "Messages after the given id (plus the last minute) and the unread count.")
    public ResponseEntity<SupportDtos.Updates> updates(@RequestParam(required = false) Long after, HttpServletRequest request) {
        return noStore(help.updates(identities.current(request), after));
    }

    @GetMapping(value = "/support/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Live notices for my support thread (server-sent events)", description = "Events \"support\" with "
            + "{type: MESSAGE|READ, messageId}; no message text. Reload the thread after each event and after reconnecting. "
            + "204 without a support identity or when streams are disabled; 429 when too many streams are open.")
    public ResponseEntity<SseEmitter> stream(HttpServletRequest request) {
        origins.check(request);
        if (!events.enabled()) {
            return ResponseEntity.noContent().build();
        }
        return identities.current(request)
                .map(identity -> streaming(() -> events.visitor(identity.guest().getId(), identity.sessionId())))
                .orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
    }

    @GetMapping("/support/unread")
    @Operation(summary = "Unread staff replies", description = "Cheap badge poll; 0 without a support identity.")
    public ResponseEntity<SupportDtos.Unread> unread(HttpServletRequest request) {
        return noStore(help.unreadFor(identities.current(request)));
    }

    @PostMapping("/support/messages")
    @Operation(summary = "Send a message", description = "Creates the visitor's support identity on first use (HttpOnly cookie for "
            + "anonymous visitors). Idempotent by clientMessageId. serviceId/organizationId route the message to that "
            + "organization; reportCategory also files a report of incorrect information.")
    public ResponseEntity<SupportDtos.Message> send(@Valid @RequestBody SupportDtos.SendRequest body, HttpServletRequest request,
                                                    HttpServletResponse response) {
        origins.check(request);
        String address = clientAddresses.resolve(request);
        SupportIdentityService.Identity identity = identities.currentOrCreate(request, response, address);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(help.send(identity, body, address));
    }

    @PostMapping("/support/read")
    @Operation(summary = "Mark staff replies as read up to a message id")
    public ResponseEntity<SupportDtos.Unread> read(@Valid @RequestBody SupportDtos.ReadRequest body, HttpServletRequest request) {
        origins.check(request);
        return noStore(help.markRead(required(request), body.upTo()));
    }

    @PutMapping("/support/contact")
    @Operation(summary = "Optional contact for support follow-up", description = "An empty e-mail removes it. Notices only if configured.")
    public ResponseEntity<SupportDtos.Contact> contact(@Valid @RequestBody SupportDtos.ContactRequest body, HttpServletRequest request) {
        origins.check(request);
        return noStore(help.updateContact(required(request), body));
    }

    @PostMapping("/support/recovery-code")
    @Operation(summary = "Create a recovery code", description = "Anonymous threads only; shown once, only its hash is stored. Replaces an older code.")
    public ResponseEntity<SupportDtos.RecoveryCode> recoveryCode(HttpServletRequest request) {
        origins.check(request);
        return noStore(new SupportDtos.RecoveryCode(identities.issueRecoveryCode(required(request))));
    }

    @PostMapping("/support/recover")
    @Operation(summary = "Open an anonymous thread in this browser with its recovery code", description = "Rate limited per address.")
    public ResponseEntity<SupportDtos.Thread> recover(@Valid @RequestBody SupportDtos.RecoverRequest body, HttpServletRequest request,
                                                      HttpServletResponse response) {
        origins.check(request);
        SupportIdentityService.Identity identity = identities.recover(body.code(), request, response, clientAddresses.resolve(request));
        return noStore(help.thread(java.util.Optional.of(identity)));
    }

    @DeleteMapping("/support/session")
    @Operation(summary = "Forget this browser", description = "Removes this browser's access; staff keep the conversation.")
    public ResponseEntity<Void> forget(HttpServletRequest request, HttpServletResponse response) {
        origins.check(request);
        identities.forgetBrowser(request, response);
        return ResponseEntity.noContent().build();
    }

    private SupportIdentityService.Identity required(HttpServletRequest request) {
        return identities.current(request).orElseThrow(() -> new NoSuchElementException("No support conversation in this browser"));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    // ---------------------------------------------------------------- staff

    @GetMapping("/inbox")
    @PreAuthorize(VIEW)
    @Operation(summary = "Support inbox", description = "status: ACTIVE (default), OPEN (waiting for staff), NEW, IN_PROGRESS, ANSWERED, "
            + "CLOSED or ALL; unread=true; sort=LAST_MESSAGE|UNREAD_FIRST. Organization staff see their organizations only; "
            + "central conversations (general=true) only global staff.")
    public List<HelpConversationSummary> inbox(@RequestParam(required = false) String status,
                                               @RequestParam(required = false) Long organizationId,
                                               @RequestParam(required = false) Long serviceId,
                                               @RequestParam(defaultValue = "false") boolean general,
                                               @RequestParam(defaultValue = "false") boolean unread,
                                               @RequestParam(defaultValue = "LAST_MESSAGE") HelpConversationService.InboxSort sort,
                                               @RequestParam(required = false) String assignee) {
        return help.inbox(new HelpConversationService.InboxQuery(HelpConversationService.statuses(status), organizationId, serviceId,
                general, AssigneeParameter.me(assignee), unread, sort));
    }

    @GetMapping(value = "/inbox/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize(VIEW)
    @Operation(summary = "Live notices for the support inbox (server-sent events)", description = "Events \"inbox\" with "
            + "{type: MESSAGE|READ|STATUS, conversationId} for conversations in the caller's organizations (central ones: "
            + "global staff only); no message text or contact details. The stream ends after a few minutes and is "
            + "authorized again on reconnect.")
    public ResponseEntity<SseEmitter> inboxStream() {
        OrganizationScope scope = access.scope(null);
        if (!events.enabled() || scope.empty()) {
            return ResponseEntity.noContent().build();
        }
        Long userId = access.principal().userId();
        return streaming(() -> events.staff(userId, scope));
    }

    private static ResponseEntity<SseEmitter> streaming(java.util.function.Supplier<SseEmitter> open) {
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Accel-Buffering", "no").body(open.get());
        } catch (RateLimitExceededException exception) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.retryAfter().toSeconds())).build();
        }
    }

    @GetMapping("/inbox/unread")
    @PreAuthorize(VIEW)
    public HelpConversationSummary.Unread inboxUnread() {
        return help.staffUnread();
    }

    @GetMapping("/inbox/{id}")
    @PreAuthorize(VIEW)
    public HelpConversationSummary.Detail detail(@PathVariable Long id) {
        return help.detail(id);
    }

    @PostMapping("/inbox/{id}/read")
    @PreAuthorize(VIEW)
    @Operation(summary = "Mark visitor messages as read up to a message id")
    public HelpConversationSummary.Detail read(@PathVariable Long id, @Valid @RequestBody SupportDtos.ReadRequest request) {
        return help.markReadByStaff(id, request.upTo());
    }

    @PostMapping("/inbox/{id}/replies")
    @PreAuthorize(REPLY)
    @Operation(summary = "Reply to the visitor", description = "Idempotent by clientMessageId. Sets ANSWERED; e-mails a notice only if the visitor opted in and mail is configured.")
    public HelpConversationSummary.Detail reply(@PathVariable Long id, @Valid @RequestBody HelpMessageRequest request) {
        return help.reply(id, request.message(), request.clientMessageId());
    }

    @PutMapping("/inbox/{id}/status")
    @PreAuthorize(REPLY)
    @Operation(summary = "Mark open, in progress or closed; reopen")
    public HelpConversationSummary.Detail status(@PathVariable Long id, @Valid @RequestBody HelpStatusRequest request) {
        return help.changeStatus(id, request.status());
    }

    @GetMapping("/inbox/{id}/candidates")
    @PreAuthorize(REPLY)
    public List<StaffMember> candidates(@PathVariable Long id) {
        return help.candidates(id);
    }

    @PutMapping("/inbox/{id}/assignee")
    @PreAuthorize(REPLY)
    public HelpConversationSummary.Detail assign(@PathVariable Long id, @Valid @RequestBody HelpAssigneeRequest request) {
        return help.assign(id, request.userId());
    }
}
