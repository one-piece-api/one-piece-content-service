package dev.onepieceapi.contentservice.service.devilfruit;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitTypeVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentRules;
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

/**
 * What a Devil Fruit needs of its type (implementation plan of the Devil Fruit, D2): the
 * rules of every relation ({@link RelationRules}) for its type - a fruit goes online only
 * while the type is - and, on top, when it names a subcategory, only while the type's
 * online version has it (implementation plan of the subcategories, S6). The other actions
 * ask nothing of the type: a fruit can be reviewed or set aside whatever became of it.
 * <p>
 * Going online holds the type's content shared first ({@link RelationRules}), and putting
 * one of the type's versions online holds it exclusively ({@code DevilFruitTypeRules}):
 * the two never run at the same time, so neither can read a type that the other is about
 * to change.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class DevilFruitRules implements ContentRules<DevilFruit> {

	private final ContentRepository contentRepository;

	private final DevilFruitTypeVersionRepository typeRepository;

	@Override
	public void lockBefore(VersionAction action, UUID contentId, Version<DevilFruit> version) {
		RelationRules.lockTargetBefore(action, DevilFruitRelations.TYPE, version.body(), this.contentRepository);
	}

	@Override
	public Optional<ActionBlock> blockOf(VersionAction action, UUID contentId, Version<DevilFruit> version) {
		return RelationRules.targetNotOnline(action, DevilFruitRelations.TYPE, version.body(), this.typeRepository)
			.or(() -> subcategoryNotOnline(action, version.body()));
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
