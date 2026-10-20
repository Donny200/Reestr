package adliya.uz.task1.repository;

import adliya.uz.task1.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    /** Lists load role, permissions and organizations in the same query instead of one select per user. */
    @Override
    @EntityGraph(attributePaths = {"role", "role.permissions", "organizations"})
    List<User> findAll();

    @Override
    @EntityGraph(attributePaths = {"role", "role.permissions", "organizations"})
    Page<User> findAll(Pageable pageable);

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByRole_Name(String roleName);
    boolean existsByRole_Id(Long roleId);
    long countByRole_NameAndEnabledTrue(String roleName);
    @EntityGraph(attributePaths = {"role", "role.permissions", "organizations"})
    List<User> findAllByRole_Name(String roleName);

}
