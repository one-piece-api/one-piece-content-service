package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.domain.devilfruit.TypeReference;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentSortField;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.domain.workflow.VisibilityPolicy;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitTypeVersionMapper;
import dev.onepieceapi.contentservice.persistence.projection.TypeFruitCount;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What the Devil Fruit Types and the Devil Fruits say of each other (implementation plan
 * of the Devil Fruit, D1, D4, D5): the type a fruit points to, as it is today; the types
 * a fruit may be linked to; and how many fruits a type has. Each answers in batch, one
 * query for a whole page.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class DevilFruitTypeLinks {

	/** The language a sort by name would read; the types are sorted by romaji. */
	private static final String NO_LANGUAGE = "en";

	private final DevilFruitTypeTitleSource titles;

	private final DevilFruitTypeVersionRepository typeRepository;

	private final DevilFruitVersionRepository fruitRepository;

	private final Clock clock;

	/**
	 * The types as they were last approved, by content id; a type with no approved
	 * version is not in the map. Visible to whoever reads contents: a type a fruit can be
	 * linked to is never hidden from them (D2).
	 */
	public Map<UUID, TypeReference> referencesOf(Collection<UUID> typeContentIds) {
		Map<UUID, TypeReference> references = new HashMap<>();
		this.titles.titles(typeContentIds, VersionStatus.approved())
			.forEach((id, title) -> references.put(id, new TypeReference(id, title.fallback(), title.names())));
		return references;
	}

	/**
	 * The types a fruit may be linked to - those with a version that passed review - one
	 * page at a time, by romaji, narrowed by what their romaji or a name contains.
	 */
	public Page<TypeReference> linkable(String text, int page, int size) {
		var filter = new ContentFilter(null, text, null, null);
		var pageable = PageRequest.of(page, size, Sort.by(ContentSortField.ROMAJI.field()));
		return this.typeRepository.search(VersionStatus.approved(), filter, this.clock, pageable, NO_LANGUAGE)
			.map(version -> {
				var type = DevilFruitTypeVersionMapper.toDomain(version).body();
				return new TypeReference(version.getContentId(), type.romaji(), type.names());
			});
	}

	/**
	 * How many fruits each of the given types has for this caller: the fruits their list
	 * shows pointing to it, which is also what the list of fruits narrowed by that type
	 * counts. A type with none is not in the map.
	 */
	public Map<UUID, Long> fruitCounts(Set<Permission> permissions, Collection<UUID> typeContentIds) {
		Set<VersionStatus> visible = VisibilityPolicy.visibleStatuses(permissions);
		if (visible.isEmpty() || typeContentIds.isEmpty()) {
			return Map.of();
		}
		return this.fruitRepository.countByTypes(typeContentIds, visible)
			.stream()
			.collect(Collectors.toMap(TypeFruitCount::typeContentId, TypeFruitCount::count));
	}

}
