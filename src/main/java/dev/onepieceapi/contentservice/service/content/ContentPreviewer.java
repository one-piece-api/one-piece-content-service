package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.domain.readertime.Preview;
import dev.onepieceapi.readertime.ReaderPosition;

import java.util.UUID;

/**
 * How a version of one entity is seen by a reader at chapter or episode N
 * (docs/user-flows/content-editorial-workflow.md 3.8, UF-CNT-23; reveals plan R6): one
 * per entity on the reader axis, run by {@link ContentService#preview} on a version the
 * caller sees. Volumes, chapters and episodes have none: they are the axis.
 *
 * @param <T> what a version of the entity says
 */
public interface ContentPreviewer<T> {

	Preview<T> previewOf(UUID contentId, T body, ReaderPosition position);

}
