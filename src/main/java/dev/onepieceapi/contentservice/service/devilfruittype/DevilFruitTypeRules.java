package dev.onepieceapi.contentservice.service.devilfruittype;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitVersionEntity;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentRules;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * What a Devil Fruit Type owes its fruits (implementation plan of the Devil Fruit, D2): a
 * fruit online always has its type online, so a type cannot be retired while fruits are
 * online with it; and a fruit online always has its subcategory online, so a version of
 * the type cannot go online - published or restored - leaving out a subcategory online
 * fruits name (implementation plan of the subcategories, S7). A type archived with no
 * fruit online is not affected. Not even {@code content:admin} lifts it.
 * <p>
 * Each of these holds the type's content exclusively first, so it waits for any fruit
 * going online with it ({@code DevilFruitRules}) and what it then reads includes that
 * fruit.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class DevilFruitTypeRules implements ContentRules<DevilFruitType> {

	/** The actions that hold the type's content exclusively. */
	private static final Set<VersionAction> LOCKING = EnumSet.of(VersionAction.RETIRE, VersionAction.PUBLISH,
			VersionAction.RESTORE);

	private final ContentRepository contentRepository;

	private final DevilFruitVersionRepository fruitRepository;

	private final RulesProperties properties;

	@Override
	public void lockBefore(VersionAction action, UUID contentId, Version<DevilFruitType> version) {
		if (LOCKING.contains(action)) {
			this.contentRepository.lockExclusive(contentId);
		}
	}

	@Override
	public Optional<ActionBlock> blockOf(VersionAction action, UUID contentId, Version<DevilFruitType> version) {
		return switch (action) {
			case RETIRE -> onlineFruitsLinked(contentId);
			case PUBLISH, RESTORE -> subcategoriesInUse(contentId, version.body());
			default -> Optional.empty();
		};
	}

	private Optional<ActionBlock> onlineFruitsLinked(UUID contentId) {
		long count = this.fruitRepository.countOnlineLinkedTo(contentId);
		if (count == 0) {
			return Optional.empty();
		}
		List<Map<String, Object>> fruits = named(this.fruitRepository.findOnlineLinkedTo(contentId, preview()),
				fruit -> Map.of());
		return Optional.of(new ActionBlock(BlockReason.ONLINE_FRUITS_LINKED, Map.of("count", count, "fruits", fruits)));
	}

	/** The online fruits whose subcategory the version leaves out, each with it. */
	private Optional<ActionBlock> subcategoriesInUse(UUID contentId, DevilFruitType type) {
		Page<DevilFruitVersionEntity> page = this.fruitRepository.findOnlineOutsideSubcategories(contentId,
				type.subcategoryIds(), preview());
		if (page.getTotalElements() == 0) {
			return Optional.empty();
		}
		List<Map<String, Object>> fruits = named(page.getContent(),
				fruit -> Map.of("subcategoryId", fruit.getSubcategoryId()));
		return Optional.of(new ActionBlock(BlockReason.SUBCATEGORY_IN_USE,
				Map.of("count", page.getTotalElements(), "fruits", fruits)));
	}

	private PageRequest preview() {
		return PageRequest.ofSize(this.properties.blockingPreviewSize());
	}

	/** Each fruit by its id and romaji, then what else the block says of it. */
	private static List<Map<String, Object>> named(List<DevilFruitVersionEntity> fruits,
			Function<DevilFruitVersionEntity, Map<String, Object>> more) {
		return fruits.stream().map(fruit -> {
			Map<String, Object> named = new LinkedHashMap<>();
			named.put("id", fruit.getContentId());
			named.put("romaji", fruit.getRomaji());
			named.putAll(more.apply(fruit));
			return named;
		}).toList();
	}

}
