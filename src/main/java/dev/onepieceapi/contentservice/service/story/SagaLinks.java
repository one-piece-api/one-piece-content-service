package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import dev.onepieceapi.contentservice.domain.story.Saga;
import dev.onepieceapi.contentservice.persistence.entity.SagaVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.StoryVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.SagaVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** The sagas as the arcs point to them, and those an arc may be linked to. */
@Service
public class SagaLinks extends NumberedLinks<Saga, SagaVersionEntity> {

	@Autowired
	public SagaLinks(SagaVersionRepository repository, Clock clock) {
		super(repository, StoryVersionMapper::toDomain, clock);
	}

	/** The sagas the given arcs point to, as {@link #referencesOf}. */
	public Map<UUID, NumberedReference> referencesFor(Collection<Arc> arcs) {
		return referencesOf(arcs.stream().map(Arc::sagaContentId).filter(Objects::nonNull).collect(Collectors.toSet()));
	}

}
