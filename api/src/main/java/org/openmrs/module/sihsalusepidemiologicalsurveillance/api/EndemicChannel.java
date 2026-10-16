package org.openmrs.module.sihsalusepidemiologicalsurveillance.api;

import java.util.*;

/** Linear interpolation (R type 7). Missing historical periods are not silently treated as zero. */
public class EndemicChannel {
	
	public static class Thresholds {
		
		public Double q1;
		
		public Double q2;
		
		public Double q3;
		
		public int sampleSize;
		
		public String zone;
	}
	
	public Thresholds evaluate(List<Integer> history, int current, int minimum) {
		Thresholds result = new Thresholds();
		result.sampleSize = history.size();
		if (history.size() < minimum) {
			result.zone = "INSUFFICIENT_HISTORY";
			return result;
		}
		result.q1 = quantile(history, 0.25);
		result.q2 = quantile(history, 0.5);
		result.q3 = quantile(history, 0.75);
		result.zone = current > result.q3 ? "EPIDEMIC"
		        : current >= result.q2 ? "ALERT" : current >= result.q1 ? "SAFETY" : "SUCCESS";
		// An all-zero baseline with zero current cases is not an alert.
		if (current == 0 && result.q3 == 0)
			result.zone = "SUCCESS";
		return result;
	}
	
	public double quantile(List<Integer> sample, double probability) {
		if (sample.isEmpty() || probability < 0 || probability > 1)
			throw new IllegalArgumentException();
		List<Integer> sorted = new ArrayList<Integer>(sample);
		Collections.sort(sorted);
		double index = (sorted.size() - 1) * probability;
		int lower = (int) Math.floor(index), upper = (int) Math.ceil(index);
		return sorted.get(lower) + (sorted.get(upper) - sorted.get(lower)) * (index - lower);
	}
}
