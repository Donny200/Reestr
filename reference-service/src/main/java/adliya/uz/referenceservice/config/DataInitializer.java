package adliya.uz.referenceservice.config;

import adliya.uz.referenceservice.entity.Region;
import adliya.uz.referenceservice.repository.RegionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private record RegionSeed(String name, String code) {
    }

    private final RegionRepository regionRepository;

    public DataInitializer(RegionRepository regionRepository) {
        this.regionRepository = regionRepository;
    }

    @Override
    public void run(String... args) {
        seedRegions();
    }

    private void seedRegions() {
        List<RegionSeed> seeds = List.of(
                new RegionSeed("Tashkent", "1700"),
                new RegionSeed("Samarkand", "1706"),
                new RegionSeed("Khorezm", "1710"),
                new RegionSeed("Bukhara", "1703"),
                new RegionSeed("Fergana", "1707")
        );

        for (RegionSeed seed : seeds) {
            if (!regionRepository.existsByCode(seed.code())) {
                regionRepository.save(Region.builder()
                        .name(seed.name())
                        .code(seed.code())
                        .build());
            }
        }
    }
}
