package adliya.uz.referenceservice.repository;

import adliya.uz.referenceservice.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RegionRepository extends JpaRepository<Region, Long> {
    boolean existsByCode(String code);
}

