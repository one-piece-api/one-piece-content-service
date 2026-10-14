package dev.onepieceapi.contentservice.web.dto.response;

import java.time.LocalDate;

/**
 * The day something came out in one country.
 *
 * @param country ISO 3166-1 alpha-2 code
 * @param date the calendar day in that country
 */
public record ReleaseResponse(String country, LocalDate date) {

}
