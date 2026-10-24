package org.openmrs.module.sihsalusepidemiologicalsurveillance.api;

import java.time.LocalDate;
import java.util.*;

import org.openmrs.User;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.SurveillanceConstants;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.ClinicalCatalog;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.OutbreakRule;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.*;

/**
 * Event-time rules. Immutable snapshots prevent Hibernate mutations from changing the before image.
 */
public class AlertRules {
	
	public static final List<String> PRIVILEGES = Arrays.asList(SurveillanceConstants.ACCESS, SurveillanceConstants.VIEW,
	    SurveillanceConstants.REGISTER, SurveillanceConstants.REPORT, SurveillanceConstants.MANAGE);
	
	public static final List<String> REASONS = Arrays.asList("IMMEDIATE_EVENT", "STATUS_CHANGED", "DEATH",
	    "CONFIRMED_PREGNANCY", "EPIDEMIC_THRESHOLD", "SUSTAINED_INCREASE", "AUTOCHTHONOUS_ELIMINATED", "FOCUS_CASES");
	
	public static boolean isResponsible(User user) {
		return user != null && PRIVILEGES.stream().allMatch(user::hasPrivilege);
	}
	
	public static class Snapshot {
		
		public String uuid, status, origin;
		
		public Integer center;
		
		public LocalDate onset;
		
		public boolean pregnant, death, notified;
		
		public static Snapshot of(SurveillanceCase value, EpidemiologicalCalendar calendar) {
			Snapshot s = new Snapshot();
			s.uuid = value.getUuid();
			s.notified = value.getNotificationDate() != null;
			s.status = value.getDiagnosisType();
			s.origin = value.getOrigin();
			s.center = value.getInfectionAddress();
			s.onset = value.getOnsetDate() == null ? null : calendar.local(value.getOnsetDate());
			s.pregnant = Boolean.TRUE.equals(value.getPregnant()) && "CONFIRMADO".equals(s.status);
			s.death = value.getPatient() != null && value.getPatient().getDeathDate() != null
			        && "SI".equals(value.getDeathAttribution());
			return s;
		}
	}
	
	public static class Trigger {
		
		public String reason, key, information;
		
		public Trigger(String reason, String key, String information) {
			this.reason = reason;
			this.key = key;
			this.information = information;
		}
	}
	
	public List<Trigger> evaluate(Snapshot current, Snapshot previous, List<Snapshot> before, List<Snapshot> after,
	        String periodicity, ClinicalCatalog metadata) {
		return evaluate(current, previous, before, after, periodicity, metadata, OutbreakRuleSettings.defaults());
	}

	public List<Trigger> evaluate(Snapshot current, Snapshot previous, List<Snapshot> before, List<Snapshot> after,
	        String periodicity, ClinicalCatalog metadata, List<OutbreakRule> rules) {
		List<Trigger> result = new ArrayList<>();
		if (previous == null && !current.notified && "INMEDIATA".equalsIgnoreCase(periodicity))
			result.add(new Trigger("IMMEDIATE_EVENT", current.uuid, null));
		if (previous != null && !Objects.equals(previous.status, current.status))
			result.add(new Trigger("STATUS_CHANGED", current.uuid, previous.status + " → " + current.status));
		if (current.death && (previous == null || !previous.death))
			result.add(new Trigger("DEATH", current.uuid, null));
		if (current.pregnant && (previous == null || !previous.pregnant))
			result.add(new Trigger("CONFIRMED_PREGNANCY", current.uuid, null));
		if (!included(current))
			return result;
		EpidemiologicalCalendar calendar = new EpidemiologicalCalendar(metadata);
		LocalDate week = calendar.start(current.onset, "semana");
		for (OutbreakRule rule : rules) {
			if ("FOCUS_CASES".equals(rule.type)) {
				evaluateFocus(rule, current, before, after, calendar, result);
				continue;
			}
			// A late entry can complete any sequence containing the changed week.
			for (int offset = 0; offset < rule.weeks; offset++) {
				LocalDate end = week.plusWeeks(offset);
				boolean crossed = true, alreadyAbove = true;
				StringBuilder info = new StringBuilder("Percentil: " + rule.percentile + "; semanas: " + rule.weeks);
				for (int n = 0; n < rule.weeks; n++) {
					LocalDate date = end.minusWeeks(n);
					Double threshold = percentile(after, current.center, date, metadata, calendar, rule.percentile);
					int count = count(after, current.center, date, calendar);
					int oldCount = count(before, current.center, date, calendar);
					crossed &= threshold != null && count > threshold;
					alreadyAbove &= threshold != null && oldCount > threshold;
					info.append("; semana ").append(date).append(": ").append(count).append(" / ").append(threshold);
				}
				if (crossed && !alreadyAbove) {
					String reason = "THRESHOLD".equals(rule.type) ? "EPIDEMIC_THRESHOLD" : "SUSTAINED_INCREASE";
					result.add(new Trigger(reason, occurrence(rule, current.center + ":" + end), info.toString()));
				}
			}
		}
		return result;
	}
	
	private String occurrence(OutbreakRule rule, String key) {
		return (rule.id.equals("default-" + rule.type) ? "" : rule.id + ":") + key;
	}

	private boolean included(Snapshot s) {
		return s.center != null && s.onset != null && ("CONFIRMADO".equals(s.status) || "PROBABLE".equals(s.status));
	}
	
	private void evaluateFocus(OutbreakRule rule, Snapshot current, List<Snapshot> before, List<Snapshot> after,
	        EpidemiologicalCalendar calendar, List<Trigger> result) {
		LocalDate start = calendar.start(current.onset, "semana");
		long count = focusCount(after, current.center, start, rule.origin, calendar);
		long oldCount = focusCount(before, current.center, start, rule.origin, calendar);
		if (count <= rule.minimumCases || oldCount > rule.minimumCases)
			return;
		Integer lastLocal = null;
		boolean unknown = false;
		for (Snapshot s : before) {
			if (!included(s) || !Objects.equals(current.center, s.center) || !s.onset.isBefore(start))
				continue;
			if ("AUTOCTONO".equals(s.origin))
				lastLocal = lastLocal == null ? s.onset.getYear() : Math.max(lastLocal, s.onset.getYear());
			if (s.onset.getYear() >= start.getYear() - 3
			        && (!GeographicCalculator.ORIGINS.contains(s.origin) || "UNKNOWN".equals(s.origin)))
				unknown = true;
		}
		String focus = lastLocal != null && lastLocal == start.getYear() ? "ACTIVE"
		        : lastLocal != null && lastLocal >= start.getYear() - 3 ? "RESIDUAL"
		                : unknown ? "UNCLASSIFIED" : "ELIMINATED";
		if (!rule.focus.equals(focus))
			return;
		boolean legacy = "default-FOCUS_CASES".equals(rule.id) && "AUTOCTONO".equals(rule.origin)
		        && "ELIMINATED".equals(rule.focus);
		String key = current.center + ":" + (legacy ? String.valueOf(current.onset.getYear()) : start.toString());
		result.add(new Trigger(legacy ? "AUTOCHTHONOUS_ELIMINATED" : "FOCUS_CASES", occurrence(rule, key),
		        "Semana desde " + start + "; origen: " + rule.origin + "; foco previo: " + rule.focus + "; casos: " + count
		                + "; superar: " + rule.minimumCases));
	}

	private long focusCount(List<Snapshot> records, Integer center, LocalDate week, String origin,
	        EpidemiologicalCalendar calendar) {
		return records.stream().filter(s -> included(s) && Objects.equals(center, s.center) && origin.equals(s.origin)
		        && calendar.start(s.onset, "semana").equals(week)).map(s -> s.uuid).distinct().count();
	}
	
	private int count(List<Snapshot> records, Integer center, LocalDate week, EpidemiologicalCalendar calendar) {
		return (int) records.stream()
		        .filter(
		            s -> included(s) && Objects.equals(center, s.center) && calendar.start(s.onset, "semana").equals(week))
		        .map(s -> s.uuid).distinct().count();
	}
	
	private Double percentile(List<Snapshot> records, Integer center, LocalDate week, ClinicalCatalog metadata,
	        EpidemiologicalCalendar calendar, double percentile) {
		Map<Integer, Integer> years = new TreeMap<>();
		int year = calendar.year(week, "semana"), number = calendar.number(week, "semana");
		for (Snapshot s : records) {
			if (!included(s) || !Objects.equals(center, s.center))
				continue;
			int sampleYear = calendar.year(s.onset, "semana");
			if (sampleYear < year && sampleYear >= year - metadata.historicalYears
			        && calendar.number(s.onset, "semana") == number)
				years.merge(sampleYear, 1, Integer::sum);
		}
		return years.size() < metadata.minimumHistoricalYears ? null
		        : new EndemicChannel().quantile(new ArrayList<>(years.values()), percentile / 100.0);
	}
}
