package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.readertime.Reveal;
import dev.onepieceapi.contentservice.domain.readertime.RevealableField;
import dev.onepieceapi.contentservice.domain.readertime.RevealedValues;
import dev.onepieceapi.contentservice.web.dto.request.RevealRequest;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * From the reveals of a request to the domain (workflow 3.7.1), whatever their entity:
 * the entity gives how its values read. A language sent with nothing in it is a language
 * with nothing written.
 */
@UtilityClass
public class RevealRequestMapper {

	/** In request order; left out is none. */
	public <R, T> List<T> each(List<R> requests, Function<R, T> mapper) {
		return Optional.ofNullable(requests).orElseGet(List::of).stream().map(mapper).toList();
	}

	/**
	 * One reveal, with the values it says.
	 * @param values as the entity reads them from the request
	 */
	public <F extends Enum<F> & RevealableField, V extends RevealedValues<F, V>> Reveal<F, V> toDomain(
			RevealRequest<F> request, V values) {
		return new Reveal<>(FirstAppearanceMapper.toDomain(request.revealedAt()), request.fields(), values,
				names(request.titles(), Function.identity()));
	}

	/** The name of each translation that has one, by language code. */
	public <T> Map<String, String> names(Map<String, T> translations, Function<T, String> name) {
		Map<String, String> names = new TreeMap<>();
		Optional.ofNullable(translations)
			.orElseGet(Map::of)
			.forEach((language, translation) -> Optional.ofNullable(translation)
				.map(name)
				.ifPresent(value -> names.put(language, value)));
		return names;
	}

}
