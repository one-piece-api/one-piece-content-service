package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.persistence.entity.VersionBodyEntity;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.ToLongFunction;

/**
 * How the online items of one relation pointing to a content are found - queries of the
 * entity holding the relation, e.g. the fruits online with a type.
 *
 * @param countOnline how many items are online pointing to the content
 * @param findOnline the items online pointing to it, by natural key: as many as the page
 * asks for
 */
public record LinkedItems(ToLongFunction<UUID> countOnline,
		BiFunction<UUID, Pageable, List<? extends VersionBodyEntity>> findOnline) {

}
