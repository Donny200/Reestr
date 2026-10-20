package adliya.uz.functioncatalogservice.repository;

import adliya.uz.functioncatalogservice.entity.AppointmentSchedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AppointmentScheduleRepository extends JpaRepository<AppointmentSchedule, Long> {
    /** Serializes bookings of one organization (SELECT … FOR UPDATE on its schedule row). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AppointmentSchedule s where s.organizationId = :organizationId")
    Optional<AppointmentSchedule> lock(Long organizationId);
}
