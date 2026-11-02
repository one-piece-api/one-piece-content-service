package dev.onepieceapi.contentservice.domain.readertime;

import dev.onepieceapi.contentservice.domain.workflow.NaturalKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The capability of an item whose single-value fields change in the reader's knowledge
 * (docs/user-flows/content-editorial-workflow.md 3.7.1; ADR-0012): its main fields are
 * the current value, its initial knowledge what the reader believes at the first
 * appearance, and its reveals what the reader learns later, in list order. Devil Fruit,
 * Character, Bounty. Its points follow the rules of every point
 * ({@code ReaderAxisRules}); what it needs to be saved is in {@code RevealRules}, to be
 * submitted here.
 *
 * @param <F> the fields of the entity a reveal may name
 * @param <V> the values of the entity
 */
public interface Revealing<F extends Enum<F> & RevealableField, V extends RevealedValues<F, V>> extends WindowedItem {

	/** The initial knowledge, as a request names it. */
	String INITIAL_KNOWLEDGE = "initialKnowledge";

	/** One reveal, as a request names it. */
	String REVEAL_FIELD = "reveals[%d]";

	/** The title of a reveal in a language, as a request names it. */
	String TITLE_FIELD = REVEAL_FIELD + ".titles[%s]";

	/** What the reader knows at the first appearance; nothing held when nothing is. */
	V initialKnowledge();

	/** In list order; empty for none. */
	List<Reveal<F, V>> reveals();

	/** Every field of the entity a reveal may name. */
	Set<F> revealableFields();

	/**
	 * Whether some reveal names the field: if none does, it is the current value
	 * throughout.
	 */
	default boolean revealed(F field) {
		return reveals().stream().anyMatch(reveal -> reveal.names(field));
	}

	@Override
	default List<NamedReveal> namedReveals() {
		return IntStream.range(0, reveals().size()).mapToObj(this::namedReveal).toList();
	}

	private NamedReveal namedReveal(int index) {
		Reveal<F, V> reveal = reveals().get(index);
		Set<String> names = reveal.fields().stream().map(RevealableField::fieldName).collect(Collectors.toSet());
		return new NamedReveal(REVEAL_FIELD.formatted(index), reveal.revealedAt(), names);
	}

	/**
	 * What submitting asks of the reveals (3.7.1): every title, and every translated
	 * value held, initial or learned, in every language.
	 */
	default List<String> missingReveals(Collection<String> languages) {
		List<String> missing = new ArrayList<>();
		revealableFields().stream()
			.filter(field -> initialKnowledge().holds(field))
			.flatMap(field -> initialKnowledge().missingTranslations(field, languages).stream())
			.map(field -> INITIAL_KNOWLEDGE + "." + field)
			.forEach(missing::add);
		for (int index = 0; index < reveals().size(); index++) {
			Reveal<F, V> reveal = reveals().get(index);
			String prefix = REVEAL_FIELD.formatted(index) + ".";
			for (String language : languages) {
				if (reveal.titleIn(language) == null) {
					missing.add(TITLE_FIELD.formatted(index, language));
				}
			}
			reveal.fields()
				.stream()
				.filter(field -> reveal.values().holds(field))
				.flatMap(field -> reveal.values().missingTranslations(field, languages).stream())
				.map(field -> prefix + field)
				.forEach(missing::add);
		}
		return missing;
	}

	/** Every language a title or a value of the reveals is written in. */
	default Set<String> revealLanguages() {
		Set<String> languages = new TreeSet<>(initialKnowledge().languages());
		reveals().forEach(reveal -> languages.addAll(reveal.languages()));
		return languages;
	}

	/**
	 * Every romaji of the initial knowledge and of the reveals, each named where a
	 * request names it: natural keys for the slug only (3.3).
	 */
	default List<NaturalKey> revealedNaturalKeys() {
		Stream<NaturalKey> initial = Stream.of(keyOf(INITIAL_KNOWLEDGE, initialKnowledge()));
		Stream<NaturalKey> learned = IntStream.range(0, reveals().size())
			.mapToObj(index -> keyOf(REVEAL_FIELD.formatted(index), reveals().get(index).values()));
		return Stream.concat(initial, learned).filter(key -> !key.isMissing()).toList();
	}

	private static NaturalKey keyOf(String prefix, RevealedValues<?, ?> values) {
		return new NaturalKey(prefix + ".romaji", values.romaji());
	}

	/** The initial knowledge and every reveal as they are stored and compared. */
	static <F extends Enum<F> & RevealableField, V extends RevealedValues<F, V>> List<Reveal<F, V>> normalized(
			List<Reveal<F, V>> reveals) {
		return reveals.stream().map(Reveal::normalized).toList();
	}

}
