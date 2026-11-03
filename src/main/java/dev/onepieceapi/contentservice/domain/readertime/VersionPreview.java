package dev.onepieceapi.contentservice.domain.readertime;

import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;

/**
 * A version previewed at N: the preview, and the version saying the resolved body, so the
 * body is answered in the section's own shape by the same mapping as a version.
 *
 * @param resolved the version with the resolved body, and no action allowed; its body is
 * null when the item is not visible
 */
public record VersionPreview<T>(Preview<T> preview, VersionAccess<T> resolved) {

}
