package org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model;

/** Shared outbreak rule. Identifiers remain stable when parameters change. */
public class OutbreakRule {
	
	public String id;
	
	public String type;
	
	public Integer weeks;
	
	public Double percentile;
	
	public Integer minimumCases;
	
	public String origin;
	
	public String focus;
	
	// Reject fractional JSON input rather than silently coercing it to an integer.
	public void setWeeks(Number value) {
		weeks = integer(value);
	}
	
	public void setMinimumCases(Number value) {
		minimumCases = integer(value);
	}
	
	private Integer integer(Number value) {
		if (value == null)
			return null;
		double number = value.doubleValue();
		if (!Double.isFinite(number) || number != Math.rint(number) || number < Integer.MIN_VALUE
		        || number > Integer.MAX_VALUE)
			throw new IllegalArgumentException("INVALID_OUTBREAK_RULES");
		return value.intValue();
	}
}
