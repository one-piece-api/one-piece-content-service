package dev.onepieceapi.contentservice.domain.workflow;

import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

/** How a text typed by an editor is read. */
@UtilityClass
public class Text {

	/** The text without the space around it; null when nothing is left. */
	public String stripToNull(String text) {
		return Optional.ofNullable(text).map(String::strip).filter(Predicate.not(String::isEmpty)).orElse(null);
	}

	/**
	 * A text per language code, each without the space around it; a language left blank
	 * is not written.
	 */
	public Map<String, String> written(Map<String, String> byLanguage) {
		Map<String, String> written = new TreeMap<>();
		byLanguage.forEach((language, value) -> {
			String normalized = stripToNull(value);
			if (normalized != null) {
				written.put(language, normalized);
			}
		});
		return written;
	}

}
