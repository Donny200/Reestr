package adliya.uz.task1.controller;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ControllerDtoContractTest {

    @Test
    void userAndRoleControllersDoNotExposeJpaEntitiesInPublicSignatures() {
        Stream.of(UserController.class, RoleController.class)
                .flatMap(controller -> Arrays.stream(controller.getDeclaredMethods()))
                .filter(method -> !method.isSynthetic())
                .forEach(this::assertDtoOnlySignature);
    }

    private void assertDtoOnlySignature(Method method) {
        Stream<Type> signatureTypes = Stream.concat(
                Stream.of(method.getGenericReturnType()),
                Arrays.stream(method.getGenericParameterTypes())
        );

        assertThat(signatureTypes.map(Type::getTypeName).toList())
                .as("public contract of %s", method)
                .noneMatch(type -> type.contains("adliya.uz.task1.entity."));
    }
}
