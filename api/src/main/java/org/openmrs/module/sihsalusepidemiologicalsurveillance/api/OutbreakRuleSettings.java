package org.openmrs.module.sihsalusepidemiologicalsurveillance.api;

import java.util.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.OutbreakRule;

public class OutbreakRuleSettings {

	public static List<OutbreakRule> defaults() {
		List<OutbreakRule> result = new ArrayList<>();
		for (String type : Arrays.asList("THRESHOLD", "CONSECUTIVE", "FOCUS_CASES")) {
			OutbreakRule rule = new OutbreakRule();
			rule.id = "default-" + type;
			rule.type = type;
			if ("FOCUS_CASES".equals(type)) {
				rule.minimumCases = 0;
				rule.origin = "AUTOCTONO";
				rule.focus = "ELIMINATED";
			} else {
				rule.percentile = 75.0;
				rule.weeks = "CONSECUTIVE".equals(type) ? 2 : 1;
			}
			result.add(rule);
		}
		return result;
	}

	public static List<OutbreakRule> read(String stored) {
		if (stored == null)
			return defaults();
		try {
			List<OutbreakRule> result = new ObjectMapper().readValue(stored, new TypeReference<List<OutbreakRule>>() {});
			validate(result);
			return result;
		}
		catch (Exception ex) {
			throw new SurveillanceException(422, "INVALID_OUTBREAK_RULES");
		}
	}

	public static String validate(List<OutbreakRule> rules) {
		if (rules == null || rules.isEmpty())
			invalid();
		Set<String> ids = new HashSet<>();
		for (OutbreakRule r : rules) {
			if (r == null || r.id == null || !r.id.matches("[A-Za-z0-9_-]{1,64}") || !ids.add(r.id))
				invalid();
			if ("FOCUS_CASES".equals(r.type)) {
				if (r.minimumCases == null || r.minimumCases < 0 || r.minimumCases == Integer.MAX_VALUE
				        || !GeographicCalculator.ORIGINS.contains(r.origin) || "UNKNOWN".equals(r.origin)
				        || !Arrays.asList("ACTIVE", "RESIDUAL", "ELIMINATED").contains(r.focus) || r.weeks != null
				        || r.percentile != null)
					invalid();
			} else if ("THRESHOLD".equals(r.type) || "CONSECUTIVE".equals(r.type)) {
				if (r.percentile == null || !Double.isFinite(r.percentile) || r.percentile < 0 || r.percentile > 100
				        || r.weeks == null || r.weeks < 1 || r.weeks > 53 || ("THRESHOLD".equals(r.type) && r.weeks != 1)
				        || r.minimumCases != null || r.origin != null || r.focus != null)
					invalid();
			} else
				invalid();
		}
		try {
			return new ObjectMapper().writeValueAsString(rules);
		}
		catch (Exception ex) {
			throw new SurveillanceException(422, "INVALID_OUTBREAK_RULES");
		}
	}

	private static void invalid() {
		throw new SurveillanceException(422, "INVALID_OUTBREAK_RULES");
	}
}
