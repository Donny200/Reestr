package adliya.uz.referenceservice.repository;

import adliya.uz.referenceservice.entity.TranslationKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TranslationKeyRepository extends JpaRepository<TranslationKey, Long> {
    Optional<TranslationKey> findByTranslationKey(String translationKey);

    List<TranslationKey> findAllByActiveTrueOrderByTranslationKeyAsc();

    List<TranslationKey> findAllByTranslationKeyInAndActiveTrue(Collection<String> translationKeys);
}
