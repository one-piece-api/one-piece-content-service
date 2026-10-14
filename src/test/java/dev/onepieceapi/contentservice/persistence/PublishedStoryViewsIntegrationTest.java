package dev.onepieceapi.contentservice.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * The story structure in the interface one-piece-public-api reads ({@code V16}) against a
 * real PostgreSQL (Testcontainers): only the versions online are shown, in the languages
 * they are written in, with the summary of what they point to and the values derived from
 * the children online. Seeded in SQL, as the table owner, and read through the
 * {@code published} views.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class PublishedStoryViewsIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Timestamp EARLIER = Timestamp.from(Instant.parse("2026-10-01T10:00:00Z"));

	private static final String[] BOTH = { "it", "en" };

	@Autowired
	private DataSource dataSource;

	private JdbcClient jdbc;

	@BeforeEach
	void setUp() {
		this.jdbc = JdbcClient.create(this.dataSource);
	}

	@Test
	void onlyTheVersionsOnlineAreShownOnePerLanguageWithTheNumberAsSlugAndTheTitleAsName() {
		UUID saga = content("SAGA");
		saga(saga, 1, "SUPERSEDED", 1, BOTH);
		saga(saga, 2, "PUBLISHED", 1, BOTH);
		saga(content("SAGA"), 1, "DRAFT", 2, BOTH);
		saga(content("SAGA"), 1, "RETIRED", 3, BOTH);
		UUID arc = content("ARC");
		arc(arc, 1, "PUBLISHED", 7, saga, BOTH);
		arc(content("ARC"), 1, "DRAFT", 8, saga, BOTH);
		UUID volume = content("VOLUME");
		volume(volume, 1, "PUBLISHED", 12, null, "it");
		volume(content("VOLUME"), 1, "RETIRED", 13, null, "it");
		UUID chapter = content("CHAPTER");
		chapter(chapter, 1, "PUBLISHED", 105, arc, null, BOTH);
		chapter(content("CHAPTER"), 1, "SUPERSEDED", 106, arc, null, BOTH);
		UUID episode = content("EPISODE");
		episode(episode, 1, "PUBLISHED", 9, arc, BOTH);
		episode(content("EPISODE"), 1, "DRAFT", 10, arc, BOTH);

		assertThat(rows("saga")).containsExactlyInAnyOrder(tuple(saga, "1", "it", "Title IT"),
				tuple(saga, "1", "en", "Title EN"));
		assertThat(rows("arc")).containsExactlyInAnyOrder(tuple(arc, "7", "it", "Title IT"),
				tuple(arc, "7", "en", "Title EN"));
		assertThat(rows("volume")).containsExactly(tuple(volume, "12", "it", "Title IT"));
		assertThat(rows("chapter")).containsExactlyInAnyOrder(tuple(chapter, "105", "it", "Title IT"),
				tuple(chapter, "105", "en", "Title EN"));
		assertThat(rows("episode")).containsExactlyInAnyOrder(tuple(episode, "9", "it", "Title IT"),
				tuple(episode, "9", "en", "Title EN"));
	}

	@Test
	void anArcShowsItsSagaInTheSameLanguageAndIsAbsentWhereTheSagaIsNotWritten() {
		UUID saga = content("SAGA");
		saga(saga, 1, "PUBLISHED", 3, "it");
		UUID arc = content("ARC");
		arc(arc, 1, "PUBLISHED", 5, saga, BOTH);

		var rows = this.jdbc.sql("select id, language, saga_id, saga_slug, saga_number, saga_title from published.arc")
			.query((rs, n) -> tuple(rs.getObject("id"), rs.getString("language"), rs.getObject("saga_id"),
					rs.getString("saga_slug"), rs.getInt("saga_number"), rs.getString("saga_title")))
			.list();

		assertThat(rows).containsExactly(tuple(arc, "it", saga, "3", 3, "Title IT"));
	}

	@Test
	void anArcWhoseSagaIsNotOnlineIsNotShown() {
		UUID saga = content("SAGA");
		saga(saga, 1, "DRAFT", 3, BOTH);
		arc(content("ARC"), 1, "PUBLISHED", 5, saga, BOTH);

		assertThat(count("select count(*) from published.arc")).isZero();
	}

	@Test
	void aChapterShowsItsArcAndItsVolumeAndKeepsItsRowWithoutVolume() {
		String image = image('a');
		UUID saga = content("SAGA");
		saga(saga, 1, "PUBLISHED", 1, BOTH);
		UUID arc = content("ARC");
		arc(arc, 1, "PUBLISHED", 2, saga, BOTH);
		UUID volume = content("VOLUME");
		volume(volume, 1, "PUBLISHED", 4, image, BOTH);
		UUID withVolume = content("CHAPTER");
		chapter(withVolume, 1, "PUBLISHED", 30, arc, volume, BOTH);
		UUID withoutVolume = content("CHAPTER");
		chapter(withoutVolume, 1, "PUBLISHED", 31, arc, null, "it");

		var rows = this.jdbc
			.sql("select id, language, arc_id, arc_slug, arc_number, arc_title, volume_id,"
					+ " volume_slug, volume_number, volume_title, volume_cover_image_id from published.chapter")
			.query((rs, n) -> tuple(rs.getObject("id"), rs.getString("language"), rs.getObject("arc_id"),
					rs.getString("arc_slug"), rs.getInt("arc_number"), rs.getString("arc_title"),
					rs.getObject("volume_id"), rs.getString("volume_slug"), rs.getObject("volume_number"),
					rs.getString("volume_title"), rs.getString("volume_cover_image_id")))
			.list();

		assertThat(rows).containsExactlyInAnyOrder(
				tuple(withVolume, "it", arc, "2", 2, "Title IT", volume, "4", 4, "Title IT", image),
				tuple(withVolume, "en", arc, "2", 2, "Title EN", volume, "4", 4, "Title EN", image),
				tuple(withoutVolume, "it", arc, "2", 2, "Title IT", null, null, null, null, null));
	}

	@Test
	void aChapterKeepsItsRowInALanguageItsVolumeIsNotWrittenIn() {
		UUID saga = content("SAGA");
		saga(saga, 1, "PUBLISHED", 1, BOTH);
		UUID arc = content("ARC");
		arc(arc, 1, "PUBLISHED", 2, saga, BOTH);
		UUID volume = content("VOLUME");
		volume(volume, 1, "PUBLISHED", 4, null, "it");
		UUID chapter = content("CHAPTER");
		chapter(chapter, 1, "PUBLISHED", 30, arc, volume, BOTH);

		var rows = this.jdbc.sql("select language, volume_number is null as missing from published.chapter")
			.query((rs, n) -> tuple(rs.getString("language"), rs.getBoolean("missing")))
			.list();

		assertThat(rows).containsExactlyInAnyOrder(tuple("it", false), tuple("en", true));
	}

	@Test
	void anEpisodeWithoutArcKeepsItsRowWithTheArcColumnsNull() {
		UUID saga = content("SAGA");
		saga(saga, 1, "PUBLISHED", 1, BOTH);
		UUID arc = content("ARC");
		arc(arc, 1, "PUBLISHED", 2, saga, BOTH);
		UUID canon = content("EPISODE");
		episode(canon, 1, "PUBLISHED", 1, arc, "it");
		UUID filler = content("EPISODE");
		episode(filler, 1, "PUBLISHED", 2, null, "it");

		var rows = this.jdbc.sql("select id, arc_id, arc_slug, arc_number, arc_title from published.episode")
			.query((rs, n) -> tuple(rs.getObject("id"), rs.getObject("arc_id"), rs.getString("arc_slug"),
					rs.getObject("arc_number"), rs.getString("arc_title")))
			.list();

		assertThat(rows).containsExactlyInAnyOrder(tuple(canon, arc, "2", 2, "Title IT"),
				tuple(filler, null, null, null, null));
	}

	@Test
	void theChapterSpansCountOnlyTheChaptersOnlineAndTheirJapaneseDay() {
		UUID saga = content("SAGA");
		saga(saga, 1, "PUBLISHED", 1, "it");
		UUID first = content("ARC");
		arc(first, 1, "PUBLISHED", 1, saga, "it");
		UUID second = content("ARC");
		arc(second, 1, "PUBLISHED", 2, saga, "it");
		UUID empty = content("ARC");
		arc(empty, 1, "PUBLISHED", 3, saga, "it");
		UUID volume = content("VOLUME");
		volume(volume, 1, "PUBLISHED", 1, null, "it");
		UUID emptyVolume = content("VOLUME");
		volume(emptyVolume, 1, "PUBLISHED", 2, null, "it");

		chapterWithReleases(first, volume, 1, "PUBLISHED", "JP", "2000-01-01", "IT", "1999-01-01");
		chapterWithReleases(first, volume, 2, "PUBLISHED", "IT", "1998-01-01");
		chapterWithReleases(first, volume, 3, "RETIRED", "JP", "1990-01-01");
		chapterWithReleases(first, volume, 4, "DRAFT", "JP", "2030-01-01");
		chapterWithReleases(second, null, 10, "PUBLISHED", "JP", "2001-01-01");

		assertThat(spanOf("arc", first, "chapter")).containsExactly(2, 1, 2, date("2000-01-01"), date("2000-01-01"));
		assertThat(spanOf("arc", second, "chapter")).containsExactly(1, 10, 10, date("2001-01-01"), date("2001-01-01"));
		assertThat(spanOf("arc", empty, "chapter")).containsExactly(0, null, null, null, null);
		assertThat(spanOf("saga", saga, "chapter")).containsExactly(3, 1, 10, date("2000-01-01"), date("2001-01-01"));
		assertThat(spanOf("volume", volume, "chapter")).containsExactly(2, 1, 2, date("2000-01-01"),
				date("2000-01-01"));
		assertThat(spanOf("volume", emptyVolume, "chapter")).containsExactly(0, null, null, null, null);
	}

	@Test
	void theEpisodeSpansCountOnlyTheEpisodesOnlineAndTheirJapaneseBroadcast() {
		UUID saga = content("SAGA");
		saga(saga, 1, "PUBLISHED", 1, "it");
		UUID first = content("ARC");
		arc(first, 1, "PUBLISHED", 1, saga, "it");
		UUID second = content("ARC");
		arc(second, 1, "PUBLISHED", 2, saga, "it");
		UUID empty = content("ARC");
		arc(empty, 1, "PUBLISHED", 3, saga, "it");
		UUID otherSaga = content("SAGA");
		saga(otherSaga, 1, "PUBLISHED", 2, "it");
		UUID otherArc = content("ARC");
		arc(otherArc, 1, "PUBLISHED", 4, otherSaga, "it");

		episodeWithBroadcasts(first, 1, "PUBLISHED", "JP", "2000-02-01", "IT", "1999-02-01");
		episodeWithBroadcasts(first, 2, "PUBLISHED", "IT", "1998-02-01");
		episodeWithBroadcasts(first, 3, "RETIRED", "JP", "1990-02-01");
		episodeWithBroadcasts(first, 4, "DRAFT", "JP", "2030-02-01");
		episodeWithBroadcasts(second, 10, "PUBLISHED", "JP", "2001-02-01");
		episodeWithBroadcasts(null, 20, "PUBLISHED", "JP", "2005-02-01");

		assertThat(spanOf("arc", first, "episode")).containsExactly(2, 1, 2, date("2000-02-01"), date("2000-02-01"));
		assertThat(spanOf("arc", second, "episode")).containsExactly(1, 10, 10, date("2001-02-01"), date("2001-02-01"));
		assertThat(spanOf("arc", empty, "episode")).containsExactly(0, null, null, null, null);
		assertThat(spanOf("saga", saga, "episode")).containsExactly(3, 1, 10, date("2000-02-01"), date("2001-02-01"));
		assertThat(spanOf("saga", otherSaga, "episode")).containsExactly(0, null, null, null, null);
	}

	@Test
	void theReleasesAndBroadcastsListEveryCountryOfTheVersionsOnlineOnly() {
		UUID volume = content("VOLUME");
		UUID volumeOnline = volume(volume, 1, "PUBLISHED", 1, null, "it");
		release("volume_version_release", volumeOnline, "JP", "2000-01-01");
		release("volume_version_release", volumeOnline, "IT", "2001-01-01");
		UUID volumeDraft = volume(content("VOLUME"), 1, "DRAFT", 2, null, "it");
		release("volume_version_release", volumeDraft, "JP", "2002-01-01");

		UUID chapter = content("CHAPTER");
		UUID chapterOnline = chapter(chapter, 1, "PUBLISHED", 1, null, null, "it");
		release("chapter_version_release", chapterOnline, "JP", "2000-01-02");
		release("chapter_version_release", chapterOnline, "FR", "2001-01-02");
		UUID chapterRetired = chapter(content("CHAPTER"), 1, "RETIRED", 2, null, null, "it");
		release("chapter_version_release", chapterRetired, "JP", "2002-01-02");

		UUID episode = content("EPISODE");
		UUID episodeOnline = episode(episode, 1, "PUBLISHED", 1, null, "it");
		release("episode_version_broadcast", episodeOnline, "JP", "2000-01-03");
		release("episode_version_broadcast", episodeOnline, "ES", "2001-01-03");
		UUID episodeSuperseded = episode(content("EPISODE"), 1, "SUPERSEDED", 2, null, "it");
		release("episode_version_broadcast", episodeSuperseded, "JP", "2002-01-03");

		assertThat(this.jdbc.sql("select volume_id, country, release_date from published.volume_release")
			.query((rs, n) -> tuple(rs.getObject(1), rs.getString(2), rs.getObject(3, LocalDate.class)))
			.list()).containsExactlyInAnyOrder(tuple(volume, "JP", date("2000-01-01")),
					tuple(volume, "IT", date("2001-01-01")));
		assertThat(this.jdbc.sql("select chapter_id, country, release_date from published.chapter_release")
			.query((rs, n) -> tuple(rs.getObject(1), rs.getString(2), rs.getObject(3, LocalDate.class)))
			.list()).containsExactlyInAnyOrder(tuple(chapter, "JP", date("2000-01-02")),
					tuple(chapter, "FR", date("2001-01-02")));
		assertThat(this.jdbc.sql("select episode_id, country, broadcast_date from published.episode_broadcast")
			.query((rs, n) -> tuple(rs.getObject(1), rs.getString(2), rs.getObject(3, LocalDate.class)))
			.list()).containsExactlyInAnyOrder(tuple(episode, "JP", date("2000-01-03")),
					tuple(episode, "ES", date("2001-01-03")));
	}

	@Test
	void theImagesShownIncludeTheCoversOfTheVolumesOnlineAndStillTheFruitsOnes() {
		String online = image('a');
		String draft = image('b');
		String fruit = image('c');
		String unused = image('d');
		volume(content("VOLUME"), 1, "PUBLISHED", 1, online, "it");
		volume(content("VOLUME"), 1, "DRAFT", 2, draft, "it");
		fruitWithImage(fruit);

		assertThat(this.jdbc.sql("select id from published.image").query(String.class).list())
			.containsExactlyInAnyOrder(online, fruit)
			.doesNotContain(draft, unused);
	}

	@Test
	void theReaderReadsTheStoryViews() {
		this.jdbc.sql("set local role public_api_reader").update();

		for (String view : new String[] { "saga", "arc", "volume", "chapter", "episode", "volume_release",
				"chapter_release", "episode_broadcast", "arc_chapter_span", "arc_episode_span", "volume_chapter_span",
				"saga_chapter_span", "saga_episode_span" }) {
			assertThat(count("select count(*) from published." + view)).as(view).isZero();
		}
	}

	private java.util.List<Object> rows(String view) {
		return this.jdbc.sql("select id, slug, language, name from published." + view)
			.query((rs,
					n) -> (Object) tuple(rs.getObject("id"), rs.getString("slug"), rs.getString("language"),
							rs.getString("name")))
			.list();
	}

	/**
	 * The derived values of a parent in {@code published.<parent>}, read on its first
	 * row: count, first and last number, first and last day.
	 */
	private java.util.List<Object> spanOf(String parent, UUID id, String child) {
		String prefix = child + "_";
		return this.jdbc
			.sql("select " + prefix + "count, first_" + prefix + "number, last_" + prefix + "number, first_" + prefix
					+ "date, last_" + prefix + "date from published." + parent + " where id = ?")
			.param(id)
			.query((rs, n) -> java.util.Arrays.<Object>asList(rs.getInt(1), rs.getObject(2), rs.getObject(3),
					rs.getObject(4, LocalDate.class), rs.getObject(5, LocalDate.class)))
			.list()
			.getFirst();
	}

	private long count(String sql) {
		return this.jdbc.sql(sql).query(Long.class).single();
	}

	private static LocalDate date(String iso) {
		return LocalDate.parse(iso);
	}

	private UUID content(String entityType) {
		UUID id = UUID.randomUUID();
		this.jdbc.sql("insert into content (id, entity_type, created_at) values (?, ?, ?)")
			.params(id, entityType, EARLIER)
			.update();
		return id;
	}

	private UUID version(UUID contentId, int versionNumber, String status) {
		UUID id = UUID.randomUUID();
		this.jdbc.sql("""
				insert into content_version (id, content_id, version_number, author_user_id, author_username,
				                             author_email, status, created_at, updated_at)
				values (?, ?, ?, ?, 'chopper', 'chopper@onepiece.local', ?, ?, ?)""")
			.params(id, contentId, versionNumber, UUID.randomUUID(), status, EARLIER, EARLIER)
			.update();
		return id;
	}

	/** Seeds the titles of a version, "Title LANGUAGE", in each given language. */
	private void translations(String table, UUID versionId, String... languages) {
		for (String language : languages) {
			this.jdbc.sql("insert into " + table + "_translation (version_id, language_code, title) values (?, ?, ?)")
				.params(versionId, language, "Title " + language.toUpperCase())
				.update();
		}
	}

	private UUID saga(UUID contentId, int versionNumber, String status, int number, String... languages) {
		UUID id = version(contentId, versionNumber, status);
		this.jdbc.sql("insert into saga_version (version_id, number) values (?, ?)").params(id, number).update();
		translations("saga_version", id, languages);
		return id;
	}

	private UUID arc(UUID contentId, int versionNumber, String status, int number, UUID sagaId, String... languages) {
		UUID id = version(contentId, versionNumber, status);
		this.jdbc.sql("insert into arc_version (version_id, number, saga_content_id) values (?, ?, ?)")
			.params(id, number, sagaId)
			.update();
		translations("arc_version", id, languages);
		return id;
	}

	private UUID volume(UUID contentId, int versionNumber, String status, int number, String coverImageId,
			String... languages) {
		UUID id = version(contentId, versionNumber, status);
		this.jdbc.sql("insert into volume_version (version_id, number, cover_image_id) values (?, ?, ?)")
			.params(id, number, coverImageId)
			.update();
		translations("volume_version", id, languages);
		return id;
	}

	private UUID chapter(UUID contentId, int versionNumber, String status, int number, UUID arcId, UUID volumeId,
			String... languages) {
		UUID id = version(contentId, versionNumber, status);
		this.jdbc
			.sql("insert into chapter_version (version_id, number, arc_content_id, volume_content_id)"
					+ " values (?, ?, ?, ?)")
			.params(id, number, arcId, volumeId)
			.update();
		translations("chapter_version", id, languages);
		return id;
	}

	private UUID episode(UUID contentId, int versionNumber, String status, int number, UUID arcId,
			String... languages) {
		UUID id = version(contentId, versionNumber, status);
		this.jdbc.sql("insert into episode_version (version_id, number, arc_content_id) values (?, ?, ?)")
			.params(id, number, arcId)
			.update();
		translations("episode_version", id, languages);
		return id;
	}

	/** Seeds a chapter of version 1 with its releases, given as country and day pairs. */
	private void chapterWithReleases(UUID arcId, UUID volumeId, int number, String status, String... countryAndDay) {
		UUID id = chapter(content("CHAPTER"), 1, status, number, arcId, volumeId, "it");
		for (int i = 0; i < countryAndDay.length; i += 2) {
			release("chapter_version_release", id, countryAndDay[i], countryAndDay[i + 1]);
		}
	}

	/**
	 * Seeds an episode of version 1 with its broadcasts, given as country and day pairs.
	 */
	private void episodeWithBroadcasts(UUID arcId, int number, String status, String... countryAndDay) {
		UUID id = episode(content("EPISODE"), 1, status, number, arcId, "it");
		for (int i = 0; i < countryAndDay.length; i += 2) {
			release("episode_version_broadcast", id, countryAndDay[i], countryAndDay[i + 1]);
		}
	}

	/** The table name is this test's constant. */
	private void release(String table, UUID versionId, String country, String day) {
		String dateColumn = table.startsWith("episode") ? "broadcast_date" : "release_date";
		this.jdbc.sql("insert into " + table + " (version_id, country, " + dateColumn + ") values (?, ?, ?)")
			.params(versionId, country, java.sql.Date.valueOf(day))
			.update();
	}

	/** Seeds a published fruit using the image: the fruit's type is not needed here. */
	private void fruitWithImage(String imageId) {
		UUID id = version(content("DEVIL_FRUIT"), 1, "PUBLISHED");
		this.jdbc.sql("insert into devil_fruit_version (version_id, romaji, image_id) values (?, 'Mera', ?)")
			.params(id, imageId)
			.update();
	}

	/**
	 * Seeds a stored image whose id is the given digit repeated: its bytes do not matter
	 * here.
	 */
	private String image(char digit) {
		String id = String.valueOf(digit).repeat(64);
		this.jdbc
			.sql("insert into image (id, content_type, width, height, size_bytes, bytes, created_at)"
					+ " values (?, 'image/png', 640, 800, 1, ?, ?)")
			.params(id, new byte[] { 1 }, EARLIER)
			.update();
		return id;
	}

}
