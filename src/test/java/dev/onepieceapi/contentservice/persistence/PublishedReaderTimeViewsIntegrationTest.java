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
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * Reader time in the published interface ({@code V23}, plan of the timeline TL5, ADR-0003
 * addendum): the first appearance on fruit, type and subcategory, and the reveal history
 * of a fruit read forward and resolved. Seeded in SQL, as a Gomu Gomu no Mi online:
 * <ul>
 * <li>initial knowledge: name "Gomu Gomu no Mi", romaji "Gomu Gomu no Mi", Paramecia</li>
 * <li>chapter 100: the description, an intermediate value (Italian only)</li>
 * <li>chapter 1044: name, romaji, type and description, the truth (Zoan, Mythical)</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class PublishedReaderTimeViewsIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Timestamp EARLIER = Timestamp.from(Instant.parse("2026-10-01T10:00:00Z"));

	private static final String TRUE_NAME = "Hito Hito no Mi, Modello Nika";

	@Autowired
	private DataSource dataSource;

	private JdbcClient jdbc;

	private UUID chapter1;

	private UUID chapter1044;

	private UUID paramecia;

	private UUID zoan;

	private UUID mythical;

	private UUID gomu;

	private UUID gomuVersion;

	@BeforeEach
	void setUp() {
		this.jdbc = JdbcClient.create(this.dataSource);
		UUID arc = content("ARC");
		UUID arcVersion = version(arc, "PUBLISHED");
		this.jdbc.sql("insert into arc_version (version_id, number) values (?, 1)").param(arcVersion).update();
		title("arc_version", arcVersion, "it", "Romance Dawn");
		this.chapter1 = chapter(arc, 1, "Romance Dawn");
		UUID chapter100 = chapter(arc, 100, "Capitolo 100");
		this.chapter1044 = chapter(arc, 1044, "Guerriero della liberazione");
		this.paramecia = type("Paramecia", this.chapter1, null);
		this.mythical = UUID.randomUUID();
		this.zoan = type("Zoan", this.chapter1, this.mythical);
		this.gomu = content("DEVIL_FRUIT");
		this.gomuVersion = version(this.gomu, "PUBLISHED");
		this.jdbc
			.sql("insert into devil_fruit_version (version_id, romaji, type_content_id, subcategory_id,"
					+ " first_appearance_chapter_id) values (?, 'Hito Hito no Mi', ?, ?, ?)")
			.params(this.gomuVersion, this.zoan, this.mythical, this.chapter1)
			.update();
		fruitTranslation("it", TRUE_NAME, "Il vero potere");
		fruitTranslation("en", "Human-Human Fruit, Model Nika", "The true power");
		this.jdbc
			.sql("insert into devil_fruit_version_initial_knowledge (version_id, romaji, type_content_id)"
					+ " values (?, 'Gomu Gomu no Mi', ?)")
			.params(this.gomuVersion, this.paramecia)
			.update();
		this.jdbc
			.sql("insert into devil_fruit_version_initial_knowledge_translation (version_id, language_code, name)"
					+ " values (?, 'it', 'Gomu Gomu no Mi')")
			.param(this.gomuVersion)
			.update();
		UUID middle = reveal(0, chapter100, null, null, "DESCRIPTION");
		revealTranslation(middle, "Un frutto strano", "Il corpo diventa di gomma");
		UUID last = reveal(1, this.chapter1044, null, null, "NAME", "ROMAJI", "TYPE", "DESCRIPTION");
		revealTranslation(last, "Il frutto è Nika", null);
	}

	@Test
	void fruitTypeAndSubcategoryShowTheChapterOfTheirFirstAppearanceInTheSameLanguage() {
		assertThat(firstAppearance("devil_fruit", "id = '" + this.gomu + "' and language = 'it'"))
			.containsExactly(tuple(this.chapter1, "1", 1, "Romance Dawn"));
		assertThat(firstAppearance("devil_fruit_type", "id = '" + this.zoan + "' and language = 'it'"))
			.containsExactly(tuple(this.chapter1, "1", 1, "Romance Dawn"));
		assertThat(firstAppearance("devil_fruit_type_subcategory", "id = '" + this.mythical + "'"))
			.containsExactly(tuple(this.chapter1044, "1044", 1044, "Guerriero della liberazione"));
	}

	@Test
	void theInitialKnowledgeHoldsOnlyTheRevealedFieldsAndLeavesTheUnknownNull() {
		var rows = this.jdbc
			.sql("select fields, name, romaji, type_id, type_name, subcategory_id, description, advantages"
					+ " from published.devil_fruit_initial_knowledge where devil_fruit_id = ? and language = 'it'")
			.param(this.gomu)
			.query((rs, n) -> tuple(fields(rs), rs.getString("name"), rs.getString("romaji"), rs.getObject("type_id"),
					rs.getString("type_name"), rs.getObject("subcategory_id"), rs.getString("description"),
					rs.getString("advantages")))
			.list();

		assertThat(rows).containsExactly(tuple(List.of("DESCRIPTION", "NAME", "ROMAJI", "TYPE"), "Gomu Gomu no Mi",
				"Gomu Gomu no Mi", this.paramecia, "Paramecia IT", null, null, null));
	}

	@Test
	void eachRevealShowsWhatIsLearnedThereTheTruthAtTheLastMentionOfAField() {
		var rows = this.jdbc
			.sql("select position, chapter_number, chapter_title, title, fields, name, romaji, type_name,"
					+ " subcategory_name, description from published.devil_fruit_reveal"
					+ " where devil_fruit_id = ? and language = 'it' order by position")
			.param(this.gomu)
			.query((rs, n) -> tuple(rs.getInt("position"), rs.getInt("chapter_number"), rs.getString("chapter_title"),
					rs.getString("title"), fields(rs), rs.getString("name"), rs.getString("romaji"),
					rs.getString("type_name"), rs.getString("subcategory_name"), rs.getString("description")))
			.list();

		assertThat(rows).containsExactly(
				tuple(0, 100, "Capitolo 100", "Un frutto strano", List.of("DESCRIPTION"), null, null, null, null,
						"Il corpo diventa di gomma"),
				tuple(1, 1044, "Guerriero della liberazione", "Il frutto è Nika",
						List.of("DESCRIPTION", "NAME", "ROMAJI", "TYPE"), TRUE_NAME, "Hito Hito no Mi", "Zoan IT",
						"Mitologico IT", "Il vero potere"));
	}

	@Test
	void anIntermediateValueMissingInALanguageStaysNullNeverTheTruth() {
		var descriptions = this.jdbc
			.sql("select description from published.devil_fruit_reveal"
					+ " where devil_fruit_id = ? and language = 'en' and position = 0")
			.param(this.gomu)
			.query((rs, n) -> rs.getString(1))
			.list();

		assertThat(descriptions).containsExactly((String) null);
	}

	@Test
	void theRevealsOfAVersionNotOnlineAreNotShown() {
		this.jdbc.sql("update content_version set status = 'SUPERSEDED' where id = ?").param(this.gomuVersion).update();

		assertThat(count("published.devil_fruit_reveal")).isZero();
		assertThat(count("published.devil_fruit_initial_knowledge")).isZero();
	}

	@Test
	void theMentionsHelperIsNotPartOfThePublishedInterface() {
		long published = this.jdbc
			.sql("select count(*) from information_schema.views where table_schema = 'published'"
					+ " and table_name = 'devil_fruit_reveal_mention'")
			.query(Long.class)
			.single();

		assertThat(published).isZero();
	}

	private List<Object> firstAppearance(String view, String where) {
		return this.jdbc
			.sql("select first_appearance_chapter_id, first_appearance_chapter_slug, first_appearance_chapter_number,"
					+ " first_appearance_chapter_title from published." + view + " where " + where)
			.query((rs, n) -> (Object) tuple(rs.getObject(1), rs.getString(2), rs.getInt(3), rs.getString(4)))
			.list();
	}

	private static List<String> fields(ResultSet rs) throws SQLException {
		Array array = rs.getArray("fields");
		return Arrays.asList((String[]) array.getArray());
	}

	private long count(String view) {
		return this.jdbc.sql("select count(*) from " + view).query(Long.class).single();
	}

	private UUID content(String entityType) {
		UUID id = UUID.randomUUID();
		this.jdbc.sql("insert into content (id, entity_type, created_at) values (?, ?, ?)")
			.params(id, entityType, EARLIER)
			.update();
		return id;
	}

	private UUID version(UUID contentId, String status) {
		UUID id = UUID.randomUUID();
		this.jdbc.sql("""
				insert into content_version (id, content_id, version_number, author_user_id, author_username,
				                             author_email, status, created_at, updated_at)
				values (?, ?, 1, ?, 'chopper', 'chopper@onepiece.local', ?, ?, ?)""")
			.params(id, contentId, UUID.randomUUID(), status, EARLIER, EARLIER)
			.update();
		return id;
	}

	/** The table name is this test's constant. */
	private void title(String table, UUID versionId, String language, String title) {
		this.jdbc.sql("insert into " + table + "_translation (version_id, language_code, title) values (?, ?, ?)")
			.params(versionId, language, title)
			.update();
	}

	/** A chapter online in Italian only. */
	private UUID chapter(UUID arcId, int number, String title) {
		UUID id = content("CHAPTER");
		UUID versionId = version(id, "PUBLISHED");
		this.jdbc.sql("insert into chapter_version (version_id, number, arc_content_id) values (?, ?, ?)")
			.params(versionId, number, arcId)
			.update();
		title("chapter_version", versionId, "it", title);
		return id;
	}

	/**
	 * A type online in Italian and English, first appearing at the given chapter; with a
	 * subcategory "Mitologico" first appearing at chapter 1044 when its id is given.
	 */
	private UUID type(String romaji, UUID firstAppearance, UUID subcategoryId) {
		UUID id = content("DEVIL_FRUIT_TYPE");
		UUID versionId = version(id, "PUBLISHED");
		this.jdbc
			.sql("insert into devil_fruit_type_version (version_id, romaji, first_appearance_chapter_id)"
					+ " values (?, ?, ?)")
			.params(versionId, romaji, firstAppearance)
			.update();
		for (String language : List.of("it", "en")) {
			this.jdbc
				.sql("insert into devil_fruit_type_version_translation (version_id, language_code, name)"
						+ " values (?, ?, ?)")
				.params(versionId, language, romaji + " " + language.toUpperCase())
				.update();
		}
		if (subcategoryId != null) {
			UUID row = UUID.randomUUID();
			this.jdbc
				.sql("insert into devil_fruit_type_version_subcategory (id, version_id, subcategory_id, position,"
						+ " first_appearance_chapter_id) values (?, ?, ?, 0, ?)")
				.params(row, versionId, subcategoryId, this.chapter1044)
				.update();
			this.jdbc
				.sql("insert into devil_fruit_type_version_subcategory_translation (subcategory_row_id,"
						+ " language_code, name) values (?, 'it', 'Mitologico IT')")
				.param(row)
				.update();
		}
		return id;
	}

	private void fruitTranslation(String language, String name, String description) {
		this.jdbc
			.sql("insert into devil_fruit_version_translation (version_id, language_code, name, description)"
					+ " values (?, ?, ?, ?)")
			.params(this.gomuVersion, language, name, description)
			.update();
	}

	private UUID reveal(int position, UUID chapterId, UUID typeId, String romaji, String... fields) {
		UUID id = UUID.randomUUID();
		this.jdbc
			.sql("insert into devil_fruit_version_reveal (id, version_id, position, revealed_at_chapter_id, romaji,"
					+ " type_content_id) values (?, ?, ?, ?, ?, ?)")
			.params(id, this.gomuVersion, position, chapterId, romaji, typeId)
			.update();
		for (String field : fields) {
			this.jdbc.sql("insert into devil_fruit_version_reveal_field (reveal_id, field) values (?, ?)")
				.params(id, field)
				.update();
		}
		return id;
	}

	private void revealTranslation(UUID revealId, String title, String description) {
		this.jdbc
			.sql("insert into devil_fruit_version_reveal_translation (reveal_id, language_code, title, description)"
					+ " values (?, 'it', ?, ?)")
			.params(revealId, title, description)
			.update();
	}

}
