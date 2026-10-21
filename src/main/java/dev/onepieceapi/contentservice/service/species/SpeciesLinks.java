package dev.onepieceapi.contentservice.service.species;

import dev.onepieceapi.contentservice.domain.species.Species;
import dev.onepieceapi.contentservice.domain.species.SpeciesReference;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentSortField;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.SpeciesVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.SpeciesVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.SpeciesVersionRepository;
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
 * What a Character needs to say of a Species (implementation plan of the Species, D7):
 * the species it may be linked to, and the species it points to as they are today. Each
 * answers in batch, one query for a whole page.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class SpeciesLinks {

	/** The language a sort by name would read; the species are sorted by romaji. */
	private static final String NO_LANGUAGE = "en";

	private final SpeciesVersionRepository repository;

	private final Clock clock;

	/**
	 * The species as they were last approved, by content id. An id naming nothing, and a
	 * species with no approved version, are alike not in the map.
	 */
	public Map<UUID, SpeciesReference> referencesOf(Collection<UUID> contentIds) {
		if (contentIds.isEmpty()) {
			return Map.of();
		}
		return this.repository.findMostRecent(contentIds, VersionStatus.approved())
			.stream()
			.map(SpeciesLinks::referenceOf)
			.collect(Collectors.toMap(SpeciesReference::id, Function.identity()));
	}

	/**
	 * The species a character may be linked to - those with a version that passed review
	 * - one page at a time, by romaji, narrowed by what their romaji or a name contains.
	 */
	public Page<SpeciesReference> linkable(String text, int page, int size) {
		var filter = new ContentFilter(null, text, null, null);
		var pageable = PageRequest.of(page, size, Sort.by(ContentSortField.ROMAJI.field()));
		return this.repository.search(VersionStatus.approved(), filter, this.clock, pageable, NO_LANGUAGE)
			.map(SpeciesLinks::referenceOf);
	}

	private static SpeciesReference referenceOf(SpeciesVersionEntity version) {
		Species species = SpeciesVersionMapper.toDomain(version).body();
		return new SpeciesReference(version.getContentId(), species.romaji(), species.names());
	}

}
