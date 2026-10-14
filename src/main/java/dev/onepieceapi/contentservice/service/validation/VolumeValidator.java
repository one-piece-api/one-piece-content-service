package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.story.Volume;
import dev.onepieceapi.contentservice.persistence.entity.VolumeVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.StoryVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.repository.VolumeVersionRepository;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.exception.web.FieldViolation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The rules a volume must meet (implementation plan of the story structure, S2, S3, S13):
 * those of every content - see {@link ContentValidator}, here among the volumes only - so
 * its number is unique across every version of every other volume, gaps allowed; its
 * title may repeat. The days of release are checked by {@link ReleaseRules}.
 */
@Component
public class VolumeValidator extends ContentValidator<Volume, VolumeVersionEntity> {

	@Autowired
	public VolumeValidator(VolumeVersionRepository versionRepository, LanguageRepository languageRepository) {
		super(versionRepository, languageRepository, StoryVersionMapper::toDomain);
	}

	@Override
	protected void requireValidFields(Volume body) {
		List<FieldViolation> violations = ReleaseRules.violationsOf("releases", body.releases());
		if (!violations.isEmpty()) {
			throw new ValueInvalidException(violations);
		}
	}

}
