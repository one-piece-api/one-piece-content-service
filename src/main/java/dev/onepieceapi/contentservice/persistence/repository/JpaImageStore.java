package dev.onepieceapi.contentservice.persistence.repository;

import dev.onepieceapi.contentservice.domain.image.ImageStore;
import dev.onepieceapi.contentservice.domain.image.NormalizedImage;
import dev.onepieceapi.contentservice.persistence.mapper.ImageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.Optional;

/**
 * {@link ImageStore} on the {@code image} table.
 */
@Repository
@RequiredArgsConstructor
public class JpaImageStore implements ImageStore {

	private final ImageRepository repository;

	private final Clock clock;

	@Override
	public void save(NormalizedImage image) {
		if (!this.repository.existsById(image.id())) {
			this.repository.save(ImageMapper.toEntity(image, this.clock.instant()));
		}
	}

	@Override
	public Optional<NormalizedImage> find(String id) {
		return this.repository.findById(id).map(ImageMapper::toDomain);
	}

	@Override
	public void deleteIfUnreferenced(String id) {
		this.repository.deleteIfUnreferenced(id);
	}

}
