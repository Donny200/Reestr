package adliya.uz.referenceservice.repository;

import adliya.uz.referenceservice.entity.Translation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TranslationRepository extends JpaRepository<Translation, Long> {

    @Query("""
            select translation
            from Translation translation
            join fetch translation.translationKey translationKey
            where translation.languageCode = :languageCode
            order by translationKey.translationKey asc
            """)
    List<Translation> findDictionary(@Param("languageCode") String languageCode);

    @Query("""
            select translation
            from Translation translation
            join fetch translation.translationKey translationKey
            where translation.languageCode = :languageCode
              and translationKey.active = true
            order by translationKey.translationKey asc
            """)
    List<Translation> findActiveDictionary(@Param("languageCode") String languageCode);

    @Query("""
            select translation
            from Translation translation
            join fetch translation.translationKey translationKey
            where translation.languageCode = :languageCode
              and translationKey.id in :translationKeyIds
            """)
    List<Translation> findAllByLanguageAndKeyIds(
            @Param("languageCode") String languageCode,
            @Param("translationKeyIds") Collection<Long> translationKeyIds
    );

    Optional<Translation> findByLanguageCodeAndTranslationKey_TranslationKey(
            String languageCode,
            String translationKey
    );

}
