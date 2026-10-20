package dev.onepieceapi.contentservice.service.image;

import dev.onepieceapi.contentservice.domain.image.ImageStore;
import dev.onepieceapi.contentservice.domain.image.NormalizedImage;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.domain.workflow.VisibilityPolicy;
import dev.onepieceapi.contentservice.persistence.repository.AffiliationVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.BountyVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.VolumeVersionRepository;
import dev.onepieceapi.contentservice.service.exception.ImageNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Reading an image (implementation plan of the Devil Fruit, D5): only for a caller who
 * sees at least one version using it, by the same {@link VisibilityPolicy} as the
 * versions themselves - a reviewer cannot see a draft's image, as they cannot see its
 * text. Every entity whose versions have images is asked here: a new one adds its own.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ImageService {

	private final ImageStore store;

	private final DevilFruitVersionRepository devilFruitRepository;

	private final VolumeVersionRepository volumeRepository;

	private final BountyVersionRepository bountyRepository;

	private final AffiliationVersionRepository affiliationRepository;

	public NormalizedImage get(Set<Permission> permissions, String id) {
		Set<VersionStatus> statuses = VisibilityPolicy.visibleStatuses(permissions);
		if (statuses.isEmpty() || !usedBy(id, statuses)) {
			throw new ImageNotFoundException(id);
		}
		return this.store.find(id).orElseThrow(() -> new ImageNotFoundException(id));
	}

	private boolean usedBy(String id, Set<VersionStatus> statuses) {
		return this.devilFruitRepository.usesImage(id, statuses) || this.volumeRepository.usesImage(id, statuses)
				|| this.bountyRepository.usesImage(id, statuses) || this.affiliationRepository.usesImage(id, statuses);
	}

}
