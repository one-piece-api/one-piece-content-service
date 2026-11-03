package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.readertime.ParagraphSection;
import dev.onepieceapi.contentservice.domain.readertime.Preview;
import dev.onepieceapi.contentservice.domain.story.Saga;
import dev.onepieceapi.contentservice.persistence.repository.SagaVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentPreviewer;
import dev.onepieceapi.contentservice.service.content.PreviewContext;
import dev.onepieceapi.contentservice.service.content.ReaderPreviews;
import dev.onepieceapi.readertime.Point;
import dev.onepieceapi.readertime.ReaderPosition;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * A Saga as a reader at N sees it (UF-CNT-23): met from its first chapter or episode
 * online on that axis (workflow 3.8), its description as the paragraphs read there.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class SagaPreviewer implements ContentPreviewer<Saga> {

	private final ReaderPreviews previews;

	private final SagaVersionRepository repository;

	@Override
	public Preview<Saga> previewOf(UUID contentId, Saga body, ReaderPosition position) {
		PreviewContext context = this.previews.contextFor(position, body.points(), Map.of());
		Point firstOnline = new Point(this.repository.firstChapterOnline(contentId),
				this.repository.firstEpisodeOnline(contentId));
		if (!context.hasMet(firstOnline)) {
			return Preview.notVisible(position);
		}
		return context.previewOf(new Saga(body.number(), body.translations(),
				context.paragraphs(ParagraphSection.DESCRIPTION, body.description())));
	}

}
