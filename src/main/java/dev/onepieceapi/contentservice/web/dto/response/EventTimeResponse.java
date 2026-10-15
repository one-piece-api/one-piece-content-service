package dev.onepieceapi.contentservice.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * When an event happens or ends (implementation plan of the timeline, T3): exactly one of
 * the two is present - a chapter, as it is today, or a world date.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventTimeResponse(NumberedReferenceResponse chapter, WorldDateResponse worldDate) {

}
