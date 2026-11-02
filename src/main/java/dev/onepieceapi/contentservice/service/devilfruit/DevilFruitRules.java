package dev.onepieceapi.contentservice.service.devilfruit;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.TypeChoice;
import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitTypeVersionMapper;
import dev.onepieceapi.contentservice.persistence.projection.FruitUseWindow;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentRules;
import dev.onepieceapi.contentservice.service.character.CharacterRelations;
import dev.onepieceapi.contentservice.service.content.FirstAppearanceRules;
import dev.onepieceapi.contentservice.service.content.LinkedItems;
import dev.onepieceapi.contentservice.service.content.ReaderAxisRules;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * What a Devil Fruit needs of its type (implementation plan of the Devil Fruit, D2): the
 * rules of every relation ({@link RelationRules}) for its type - a fruit goes online only
 * while the type is - and, on top, when it names a subcategory, only while the type's
 * online version has it (implementation plan of the subcategories, S6). The other actions
 * ask nothing of the type: a fruit can be reviewed or set aside whatever became of it.
 * Going online also needs a first appearance, in a chapter or an episode online - the
 * same relation rules for {@link DevilFruitRelations#FIRST_APPEARANCE_CHAPTER} and
 * {@link DevilFruitRelations#FIRST_APPEARANCE_EPISODE} (implementation plan of the
 * timeline, T7): first what the fruit itself lacks, then what it points to. The types and
 * subcategories of its initial knowledge and reveals (workflow 3.7.1) follow the same
 * rules as its current one, the detail of a block naming where they are.
 * <p>
 * Going online holds the type's content shared first ({@link RelationRules}), and putting
 * one of the type's versions online holds it exclusively ({@code DevilFruitTypeRules}):
 * the two never run at the same time, so neither can read a type that the other is about
 * to change.
 * <p>
 * What it owes its characters (implementation plan of the Character, D12): it cannot be
 * retired while online characters name it, nor go online with one user at a time while
 * the windows of characters online using it overlap on an axis (workflow 3.4.8). Going
 * online holds its own content exclusively, as a character going online with it does
 * ({@code CharacterRules}): the two never compare the users together.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class DevilFruitRules implements ContentRules<DevilFruit> {

	private final ContentRepository contentRepository;

	private final DevilFruitTypeVersionRepository typeRepository;

	private final ChapterVersionRepository chapterRepository;

	private final EpisodeVersionRepository episodeRepository;

	private final CharacterVersionRepository characterRepository;

	private final ReaderAxisRules readerAxisRules;

	private final RulesProperties properties;

	@Override
	public void lockBefore(VersionAction action, UUID contentId, Version<DevilFruit> version) {
		RelationRules.lockLinkedBefore(action, contentId, this.contentRepository);
		if (RelationRules.GOING_ONLINE.contains(action)) {
			this.contentRepository.lockExclusive(contentId);
		}
		RelationRules.lockTargetBefore(action, DevilFruitRelations.TYPE, version.body(), this.contentRepository);
		RelationRules.lockTargetBefore(action, DevilFruitRelations.REVEALED_TYPE, version.body().revealedTypes(),
				this.contentRepository);
		RelationRules.lockTargetBefore(action, DevilFruitRelations.FIRST_APPEARANCE_CHAPTER, version.body(),
				this.contentRepository);
		RelationRules.lockTargetBefore(action, DevilFruitRelations.FIRST_APPEARANCE_EPISODE, version.body(),
				this.contentRepository);
		this.readerAxisRules.lockBefore(action, version.body());
	}

	@Override
	public Optional<ActionBlock> blockOf(VersionAction action, UUID contentId, Version<DevilFruit> version) {
		DevilFruit fruit = version.body();
		return FirstAppearanceRules.missing(action, fruit)
			.or(() -> RelationRules.targetNotOnline(action, DevilFruitRelations.TYPE, fruit, this.typeRepository))
			.or(() -> subcategoryNotOnline(action,
					new TypeChoice("", Map.of(), fruit.typeContentId(), fruit.subcategoryId())))
			.or(() -> revealedTypeNotOnline(action, fruit))
			.or(() -> RelationRules.targetNotOnline(action, DevilFruitRelations.FIRST_APPEARANCE_CHAPTER, fruit,
					this.chapterRepository))
			.or(() -> RelationRules.targetNotOnline(action, DevilFruitRelations.FIRST_APPEARANCE_EPISODE, fruit,
					this.episodeRepository))
			.or(() -> this.readerAxisRules.blockOf(action, version.body()))
			.or(() -> severalUsers(action, contentId, fruit))
			.or(() -> RelationRules.onlineItemsLinked(action, CharacterRelations.DEVIL_FRUIT, contentId,
					linkedCharacters(), this.properties.blockingPreviewSize()));
	}

	/**
	 * Going online with one user at a time, while the windows of characters online using
	 * it overlap, on the numbers online: how many of them, and the first few by romaji.
	 */
	private Optional<ActionBlock> severalUsers(VersionAction action, UUID contentId, DevilFruit fruit) {
		if (!RelationRules.GOING_ONLINE.contains(action) || fruit.multipleUsers()) {
			return Optional.empty();
		}
		List<FruitUseWindow> overlapping = overlappingUsers(this.characterRepository.findOnlineUsesOf(contentId));
		if (overlapping.isEmpty()) {
			return Optional.empty();
		}
		List<Map<String, Object>> named = overlapping.stream()
			.limit(this.properties.blockingPreviewSize())
			.map(FruitUseWindow::named)
			.toList();
		return Optional.of(new ActionBlock(BlockReason.DEVIL_FRUIT_IN_USE,
				Map.of("count", (long) overlapping.size(), CharacterRelations.ITEMS_NAME, named)));
	}

	/** The users whose window overlaps that of another user, in their order. */
	private List<FruitUseWindow> overlappingUsers(List<FruitUseWindow> users) {
		var windows = this.readerAxisRules.onlineWindows(users.stream().map(FruitUseWindow::window).toList());
		return IntStream.range(0, users.size())
			.filter(index -> IntStream.range(0, users.size())
				.anyMatch(other -> other != index && !windows.get(index).overlapsOn(windows.get(other)).isEmpty()))
			.mapToObj(users::get)
			.toList();
	}

	/** The queries of the characters for the fruit they name. */
	private LinkedItems linkedCharacters() {
		return new LinkedItems(this.characterRepository::countOnlineWithDevilFruit,
				this.characterRepository::findOnlineWithDevilFruit);
	}

	/**
	 * The first type, or subcategory, of the initial knowledge or a reveal not online.
	 */
	private Optional<ActionBlock> revealedTypeNotOnline(VersionAction action, DevilFruit fruit) {
		return fruit.revealedTypes()
			.stream()
			.map(choice -> RelationRules
				.targetNotOnline(action, DevilFruitRelations.REVEALED_TYPE, choice, choice.part(), this.typeRepository)
				.or(() -> subcategoryNotOnline(action, choice)))
			.flatMap(Optional::stream)
			.findFirst();
	}

	/** Checked once the type is known to be online. */
	private Optional<ActionBlock> subcategoryNotOnline(VersionAction action, TypeChoice choice) {
		UUID typeContentId = choice.typeContentId();
		UUID subcategoryId = choice.subcategoryId();
		if (!RelationRules.GOING_ONLINE.contains(action) || typeContentId == null || subcategoryId == null
				|| typeAsOf(typeContentId, Set.of(VersionStatus.PUBLISHED))
					.flatMap(type -> type.subcategory(subcategoryId))
					.isPresent()) {
			return Optional.empty();
		}
		return Optional.of(subcategoryNotOnline(choice.part(), typeContentId, subcategoryId));
	}

	/**
	 * Named as the most recent approved version of the type that has it says, when one
	 * does - the version online may well have left it out - after where it is.
	 */
	private ActionBlock subcategoryNotOnline(Map<String, Object> part, UUID typeContentId, UUID subcategoryId) {
		Map<String, Object> detail = new LinkedHashMap<>(part);
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
