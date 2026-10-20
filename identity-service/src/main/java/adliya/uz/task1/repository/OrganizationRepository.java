package adliya.uz.task1.repository;

import adliya.uz.task1.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    boolean existsByName(String name);

    List<Organization> findAllByEnabledTrue();
}
