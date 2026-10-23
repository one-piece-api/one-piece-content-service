package dev.onepieceapi.contentservice.persistence.repository;

import dev.onepieceapi.contentservice.domain.affiliation.AffiliationKind;
import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.CharacterStatus;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.CharacterVersionEntity;
import dev.onepieceapi.contentservice.persistence.projection.AffiliationMemberCount;
import dev.onepieceapi.contentservice.persistence.projection.FruitUserCount;
import dev.onepieceapi.contentservice.persistence.projection.RelativeLink;
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
 * entity's own tables by name, the image's, the first appearance's, the species' and the
 * memberships'.
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
		if (filter.attribute(Character.AFFILIATION_ATTRIBUTE) instanceof UUID affiliation) {
			conditions = conditions.and(CharacterSpecifications.withAffiliation(affiliation));
		}
		if (filter.attribute(Character.AFFILIATION_KIND_ATTRIBUTE) instanceof AffiliationKind kind) {
			conditions = conditions.and(CharacterSpecifications.withAffiliationKind(kind));
		}
		if (filter.attribute(Character.DEVIL_FRUIT_ATTRIBUTE) instanceof UUID devilFruit) {
			conditions = conditions.and(CharacterSpecifications.withDevilFruit(devilFruit));
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

	/**
	 * How many characters are online naming this affiliation, former members too (D10).
	 */
	@Query("""
			select count(d) from CharacterVersionEntity d join d.version v join d.affiliations m
			where m.affiliationContentId = :affiliationContentId
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED""")
	long countOnlineWithAffiliation(UUID affiliationContentId);

	/**
	 * The characters online naming this affiliation, by romaji: as many as the page asks
	 * for.
	 */
	@Query("""
			select d from CharacterVersionEntity d join fetch d.version v join d.affiliations m
			where m.affiliationContentId = :affiliationContentId
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
			order by d.romaji""")
	List<CharacterVersionEntity> findOnlineWithAffiliation(UUID affiliationContentId, Pageable pageable);

	/** How many characters are online holding a rank in this affiliation (D10). */
	@Query("""
			select count(d) from CharacterVersionEntity d join d.version v join d.affiliations m
			where m.affiliationContentId = :affiliationContentId and m.marineRank is not null
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED""")
	long countOnlineWithRankIn(UUID affiliationContentId);

	/**
	 * The characters online holding a rank in this affiliation, by romaji: as many as the
	 * page asks for.
	 */
	@Query("""
			select d from CharacterVersionEntity d join fetch d.version v join d.affiliations m
			where m.affiliationContentId = :affiliationContentId and m.marineRank is not null
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
			order by d.romaji""")
	List<CharacterVersionEntity> findOnlineWithRankIn(UUID affiliationContentId, Pageable pageable);

	/**
	 * How many characters online belong to each of these affiliations: those whose
	 * membership is not marked former. An affiliation with none is not in the result.
	 */
	@Query("""
			select new dev.onepieceapi.contentservice.persistence.projection.AffiliationMemberCount(
				m.affiliationContentId, count(d))
			from CharacterVersionEntity d join d.version v join d.affiliations m
			where m.affiliationContentId in :affiliationContentIds and m.former = false
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
			group by m.affiliationContentId""")
	List<AffiliationMemberCount> countMembersOf(Collection<UUID> affiliationContentIds);

	/** How many characters are online naming this Devil Fruit, former users too (D12). */
	@Query("""
			select count(d) from CharacterVersionEntity d join d.version v join d.devilFruits u
			where u.devilFruitContentId = :devilFruitContentId
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED""")
	long countOnlineWithDevilFruit(UUID devilFruitContentId);

	/**
	 * The characters online naming this Devil Fruit, by romaji: as many as the page asks
	 * for.
	 */
	@Query("""
			select d from CharacterVersionEntity d join fetch d.version v join d.devilFruits u
			where u.devilFruitContentId = :devilFruitContentId
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
			order by d.romaji""")
	List<CharacterVersionEntity> findOnlineWithDevilFruit(UUID devilFruitContentId, Pageable pageable);

	/** How many characters are online with this Devil Fruit as a current one (D12). */
	@Query("""
			select count(d) from CharacterVersionEntity d join d.version v join d.devilFruits u
			where u.devilFruitContentId = :devilFruitContentId and u.former = false
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED""")
	long countOnlineCurrentUsersOf(UUID devilFruitContentId);

	/**
	 * The characters online with this Devil Fruit as a current one, by romaji: as many as
	 * the page asks for.
	 */
	@Query("""
			select d from CharacterVersionEntity d join fetch d.version v join d.devilFruits u
			where u.devilFruitContentId = :devilFruitContentId and u.former = false
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
			order by d.romaji""")
	List<CharacterVersionEntity> findOnlineCurrentUsersOf(UUID devilFruitContentId, Pageable pageable);

	/**
	 * The same, the character of this content left out: the one going online, whose
	 * previous version may be the one online.
	 */
	@Query("""
			select d from CharacterVersionEntity d join fetch d.version v join d.devilFruits u
			where u.devilFruitContentId = :devilFruitContentId and u.former = false
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
				and v.contentId <> :excludedContentId
			order by d.romaji""")
	List<CharacterVersionEntity> findOnlineOtherCurrentUsersOf(UUID devilFruitContentId, UUID excludedContentId,
			Pageable pageable);

	/**
	 * How many characters online currently use each of these Devil Fruits: those whose
	 * entry is not marked former. A fruit with none is not in the result.
	 */
	@Query("""
			select new dev.onepieceapi.contentservice.persistence.projection.FruitUserCount(
				u.devilFruitContentId, count(d))
			from CharacterVersionEntity d join d.version v join d.devilFruits u
			where u.devilFruitContentId in :devilFruitContentIds and u.former = false
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
			group by u.devilFruitContentId""")
	List<FruitUserCount> countCurrentUsersOf(Collection<UUID> devilFruitContentIds);

	/** How many characters are online naming this character as a relative (D13). */
	@Query("""
			select count(d) from CharacterVersionEntity d join d.version v join d.relatives r
			where r.relativeContentId = :relativeContentId
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED""")
	long countOnlineWithRelative(UUID relativeContentId);

	/**
	 * The characters online naming this character as a relative, by romaji: as many as
	 * the page asks for.
	 */
	@Query("""
			select d from CharacterVersionEntity d join fetch d.version v join d.relatives r
			where r.relativeContentId = :relativeContentId
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
			order by d.romaji""")
	List<CharacterVersionEntity> findOnlineWithRelative(UUID relativeContentId, Pageable pageable);

	/**
	 * Every link the characters online hold to this one, with the kind as they stored it
	 * (D13).
	 */
	@Query("""
			select new dev.onepieceapi.contentservice.persistence.projection.RelativeLink(v.contentId, r.kind)
			from CharacterVersionEntity d join d.version v join d.relatives r
			where r.relativeContentId = :relativeContentId
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED""")
	List<RelativeLink> findLinksOnlineTo(UUID relativeContentId);

	/**
	 * Whether a version of this character, in one of these statuses, names that one as a
	 * relative (D13): a pair is linked by one of the two only.
	 */
	@Query("""
			select count(d) > 0 from CharacterVersionEntity d join d.version v join d.relatives r
			where v.contentId = :holderContentId and r.relativeContentId = :namedContentId
				and v.status in :statuses""")
	boolean namesAsRelative(UUID holderContentId, UUID namedContentId, Collection<VersionStatus> statuses);

	/** The epithets count too: a language they are written in is in use. */
	@Override
	@Query(value = """
			select exists (select 1 from character_version_translation where language_code = :languageCode)
			    or exists (select 1 from character_version_epithet_translation
			               where language_code = :languageCode)
			    or exists (select 1 from character_version_affiliation_translation
			               where language_code = :languageCode)""", nativeQuery = true)
	boolean existsByLanguage(String languageCode);

}
