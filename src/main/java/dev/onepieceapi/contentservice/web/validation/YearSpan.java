package dev.onepieceapi.contentservice.web.validation;

/** A request object holding a span of years (see {@link YearsInOrder}). */
public interface YearSpan {

	/** The property holding the last year, as a request names it. */
	String YEAR_TO = "yearTo";

	/** The first year; null when not written. */
	Integer yearFrom();

	/** The last year; null for a single year. */
	Integer yearTo();

}
