package adliya.uz.functioncatalogservice.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.context.request.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import adliya.uz.functioncatalogservice.dto.StaffMember;
import org.springframework.security.access.AccessDeniedException;

@Component
public class IdentityOrganizationClient {
    private final RestClient client;
    public IdentityOrganizationClient(@Qualifier("identityRestClientBuilder") RestClient.Builder builder) {
        client = builder.clone().baseUrl("http://identity-service").build();
    }
    public void requireExisting(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Assign an organization before submitting for review");
        }
        try {
            Organization organization = client.get().uri("/api/organizations/{id}", id)
                    .headers(callerSession())
                    .retrieve().body(Organization.class);
            if (organization == null || !id.equals(organization.id()) || !Boolean.TRUE.equals(organization.enabled())) {
                throw new IllegalArgumentException("Organization does not exist or is deactivated: " + id);
            }
        } catch (HttpClientErrorException.NotFound exception) {
            throw new IllegalArgumentException("Organization not found: " + id);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not verify organization with identity-service; retry with a current session");
        }
    }
    public PublicOrganization requirePublic(Long id) {
        try {
            PublicOrganization organization = client.get().uri("/api/public/organizations/{id}", id)
                    .retrieve().body(PublicOrganization.class);
            if (organization == null || !id.equals(organization.id())) {
                throw new NoSuchElementException("Organization not found: " + id);
            }
            return organization;
        } catch (HttpClientErrorException.NotFound exception) {
            throw new NoSuchElementException("Organization not found: " + id);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Organization directory is temporarily unavailable");
        }
    }
    /**
     * Active users authorized for the organization, as identity-service reports them to the calling user. The caller's
     * own session is forwarded, so identity-service applies its own organization scoping to the request.
     */
    public List<StaffMember> activeMembers(Long organizationId) {
        if (organizationId == null) {
            throw new IllegalArgumentException("The card has no organization");
        }
        try {
            StaffMember[] members = client.get().uri("/api/organizations/{id}/members", organizationId)
                    .headers(callerSession())
                    .retrieve().body(StaffMember[].class);
            return members == null ? List.of() : Arrays.stream(members).filter(member -> member.id() != null).toList();
        } catch (HttpClientErrorException.NotFound exception) {
            throw new NoSuchElementException("Organization not found: " + organizationId);
        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.Unauthorized exception) {
            throw new AccessDeniedException("You can only manage staff of your own organization(s)");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not load organization staff from identity-service; retry with a current session");
        }
    }
    /** Public profile of one organization, or empty when it is unknown or identity-service cannot be reached. */
    public java.util.Optional<PublicOrganization> findPublic(Long id) {
        if (id == null) {
            return java.util.Optional.empty();
        }
        try {
            PublicOrganization organization = client.get().uri("/api/public/organizations/{id}", id)
                    .retrieve().body(PublicOrganization.class);
            return organization != null && id.equals(organization.id()) ? java.util.Optional.of(organization) : java.util.Optional.empty();
        } catch (RestClientException | IllegalStateException exception) {
            return java.util.Optional.empty();
        }
    }
    /**
     * Public organization directory keyed by id (names, translations and contact details only). Returns an empty map
     * when identity-service is unreachable so that exports and guides fall back to "#id" instead of failing.
     */
    public java.util.Map<Long, PublicOrganization> publicDirectory() {
        try {
            PublicOrganization[] found = client.get().uri("/api/public/organizations").retrieve().body(PublicOrganization[].class);
            java.util.Map<Long, PublicOrganization> directory = new java.util.LinkedHashMap<>();
            if (found != null) {
                Arrays.stream(found).filter(organization -> organization.id() != null)
                        .forEach(organization -> directory.put(organization.id(), organization));
            }
            return directory;
        } catch (RestClientException | IllegalStateException exception) {
            return java.util.Map.of();
        }
    }
    /** Forwards the caller's own session cookie, so identity-service authorizes the request as that user. */
    private static Consumer<HttpHeaders> callerSession() {
        var attributes = RequestContextHolder.getRequestAttributes();
        String cookie = attributes instanceof ServletRequestAttributes servlet
                ? servlet.getRequest().getHeader(HttpHeaders.COOKIE) : null;
        return headers -> { if (cookie != null) headers.set(HttpHeaders.COOKIE, cookie); };
    }
    public record Organization(Long id, String name, Boolean enabled) {}
    public record PublicOrganization(Long id, String name,
                                     java.util.Map<String, adliya.uz.functioncatalogservice.entity.TranslatedText> nameTranslations,
                                     Contact contact) {
        public PublicOrganization(Long id, String name) {
            this(id, name, null, null);
        }
    }
    /** Public contact details of an organization as published by identity-service. */
    public record Contact(String address, String phone, String workingHours, String mapUrl) {}
}
