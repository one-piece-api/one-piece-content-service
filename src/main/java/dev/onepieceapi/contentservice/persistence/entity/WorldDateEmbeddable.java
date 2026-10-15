package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A year, or a span of years, of the world calendar (implementation plan of the timeline,
 * T4) - a value owned by an event version, once for when it occurs and once for when it
 * ends ({@link EventVersionEntity}). All columns null reads as none.
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WorldDateEmbeddable {

	private Integer yearFrom;

	private Integer yearTo;

	private Boolean approximate;

}
