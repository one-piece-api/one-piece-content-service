package dev.onepieceapi.contentservice.service.devilfruit;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitReference;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The Devil Fruits as other contents point to them - the participants of an event
 * (implementation plan of the timeline, T5): as they are today, in batch, one query for a
 * whole page.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class DevilFruitLinks {

	private final DevilFruitVersionRepository repository;

	/**
	 * The fruits as they were last approved, by content id; one with no approved version
	 * is not in the map. Visible to whoever reads contents: a fruit that can be linked to
	 * is never hidden from them (DF-D2).
	 */
	public Map<UUID, DevilFruitReference> referencesOf(Collection<UUID> fruitContentIds) {
		if (fruitContentIds.isEmpty()) {
			return Map.of();
		}
		return this.repository.findMostRecent(fruitContentIds, VersionStatus.approved())
			.stream()
			.map(DevilFruitLinks::referenceOf)
			.collect(Collectors.toMap(DevilFruitReference::id, Function.identity()));
	}

	private static DevilFruitReference referenceOf(DevilFruitVersionEntity version) {
		DevilFruit fruit = DevilFruitVersionMapper.toDomain(version).body();
		return new DevilFruitReference(version.getContentId(), fruit.romaji(), fruit.names());
	}

}
