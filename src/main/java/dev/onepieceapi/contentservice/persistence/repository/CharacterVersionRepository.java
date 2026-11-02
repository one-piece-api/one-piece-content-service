package dev.onepieceapi.contentservice.persistence.repository;

import dev.onepieceapi.contentservice.domain.affiliation.AffiliationKind;
import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.CharacterStatus;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.CharacterVersionEntity;
import dev.onepieceapi.contentservice.persistence.projection.AffiliationMemberCount;
import dev.onepieceapi.contentservice.persistence.projection.FruitUseWindow;
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

	/**
	 * Every romaji of every version of another character counts - current, initial or
	 * revealed (workflow 3.7.1) - as do the slugs it held online.
	 */
	@Override
	@Query(value = """
			select exists (select 1 from character_version d join content_version v on v.id = d.version_id
			               where d.romaji_slug = :slug and v.content_id <> :contentId)
			    or exists (select 1 from character_version_initial_knowledge k
			                   join content_version v on v.id = k.version_id
			               where k.romaji_slug = :slug and v.content_id <> :contentId)
			    or exists (select 1 from character_version_reveal r join content_version v on v.id = r.version_id
			               where r.romaji_slug = :slug and v.content_id <> :contentId)
			    or exists (select 1 from content_slug s
			               where s.entity_type = 'CHARACTER' and s.slug = :slug and s.content_id <> :contentId)""",
			nativeQuery = true)
	boolean slugIsTakenByAnother(String slug, UUID contentId);

	/** Every romaji it holds: current, initial and revealed (workflow 3.7.1). */
	@Override
	@Modifying
	@Query(value = """
			insert into content_slug (entity_type, slug, content_id, assigned_at)
			select c.entity_type, s.slug, c.id, :assignedAt
			from (select d.romaji_slug as slug from character_version d where d.version_id = :versionId
			      union select k.romaji_slug from character_version_initial_knowledge k where k.version_id = :versionId
			      union select r.romaji_slug from character_version_reveal r where r.version_id = :versionId) s
			    join content_version v on v.id = :versionId
			    join content c on c.id = v.content_id
			where s.slug is not null
			on conflict (entity_type, slug) do update set assigned_at = excluded.assigned_at
			where content_slug.content_id = excluded.content_id""", nativeQuery = true)
	int assignSlug(UUID versionId, Instant assignedAt);

	/** Whether a version in one of these statuses has this image. */
	@Query("""
			select count(d) > 0 from CharacterVersionEntity d join d.version v
			where d.imageId = :imageId and v.status in :statuses""")
	boolean usesImage(String imageId, Collection<VersionStatus> statuses);

	/**
	 * The versions online naming this chapter or episode at any point of the reader axis
	 * (workflow 3.7.4): the first appearance, a point of any window or of a reveal
	 * (workflow 3.7.1). Each part is indexed (V26, V38, V40).
	 */
	@Query(value = """
			select v.id from content_version v
			where v.status = 'PUBLISHED' and v.id in (
			    select version_id from character_version
			    where first_appearance_chapter_id = :storyContentId
			        or first_appearance_episode_id = :storyContentId
			    union select version_id from character_version_paragraph
			    where discovered_chapter_id = :storyContentId or discovered_episode_id = :storyContentId
			        or ended_chapter_id = :storyContentId or ended_episode_id = :storyContentId
			    union select version_id from character_version_epithet
			    where discovered_chapter_id = :storyContentId or discovered_episode_id = :storyContentId
			        or ended_chapter_id = :storyContentId or ended_episode_id = :storyContentId
			    union select version_id from character_version_alias
			    where discovered_chapter_id = :storyContentId or discovered_episode_id = :storyContentId
			        or ended_chapter_id = :storyContentId or ended_episode_id = :storyContentId
			    union select version_id from character_version_species
			    where discovered_chapter_id = :storyContentId or discovered_episode_id = :storyContentId
			        or ended_chapter_id = :storyContentId or ended_episode_id = :storyContentId
			    union select version_id from character_version_devil_fruit
			    where discovered_chapter_id = :storyContentId or discovered_episode_id = :storyContentId
			        or ended_chapter_id = :storyContentId or ended_episode_id = :storyContentId
			    union select version_id from character_version_relative
			    where discovered_chapter_id = :storyContentId or discovered_episode_id = :storyContentId
			        or ended_chapter_id = :storyContentId or ended_episode_id = :storyContentId
			    union select m.version_id from character_version_affiliation m
			        join character_version_affiliation_period p on p.membership_id = m.id
			    where p.discovered_chapter_id = :storyContentId or p.discovered_episode_id = :storyContentId
			        or p.ended_chapter_id = :storyContentId or p.ended_episode_id = :storyContentId
			    union select version_id from character_version_reveal
			    where revealed_chapter_id = :storyContentId or revealed_episode_id = :storyContentId)""",
			nativeQuery = true)
	List<UUID> findOnlineVersionIdsWithPointIn(UUID storyContentId);

	/** How many characters are online naming this chapter or episode at any point. */
	default long countOnlineWithPointIn(UUID storyContentId) {
		return findOnlineVersionIdsWithPointIn(storyContentId).size();
	}

	/**
	 * The characters online naming this chapter or episode at any point, by romaji: as
	 * many as the page asks for.
	 */
	default List<CharacterVersionEntity> findOnlineWithPointIn(UUID storyContentId, Pageable pageable) {
		List<UUID> versionIds = findOnlineVersionIdsWithPointIn(storyContentId);
		return versionIds.isEmpty() ? List.of() : findByVersionIdsByRomaji(versionIds, pageable);
	}

	/** These versions, by romaji: as many as the page asks for. */
	@Query("""
			select d from CharacterVersionEntity d join fetch d.version v
			where d.versionId in :versionIds
			order by d.romaji""")
	List<CharacterVersionEntity> findByVersionIdsByRomaji(Collection<UUID> versionIds, Pageable pageable);

	/** How many characters are online naming this species (D8). */
	@Query("""
			select count(d) from CharacterVersionEntity d join d.version v join d.species s
			where s.speciesContentId = :speciesContentId
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED""")
	long countOnlineWithSpecies(UUID speciesContentId);

	/**
	 * The characters online naming this species, by romaji: as many as the page asks for.
	 */
	@Query("""
			select d from CharacterVersionEntity d join fetch d.version v join d.species s
			where s.speciesContentId = :speciesContentId
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
			select count(distinct d) from CharacterVersionEntity d join d.version v join d.affiliations m
				join m.periods p
			where m.affiliationContentId = :affiliationContentId and p.marineRank is not null
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED""")
	long countOnlineWithRankIn(UUID affiliationContentId);

	/**
	 * The characters online holding a rank in this affiliation, by romaji: as many as the
	 * page asks for.
	 */
	@Query("""
			select distinct d from CharacterVersionEntity d join fetch d.version v join d.affiliations m
				join m.periods p
			where m.affiliationContentId = :affiliationContentId and p.marineRank is not null
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
			order by d.romaji""")
	List<CharacterVersionEntity> findOnlineWithRankIn(UUID affiliationContentId, Pageable pageable);

	/**
	 * How many characters online belong to each of these affiliations: those whose
	 * membership has a period without an end (workflow 3.4.7). An affiliation with none
	 * is not in the result.
	 */
	@Query("""
			select new dev.onepieceapi.contentservice.persistence.projection.AffiliationMemberCount(
				m.affiliationContentId, count(distinct d))
			from CharacterVersionEntity d join d.version v join d.affiliations m join m.periods p
			where m.affiliationContentId in :affiliationContentIds
				and p.window.endedChapterId is null and p.window.endedEpisodeId is null
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

	/**
	 * Every use of this Devil Fruit by a character online, with its window, by romaji:
	 * what the invariant of its users compares (workflow 3.4.8).
	 */
	@Query("""
			select new dev.onepieceapi.contentservice.persistence.projection.FruitUseWindow(v.contentId, d.romaji,
				u.window.discoveredChapterId, u.window.discoveredEpisodeId, u.window.endedChapterId,
				u.window.endedEpisodeId)
			from CharacterVersionEntity d join d.version v join d.devilFruits u
			where u.devilFruitContentId = :devilFruitContentId
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
			order by d.romaji""")
	List<FruitUseWindow> findOnlineUsesOf(UUID devilFruitContentId);

	/**
	 * How many characters online currently use each of these Devil Fruits: those whose
	 * entry has no end (workflow 3.4.8). A fruit with none is not in the result.
	 */
	@Query("""
			select new dev.onepieceapi.contentservice.persistence.projection.FruitUserCount(
				u.devilFruitContentId, count(d))
			from CharacterVersionEntity d join d.version v join d.devilFruits u
			where u.devilFruitContentId in :devilFruitContentIds
				and u.window.endedChapterId is null and u.window.endedEpisodeId is null
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
	 * (D13) and its window, the same on both sides.
	 */
	@Query("""
			select new dev.onepieceapi.contentservice.persistence.projection.RelativeLink(v.contentId, r.kind,
				r.window.discoveredChapterId, r.window.discoveredEpisodeId, r.window.endedChapterId,
				r.window.endedEpisodeId)
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

	/**
	 * The epithets, the roles, the paragraphs, the initial knowledge and the reveals
	 * count too: a language they are written in is in use.
	 */
	@Override
	@Query(value = """
			select exists (select 1 from character_version_translation where language_code = :languageCode)
			    or exists (select 1 from character_version_epithet_translation
			               where language_code = :languageCode)
			    or exists (select 1 from character_version_affiliation_period_translation
			               where language_code = :languageCode)
			    or exists (select 1 from character_version_paragraph_translation
			               where language_code = :languageCode)
			    or exists (select 1 from character_version_initial_knowledge_translation
			               where language_code = :languageCode)
			    or exists (select 1 from character_version_reveal_translation
			               where language_code = :languageCode)""", nativeQuery = true)
	boolean existsByLanguage(String languageCode);

}
