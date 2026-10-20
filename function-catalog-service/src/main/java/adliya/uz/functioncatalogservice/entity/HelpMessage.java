package adliya.uz.functioncatalogservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "help_messages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class HelpMessage {

    public enum Author { VISITOR, STAFF }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long conversationId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Author author;
    private Long staffUserId;
    /** Staff e-mail at the time of the reply; shown to colleagues only, never to the visitor. */
    @Column(length = 320)
    private String staffLabel;
    @Column(nullable = false, length = 2000)
    private String body;
    @Column(nullable = false)
    private Instant createdAt;
    /** Sender-generated key; a retry with the same key returns the stored message instead of a duplicate. */
    @Column(length = 64)
    private String clientMessageId;
    /** When the other side (visitor for staff replies, staff for visitor messages) viewed the message. */
    private Instant readAt;
    /** Service the message is about, if the visitor chose one. */
    private Long functionId;
    /** Name of that service (or organization) when the message was written. */
    @Column(length = 150)
    private String contextLabel;
    /** Set for a report of incorrect information: the report category, e.g. INCORRECT_INFORMATION. */
    @Column(length = 40)
    private String category;
    /** The information report created for the editorial workflow from this message. */
    private Long reportId;
}
