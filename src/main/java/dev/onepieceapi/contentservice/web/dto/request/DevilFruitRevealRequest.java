package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitField;
import dev.onepieceapi.contentservice.domain.readertime.Reveal;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A reveal of a Devil Fruit (workflow 3.7.1), see {@link RevealRequest}: {@code NAME} is
 * learned with {@code translations}, {@code ROMAJI} with {@code romaji}, {@code TYPE}
 * with {@code type} and {@code subcategory}.
 */
public record DevilFruitRevealRequest(FirstAppearanceRequest revealedAt,
		Map<String, @Size(max = Reveal.TITLE_MAX_LENGTH) String> titles, Set<@NotNull DevilFruitField> fields,
		@Size(max = DevilFruit.ROMAJI_MAX_LENGTH) String romaji,
		@Schema(description = "The content id of a Devil Fruit Type") UUID type,
		@Schema(description = "The id of a subcategory of that type") UUID subcategory,
		Map<String, @Valid DevilFruitTranslationRequest> translations) implements RevealRequest<DevilFruitField> {

}
