package dev.onepieceapi.contentservice.service.devilfruit;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitReference;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentSortField;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitVersionMapper;
import dev.onepieceapi.contentservice.persistence.projection.FruitUserCount;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What a Character needs to say of a Devil Fruit (implementation plan of the Character,
 * C10): the fruits it may be linked to, the fruits it points to as they are today, and
 * how many characters online currently use each. Each answers in batch, one query for a
 * whole page.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class DevilFruitLinks {

	/** The language a sort by name would read; the fruits are sorted by romaji. */
	private static final String NO_LANGUAGE = "en";

	private final DevilFruitVersionRepository repository;

	private final CharacterVersionRepository characters;

	private final Clock clock;

	/**
	 * The fruits as they were last approved, by content id. An id naming nothing, and a
	 * fruit with no approved version, are alike not in the map.
	 */
	public Map<UUID, DevilFruitReference> referencesOf(Collection<UUID> contentIds) {
		if (contentIds.isEmpty()) {
			return Map.of();
		}
		return this.repository.findMostRecent(contentIds, VersionStatus.approved())
			.stream()
			.map(DevilFruitLinks::referenceOf)
			.collect(Collectors.toMap(DevilFruitReference::id, Function.identity()));
	}

	/**
	 * The fruits a character may be linked to - those with a version that passed review -
	 * one page at a time, by romaji, narrowed by what their romaji or a name contains.
	 */
	public Page<DevilFruitReference> linkable(String text, int page, int size) {
		var filter = new ContentFilter(null, text, null, null);
		var pageable = PageRequest.of(page, size, Sort.by(ContentSortField.ROMAJI.field()));
		return this.repository.search(VersionStatus.approved(), filter, this.clock, pageable, NO_LANGUAGE)
			.map(DevilFruitLinks::referenceOf);
	}

	/**
	 * How many characters online currently use each of these fruits, those who lost its
	 * power not counted. A fruit with none is not in the map.
	 */
	public Map<UUID, Long> userCounts(Collection<UUID> contentIds) {
		if (contentIds.isEmpty()) {
			return Map.of();
		}
		return this.characters.countCurrentUsersOf(contentIds)
			.stream()
			.collect(Collectors.toMap(FruitUserCount::devilFruitContentId, FruitUserCount::count));
	}

	private static DevilFruitReference referenceOf(DevilFruitVersionEntity version) {
		DevilFruit fruit = DevilFruitVersionMapper.toDomain(version).body();
		return new DevilFruitReference(version.getContentId(), fruit.romaji(), fruit.names(), fruit.multipleUsers());
	}

}
