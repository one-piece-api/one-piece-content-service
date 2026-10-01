package dev.onepieceapi.contentservice.persistence.repository;

import dev.onepieceapi.contentservice.persistence.entity.LanguageEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LanguageRepository extends JpaRepository<LanguageEntity, String> {

	/** Stable, human-friendly ordering for the catalog management screen. */
	List<LanguageEntity> findAllByOrderByCode();

}
