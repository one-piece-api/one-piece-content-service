package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.util.UUID;

/**
 * A window on the reader axis as a list row writes it (workflow 3.7.2; V38): the content
 * of the chapter and of the episode of each of its points, every one optional. The same
 * four columns on every table that holds windows. Read back as null when all four are.
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
public class StoryWindowEmbeddable {

	private UUID discoveredChapterId;

	private UUID discoveredEpisodeId;

	private UUID endedChapterId;

	private UUID endedEpisodeId;

}
