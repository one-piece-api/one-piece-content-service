package dev.onepieceapi.contentservice.web.dto.response;

import java.util.List;

/**
 * What takes part in an event (implementation plan of the timeline, T5).
 *
 * @param devilFruits the Devil Fruits, as they are today, in the order of their ids
 */
public record EventParticipantsResponse(List<DevilFruitReferenceResponse> devilFruits) {

}
