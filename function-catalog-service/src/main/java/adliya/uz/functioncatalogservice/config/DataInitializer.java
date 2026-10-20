package adliya.uz.functioncatalogservice.config;

import adliya.uz.functioncatalogservice.entity.FunctionCategory;
import adliya.uz.functioncatalogservice.entity.FunctionStatus;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.repository.FunctionCategoryRepository;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "catalog.seed-legacy.enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements CommandLineRunner {

    private static final long LEGACY_ORGANIZATION_ID = 1L;

    private final OrgFunctionRepository orgFunctionRepository;
    private final FunctionCategoryRepository categories;

    @Override
    public void run(String... args) {
        if (orgFunctionRepository.count() > 0) {
            return;
        }
        FunctionCategory civilRegistration = category("Civil Registration");
        FunctionCategory businessServices = category("Business Services");

        orgFunctionRepository.save(published("Passport renewal", "Renew an expired or damaged passport",
                civilRegistration, "Old passport, 1 photo, application fee"));
        orgFunctionRepository.save(published("Marriage registration", "Register a marriage",
                civilRegistration, "Both parties' IDs, witnesses"));
        orgFunctionRepository.save(published("Business license application", "Apply for a new business operating license",
                businessServices, "Business plan, tax ID, application fee"));
    }

    private OrgFunction published(String name, String description, FunctionCategory category, String requirements) {
        return OrgFunction.builder()
                .name(name)
                .description(description)
                .functionCategory(category)
                .status(FunctionStatus.PUBLISHED)
                .organizationId(LEGACY_ORGANIZATION_ID)
                .requirements(requirements)
                .build();
    }

    private FunctionCategory category(String name) {
        return categories.findByName(name)
                .orElseGet(() -> categories.save(FunctionCategory.builder().name(name).build()));
    }
}
