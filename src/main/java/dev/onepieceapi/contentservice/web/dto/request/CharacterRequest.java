package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.readertime.Galleried;
import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.CharacterStatus;
import dev.onepieceapi.contentservice.domain.character.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.MonthDay;
import java.util.List;
import java.util.Map;

/**
 * What a draft of a Character is saved with - everything the version says, in place of
 * what it said. Any part may be missing: a draft is saved incomplete. Lengths and the
 * size of the lists are the only limits checked here; whether a value is free to use, an
 * entry blank or repeated, and whether the links may be made, is for the service to say.
 *
 * @param romaji the official Latin-script name, e.g. {@code Monkey D. Luffy}
 * @param translations the name per language code; a language left out, or with nothing
 * written in it, has no translation
 * @param birthday day and month, ISO 8601 {@code --MM-DD}; a date that does not exist is
 * an unreadable request
 * @param epithets in the order they are shown; left out for none
 * @param aliases in the order they are shown; left out for none
 * @param species its species, in order; left out for none
 * @param affiliations its memberships, in order; left out for none
 * @param devilFruits the fruits it ate, in order; left out for none
 * @param relatives its relatives, in order; left out for none
 * @param initialKnowledge what the reader believes at the first appearance (workflow
 * 3.7.1), for the fields some reveal names; left out for nothing
 * @param reveals what the reader learns later, in order; left out for none
 * @param gallery its images in order (workflow 3.5.1), each kept or uploaded; left out
 * for none
 * @param firstAppearance the chapter and/or the episode it is first shown or named in;
 * left out for none, which a draft may be - it is required to submit
 */
@Builder
public record CharacterRequest(@Size(max = Character.ROMAJI_MAX_LENGTH) String romaji,
		@Size(max = Character.JAPANESE_NAME_MAX_LENGTH) String japaneseName,
		Map<String, @Valid CharacterTranslationRequest> translations, CharacterStatus status, Gender gender,
		@Schema(type = "string", example = "--05-05") MonthDay birthday,
		@Size(max = Character.MAX_EPITHETS) List<@NotNull @Valid EpithetRequest> epithets,
		@Size(max = Character.MAX_ALIASES) List<@NotNull @Valid AliasRequest> aliases,
		List<@NotNull @Valid SpeciesEntryRequest> species, List<@NotNull @Valid MembershipRequest> affiliations,
		List<@NotNull @Valid FruitUseRequest> devilFruits, List<@NotNull @Valid RelativeRequest> relatives,
		@Size(max = Galleried.MAX_IMAGES) List<@NotNull @Valid GalleryImageRequest> gallery,
		FirstAppearanceRequest firstAppearance,
		@Size(max = Paragraph.MAX_PER_SECTION) List<@NotNull @Valid ParagraphRequest> description,
		@Valid CharacterKnowledgeRequest initialKnowledge, List<@NotNull @Valid CharacterRevealRequest> reveals) {

}
