package adliya.uz.functioncatalogservice.controller;

import adliya.uz.functioncatalogservice.dto.FeedbackVoteRequest;
import adliya.uz.functioncatalogservice.service.ClientAddressResolver;
import adliya.uz.functioncatalogservice.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
@Tag(name = "Information feedback", description = "Anonymous, aggregate answers to \"Was this information helpful?\" on public service pages. "
        + "They measure the usefulness of the published text, not the organization, the government service or its staff.")
public class FeedbackController {

    private final FeedbackService feedback;
    private final ClientAddressResolver clientAddresses;

    @PostMapping
    @Operation(summary = "Count one helpful / not helpful vote",
            description = "Adds one to today's aggregate for a published service. No visitor identifiers or IP addresses are stored; "
                    + "repeated votes from the same browser are discouraged client-side only. Signed-in staff are not counted.")
    public ResponseEntity<Void> vote(@Valid @RequestBody FeedbackVoteRequest request, HttpServletRequest http) {
        feedback.record(request, clientAddresses.resolve(http));
        return ResponseEntity.accepted().build();
    }
}
