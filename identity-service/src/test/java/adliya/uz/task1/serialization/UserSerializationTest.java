package adliya.uz.task1.serialization;

import adliya.uz.task1.dto.UserResponse;
import adliya.uz.task1.entity.Role;
import adliya.uz.task1.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserSerializationTest {

    private static final String PASSWORD_SENTINEL = "plaintext-must-never-leak";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void entityPasswordIsIgnoredAsDefenseInDepth() throws Exception {
        Role role = Role.builder()
                .id(1L)
                .name("ROLE_SUPER_ADMIN")
                .build();
        User user = User.builder()
                .id(7L)
                .firstName("Alice")
                .lastName("Admin")
                .email("alice@example.com")
                .password(PASSWORD_SENTINEL)
                .role(role)
                .enabled(true)
                .build();

        String json = objectMapper.writeValueAsString(user);
        JsonNode tree = objectMapper.readTree(json);

        assertThat(tree.has("password")).isFalse();
        assertThat(json).doesNotContain(PASSWORD_SENTINEL);
    }

    @Test
    void responseDtoHasNoPasswordProperty() throws Exception {
        UserResponse response = UserResponse.builder()
                .id(7L)
                .firstName("Alice")
                .lastName("Admin")
                .email("alice@example.com")
                .phone(null)
                .role("ROLE_SUPER_ADMIN")
                .enabled(true)
                .mustChangePassword(true)
                .createdAt(LocalDateTime.of(2026, 8, 25, 10, 0))
                .organizationIds(Set.of())
                .build();

        JsonNode tree = objectMapper.valueToTree(response);

        assertThat(tree.has("password")).isFalse();
        assertThat(tree.path("mustChangePassword").asBoolean()).isTrue();
    }
}
