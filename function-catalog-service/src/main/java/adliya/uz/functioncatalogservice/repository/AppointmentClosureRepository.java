package adliya.uz.functioncatalogservice.repository;

import adliya.uz.functioncatalogservice.entity.AppointmentClosure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AppointmentClosureRepository extends JpaRepository<AppointmentClosure, Long> {
    /** Closures that have not ended before {@code from}, earliest first. */
    List<AppointmentClosure> findAllByOrganizationIdAndEndDateGreaterThanEqualOrderByStartDateAscIdAsc(Long organizationId, LocalDate from);
}
