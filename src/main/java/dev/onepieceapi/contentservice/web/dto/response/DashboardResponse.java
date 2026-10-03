package dev.onepieceapi.contentservice.web.dto.response;

import java.util.List;

/**
 * The dashboard's counters: one per status the caller sees, in workflow order, zero
 * included.
 */
public record DashboardResponse(List<StatusCountResponse> statuses) {

}
