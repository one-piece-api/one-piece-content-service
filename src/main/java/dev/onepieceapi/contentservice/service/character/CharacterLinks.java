package dev.onepieceapi.contentservice.service.character;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.CharacterReference;
import dev.onepieceapi.contentservice.domain.character.RelativeOf;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentSortField;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.CharacterVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.CharacterVersionMapper;
import dev.onepieceapi.contentservice.persistence.projection.RelativeLink;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What a content needs to say of a Character (implementation plan of the Character, C6):
 * the characters it may be linked to, and the character it points to as it is today. Each
 * answers in batch, one query for a whole page.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class CharacterLinks {

	/** The language a sort by name would read; the characters are sorted by romaji. */
	private static final String NO_LANGUAGE = "en";

	private final CharacterVersionRepository repository;

	private final Clock clock;

	/**
	 * The characters as they were last approved, by content id. An id naming nothing, and
	 * a character with no approved version, are alike not in the map.
	 */
	public Map<UUID, CharacterReference> referencesOf(Collection<UUID> contentIds) {
		if (contentIds.isEmpty()) {
			return Map.of();
		}
		return this.repository.findMostRecent(contentIds, VersionStatus.approved())
			.stream()
			.map(CharacterLinks::referenceOf)
			.collect(Collectors.toMap(CharacterReference::id, Function.identity()));
	}

	/**
	 * The characters online that name this one as their relative, each with the kind as
	 * this one reads it - the inverse of the stored one (D13) - by romaji. One with
	 * nothing approved to call it by is left out, which a character online never is.
	 */
	public List<RelativeOf> relativesOf(UUID contentId) {
		List<RelativeLink> links = this.repository.findLinksOnlineTo(contentId);
		Map<UUID, CharacterReference> holders = referencesOf(
				links.stream().map(RelativeLink::holderContentId).toList());
		return links.stream()
			.filter(link -> holders.containsKey(link.holderContentId()))
			.map(link -> new RelativeOf(holders.get(link.holderContentId()), link.kind().inverse()))
			.sorted(Comparator.comparing(RelativeOf::romaji))
			.toList();
	}

	/**
	 * The characters a content may be linked to - those with a version that passed review
	 * - one page at a time, by romaji, narrowed by what their romaji or a name contains.
	 */
	public Page<CharacterReference> linkable(String text, int page, int size) {
		var filter = new ContentFilter(null, text, null, null);
		var pageable = PageRequest.of(page, size, Sort.by(ContentSortField.ROMAJI.field()));
		return this.repository.search(VersionStatus.approved(), filter, this.clock, pageable, NO_LANGUAGE)
			.map(CharacterLinks::referenceOf);
	}

	private static CharacterReference referenceOf(CharacterVersionEntity version) {
		Character character = CharacterVersionMapper.toDomain(version).body();
		return new CharacterReference(version.getContentId(), character.romaji(), character.names(),
				character.gender());
	}

}
