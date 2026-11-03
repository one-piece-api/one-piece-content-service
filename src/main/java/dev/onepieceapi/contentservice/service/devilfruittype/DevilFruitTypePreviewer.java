package dev.onepieceapi.contentservice.service.devilfruittype;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSubcategory;
import dev.onepieceapi.contentservice.domain.readertime.ParagraphSection;
import dev.onepieceapi.contentservice.domain.readertime.Preview;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.service.content.ContentPreviewer;
import dev.onepieceapi.contentservice.service.content.PreviewContext;
import dev.onepieceapi.contentservice.service.content.ReaderPreviews;
import dev.onepieceapi.readertime.ReaderPosition;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * A Devil Fruit Type as a reader at N sees it (UF-CNT-23): met from its first appearance,
 * its subcategories each from its own (workflow 3.7), or with it when they have none, its
 * long texts as the paragraphs read there.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class DevilFruitTypePreviewer implements ContentPreviewer<DevilFruitType> {

	private final ReaderPreviews previews;

	@Override
	public Preview<DevilFruitType> previewOf(UUID contentId, DevilFruitType body, ReaderPosition position) {
		List<StoryPoint> points = Stream
			.concat(body.points().stream(),
					body.subcategories().stream().map(DevilFruitTypeSubcategory::firstAppearance))
			.toList();
		PreviewContext context = this.previews.contextFor(position, points, Map.of());
		if (!context.hasMet(body.firstAppearance())) {
			return Preview.notVisible(position);
		}
		List<DevilFruitTypeSubcategory> subcategories = body.subcategories()
			.stream()
			.filter(subcategory -> subcategory.lacksFirstAppearance() || context.hasMet(subcategory.firstAppearance()))
			.map(subcategory -> new DevilFruitTypeSubcategory(subcategory.id(), subcategory.translations(),
					context.trimmed(subcategory.firstAppearance())))
			.toList();
		return context.previewOf(body.toBuilder()
			.firstAppearance(context.trimmed(body.firstAppearance()))
			.subcategories(subcategories)
			.description(context.paragraphs(ParagraphSection.DESCRIPTION, body.description()))
			.advantages(context.paragraphs(ParagraphSection.ADVANTAGES, body.advantages()))
			.disadvantages(context.paragraphs(ParagraphSection.DISADVANTAGES, body.disadvantages()))
			.build());
	}

}
