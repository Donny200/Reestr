package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.entity.AuditAction;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class FunctionVerificationService {

    private final OrgFunctionService functions;
    private final OrgFunctionRepository repository;
    private final AuditWriter audit;

    /**
     * Records that organization staff reviewed the card's content. This is an internal check by the responsible
     * user, not an official government confirmation; an external source link is optional supporting information.
     */
    @Transactional
    public OrgFunction verify(Long id) {
        OrgFunction function = functions.getForAdmin(id);
        AuditWriter.Actor actor = audit.actor();
        function.setLastVerifiedAt(Instant.now());
        function.setVerifiedByUserId(actor.id());
        function.setVerificationOutdated(false);
        repository.saveAndFlush(function);
        String source = function.getOfficialSourceUrl();
        audit.function(function, AuditAction.VERIFY, "Verified by organization staff"
                + (source == null || source.isBlank() ? "; no external source link" : "; external source: " + source));
        return function;
    }
}
