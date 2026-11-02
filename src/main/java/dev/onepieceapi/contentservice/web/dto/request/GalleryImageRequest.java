package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.readertime.GalleryImage;
import dev.onepieceapi.readertime.ImageKind;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * An image of a gallery, as a draft is saved (workflow 3.5.1). Names its image in one of
 * two ways, never both: {@code imageId} keeps an image the draft already shows,
 * {@code upload} points to a file of the multipart part {@code images}.
 *
 * @param imageId the id of an image the draft shows; left out for an upload
 * @param upload the index of its file in the part {@code images}, from 0; left out to
 * keep {@code imageId}
 * @param kind appearance or form
 * @param caption per language code; a language left out, or blank, is not written yet
 * @param discovered as {@link WindowedRequest#discovered()}
 * @param ended as {@link WindowedRequest#ended()}
 */
public record GalleryImageRequest(String imageId, @Min(0) Integer upload, @NotNull ImageKind kind,
		Map<String, @Size(max = GalleryImage.CAPTION_MAX_LENGTH) String> caption, FirstAppearanceRequest discovered,
		FirstAppearanceRequest ended) implements WindowedRequest {

}
