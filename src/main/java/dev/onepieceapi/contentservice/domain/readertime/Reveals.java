package dev.onepieceapi.contentservice.domain.readertime;

import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * How the value of one field is read at a point of the story (implementation plan of the
 * timeline, T9): from the first reveal still believed there that defines it; otherwise
 * the truth. A field changing twice works, since each reveal holds the belief before its
 * own reveal. Written once for any {@link Reveal}; which fields go together is the
 * entity's to say.
 */
@UtilityClass
public class Reveals {

	/**
	 * The reveals still believed by a reader at this chapter: those revealed at a later
	 * one. A reveal whose chapter has no known number is never believed.
	 * @param chapterNumbers the number of a chapter by its content; null when unknown
	 */
	public Predicate<Reveal> believedAt(int chapterNumber, Function<UUID, Integer> chapterNumbers) {
		return reveal -> {
			UUID chapter = reveal.revealedAtChapterId();
			Integer revealedAt = chapter == null ? null : chapterNumbers.apply(chapter);
			return revealedAt != null && revealedAt > chapterNumber;
		};
	}

	/**
	 * The value of a field, as believed: a null value is a field the reveal does not
	 * define.
	 */
	public <P extends Reveal, V> V resolve(List<P> reveals, Predicate<? super P> believed, Function<? super P, V> field,
			V truth) {
		return resolve(reveals, believed, reveal -> field.apply(reveal) != null, field, truth);
	}

	/**
	 * The same, for a field a reveal may define as null - e.g. no subcategory, believed
	 * together with a type.
	 * @param defines whether the reveal defines the field
	 */
	public <P extends Reveal, V> V resolve(List<P> reveals, Predicate<? super P> believed, Predicate<? super P> defines,
			Function<? super P, V> field, V truth) {
		for (P reveal : reveals) {
			if (believed.test(reveal) && defines.test(reveal)) {
				return field.apply(reveal);
			}
		}
		return truth;
	}

}
