package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.VersionBodyEntity;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What a Devil Fruit needs of its type (implementation plan of the Devil Fruit, D2): a
 * fruit online always has its type online, so a fruit goes online - published, or
 * restored - only while the type is. The other actions ask nothing of the type: a fruit
 * can be reviewed or set aside whatever became of it. Not even {@code content:admin}
 * lifts it: it relaxes who may act, not the data.
 * <p>
 * Going online holds the type's content shared first, and retiring a type holds it
 * exclusively ({@link DevilFruitTypeRules}): the two never run at the same time, so
 * neither can read a type that the other is about to change.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
class DevilFruitRules implements ContentRules<DevilFruit> {

	/** The actions that put a version online. */
	private static final Set<VersionAction> GOING_ONLINE = EnumSet.of(VersionAction.PUBLISH, VersionAction.RESTORE);

	private final ContentRepository contentRepository;

	private final DevilFruitTypeVersionRepository typeRepository;

	@Override
	public void lockBefore(VersionAction action, UUID contentId, Version<DevilFruit> version) {
		if (GOING_ONLINE.contains(action) && version.body().typeContentId() != null) {
			this.contentRepository.lockShared(version.body().typeContentId());
		}
	}

	@Override
	public Optional<ActionBlock> blockOf(VersionAction action, UUID contentId, Version<DevilFruit> version) {
		UUID typeContentId = version.body().typeContentId();
		if (!GOING_ONLINE.contains(action) || typeContentId == null
				|| this.typeRepository.existsWithStatus(typeContentId, Set.of(VersionStatus.PUBLISHED))) {
			return Optional.empty();
		}
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("typeId", typeContentId);
		romajiOfType(typeContentId).ifPresent(romaji -> detail.put("typeRomaji", romaji));
		return Optional.of(new ActionBlock(BlockReason.TYPE_NOT_ONLINE, detail));
	}

	/** The type as it was last approved: what to call it when it is not online. */
	private Optional<String> romajiOfType(UUID typeContentId) {
		return this.typeRepository.findMostRecent(List.of(typeContentId), VersionStatus.approved())
			.stream()
			.map(VersionBodyEntity::getRomaji)
			.findFirst();
	}

}
