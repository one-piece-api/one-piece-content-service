package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What a Devil Fruit Type owes its fruits (implementation plan of the Devil Fruit, D2): a
 * fruit online always has its type online, so a type cannot be retired while fruits are
 * online with it. A type with a new online version, or archived with no fruit online, is
 * not affected. Not even {@code content:admin} lifts it.
 * <p>
 * Retiring holds the type's content exclusively first, so it waits for any fruit going
 * online with it ({@link DevilFruitRules}) and the count it then reads includes that
 * fruit.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
class DevilFruitTypeRules implements ContentRules<DevilFruitType> {

	private final ContentRepository contentRepository;

	private final DevilFruitVersionRepository fruitRepository;

	private final RulesProperties properties;

	@Override
	public void lockBefore(VersionAction action, UUID contentId, Version<DevilFruitType> version) {
		if (action == VersionAction.RETIRE) {
			this.contentRepository.lockExclusive(contentId);
		}
	}

	@Override
	public Optional<ActionBlock> blockOf(VersionAction action, UUID contentId, Version<DevilFruitType> version) {
		if (action != VersionAction.RETIRE) {
			return Optional.empty();
		}
		long count = this.fruitRepository.countOnlineLinkedTo(contentId);
		if (count == 0) {
			return Optional.empty();
		}
		List<Map<String, Object>> fruits = this.fruitRepository
			.findOnlineLinkedTo(contentId, PageRequest.ofSize(this.properties.blockingPreviewSize()))
			.stream()
			.map(fruit -> {
				Map<String, Object> named = new LinkedHashMap<>();
				named.put("id", fruit.getContentId());
				named.put("romaji", fruit.getRomaji());
				return named;
			})
			.toList();
		return Optional.of(new ActionBlock(BlockReason.ONLINE_FRUITS_LINKED, Map.of("count", count, "fruits", fruits)));
	}

}
