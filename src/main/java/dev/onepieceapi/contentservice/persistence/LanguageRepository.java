package dev.onepieceapi.contentservice.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LanguageRepository extends JpaRepository<LanguageEntity, String> {

	List<LanguageEntity> findAllByCodeIn(List<String> codes);

}
