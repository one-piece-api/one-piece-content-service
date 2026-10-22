package dev.onepieceapi.contentservice.service.affiliation;

import dev.onepieceapi.contentservice.domain.affiliation.Affiliation;
import dev.onepieceapi.contentservice.domain.affiliation.AffiliationReference;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentSortField;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.AffiliationVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.AffiliationVersionMapper;
import dev.onepieceapi.contentservice.persistence.projection.AffiliationMemberCount;
import dev.onepieceapi.contentservice.persistence.repository.AffiliationVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
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
 * What a Character needs to say of an Affiliation (implementation plan of the Character,
 * C8): the affiliations it may be linked to, the affiliations it points to as they are
 * today, and how many characters online belong to each. Each answers in batch, one query
 * for a whole page.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class AffiliationLinks {

	/** The language a sort by name would read; the affiliations are sorted by romaji. */
	private static final String NO_LANGUAGE = "en";

	private final AffiliationVersionRepository repository;

	private final CharacterVersionRepository characters;

	private final Clock clock;

	/**
	 * The affiliations as they were last approved, by content id. An id naming nothing,
	 * and an affiliation with no approved version, are alike not in the map.
	 */
	public Map<UUID, AffiliationReference> referencesOf(Collection<UUID> contentIds) {
		if (contentIds.isEmpty()) {
			return Map.of();
		}
		return this.repository.findMostRecent(contentIds, VersionStatus.approved())
			.stream()
			.map(AffiliationLinks::referenceOf)
			.collect(Collectors.toMap(AffiliationReference::id, Function.identity()));
	}

	/**
	 * The affiliations a character may be linked to - those with a version that passed
	 * review - one page at a time, by romaji, narrowed by what their romaji or a name
	 * contains.
	 */
	public Page<AffiliationReference> linkable(String text, int page, int size) {
		var filter = new ContentFilter(null, text, null, null);
		var pageable = PageRequest.of(page, size, Sort.by(ContentSortField.ROMAJI.field()));
		return this.repository.search(VersionStatus.approved(), filter, this.clock, pageable, NO_LANGUAGE)
			.map(AffiliationLinks::referenceOf);
	}

	/**
	 * How many characters online belong to each of these affiliations, those who left it
	 * not counted. An affiliation with none is not in the map.
	 */
	public Map<UUID, Long> memberCounts(Collection<UUID> contentIds) {
		if (contentIds.isEmpty()) {
			return Map.of();
		}
		return this.characters.countMembersOf(contentIds)
			.stream()
			.collect(Collectors.toMap(AffiliationMemberCount::affiliationContentId, AffiliationMemberCount::count));
	}

	private static AffiliationReference referenceOf(AffiliationVersionEntity version) {
		Affiliation affiliation = AffiliationVersionMapper.toDomain(version).body();
		return new AffiliationReference(version.getContentId(), affiliation.romaji(), affiliation.names(),
				affiliation.kind());
	}

}
