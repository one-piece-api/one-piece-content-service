package dev.onepieceapi.contentservice.web.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitField;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A field of a Devil Fruit a reveal names as learned (implementation plan of the
 * timeline, T13), by the name of its property; {@code type} stands for the subcategory
 * too. Sent and answered alike.
 */
@Schema(description = "A field the reader learns at a reveal; type stands for the subcategory too")
public enum RevealedField {

	@JsonProperty("name")
	NAME(DevilFruitField.NAME),

	@JsonProperty("romaji")
	ROMAJI(DevilFruitField.ROMAJI),

	@JsonProperty("type")
	TYPE(DevilFruitField.TYPE),

	@JsonProperty("description")
	DESCRIPTION(DevilFruitField.DESCRIPTION),

	@JsonProperty("advantages")
	ADVANTAGES(DevilFruitField.ADVANTAGES),

	@JsonProperty("disadvantages")
	DISADVANTAGES(DevilFruitField.DISADVANTAGES);

	private final DevilFruitField field;

	RevealedField(DevilFruitField field) {
		this.field = field;
	}

	public DevilFruitField toDomain() {
		return this.field;
	}

	public static RevealedField of(DevilFruitField field) {
		return valueOf(field.name());
	}

}
