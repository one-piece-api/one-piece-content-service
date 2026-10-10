package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSubcategory;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitTypeVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.exception.web.FieldViolation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The rules a Devil Fruit Type must meet (docs/user-flows/content-editorial-workflow.md
 * 3.3): those of every content - see {@link ContentValidator} - and those of its
 * subcategories (3.1.1). A subcategory sent with an id must be one the draft already has:
 * the id is the server's to give (implementation plan of the subcategories, SC1), so a
 * client can neither invent one nor bring back one removed. Within the type, a
 * subcategory's name is unique per language.
 */
@Component
public class DevilFruitTypeValidator extends ContentValidator<DevilFruitType, DevilFruitTypeVersionEntity> {

	private static final String ID_FIELD = "subcategories[%d].id";

	private static final String NAME_FIELD = "subcategories[%d].translations[%s].name";

	private static final String UNKNOWN_ID = "must be the id of a subcategory of this draft";

	private static final String REPEATED_ID = "is given to another subcategory";

	private static final String REPEATED_NAME = "is the name of another subcategory of this type";

	@Autowired
	public DevilFruitTypeValidator(DevilFruitTypeVersionRepository versionRepository,
			LanguageRepository languageRepository) {
		super(versionRepository, languageRepository, DevilFruitTypeVersionMapper::toDomain);
	}

	/** Every subcategory at fault is reported, not just the first one found. */
	@Override
	protected void requireValidRevision(Optional<DevilFruitType> previous, DevilFruitType body) {
		Set<UUID> known = previous.map(DevilFruitType::subcategoryIds).orElseGet(Set::of);
		Set<UUID> seenIds = new HashSet<>();
		Set<String> seenNames = new HashSet<>();
		List<FieldViolation> violations = new ArrayList<>();
		List<DevilFruitTypeSubcategory> subcategories = body.subcategories();
		for (int index = 0; index < subcategories.size(); index++) {
			DevilFruitTypeSubcategory subcategory = subcategories.get(index);
			UUID id = subcategory.id();
			if (id != null && !known.contains(id)) {
				violations.add(new FieldViolation(ID_FIELD.formatted(index), UNKNOWN_ID));
			}
			else if (id != null && !seenIds.add(id)) {
				violations.add(new FieldViolation(ID_FIELD.formatted(index), REPEATED_ID));
			}
			int position = index;
			subcategory.names().forEach((language, name) -> {
				if (!seenNames.add(language + ':' + name.toLowerCase(Locale.ROOT))) {
					violations.add(new FieldViolation(NAME_FIELD.formatted(position, language), REPEATED_NAME));
				}
			});
		}
		if (!violations.isEmpty()) {
			throw new ValueInvalidException(violations);
		}
	}

}
