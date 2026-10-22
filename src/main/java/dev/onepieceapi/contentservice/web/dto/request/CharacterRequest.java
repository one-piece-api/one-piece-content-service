package dev.onepieceapi.contentservice.web.dto.request;

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
import java.util.UUID;

/**
 * What a draft of a Character is saved with - everything the version says, in place of
 * what it said. Any part may be missing: a draft is saved incomplete. Lengths and the
 * size of the lists are the only limits checked here; whether a value is free to use, an
 * entry blank or repeated, and whether the links may be made, is for the service to say.
 *
 * @param romaji the official Latin-script name, e.g. {@code Monkey D. Luffy}
 * @param translations name and description per language code; a language left out, or
 * with nothing written in it, has no translation
 * @param birthday day and month, ISO 8601 {@code --MM-DD}; a date that does not exist is
 * an unreadable request
 * @param epithets in the order they are shown; left out for none
 * @param aliases in the order they are shown; left out for none
 * @param species the content ids of its species, in order; left out for none
 * @param affiliations its memberships, in order; left out for none
 * @param removeImage true to remove the draft's portrait; not together with an uploaded
 * one. Left out, the portrait is kept - or replaced, when one is uploaded with the
 * request
 * @param firstAppearance the chapter and/or the episode it is first shown or named in;
 * left out for none, which a draft may be - it is required to submit
 */
@Builder
public record CharacterRequest(@Size(max = Character.ROMAJI_MAX_LENGTH) String romaji,
		@Size(max = Character.JAPANESE_NAME_MAX_LENGTH) String japaneseName,
		Map<String, @Valid CharacterTranslationRequest> translations, CharacterStatus status, Gender gender,
		@Schema(type = "string", example = "--05-05") MonthDay birthday,
		@Size(max = Character.MAX_EPITHETS) List<@NotNull @Valid EpithetRequest> epithets,
		@Size(max = Character.MAX_ALIASES) List<@NotNull @Size(max = Character.ALIAS_MAX_LENGTH) String> aliases,
		@Schema(description = "The content ids of its species") List<@NotNull UUID> species,
		List<@NotNull @Valid MembershipRequest> affiliations, Boolean removeImage,
		FirstAppearanceRequest firstAppearance) {

	/** Whether the portrait is to be removed; not saying so keeps it. */
	public boolean removesImage() {
		return Boolean.TRUE.equals(this.removeImage);
	}

}
