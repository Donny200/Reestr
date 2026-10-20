package adliya.uz.functioncatalogservice.dto;

/** Active user authorized for an organization, as reported by identity-service. */
public record StaffMember(Long id, String firstName, String lastName, String email, String role) {
    public String displayName() {
        String name = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).strip();
        return name.isEmpty() ? email : name;
    }
}
