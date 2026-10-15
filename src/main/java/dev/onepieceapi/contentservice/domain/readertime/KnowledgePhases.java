package dev.onepieceapi.contentservice.domain.readertime;

import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * How the value of one field is read at a point of the story (implementation plan of the
 * timeline, T9): from the first phase still believed there that defines it; otherwise the
 * truth. A field changing twice works, since each phase holds the belief before its own
 * reveal. Written once for any {@link KnowledgePhase}; which fields go together is the
 * entity's to say.
 */
@UtilityClass
public class KnowledgePhases {

	/**
	 * The phases still believed by a reader at this chapter: those superseded at a later
	 * one. A phase whose chapter has no known number is never believed.
	 * @param chapterNumbers the number of a chapter by its content; null when unknown
	 */
	public Predicate<KnowledgePhase> believedAt(int chapterNumber, Function<UUID, Integer> chapterNumbers) {
		return phase -> {
			UUID chapter = phase.supersededAtChapterId();
			Integer supersededAt = chapter == null ? null : chapterNumbers.apply(chapter);
			return supersededAt != null && supersededAt > chapterNumber;
		};
	}

	/**
	 * The value of a field, as believed: a null value is a field the phase does not
	 * define.
	 */
	public <P extends KnowledgePhase, V> V resolve(List<P> phases, Predicate<? super P> believed,
			Function<? super P, V> field, V truth) {
		return resolve(phases, believed, phase -> field.apply(phase) != null, field, truth);
	}

	/**
	 * The same, for a field a phase may define as null - e.g. no subcategory, believed
	 * together with a type.
	 * @param defines whether the phase defines the field
	 */
	public <P extends KnowledgePhase, V> V resolve(List<P> phases, Predicate<? super P> believed,
			Predicate<? super P> defines, Function<? super P, V> field, V truth) {
		for (P phase : phases) {
			if (believed.test(phase) && defines.test(phase)) {
				return field.apply(phase);
			}
		}
		return truth;
	}

}
