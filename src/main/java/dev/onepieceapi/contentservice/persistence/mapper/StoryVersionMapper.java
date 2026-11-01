package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.domain.story.Chapter;
import dev.onepieceapi.contentservice.domain.story.Episode;
import dev.onepieceapi.contentservice.domain.story.Release;
import dev.onepieceapi.contentservice.domain.story.Saga;
import dev.onepieceapi.contentservice.domain.story.StoryTranslation;
import dev.onepieceapi.contentservice.domain.story.Volume;
import dev.onepieceapi.contentservice.domain.story.TitleTranslation;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.ArcVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ChapterVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.EpisodeVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.SagaVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.StoryTranslationEmbeddable;
import dev.onepieceapi.contentservice.persistence.entity.TitleTranslationEmbeddable;
import dev.onepieceapi.contentservice.persistence.entity.VolumeVersionEntity;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Between the rows of a story version - saga, arc, volume, chapter, episode - and the
 * domain: the workflow in the shared table, what it says in the entity's own.
 */
@UtilityClass
public class StoryVersionMapper {

	public Version<Saga> toDomain(SagaVersionEntity entity) {
		var body = new Saga(entity.getNumber(), titlesOf(entity.getTranslations()),
				ParagraphMapper.descriptionOf(entity.getParagraphs()));
		return ContentVersionMapper.toDomain(entity.getVersion(), body);
	}

	public Version<Arc> toDomain(ArcVersionEntity entity) {
		var body = new Arc(entity.getNumber(), entity.getSagaContentId(), titlesOf(entity.getTranslations()),
				ParagraphMapper.descriptionOf(entity.getParagraphs()));
		return ContentVersionMapper.toDomain(entity.getVersion(), body);
	}

	public Version<Volume> toDomain(VolumeVersionEntity entity) {
		var body = new Volume(entity.getNumber(), entity.getJapaneseTitle(), toReleases(entity.getReleases()),
				entity.getCoverImageId(), titlesOf(entity.getTranslations()));
		return ContentVersionMapper.toDomain(entity.getVersion(), body);
	}

	public Version<Chapter> toDomain(ChapterVersionEntity entity) {
		var body = Chapter.builder()
			.number(entity.getNumber())
			.arcContentId(entity.getArcContentId())
			.volumeContentId(entity.getVolumeContentId())
			.japaneseTitle(entity.getJapaneseTitle())
			.pageCount(entity.getPageCount())
			.releases(toReleases(entity.getReleases()))
			.translations(toDomain(entity.getTranslations()))
			.build();
		return ContentVersionMapper.toDomain(entity.getVersion(), body);
	}

	public Version<Episode> toDomain(EpisodeVersionEntity entity) {
		var body = Episode.builder()
			.number(entity.getNumber())
			.arcContentId(entity.getArcContentId())
			.japaneseTitle(entity.getJapaneseTitle())
			.broadcasts(toReleases(entity.getBroadcasts()))
			.translations(toDomain(entity.getTranslations()))
			.build();
		return ContentVersionMapper.toDomain(entity.getVersion(), body);
	}

	/** Makes the version say what was given, in place of what it said. */
	public void rewrite(SagaVersionEntity entity, Saga body, Instant now) {
		entity.rewrite(body.number(), titleRows(body.translations()), ParagraphMapper.toFields(body.paragraphs()), now);
	}

	/** Makes the version say what was given, in place of what it said. */
	public void rewrite(ArcVersionEntity entity, Arc body, Instant now) {
		entity.rewrite(body.number(), body.sagaContentId(), titleRows(body.translations()),
				ParagraphMapper.toFields(body.paragraphs()), now);
	}

	/** Makes the version say what was given, in place of what it said. */
	public void rewrite(VolumeVersionEntity entity, Volume body, Instant now) {
		entity.rewrite(body.number(), body.japaneseTitle(), body.coverImageId(), toDates(body.releases()),
				titleRows(body.translations()), now);
	}

	/** Makes the version say what was given, in place of what it said. */
	public void rewrite(ChapterVersionEntity entity, Chapter body, Instant now) {
		entity.rewrite(body.number(), body.arcContentId(), body.volumeContentId(), body.japaneseTitle(),
				body.pageCount(), toDates(body.releases()), toEmbeddables(body.translations()));
		entity.touch(now);
	}

	/** Makes the version say what was given, in place of what it said. */
	public void rewrite(EpisodeVersionEntity entity, Episode body, Instant now) {
		entity.rewrite(body.number(), body.arcContentId(), body.japaneseTitle(), toDates(body.broadcasts()),
				toEmbeddables(body.translations()), now);
	}

	/** Ordered by date, as a release list always reads. */
	private static List<Release> toReleases(Map<String, LocalDate> datesByCountry) {
		List<Release> releases = new ArrayList<>();
		datesByCountry.forEach((country, date) -> releases.add(new Release(country, date)));
		return Release.normalized(releases);
	}

	private static Map<String, LocalDate> toDates(List<Release> releases) {
		Map<String, LocalDate> datesByCountry = new HashMap<>();
		releases.forEach(release -> datesByCountry.put(release.country(), release.date()));
		return datesByCountry;
	}

	/** Sorted by language code, so the same version always reads the same way. */
	private static Map<String, TitleTranslation> titlesOf(Map<String, TitleTranslationEmbeddable> translations) {
		Map<String, TitleTranslation> titles = new TreeMap<>();
		translations
			.forEach((language, translation) -> titles.put(language, new TitleTranslation(translation.getName())));
		return titles;
	}

	private static Map<String, TitleTranslationEmbeddable> titleRows(Map<String, TitleTranslation> translations) {
		Map<String, TitleTranslationEmbeddable> titles = new HashMap<>();
		translations.forEach(
				(language, translation) -> titles.put(language, new TitleTranslationEmbeddable(translation.title())));
		return titles;
	}

	/** Sorted by language code, so the same version always reads the same way. */
	private static Map<String, StoryTranslation> toDomain(Map<String, StoryTranslationEmbeddable> translations) {
		Map<String, StoryTranslation> byLanguage = new TreeMap<>();
		translations.forEach((language, translation) -> byLanguage.put(language,
				new StoryTranslation(translation.getName(), translation.getDescription())));
		return byLanguage;
	}

	private static Map<String, StoryTranslationEmbeddable> toEmbeddables(Map<String, StoryTranslation> translations) {
		Map<String, StoryTranslationEmbeddable> byLanguage = new HashMap<>();
		translations.forEach((language, translation) -> byLanguage.put(language,
				new StoryTranslationEmbeddable(translation.title(), translation.description())));
		return byLanguage;
	}

}
