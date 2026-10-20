package adliya.uz.task1.repository;

import adliya.uz.task1.entity.Language;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LanguageRepository extends JpaRepository<Language, Long> {
    boolean existsByCodeIgnoreCase(String code);

    Optional<Language> findByCode(String code);

    List<Language> findAllByActiveTrue();

    Optional<Language> findByIsDefaultTrueAndActiveTrue();
}
