package org.openmrs.module.sihsalusepidemiologicalsurveillance.api;

import java.time.LocalDate;
import java.util.*;

import org.openmrs.User;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.SurveillanceConstants;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.ClinicalCatalog;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.*;

/**
 * Event-time rules. Immutable snapshots prevent Hibernate mutations from changing the before image.
 */
public class AlertRules {
	
	public static final List<String> PRIVILEGES = Arrays.asList(SurveillanceConstants.ACCESS, SurveillanceConstants.VIEW,
	    SurveillanceConstants.REGISTER, SurveillanceConstants.REPORT, SurveillanceConstants.MANAGE);
	
	public static final List<String> REASONS = Arrays.asList("IMMEDIATE_EVENT", "STATUS_CHANGED", "DEATH",
	    "CONFIRMED_PREGNANCY", "EPIDEMIC_THRESHOLD", "SUSTAINED_INCREASE", "AUTOCHTHONOUS_ELIMINATED");
	
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
		boolean appeared = previous == null || !included(previous) || !"AUTOCTONO".equals(previous.origin)
		        || !Objects.equals(previous.center, current.center);
		if (appeared && "AUTOCTONO".equals(current.origin) && eliminated(before, current))
			result.add(new Trigger("AUTOCHTHONOUS_ELIMINATED", current.center + ":" + current.onset.getYear(), null));
		LocalDate week = calendar.start(current.onset, "semana");
		// Re-evaluate the following week too: late registration can complete a two-week sequence.
		for (LocalDate date : Arrays.asList(week, week.plusWeeks(1))) {
			int count = count(after, current.center, date, calendar);
			int oldCount = count(before, current.center, date, calendar);
			Double q3 = q3(after, current.center, date, metadata, calendar);
			if (q3 == null)
				continue;
			String key = current.center + ":" + date;
			String info = "Semana desde " + date + "; casos: " + count + "; Q3: " + q3;
			if (count > q3 && oldCount <= q3)
				result.add(new Trigger("EPIDEMIC_THRESHOLD", key, info));
			LocalDate priorWeek = date.minusWeeks(1);
			Double priorQ3 = q3(after, current.center, priorWeek, metadata, calendar);
			int prior = count(after, current.center, priorWeek, calendar);
			int oldPrior = count(before, current.center, priorWeek, calendar);
			if (priorQ3 != null && count > q3 && prior > priorQ3 && !(oldCount > q3 && oldPrior > priorQ3))
				result.add(new Trigger("SUSTAINED_INCREASE", key, info + "; semana anterior desde " + priorWeek
				        + "; casos anteriores: " + prior + "; Q3 anterior: " + priorQ3));
		}
		return result;
	}
	
	private boolean included(Snapshot s) {
		return s.center != null && s.onset != null && ("CONFIRMADO".equals(s.status) || "PROBABLE".equals(s.status));
	}
	
	private boolean eliminated(List<Snapshot> before, Snapshot current) {
		for (Snapshot s : before)
			if (included(s) && Objects.equals(s.center, current.center) && s.onset.getYear() >= current.onset.getYear() - 3
			        && !s.onset.isAfter(current.onset) && ("AUTOCTONO".equals(s.origin) || s.origin == null))
				return false;
		return true;
	}
	
	private int count(List<Snapshot> records, Integer center, LocalDate week, EpidemiologicalCalendar calendar) {
		return (int) records.stream()
		        .filter(
		            s -> included(s) && Objects.equals(center, s.center) && calendar.start(s.onset, "semana").equals(week))
		        .map(s -> s.uuid).distinct().count();
	}
	
	private Double q3(List<Snapshot> records, Integer center, LocalDate week, ClinicalCatalog metadata,
	        EpidemiologicalCalendar calendar) {
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
		return new EndemicChannel().evaluate(new ArrayList<>(years.values()), 0, metadata.minimumHistoricalYears).q3;
	}
}
