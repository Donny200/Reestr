package adliya.uz.functioncatalogservice.controller;

import adliya.uz.functioncatalogservice.dto.AssignFunctionRequest;
import adliya.uz.functioncatalogservice.dto.AssignmentOverview;
import adliya.uz.functioncatalogservice.dto.OrgFunctionResponse;
import adliya.uz.functioncatalogservice.dto.StaffMember;
import adliya.uz.functioncatalogservice.service.FunctionAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/functions")
@RequiredArgsConstructor
@Tag(name = "Responsible staff", description = "Which staff member keeps each service card current. Scoped to the caller's organizations.")
public class FunctionAssignmentController {

    private final FunctionAssignmentService assignments;

    @GetMapping("/assignments")
    @PreAuthorize("hasAnyAuthority('FUNCTIONS_ASSIGN', 'FUNCTIONS_VIEW', 'ORG_ANALYTICS_VIEW')")
    @Operation(summary = "Assignment overview", description = "Cards in scope with their responsible staff member; flags cards without one "
            + "and cards whose assignee is no longer an active member of the organization.")
    public AssignmentOverview overview(@RequestParam(required = false) Long organizationId,
                                       @RequestParam(required = false) AssignmentOverview.State state) {
        return assignments.overview(organizationId, state);
    }

    @GetMapping("/{id}/assignee/candidates")
    @PreAuthorize("hasAuthority('FUNCTIONS_ASSIGN')")
    @Operation(summary = "Active staff of the card's organization who can be made responsible")
    public List<StaffMember> candidates(@PathVariable Long id) {
        return assignments.candidates(id);
    }

    @PutMapping("/{id}/assignee")
    @PreAuthorize("hasAuthority('FUNCTIONS_ASSIGN')")
    @Operation(summary = "Assign or reassign the responsible staff member", description = "The user must be an active member of the card's organization.")
    public OrgFunctionResponse assign(@PathVariable Long id, @Valid @RequestBody AssignFunctionRequest request) {
        return OrgFunctionResponse.from(assignments.assign(id, request.userId()));
    }

    @DeleteMapping("/{id}/assignee")
    @PreAuthorize("hasAuthority('FUNCTIONS_ASSIGN')")
    @Operation(summary = "Remove the responsible staff member", description = "Organization-level reminders remain visible for the card.")
    public OrgFunctionResponse unassign(@PathVariable Long id) {
        return OrgFunctionResponse.from(assignments.unassign(id));
    }
}
