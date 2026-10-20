package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.HelpConversation;
import adliya.uz.functioncatalogservice.entity.HelpConversationStatus;
import adliya.uz.functioncatalogservice.entity.HelpMessage;

import java.time.Instant;
import java.util.List;

/** Staff inbox row. Contact details are never part of the list; see {@link Detail}. */
public record HelpConversationSummary(Long id, HelpConversationStatus status, Long serviceId, String serviceName,
                                      Long organizationId, boolean general, String language, Long assignedUserId,
                                      String assignedUserName, Instant createdAt, Instant updatedAt, Instant lastMessageAt,
                                      String lastMessagePreview, HelpMessage.Author lastMessageAuthor, long messageCount,
                                      long unreadCount, boolean contactAvailable) {

    private static final int PREVIEW_LENGTH = 140;

    public static HelpConversationSummary of(HelpConversation conversation, long messageCount, long unreadCount,
                                             HelpMessage latest, boolean contactAvailable) {
        String preview = latest == null ? null
                : latest.getBody().length() <= PREVIEW_LENGTH ? latest.getBody() : latest.getBody().substring(0, PREVIEW_LENGTH) + "…";
        return new HelpConversationSummary(conversation.getId(), conversation.getStatus(), conversation.getFunctionId(),
                conversation.getSubjectLabel(), conversation.getOrganizationId(), conversation.getOrganizationId() == null,
                conversation.getLanguage(), conversation.getAssignedUserId(), conversation.getAssignedUserName(),
                conversation.getCreatedAt(), conversation.getUpdatedAt(), conversation.getLastMessageAt(), preview,
                latest == null ? null : latest.getAuthor(), messageCount, unreadCount, contactAvailable);
    }

    /** Contact is present only for staff allowed to reply, and only if the visitor left one. */
    public record Detail(HelpConversationSummary conversation, List<Message> messages, SupportDtos.Contact contact) {}

    public record Message(Long id, HelpMessage.Author author, String body, Instant createdAt, Long staffUserId, String staffLabel,
                          Long serviceId, String contextLabel, String category, Long reportId, Instant readAt) {
        public static Message of(HelpMessage message) {
            return new Message(message.getId(), message.getAuthor(), message.getBody(), message.getCreatedAt(),
                    message.getStaffUserId(), message.getStaffLabel(), message.getFunctionId(), message.getContextLabel(),
                    message.getCategory(), message.getReportId(), message.getReadAt());
        }
    }

    public record Unread(long conversations, long messages) {}
}
