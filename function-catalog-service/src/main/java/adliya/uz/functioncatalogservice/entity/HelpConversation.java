package adliya.uz.functioncatalogservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "help_conversations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class HelpConversation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** Legacy (V11) per-conversation access token hash; support-chat conversations belong to a guest instead. */
    @Column(unique = true, length = 64)
    private String accessTokenHash;
    /** Support identity (anonymous browser or signed-in account) that owns the thread; null once it expired. */
    private Long guestId;
    /** Service most recently discussed in this conversation; null for central support without a service. */
    private Long functionId;
    /** Responsible organization (from the service); null routes the question to global staff. */
    private Long organizationId;
    @Column(length = 150)
    private String subjectLabel;
    @Column(length = 35)
    private String language;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    @Builder.Default
    private HelpConversationStatus status = HelpConversationStatus.NEW;
    private Long assignedUserId;
    @Column(length = 320)
    private String assignedUserName;
    /** Legacy (V11) notification address; the support chat keeps the optional contact on the guest instead. */
    @Column(length = 254)
    private String notifyEmail;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Column(nullable = false)
    private Instant lastVisitorMessageAt;
    private Instant lastStaffReplyAt;
    /** Latest visitor or staff message; orders the inbox. */
    private Instant lastMessageAt;
    private Instant closedAt;
    @Version
    private Long version;
}
