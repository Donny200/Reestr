package adliya.uz.functioncatalogservice.repository;

import adliya.uz.functioncatalogservice.entity.HelpMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HelpMessageRepository extends JpaRepository<HelpMessage, Long> {
    List<HelpMessage> findAllByConversationIdOrderByIdAsc(Long conversationId);

    /** The visitor's whole thread across its routed conversations, oldest first (by insertion id). */
    List<HelpMessage> findAllByConversationIdInOrderByIdAsc(Collection<Long> conversationIds);

    List<HelpMessage> findAllByConversationIdInAndIdGreaterThanOrderByIdAsc(Collection<Long> conversationIds, Long afterId);

    long countByConversationId(Long conversationId);

    long countByConversationIdInAndAuthorAndReadAtIsNull(Collection<Long> conversationIds, HelpMessage.Author author);

    Optional<HelpMessage> findFirstByConversationIdInAndAuthorAndClientMessageId(Collection<Long> conversationIds,
                                                                               HelpMessage.Author author, String clientMessageId);

    @Query("select m.conversationId, count(m) from HelpMessage m where m.conversationId in :ids group by m.conversationId")
    List<Object[]> countByConversationIds(Collection<Long> ids);

    /** Messages of {@code author} the other side has not read yet, per conversation. */
    @Query("select m.conversationId, count(m) from HelpMessage m where m.conversationId in :ids and m.author = :author "
            + "and m.readAt is null group by m.conversationId")
    List<Object[]> countUnread(Collection<Long> ids, HelpMessage.Author author);

    @Modifying
    @Query("update HelpMessage m set m.readAt = :now where m.conversationId in :ids and m.author = :author "
            + "and m.id <= :upTo and m.readAt is null")
    int markRead(Collection<Long> ids, HelpMessage.Author author, Long upTo, Instant now);

    /** Latest message per conversation (inbox preview). */
    @Query("select m from HelpMessage m where m.id in (select max(x.id) from HelpMessage x where x.conversationId in :ids "
            + "group by x.conversationId)")
    List<HelpMessage> findLatest(Collection<Long> ids);
}
