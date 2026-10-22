package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.affiliation.AffiliationKind;
import dev.onepieceapi.contentservice.domain.character.CharacterStatus;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/**
 * The filters of the list of characters, as query parameters: those of every entity list
 * - see {@link ContentListRequest} - and those of its own (implementation plan of the
 * Character, D16). All optional and combinable.
 *
 * @param characterStatus only the characters whose version shown has this status - not
 * {@code status}, which is the status of the version
 * @param species only the characters whose version shown names this species (its content
 * id)
 * @param affiliation only the characters whose version shown belongs to this affiliation
 * (its content id), one they have left not counted
 * @param affiliationKind only the characters whose version shown belongs to an
 * affiliation of this kind, one they have left not counted
 */
public record CharacterListRequest(VersionStatus status, String q, String author,
		@PositiveOrZero Integer updatedWithinDays, CharacterStatus characterStatus,
		@Schema(description = "The content id of a Species") UUID species,
		@Schema(description = "The content id of an Affiliation") UUID affiliation, AffiliationKind affiliationKind) {

}
