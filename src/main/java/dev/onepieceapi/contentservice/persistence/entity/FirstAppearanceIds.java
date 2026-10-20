package dev.onepieceapi.contentservice.persistence.entity;

import java.util.UUID;

/**
 * The first appearance as a version row writes it (ADR-0008): the content of its chapter
 * and of its episode.
 *
 * @param chapterId the content of the chapter; null for none
 * @param episodeId the content of the episode; null for none
 */
public record FirstAppearanceIds(UUID chapterId, UUID episodeId) {

}
