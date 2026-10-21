package dev.onepieceapi.contentservice.persistence.repository;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.CharacterStatus;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.CharacterVersionEntity;
import dev.onepieceapi.contentservice.persistence.specification.CharacterSpecifications;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The versions of Characters, content and workflow together. Every query common to all
 * entities comes from {@link VersionBodyRepository}; here, only those that read the
 * entity's own tables by name, the image's, the first appearance's and the species'.
 */
public interface CharacterVersionRepository extends VersionBodyRepository<CharacterVersionEntity> {

	@Override
	default String naturalKeyAttribute() {
		return CharacterVersionEntity.Fields.romaji;
	}

	@Override
	default Specification<CharacterVersionEntity> attributeConditions(ContentFilter filter,
			Collection<VersionStatus> statuses) {
		Specification<CharacterVersionEntity> conditions = Specification.allOf();
		if (filter.attribute(Character.STATUS_ATTRIBUTE) instanceof CharacterStatus status) {
			conditions = conditions.and(CharacterSpecifications.withStatus(status));
		}
		if (filter.attribute(Character.SPECIES_ATTRIBUTE) instanceof UUID species) {
			conditions = conditions.and(CharacterSpecifications.withSpecies(species));
		}
		return conditions;
	}

	@Override
	@Query(value = """
			select exists (select 1 from character_version d join content_version v on v.id = d.version_id
			               where d.romaji_slug = :slug and v.content_id <> :contentId)
			    or exists (select 1 from content_slug s
			               where s.entity_type = 'CHARACTER' and s.slug = :slug and s.content_id <> :contentId)""",
			nativeQuery = true)
	boolean slugIsTakenByAnother(String slug, UUID contentId);

	@Override
	@Modifying
	@Query(value = """
			insert into content_slug (entity_type, slug, content_id, assigned_at)
			select c.entity_type, d.romaji_slug, c.id, :assignedAt
			from character_version d
			    join content_version v on v.id = d.version_id
			    join content c on c.id = v.content_id
			where d.version_id = :versionId and d.romaji_slug is not null
			on conflict (entity_type, slug) do update set assigned_at = excluded.assigned_at
			where content_slug.content_id = excluded.content_id""", nativeQuery = true)
	int assignSlug(UUID versionId, Instant assignedAt);

	/** Whether a version in one of these statuses has this image. */
	@Query("""
			select count(d) > 0 from CharacterVersionEntity d join d.version v
			where d.imageId = :imageId and v.status in :statuses""")
	boolean usesImage(String imageId, Collection<VersionStatus> statuses);

	/**
	 * How many characters are online first appearing in this chapter or episode
	 * (ADR-0008).
	 */
	@Query("""
			select count(d) from CharacterVersionEntity d join d.version v
			where (d.firstAppearanceChapterId = :storyContentId or d.firstAppearanceEpisodeId = :storyContentId)
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED""")
	long countOnlineFirstAppearingIn(UUID storyContentId);

	/**
	 * The characters online first appearing in this chapter or episode, by romaji: as
	 * many as the page asks for.
	 */
	@Query("""
			select d from CharacterVersionEntity d join fetch d.version v
			where (d.firstAppearanceChapterId = :storyContentId or d.firstAppearanceEpisodeId = :storyContentId)
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
			order by d.romaji""")
	List<CharacterVersionEntity> findOnlineFirstAppearingIn(UUID storyContentId, Pageable pageable);

	/** How many characters are online naming this species (D8). */
	@Query("""
			select count(d) from CharacterVersionEntity d join d.version v
			where :speciesContentId member of d.speciesIds
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED""")
	long countOnlineWithSpecies(UUID speciesContentId);

	/**
	 * The characters online naming this species, by romaji: as many as the page asks for.
	 */
	@Query("""
			select d from CharacterVersionEntity d join fetch d.version v
			where :speciesContentId member of d.speciesIds
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
			order by d.romaji""")
	List<CharacterVersionEntity> findOnlineWithSpecies(UUID speciesContentId, Pageable pageable);

	/** The epithets count too: a language they are written in is in use. */
	@Override
	@Query(value = """
			select exists (select 1 from character_version_translation where language_code = :languageCode)
			    or exists (select 1 from character_version_epithet_translation
			               where language_code = :languageCode)""", nativeQuery = true)
	boolean existsByLanguage(String languageCode);

}
