package dev.onepieceapi.contentservice.domain.workflow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One encyclopedia entry and its linear chain of versions
 * (docs/user-flows/content-editorial-workflow.md 4.1), as one caller sees it: the chain
 * holds only the versions visible to them, oldest first, each with what they may do with
 * it.
 *
 * @param <T> what a version of this kind of content says, e.g. a {@code DevilFruitType}
 */
public record Content<T>(UUID id, List<VersionAccess<T>> versions) {

	/** The number of the version currently online, if any and if the caller sees it. */
	public Optional<Integer> onlineVersionNumber() {
		return this.versions.stream()
			.map(VersionAccess::version)
			.filter(Version::isOnline)
			.map(Version::number)
			.findFirst();
	}

}
