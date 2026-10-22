package dev.onepieceapi.contentservice.service.devilfruit;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitTypeVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentRules;
import dev.onepieceapi.contentservice.service.character.CharacterRelations;
import dev.onepieceapi.contentservice.service.content.FirstAppearanceRules;
import dev.onepieceapi.contentservice.service.content.LinkedItems;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What a Devil Fruit needs of its type (implementation plan of the Devil Fruit, D2): the
 * rules of every relation ({@link RelationRules}) for its type - a fruit goes online only
 * while the type is - and, on top, when it names a subcategory, only while the type's
 * online version has it (implementation plan of the subcategories, S6). The other actions
 * ask nothing of the type: a fruit can be reviewed or set aside whatever became of it.
 * Going online also needs a first appearance, in a chapter or an episode online - the
 * same relation rules for {@link DevilFruitRelations#FIRST_APPEARANCE_CHAPTER} and
 * {@link DevilFruitRelations#FIRST_APPEARANCE_EPISODE} (implementation plan of the
 * timeline, T7): first what the fruit itself lacks, then what it points to.
 * <p>
 * Going online holds the type's content shared first ({@link RelationRules}), and putting
 * one of the type's versions online holds it exclusively ({@code DevilFruitTypeRules}):
 * the two never run at the same time, so neither can read a type that the other is about
 * to change.
 * <p>
 * What it owes its characters (implementation plan of the Character, D12): it cannot be
 * retired while online characters name it, nor go online with one user at a time while
 * several online have it as a current one. Going online holds its own content
 * exclusively, as a character going online with it as a current one does
 * ({@code CharacterRules}): the two never check the users together.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class DevilFruitRules implements ContentRules<DevilFruit> {

	private final ContentRepository contentRepository;

	private final DevilFruitTypeVersionRepository typeRepository;

	private final ChapterVersionRepository chapterRepository;

	private final EpisodeVersionRepository episodeRepository;

	private final CharacterVersionRepository characterRepository;

	private final RulesProperties properties;

	@Override
	public void lockBefore(VersionAction action, UUID contentId, Version<DevilFruit> version) {
		RelationRules.lockLinkedBefore(action, contentId, this.contentRepository);
		if (RelationRules.GOING_ONLINE.contains(action)) {
			this.contentRepository.lockExclusive(contentId);
		}
		RelationRules.lockTargetBefore(action, DevilFruitRelations.TYPE, version.body(), this.contentRepository);
		RelationRules.lockTargetBefore(action, DevilFruitRelations.FIRST_APPEARANCE_CHAPTER, version.body(),
				this.contentRepository);
		RelationRules.lockTargetBefore(action, DevilFruitRelations.FIRST_APPEARANCE_EPISODE, version.body(),
				this.contentRepository);
	}

	@Override
	public Optional<ActionBlock> blockOf(VersionAction action, UUID contentId, Version<DevilFruit> version) {
		DevilFruit fruit = version.body();
		return FirstAppearanceRules.missing(action, fruit)
			.or(() -> RelationRules.targetNotOnline(action, DevilFruitRelations.TYPE, fruit, this.typeRepository))
			.or(() -> subcategoryNotOnline(action, fruit))
			.or(() -> RelationRules.targetNotOnline(action, DevilFruitRelations.FIRST_APPEARANCE_CHAPTER, fruit,
					this.chapterRepository))
			.or(() -> RelationRules.targetNotOnline(action, DevilFruitRelations.FIRST_APPEARANCE_EPISODE, fruit,
					this.episodeRepository))
			.or(() -> severalUsers(action, contentId, fruit))
			.or(() -> RelationRules.onlineItemsLinked(action, CharacterRelations.DEVIL_FRUIT, contentId,
					linkedCharacters(), this.properties.blockingPreviewSize()));
	}

	/**
	 * Going online with one user at a time, while several characters online have it as a
	 * current one: how many, and the first few by romaji.
	 */
	private Optional<ActionBlock> severalUsers(VersionAction action, UUID contentId, DevilFruit fruit) {
		if (!RelationRules.GOING_ONLINE.contains(action) || fruit.multipleUsers()) {
			return Optional.empty();
		}
		long count = this.characterRepository.countOnlineCurrentUsersOf(contentId);
		if (count < 2) {
			return Optional.empty();
		}
		List<Map<String, Object>> named = this.characterRepository
			.findOnlineCurrentUsersOf(contentId, PageRequest.ofSize(this.properties.blockingPreviewSize()))
			.stream()
			.map(RelationRules::named)
			.toList();
		return Optional.of(new ActionBlock(BlockReason.DEVIL_FRUIT_IN_USE,
				Map.of("count", count, CharacterRelations.ITEMS_NAME, named)));
	}

	/** The queries of the characters for the fruit they name. */
	private LinkedItems linkedCharacters() {
		return new LinkedItems(this.characterRepository::countOnlineWithDevilFruit,
				this.characterRepository::findOnlineWithDevilFruit);
	}

	/** Checked once the type is known to be online. */
	private Optional<ActionBlock> subcategoryNotOnline(VersionAction action, DevilFruit fruit) {
		UUID typeContentId = fruit.typeContentId();
		UUID subcategoryId = fruit.subcategoryId();
		if (!RelationRules.GOING_ONLINE.contains(action) || typeContentId == null || subcategoryId == null
				|| typeAsOf(typeContentId, Set.of(VersionStatus.PUBLISHED))
					.flatMap(type -> type.subcategory(subcategoryId))
					.isPresent()) {
			return Optional.empty();
		}
		return Optional.of(subcategoryNotOnline(typeContentId, subcategoryId));
	}

	/**
	 * Named as the most recent approved version of the type that has it says, when one
	 * does - the version online may well have left it out.
	 */
	private ActionBlock subcategoryNotOnline(UUID typeContentId, UUID subcategoryId) {
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("typeId", typeContentId);
		detail.put("subcategoryId", subcategoryId);
		this.typeRepository.findLastApprovedSubcategory(typeContentId, subcategoryId)
			.ifPresent(subcategory -> detail.put("subcategoryNames", subcategory.names()));
		return new ActionBlock(BlockReason.SUBCATEGORY_NOT_ONLINE, detail);
	}

	/** The type as its most recent version in these statuses says it. */
	private Optional<DevilFruitType> typeAsOf(UUID typeContentId, Collection<VersionStatus> statuses) {
		return this.typeRepository.findMostRecent(List.of(typeContentId), statuses)
			.stream()
			.findFirst()
			.map(DevilFruitTypeVersionMapper::toDomain)
			.map(Version::body);
	}

}
