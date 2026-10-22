package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/**
 * What a draft of a Devil Fruit is saved with - everything the version says, in place of
 * what it said. Any part may be missing: a draft is saved incomplete. Lengths are the
 * only limit checked here; whether a value is free to use, and whether the type may be
 * linked, is for the service to say.
 *
 * @param type the id of the content of the Devil Fruit Type it belongs to; whether it can
 * be chosen is answered by {@code GET /devil-fruit-types/linkable}
 * @param subcategory one of the subcategories of that type, as {@code linkable} lists
 * them; left out for none
 * @param multipleUsers true when several characters may use it at the same time, as the
 * SMILE fruits; left out is false
 * @param translations name, description, advantages and disadvantages per language code;
 * a language left out, or with nothing written in it, has no translation
 * @param firstAppearance the chapter the reader first meets it in; left out for none,
 * which a draft may be - it is required to go online
 * @param removeImage true to remove the draft's image; not together with an uploaded one.
 * Left out, the image is kept - or replaced, when one is uploaded with the request
 */
public record DevilFruitRequest(@Size(max = DevilFruit.ROMAJI_MAX_LENGTH) String romaji,
		@Schema(description = "The content id of its Devil Fruit Type") UUID type,
		@Schema(description = "The id of a subcategory of its Devil Fruit Type; left out for none") UUID subcategory,
		Boolean multipleUsers, Map<String, @Valid DevilFruitTranslationRequest> translations, Boolean removeImage,
		FirstAppearanceRequest firstAppearance) {

	/** Whether several characters may use it; not saying so is not. */
	public boolean allowsMultipleUsers() {
		return Boolean.TRUE.equals(this.multipleUsers);
	}

	/** Whether the image is to be removed; not saying so keeps it. */
	public boolean removesImage() {
		return Boolean.TRUE.equals(this.removeImage);
	}

}
