package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.FunctionStatus;
import adliya.uz.functioncatalogservice.entity.TranslatedText;

import java.util.List;
import java.util.Map;

/** Who is responsible for each card in scope, and which cards have nobody or someone who lost access. */
public record AssignmentOverview(Counts counts, boolean membersChecked, List<Item> items) {

    public enum State { ASSIGNED, UNASSIGNED, NEEDS_REASSIGNMENT, UNKNOWN }

    public record Counts(long assigned, long unassigned, long needsReassignment) {}

    public record Item(Long functionId, String name, Map<String, TranslatedText> nameTranslations, FunctionStatus status,
                       Long organizationId, Long categoryId, String category, AssigneeSummary assignee, State state) {}
}
