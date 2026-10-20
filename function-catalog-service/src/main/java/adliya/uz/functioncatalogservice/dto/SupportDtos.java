package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.HelpMessage;
import adliya.uz.functioncatalogservice.entity.ReportCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Request and response shapes of the visitor side of the support chat. */
public final class SupportDtos {

    public static final String CLIENT_ID_PATTERN = "[A-Za-z0-9_-]{8,64}";
    public static final String EMAIL_PATTERN = "^\\s*$|^\\s*[^\\s@]{1,64}@[^\\s@]+\\.[^\\s@]{2,}\\s*$";

    private SupportDtos() {}

    /**
     * One visitor message. {@code serviceId} (or {@code organizationId} for an organization page) routes it to the
     * responsible organization and is shown as its context; without either it goes to central support. A {@code
     * reportCategory} turns it into a report of incorrect information about that service or organization.
     */
    public record SendRequest(
            @NotBlank @Size(max = 2000) String body,
            @NotBlank @Pattern(regexp = CLIENT_ID_PATTERN) String clientMessageId,
            @Positive Long serviceId,
            @Positive Long organizationId,
            ReportCategory reportCategory,
            @Size(max = 35) @Pattern(regexp = "[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*") String language,
            @Size(max = 200) String website
    ) {}

    public record ReadRequest(@Positive Long upTo) {}

    public record ContactRequest(
            @Size(max = 254) @Pattern(regexp = EMAIL_PATTERN, message = "must be an email address") String email,
            boolean notifyByEmail
    ) {}

    public record RecoverRequest(@NotBlank @Size(max = 64) String code) {}

    /** A message as the visitor sees it: staff are shown as their organization, never by name or e-mail. */
    public record Message(Long id, HelpMessage.Author author, String body, Instant createdAt, String clientMessageId,
                          Long serviceId, String contextLabel, Long organizationId, String category, Instant readAt) {
        public static Message of(HelpMessage message, Long organizationId) {
            boolean own = message.getAuthor() == HelpMessage.Author.VISITOR;
            return new Message(message.getId(), message.getAuthor(), message.getBody(), message.getCreatedAt(),
                    own ? message.getClientMessageId() : null, message.getFunctionId(), message.getContextLabel(),
                    organizationId, message.getCategory(), message.getReadAt());
        }
    }

    public record Contact(String email, boolean notifyByEmail) {}

    /**
     * The visitor's whole support thread. {@code identity} is NONE before the first message, BROWSER for an anonymous
     * visitor (cookie) and ACCOUNT for a signed-in user. Contact details are returned only to their owner.
     */
    public record Thread(Identity identity, List<Message> messages, long unread, Contact contact,
                         boolean notificationsAvailable, boolean recoveryCodeSet) {}

    public enum Identity { NONE, BROWSER, ACCOUNT }

    /** Polling answer: messages after the cursor (plus recently created ones, de-duplicated by id on the client). */
    public record Updates(List<Message> messages, long unread, Long latestReplyId) {}

    public record Unread(long unread, Long latestReplyId) {}

    /** Shown once; only its hash is stored. */
    public record RecoveryCode(String code) {}
}
