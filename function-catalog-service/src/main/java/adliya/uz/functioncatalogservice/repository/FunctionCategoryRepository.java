package adliya.uz.functioncatalogservice.repository;

import adliya.uz.functioncatalogservice.entity.FunctionCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FunctionCategoryRepository extends JpaRepository<FunctionCategory, Long> {
    Optional<FunctionCategory> findByName(String name);
}
