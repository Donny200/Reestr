package adliya.uz.functioncatalogservice.repository;

import adliya.uz.functioncatalogservice.entity.HelpConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface HelpConversationRepository extends JpaRepository<HelpConversation, Long>, JpaSpecificationExecutor<HelpConversation> {
    List<HelpConversation> findAllByGuestId(Long guestId);

    @Query("select c from HelpConversation c where c.guestId = :guestId and "
            + "((:organizationId is null and c.organizationId is null) or c.organizationId = :organizationId)")
    Optional<HelpConversation> findRoute(Long guestId, Long organizationId);
}
