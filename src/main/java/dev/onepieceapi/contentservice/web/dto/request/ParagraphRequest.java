package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * A paragraph of a long text, as a draft is saved (workflow 3.7.3).
 *
 * @param text per language code; a language left out, or blank, is not written yet
 * @param discovered as {@link WindowedRequest#discovered()}
 * @param ended as {@link WindowedRequest#ended()}
 */
public record ParagraphRequest(Map<String, @Size(max = Paragraph.TEXT_MAX_LENGTH) String> text,
		FirstAppearanceRequest discovered, FirstAppearanceRequest ended) implements WindowedRequest {

}
