package dev.onepieceapi.contentservice.service.species;

import dev.onepieceapi.contentservice.domain.readertime.ParagraphSection;
import dev.onepieceapi.contentservice.domain.readertime.Preview;
import dev.onepieceapi.contentservice.domain.species.Species;
import dev.onepieceapi.contentservice.service.content.ContentPreviewer;
import dev.onepieceapi.contentservice.service.content.PreviewContext;
import dev.onepieceapi.contentservice.service.content.ReaderPreviews;
import dev.onepieceapi.readertime.ReaderPosition;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * A Species as a reader at N sees it (UF-CNT-23): met from its first appearance, its
 * description as the paragraphs read there.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class SpeciesPreviewer implements ContentPreviewer<Species> {

	private final ReaderPreviews previews;

	@Override
	public Preview<Species> previewOf(UUID contentId, Species body, ReaderPosition position) {
		PreviewContext context = this.previews.contextFor(position, body.points(), Map.of());
		if (!context.hasMet(body.firstAppearance())) {
			return Preview.notVisible(position);
		}
		return context.previewOf(body.toBuilder()
			.firstAppearance(context.trimmed(body.firstAppearance()))
			.description(context.paragraphs(ParagraphSection.DESCRIPTION, body.description()))
			.build());
	}

}
