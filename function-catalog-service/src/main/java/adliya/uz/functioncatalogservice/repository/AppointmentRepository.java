package adliya.uz.functioncatalogservice.repository;

import adliya.uz.functioncatalogservice.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long>, JpaSpecificationExecutor<Appointment> {
    Optional<Appointment> findByReference(String reference);

    Optional<Appointment> findByClientRequestId(String clientRequestId);

    boolean existsByReference(String reference);

    /** Start instants and number of booked places of an organization's slots in [from, to). */
    @Query("select a.startsAt, count(a) from Appointment a where a.organizationId = :organizationId "
            + "and a.status = adliya.uz.functioncatalogservice.entity.Appointment.Status.BOOKED "
            + "and a.startsAt >= :from and a.startsAt < :to group by a.startsAt")
    List<Object[]> bookedCounts(Long organizationId, Instant from, Instant to);

    @Query("select a.seat from Appointment a where a.organizationId = :organizationId and a.startsAt = :startsAt "
            + "and a.status = adliya.uz.functioncatalogservice.entity.Appointment.Status.BOOKED")
    List<Integer> bookedSeats(Long organizationId, Instant startsAt);

    @Query("select count(a) from Appointment a where a.organizationId = :organizationId "
            + "and a.status = adliya.uz.functioncatalogservice.entity.Appointment.Status.BOOKED and a.startsAt > :now "
            + "and ((:phone is not null and a.visitorPhone = :phone) or (:email is not null and lower(a.visitorEmail) = :email))")
    long countUpcomingForContact(Long organizationId, String phone, String email, Instant now);

    /** Contact details are removed once the appointment ended longer than the retention period ago. */
    @Modifying
    @Query("update Appointment a set a.visitorName = null, a.visitorPhone = null, a.visitorEmail = null, a.contactRemovedAt = :now "
            + "where a.contactRemovedAt is null and a.endsAt < :cutoff")
    int removeContacts(Instant cutoff, Instant now);
}
