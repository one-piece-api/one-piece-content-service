package dev.onepieceapi.contentservice.domain.readertime;

import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * How the value of one field is read at a point of the story (implementation plan of the
 * timeline, T13): a field no reveal names is the truth throughout; otherwise it is what
 * the last reveal reached names, else what was known at the first appearance - unknown
 * when nothing was. Written once for any {@link Reveal}; what a reveal holds for a field
 * is the entity's to say.
 */
@UtilityClass
public class Reveals {

	/**
	 * The reveals a reader at this chapter has reached: those revealed at it or before. A
	 * reveal whose chapter has no known number counts as reached, so the truth is never
	 * held back for want of a number.
	 * @param chapterNumbers the number of a chapter by its content; null when unknown
	 */
	public Predicate<Reveal> reachedAt(int chapterNumber, Function<UUID, Integer> chapterNumbers) {
		return reveal -> {
			UUID chapter = reveal.revealedAtChapterId();
			Integer revealedAt = chapter == null ? null : chapterNumbers.apply(chapter);
			return revealedAt == null || revealedAt <= chapterNumber;
		};
	}

	/** Whether some reveal names the field: if none does, it is the truth throughout. */
	public <P extends Reveal> boolean named(List<P> reveals, Predicate<? super P> names) {
		return reveals.stream().anyMatch(names);
	}

	/**
	 * The last reveal reached that names the field; empty while none is, when the field
	 * reads as known at the first appearance.
	 */
	public <P extends Reveal> Optional<P> lastReached(List<P> reveals, Predicate<? super P> reached,
			Predicate<? super P> names) {
		P last = null;
		for (P reveal : reveals) {
			if (names.test(reveal) && reached.test(reveal)) {
				last = reveal;
			}
		}
		return Optional.ofNullable(last);
	}

}
