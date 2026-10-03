package dev.onepieceapi.contentservice.domain.devilfruittype;

import lombok.experimental.UtilityClass;

import java.util.Optional;
import java.util.function.Predicate;

/** How a text typed by an editor is read. */
@UtilityClass
class Text {

	/** The text without the space around it; null when nothing is left. */
	String stripToNull(String text) {
		return Optional.ofNullable(text).map(String::strip).filter(Predicate.not(String::isEmpty)).orElse(null);
	}

}
