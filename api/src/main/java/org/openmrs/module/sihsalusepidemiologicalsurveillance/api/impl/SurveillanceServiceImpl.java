package org.openmrs.module.sihsalusepidemiologicalsurveillance.api.impl;

import java.time.*;
import java.util.*;

import org.openmrs.*;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.SurveillanceConstants;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.db.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.*;

public class SurveillanceServiceImpl extends BaseOpenmrsService implements SurveillanceService {
	
	private List<OutbreakRule> configuredOutbreakRules() {
		OutbreakRuleConfiguration config = dao.byUuid(OutbreakRuleConfiguration.class, OutbreakRuleConfiguration.UUID);
		return OutbreakRuleSettings.read(config == null ? null : config.getRules());
	}
	
	@Override
	public List<OutbreakRule> getOutbreakRules() {
		access.require(SurveillanceConstants.MANAGE);
		return configuredOutbreakRules();
	}
	
	@Override
	public List<OutbreakRule> saveOutbreakRules(List<OutbreakRule> rules) {
		User actor = access.require(SurveillanceConstants.MANAGE);
		String stored = OutbreakRuleSettings.validate(rules);
		dao.lockAlerts();
		OutbreakRuleConfiguration config = dao.byUuid(OutbreakRuleConfiguration.class, OutbreakRuleConfiguration.UUID);
		if (config == null) {
			config = new OutbreakRuleConfiguration();
			config.setUuid(OutbreakRuleConfiguration.UUID);
			config.setCreator(actor);
			config.setDateCreated(new Date());
		} else {
			config.setChangedBy(actor);
			config.setDateChanged(new Date());
		}
		config.setRules(stored);
		dao.save(config);
		return OutbreakRuleSettings.read(stored);
	}
	
	private ClinicalCatalog alertMetadata() {
		ClinicalCatalog metadata = catalog.get();
		int[] settings = dao.endemicSettings();
		metadata.historicalYears = settings == null ? 5 : settings[0];
		metadata.minimumHistoricalYears = settings == null ? 3 : settings[1];
		return metadata;
	}
	
	private boolean alertEventMatches(NotifiableEvent event, SurveillanceCase value) {
		if (value.getDiagnosis() == null || value.getDiagnosis().getDiagnosis() == null)
			return false;
		return covers(event, value.getDiagnosis().getDiagnosis().getCoded());
	}
	
	/**
	 * Each notifiable event is the diagnosis configured by epidemiology, or a concept set of diagnoses.
	 */
	private static boolean covers(NotifiableEvent event, Concept concept) {
		return concept != null && event.getConcept() != null
		        && (event.getConcept().equals(concept) || event.getConcept().getSetMembers().contains(concept));
	}
	
	/**
	 * Diagnosis concept UUID to notifiable event UUID; a direct event concept wins over set membership.
	 */
	private Map<String, String> eventsByDiagnosis() {
		Map<String, String> result = new HashMap<String, String>();
		for (NotifiableEvent event : dao.events())
			if (event.getConcept() != null)
				for (Concept member : event.getConcept().getSetMembers())
					result.put(member.getUuid(), event.getUuid());
		for (NotifiableEvent event : dao.events())
			if (event.getConcept() != null)
				result.put(event.getConcept().getUuid(), event.getUuid());
		return result;
	}
	
	private Map<String, List<AlertRules.Snapshot>> alertSnapshots(ClinicalCatalog metadata) {
		Map<String, List<AlertRules.Snapshot>> result = new LinkedHashMap<>();
		List<SurveillanceCase> cases = dao.surveillanceCases();
		EpidemiologicalCalendar calendar = new EpidemiologicalCalendar(metadata);
		for (NotifiableEvent event : dao.events()) {
			List<AlertRules.Snapshot> snapshots = new ArrayList<>();
			for (SurveillanceCase value : cases)
				if (alertEventMatches(event, value))
					snapshots.add(AlertRules.Snapshot.of(value, calendar));
			result.put(event.getUuid(), snapshots);
		}
		return result;
	}
	
	private List<Map<String, Object>> generateAlerts(SurveillanceCase value, AlertRules.Snapshot previous,
	        Map<String, List<AlertRules.Snapshot>> before, ClinicalCatalog metadata) {
		List<Map<String, Object>> generated = new ArrayList<>();
		Map<String, List<AlertRules.Snapshot>> after = alertSnapshots(metadata);
		AlertRules.Snapshot current = AlertRules.Snapshot.of(value, new EpidemiologicalCalendar(metadata));
		List<SurveillanceAlert> existing = dao.alerts();
		List<OutbreakRule> rules = configuredOutbreakRules();
		for (NotifiableEvent event : dao.events()) {
			if (!alertEventMatches(event, value))
				continue;
			for (AlertRules.Trigger trigger : new AlertRules().evaluate(current, previous,
			    before.getOrDefault(event.getUuid(), Collections.emptyList()),
			    after.getOrDefault(event.getUuid(), Collections.emptyList()), event.getPeriodicity(), metadata, rules)) {
				String key = event.getUuid() + ":" + trigger.reason + ":" + trigger.key;
				// A→B→A→B are three distinct changes; identical PUT retries have no transition.
				if ("STATUS_CHANGED".equals(trigger.reason))
					key += ":" + existing.stream()
					        .filter(a -> value.getUuid().equals(a.getCaseUuid()) && "STATUS_CHANGED".equals(a.getReason()))
					        .count();
				final String occurrence = key;
				if (existing.stream().anyMatch(a -> occurrence.equals(a.getOccurrenceKey())))
					continue;
				SurveillanceAlert alert = new SurveillanceAlert();
				alert.setUuid(UUID.randomUUID().toString());
				alert.setOccurrenceKey(key);
				alert.setType(AlertRules.REASONS.indexOf(trigger.reason) < 4 ? "IMMEDIATE" : "OUTBREAK");
				alert.setReason(trigger.reason);
				alert.setCaseUuid(value.getUuid());
				alert.setEventUuid(event.getUuid());
				alert.setEventName(
				    event.getConcept().getName() == null ? event.getUuid() : event.getConcept().getName().getName());
				alert.setCenter(
				    value.getInfectionAddress() == null ? null : dao.addressDisplay(value.getInfectionAddress()));
				alert.setAdditionalInformation(trigger.information);
				alert.setGeneratedAt(new Date());
				alert.setStatus("PENDING");
				dao.saveAlert(alert);
				existing.add(alert);
				generated.add(alertJson(alert));
				clinical.notifySurveillanceResponsibles("Vigilancia epidemiologica: nueva alerta " + alert.getUuid());
			}
		}
		if (!generated.isEmpty())
			AlertChanges.afterCommit();
		return generated;
	}
	
	private User requireAlertResponsible() {
		User user = null;
		for (String privilege : AlertRules.PRIVILEGES)
			user = access.require(privilege);
		return user;
	}
	
	private Map<String, Object> alertJson(SurveillanceAlert alert) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("uuid", alert.getUuid());
		r.put("type", alert.getType());
		r.put("reason", alert.getReason());
		r.put("caseUuid", alert.getCaseUuid());
		if (alert.getCaseUuid() != null) {
			SurveillanceCase value = dao.byUuid(SurveillanceCase.class, alert.getCaseUuid());
			if (value != null && !value.isVoided() && value.getPatient() != null && !value.getPatient().isVoided()) {
				User creator = value.getCreator();
				String name = creator == null || creator.getPersonName() == null ? null
				        : creator.getPersonName().getFullName();
				r.put("registeredBy",
				    ClinicalCatalogService.present(name) ? name : creator == null ? null : creator.getUsername());
				if ("IMMEDIATE".equals(alert.getType())) {
					SurveillanceCaseResponse detail = response(value);
					r.put("patientDisplay", detail.patientDisplay);
					r.put("encounterDate", detail.encounterDate);
					r.put("diagnosisDisplay", detail.diagnosisDisplay);
					r.put("diagnosisIcd10", detail.diagnosisIcd10);
				}
			}
		}
		
		r.put("eventUuid", alert.getEventUuid());
		r.put("eventName", alert.getEventName());
		r.put("center", alert.getCenter());
		r.put("additionalInformation", alert.getAdditionalInformation());
		r.put("generatedAt", alert.getGeneratedAt().toInstant().toString());
		r.put("status", alert.getStatus());
		r.put("attendedAt", alert.getAttendedAt() == null ? null : alert.getAttendedAt().toInstant().toString());
		User attendant = alert.getAttendedBy() == null ? null : dao.alertAttendant(alert.getAttendedBy());
		String attendantName = attendant == null ? null
		        : attendant.getPersonName() == null ? null : attendant.getPersonName().getFullName();
		r.put("attendedBy", ClinicalCatalogService.present(attendantName) ? attendantName
		        : attendant == null ? null : attendant.getUsername());
		return r;
	}
	
	@Override
	public List<Map<String, Object>> alerts(String type, String reason, String status) {
		requireAlertResponsible();
		if (type != null && !Arrays.asList("IMMEDIATE", "OUTBREAK").contains(type)
		        || reason != null && !AlertRules.REASONS.contains(reason)
		        || status != null && !Arrays.asList("PENDING", "ATTENDED").contains(status))
			throw new SurveillanceException(422, "INVALID_CLASSIFICATION");
		List<Map<String, Object>> result = new ArrayList<>();
		for (SurveillanceAlert alert : dao.alerts())
			if ((type == null || type.equals(alert.getType())) && (reason == null || reason.equals(alert.getReason()))
			        && (status == null || status.equals(alert.getStatus())))
				result.add(alertJson(alert));
		return result;
	}
	
	@Override
	public Map<String, Object> attendAlert(String uuid) {
		User actor = requireAlertResponsible();
		dao.lockAlerts();
		SurveillanceAlert alert = dao.byUuid(SurveillanceAlert.class, uuid);
		if (alert == null)
			throw new SurveillanceException(404, "INVALID_REFERENCE");
		if (!"ATTENDED".equals(alert.getStatus())) {
			alert.setStatus("ATTENDED");
			alert.setAttendedAt(new Date());
			alert.setAttendedBy(actor.getUuid());
			dao.saveAlert(alert);
			AlertChanges.afterCommit();
		}
		return alertJson(alert);
	}
	
	@Override
	public int[] getNotificationSettings() {
		access.require(SurveillanceConstants.ACCESS);
		int[] values = dao.notificationSettings();
		return values == null ? new int[] { 1, 12, 0 } : values;
	}
	
	@Override
	public int[] saveNotificationSettings(List<Integer> values) {
		access.require(SurveillanceConstants.MANAGE);
		if (values == null || values.size() != 3 || values.contains(null) || values.get(0) < 1 || values.get(0) > 7
		        || values.get(1) < 0 || values.get(1) > 23 || values.get(2) < 0 || values.get(2) > 59)
			throw new SurveillanceException(422, "INVALID_NOTIFICATION_SETTINGS");
		dao.saveNotificationSettings(values.get(0), values.get(1), values.get(2));
		return new int[] { values.get(0), values.get(1), values.get(2) };
	}
	
	@Override
	public Map<String, Object> indicators(String eventUuid, String from, String to, String zoneLevel, String address,
	        String diagnosisType, String indicator) {
		access.require(SurveillanceConstants.REPORT);
		if (!Arrays.asList("completeness", "fatality", "timeliness").contains(indicator))
			throw new SurveillanceException(422, "INVALID_INDICATOR");
		if (!Arrays.asList("CONFIRMADO", "PROBABLE", "TODOS").contains(diagnosisType)
		        || "fatality".equals(indicator) && !"CONFIRMADO".equals(diagnosisType))
			throw new SurveillanceException(422, "INVALID_DIAGNOSIS_TYPE");
		ClinicalCatalog metadata = catalog.get();
		EpidemiologicalCalendar calendar = new EpidemiologicalCalendar(metadata);
		LocalDate start, end;
		try {
			start = LocalDate.parse(from);
			end = LocalDate.parse(to);
		}
		catch (RuntimeException ex) {
			throw new SurveillanceException(422, "INVALID_DATE_RANGE");
		}
		if (start.isAfter(end) || end.isAfter(calendar.today()))
			throw new SurveillanceException(422, "INVALID_DATE_RANGE");
		if (!Arrays.asList("DEPARTAMENTO", "PROVINCIA", "DISTRITO", "CENTRO_POBLADO").contains(zoneLevel))
			throw new SurveillanceException(422, "INVALID_ZONE_LEVEL");
		Integer addressId = ClinicalCatalogService.present(address) ? dao.addressId(address, reportAddressField(zoneLevel))
		        : null;
		if (ClinicalCatalogService.present(address) && addressId == null)
			throw new SurveillanceException(422, "INVALID_REPORT_ADDRESS");
		NotifiableEvent selected = null;
		Map<String, String> diseases = eventsByDiagnosis();
		for (NotifiableEvent event : dao.events())
			if (event.getUuid().equals(eventUuid))
				selected = event;
		if (selected == null)
			throw new SurveillanceException(422, "INVALID_DEMOGRAPHIC_EVENTS");
		List<SurveillanceCase> cohort = new ArrayList<SurveillanceCase>();
		Map<Integer, Integer> ancestors = new HashMap<Integer, Integer>();
		for (SurveillanceCase value : dao.surveillanceCases()) {
			if (value.isVoided() || value.getDiagnosis() == null || value.getDiagnosis().getVoided()
			        || value.getDiagnosis().getDiagnosis() == null || value.getDiagnosis().getDiagnosis().getCoded() == null
			        || value.getEncounter() == null || value.getEncounter().getVoided()
			        || value.getEncounter().getEncounterDatetime() == null
			        || !Arrays.asList("CONFIRMADO", "PROBABLE").contains(value.getDiagnosisType())
			        || !"TODOS".equals(diagnosisType) && !diagnosisType.equals(value.getDiagnosisType()))
				continue;
			if (!eventUuid.equals(diseases.get(value.getDiagnosis().getDiagnosis().getCoded().getUuid())))
				continue;
			LocalDate date = calendar.local(value.getEncounter().getEncounterDatetime());
			if (date.isBefore(start) || date.isAfter(end))
				continue;
			if (addressId != null) {
				Integer location = value.getInfectionAddress();
				if (location == null)
					continue;
				if (!"CENTRO_POBLADO".equals(zoneLevel)) {
					if (!ancestors.containsKey(location))
						ancestors.put(location, "DISTRITO".equals(zoneLevel) ? dao.districtIdForPopulatedCenter(location)
						        : dao.ancestorIdForPopulatedCenter(location, reportAddressField(zoneLevel)));
					location = ancestors.get(location);
				}
				if (!addressId.equals(location))
					continue;
			}
			cohort.add(value);
		}
		int[] deadline = dao.notificationSettings();
		return new IndicatorCalculator().calculate(cohort, indicator, selected.getPeriodicity(), metadata,
		    deadline == null ? new int[] { 1, 12, 0 } : deadline);
	}
	
	@Override
	public int[] getEndemicSettings() {
		access.require(SurveillanceConstants.ACCESS);
		int[] values = dao.endemicSettings();
		return values == null ? new int[] { 5, 3 } : values;
	}
	
	@Override
	public int[] saveEndemicSettings(List<Integer> values) {
		access.require(SurveillanceConstants.MANAGE);
		int currentYear = new EpidemiologicalCalendar(catalog.get()).today().getYear();
		if (values == null || values.size() != 2 || values.contains(null) || values.get(0) < 1
		        || values.get(0) >= currentYear || values.get(1) < 1 || values.get(1) > values.get(0))
			throw new SurveillanceException(422, "INVALID_ENDEMIC_SETTINGS");
		dao.saveEndemicSettings(values.get(0), values.get(1));
		return new int[] { values.get(0), values.get(1) };
	}
	
	@Override
	public int[] getRiskThresholds() {
		access.require(SurveillanceConstants.ACCESS);
		return GeographicCalculator.thresholds(dao.riskThresholds());
	}
	
	@Override
	public int[] saveRiskThresholds(List<Integer> bounds) {
		access.require(SurveillanceConstants.MANAGE);
		if (bounds == null || bounds.size() != 3)
			throw new SurveillanceException(422, "INVALID_RISK_LEVELS");
		String stored = bounds.get(0) + "," + bounds.get(1) + "," + bounds.get(2);
		int[] validated = GeographicCalculator.thresholds(stored);
		dao.saveRiskThresholds(stored);
		return validated;
	}
	
	@Override
	public Map<String, Integer> importCoordinates(String csv) {
		access.require(SurveillanceConstants.MANAGE);
		Map<String, double[]> parsed = GeographicCalculator.parseCsv(csv);
		Map<String, Integer> codes = new HashMap<String, Integer>();
		Set<String> ambiguous = new HashSet<String>();
		for (Object[] row : dao.populatedCenterCodes()) {
			if (row[1] == null)
				continue;
			String code = row[1].toString();
			if (codes.put(code, ((Number) row[0]).intValue()) != null)
				ambiguous.add(code);
		}
		int pending = 0;
		for (Map.Entry<String, double[]> entry : parsed.entrySet()) {
			dao.saveCoordinateCode(entry.getKey(), entry.getValue()[0], entry.getValue()[1]);
			if (!codes.containsKey(entry.getKey()) || ambiguous.contains(entry.getKey()))
				pending++;
		}
		Map<String, Integer> result = new LinkedHashMap<String, Integer>();
		result.put("imported", parsed.size());
		result.put("matched", parsed.size() - pending);
		result.put("pending", pending);
		return result;
	}
	
	@Override
	public GeographicReport geography(String eventUuid, String from, String to, String zoneLevel, String address) {
		return geography(eventUuid, from, to, zoneLevel, address, "CONFIRMADO");
	}
	
	@Override
	public GeographicReport geography(String eventUuid, String from, String to, String zoneLevel, String address,
	        String diagnosisType) {
		return geography(eventUuid, from, to, zoneLevel, address, diagnosisType, "origin", "mes");
	}
	
	@Override
	public GeographicReport geography(String eventUuid, String from, String to, String zoneLevel, String address,
	        String diagnosisType, String column, String period) {
		access.require(SurveillanceConstants.REPORT);
		if ((!DemographicCalculator.DIMENSIONS.contains(column) && !"origin".equals(column)) || "disease".equals(column)
		        || !DemographicCalculator.PERIODS.contains(period))
			throw new SurveillanceException(422, "INVALID_DEMOGRAPHIC_DIMENSIONS");
		if (!Arrays.asList("CONFIRMADO", "PROBABLE", "TODOS").contains(diagnosisType))
			throw new SurveillanceException(422, "INVALID_DIAGNOSIS_TYPE");
		EpidemiologicalCalendar calendar = new EpidemiologicalCalendar(catalog.get());
		LocalDate start, end;
		try {
			start = LocalDate.parse(from);
			end = LocalDate.parse(to);
		}
		catch (RuntimeException ex) {
			throw new SurveillanceException(422, "INVALID_DATE_RANGE");
		}
		if (start.isAfter(end) || start.getYear() != end.getYear() || end.isAfter(calendar.today()))
			throw new SurveillanceException(422, "INVALID_GEOGRAPHIC_RANGE");
		if (!Arrays.asList("DEPARTAMENTO", "PROVINCIA", "DISTRITO", "CENTRO_POBLADO").contains(zoneLevel))
			throw new SurveillanceException(422, "INVALID_ZONE_LEVEL");
		Integer addressId = ClinicalCatalogService.present(address) ? dao.addressId(address, reportAddressField(zoneLevel))
		        : null;
		if (ClinicalCatalogService.present(address) && addressId == null)
			throw new SurveillanceException(422, "INVALID_REPORT_ADDRESS");
		Map<String, String> diseases = eventsByDiagnosis();
		boolean found = false;
		for (NotifiableEvent event : dao.events())
			if (event.getUuid().equals(eventUuid))
				found = true;
		if (!found)
			throw new SurveillanceException(422, "INVALID_EVENT");
		List<GeographicCalculator.Record> records = new ArrayList<GeographicCalculator.Record>();
		List<DemographicCalculator.Record> tableRecords = new ArrayList<>();
		Map<String, String> ethnicities = new HashMap<>();
		Map<Integer, Integer> ancestors = new HashMap<Integer, Integer>();
		for (SurveillanceCase value : dao.surveillanceCases()) {
			if (value.isVoided() || !Arrays.asList("CONFIRMADO", "PROBABLE").contains(value.getDiagnosisType())
			        || !"TODOS".equals(diagnosisType) && !diagnosisType.equals(value.getDiagnosisType())
			        || value.getDiagnosis() == null || value.getDiagnosis().getVoided()
			        || value.getDiagnosis().getDiagnosis() == null || value.getDiagnosis().getDiagnosis().getCoded() == null
			        || value.getInfectionAddress() == null || value.getOnsetDate() == null)
				continue;
			if (!eventUuid.equals(diseases.get(value.getDiagnosis().getDiagnosis().getCoded().getUuid())))
				continue;
			Integer center = value.getInfectionAddress();
			if (addressId != null) {
				if (!ancestors.containsKey(center))
					ancestors.put(center, "CENTRO_POBLADO".equals(zoneLevel) ? center
					        : dao.ancestorIdForPopulatedCenter(center, reportAddressField(zoneLevel)));
				if (!addressId.equals(ancestors.get(center)))
					continue;
			}
			GeographicCalculator.Record record = new GeographicCalculator.Record();
			record.center = center;
			record.date = calendar.local(value.getOnsetDate());
			record.origin = value.getOrigin();
			records.add(record);
			if (!record.date.isBefore(start) && !record.date.isAfter(end)) {
				DemographicCalculator.Record cell = new DemographicCalculator.Record();
				cell.center = center.toString();
				cell.origin = record.origin;
				cell.date = record.date;
				cell.age = value.getPatientAge();
				cell.sex = value.getPatientSex();
				cell.pregnant = value.getPregnant();
				if ("ethnicity".equals(column)) {
					String patient = value.getPatient() == null ? "" : value.getPatient().getUuid();
					if (!ethnicities.containsKey(patient))
						ethnicities.put(patient, demographicEthnicity(value.getPatient(), catalog.get()));
					cell.ethnicity = ethnicities.get(patient);
				}
				tableRecords.add(cell);
			}
		}
		GeographicReport report = new GeographicCalculator().calculate(records, start, end,
		    GeographicCalculator.thresholds(dao.riskThresholds()));
		report.table = new DemographicCalculator().geography(tableRecords, column, period, start, end, configuredAgeGroups(),
		    calendar);
		Map<Integer, Object[]> coordinates = new HashMap<Integer, Object[]>();
		for (Object[] row : dao.centerCoordinates())
			coordinates.put(((Number) row[0]).intValue(), row);
		for (GeographicReport.Focus focus : report.foci) {
			focus.label = dao.addressDisplay(focus.centerId);
			Object[] row = coordinates.get(focus.centerId);
			if (row != null && row[1] != null && row[2] != null) {
				focus.latitude = ((Number) row[1]).doubleValue();
				focus.longitude = ((Number) row[2]).doubleValue();
			} else
				report.unmappedCases += focus.total;
		}
		return report;
	}
	
	@Override
	public List<AgeGroup> getAgeGroups() {
		access.require(SurveillanceConstants.ACCESS);
		return configuredAgeGroups();
	}
	
	private List<AgeGroup> configuredAgeGroups() {
		AgeGroupConfiguration config = dao.byUuid(AgeGroupConfiguration.class, AgeGroupConfiguration.UUID);
		return DemographicCalculator.groups(config == null ? null : config.getLowerBounds());
	}
	
	@Override
	public List<AgeGroup> saveAgeGroups(List<AgeGroup> groups) {
		User actor = access.require(SurveillanceConstants.MANAGE);
		String bounds = DemographicCalculator.validateGroups(groups);
		AgeGroupConfiguration config = dao.byUuid(AgeGroupConfiguration.class, AgeGroupConfiguration.UUID);
		if (config == null) {
			config = new AgeGroupConfiguration();
			config.setUuid(AgeGroupConfiguration.UUID);
			config.setCreator(actor);
			config.setDateCreated(new Date());
		} else {
			config.setChangedBy(actor);
			config.setDateChanged(new Date());
		}
		config.setLowerBounds(bounds);
		dao.save(config);
		return DemographicCalculator.groups(bounds);
	}
	
	@Override
	public DemographicReport demographics(String from, String to, String row, String column, String period, String eventUuid,
	        String compareEvents, String diagnosisType) {
		return demographics(from, to, row, column, period, eventUuid, compareEvents, diagnosisType, "CENTRO_POBLADO", null);
	}
	
	@Override
	public DemographicReport demographics(String from, String to, String row, String column, String period, String eventUuid,
	        String compareEvents, String diagnosisType, String zoneLevel, String address) {
		access.require(SurveillanceConstants.REPORT);
		if (!Arrays.asList("DEPARTAMENTO", "PROVINCIA", "DISTRITO", "CENTRO_POBLADO").contains(zoneLevel))
			throw new SurveillanceException(422, "INVALID_ZONE_LEVEL");
		Integer addressId = null;
		if (ClinicalCatalogService.present(address)) {
			addressId = dao.addressId(address, reportAddressField(zoneLevel));
			if (addressId == null)
				throw new SurveillanceException(422, "INVALID_REPORT_ADDRESS");
		}
		Map<Integer, Integer> districts = new HashMap<Integer, Integer>();
		ClinicalCatalog metadata = catalog.get();
		EpidemiologicalCalendar calendar = new EpidemiologicalCalendar(metadata);
		LocalDate start, end;
		try {
			start = LocalDate.parse(from);
			end = LocalDate.parse(to);
		}
		catch (RuntimeException ex) {
			throw new SurveillanceException(422, "INVALID_DATE_RANGE");
		}
		if (start.isAfter(end) || end.isAfter(calendar.today()))
			throw new SurveillanceException(422, "INVALID_DATE_RANGE");
		if (!DemographicCalculator.DIMENSIONS.contains(row) || !DemographicCalculator.DIMENSIONS.contains(column)
		        || row.equals(column) || !DemographicCalculator.PERIODS.contains(period))
			throw new SurveillanceException(422, "INVALID_DEMOGRAPHIC_DIMENSIONS");
		if (!Arrays.asList("CONFIRMADO", "PROBABLE", "TODOS").contains(diagnosisType))
			throw new SurveillanceException(422, "INVALID_DIAGNOSIS_TYPE");
		boolean compare = "disease".equals(row) || "disease".equals(column);
		Set<String> selected = new LinkedHashSet<String>();
		if (compare) {
			if (ClinicalCatalogService.present(eventUuid) || !ClinicalCatalogService.present(compareEvents))
				throw new SurveillanceException(422, "INVALID_DEMOGRAPHIC_EVENTS");
			selected.addAll(Arrays.asList(compareEvents.split(",")));
		} else {
			if (!ClinicalCatalogService.present(eventUuid) || ClinicalCatalogService.present(compareEvents))
				throw new SurveillanceException(422, "INVALID_DEMOGRAPHIC_EVENTS");
			selected.add(eventUuid);
		}
		Map<String, String> labels = new LinkedHashMap<String, String>();
		for (NotifiableEvent event : dao.events())
			if (event.getConcept() != null && selected.contains(event.getUuid()))
				labels.put(event.getUuid(), event.getConcept().getDisplayString());
		if (labels.size() != selected.size())
			throw new SurveillanceException(422, "INVALID_DEMOGRAPHIC_EVENTS");
		Map<String, String> diseases = eventsByDiagnosis();
		List<DemographicCalculator.Record> records = new ArrayList<DemographicCalculator.Record>();
		Map<String, String> ethnicities = new HashMap<String, String>();
		int undated = 0;
		for (SurveillanceCase value : dao.surveillanceCases()) {
			if (value.isVoided() || value.getDiagnosis() == null || value.getDiagnosis().getVoided()
			        || value.getDiagnosis().getDiagnosis() == null || value.getDiagnosis().getDiagnosis().getCoded() == null
			        || !Arrays.asList("CONFIRMADO", "PROBABLE").contains(value.getDiagnosisType())
			        || !"TODOS".equals(diagnosisType) && !diagnosisType.equals(value.getDiagnosisType()))
				continue;
			String concept = value.getDiagnosis().getDiagnosis().getCoded().getUuid();
			String disease = diseases.get(concept);
			if (!selected.contains(disease))
				continue;
			if (addressId != null) {
				Integer location = value.getInfectionAddress();
				if (location == null)
					continue;
				if (!"CENTRO_POBLADO".equals(zoneLevel)) {
					if (!districts.containsKey(location))
						districts.put(location, "DISTRITO".equals(zoneLevel) ? dao.districtIdForPopulatedCenter(location)
						        : dao.ancestorIdForPopulatedCenter(location, reportAddressField(zoneLevel)));
					location = districts.get(location);
				}
				if (!addressId.equals(location))
					continue;
			}
			if (value.getOnsetDate() == null) {
				undated++;
				continue;
			}
			LocalDate date = calendar.local(value.getOnsetDate());
			if (date.isBefore(start) || date.isAfter(end))
				continue;
			DemographicCalculator.Record record = new DemographicCalculator.Record();
			record.age = value.getPatientAge();
			record.sex = value.getPatientSex();
			record.pregnant = value.getPregnant();
			record.date = date;
			record.disease = disease;
			if ("ethnicity".equals(row) || "ethnicity".equals(column)) {
				String patient = value.getPatient() == null ? "" : value.getPatient().getUuid();
				if (!ethnicities.containsKey(patient))
					ethnicities.put(patient, demographicEthnicity(value.getPatient(), metadata));
				record.ethnicity = ethnicities.get(patient);
			}
			records.add(record);
		}
		DemographicReport result = new DemographicCalculator().calculate(records, row, column, period, start, end,
		    configuredAgeGroups(), labels, calendar);
		result.excludedWithoutOnset = undated;
		result.zoneLevel = zoneLevel;
		result.address = addressId == null ? null : address;
		return result;
	}
	
	private WeeklyEthnicity weeklyEthnicity(Patient patient, ClinicalCatalog metadata) {
		Concept concept = null;
		if (patient != null && ClinicalCatalogService.present(metadata.ethnicityAttributeTypeUuid)) {
			PersonAttributeType type = clinical.attributeType(metadata.ethnicityAttributeTypeUuid);
			PersonAttribute attribute = type == null ? null : patient.getAttribute(type);
			if (attribute != null && !attribute.getVoided() && "org.openmrs.Concept".equals(type.getFormat())
			        && ClinicalCatalogService.present(attribute.getValue())) {
				try {
					concept = clinical.concept(Integer.valueOf(attribute.getValue()));
				}
				catch (NumberFormatException ex) {
					concept = clinical.concept(attribute.getValue());
				}
			}
		}
		return WeeklyEthnicity.from(concept, metadata);
	}
	
	private String demographicEthnicity(Patient patient, ClinicalCatalog metadata) {
		if (patient == null || !ClinicalCatalogService.present(metadata.ethnicityAttributeTypeUuid))
			return null;
		PersonAttributeType type = clinical.attributeType(metadata.ethnicityAttributeTypeUuid);
		PersonAttribute attribute = type == null ? null : patient.getAttribute(type);
		if (attribute == null || attribute.getVoided() || !ClinicalCatalogService.present(attribute.getValue()))
			return null;
		if (!"org.openmrs.Concept".equals(type.getFormat()))
			return attribute.getValue();
		Concept concept;
		try {
			concept = clinical.concept(Integer.valueOf(attribute.getValue()));
		}
		catch (NumberFormatException ex) {
			concept = clinical.concept(attribute.getValue());
		}
		return concept == null ? null : concept.getDisplayString();
	}
	
	private SurveillanceDao dao;
	
	private ClinicalData clinical;
	
	private ClinicalCatalogService catalog;
	
	private SurveillanceAccess access;
	
	private final ReportCalculator calculator = new ReportCalculator();
	
	public void setDao(SurveillanceDao value) {
		dao = value;
	}
	
	public void setClinical(ClinicalData value) {
		clinical = value;
	}
	
	public void setCatalog(ClinicalCatalogService value) {
		catalog = value;
	}
	
	public void setAccess(SurveillanceAccess value) {
		access = value;
	}
	
	@Override
	public Map<String, Object> getCatalog() {
		access.require(SurveillanceConstants.ACCESS);
		ClinicalCatalog m = catalog.get();
		Map<String, Object> response = new LinkedHashMap<String, Object>();
		response.put("catalog", m);
		List<Map<String, Object>> events = new ArrayList<Map<String, Object>>();
		for (NotifiableEvent event : dao.events())
			if (!event.isRetired())
				events.add(eventJson(event));
		response.put("events", events);
		return response;
	}
	
	@Override
	public Map<String, Object> healthcheck() {
		access.require(SurveillanceConstants.ACCESS);
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("tableCount", dao.verifiedTableCount());
		result.put("status", "UP");
		return result;
	}
	
	@Override
	public List<Map<String, Object>> getEvents(boolean includeRetired) {
		access.require(SurveillanceConstants.ACCESS);
		List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		for (NotifiableEvent event : dao.events())
			if (includeRetired || !event.isRetired())
				result.add(eventJson(event));
		return result;
	}
	
	@Override
	public Map<String, Object> getEvent(String uuid) {
		access.require(SurveillanceConstants.ACCESS);
		return eventJson(requiredEvent(uuid));
	}
	
	private NotifiableEvent requiredEvent(String uuid) {
		Map<String, Object> reference = Collections.<String, Object> singletonMap("uuid", uuid);
		NotifiableEvent event = dao.byUuid(NotifiableEvent.class, string(reference, "uuid"));
		if (event == null)
			throw new SurveillanceException(404, "EVENT_NOT_FOUND");
		return event;
	}
	
	@Override
	public Map<String, Object> updateEvent(String uuid, Map<String, Object> body) {
		User actor = access.require(SurveillanceConstants.MANAGE);
		NotifiableEvent event = requiredEvent(uuid);
		dao.lockEvent(event);
		if (body != null && body.size() == 1 && body.get("active") instanceof Boolean) {
			event.setActive((Boolean) body.get("active"));
			event.setChangedBy(actor);
			event.setDateChanged(new Date());
			dao.save(event);
			return eventJson(event);
		}
		return writeEvent(body, event);
	}
	
	@Override
	public SurveillanceCaseResponse createDraft(SurveillanceCaseRequest request) {
		User actor = access.require(SurveillanceConstants.REGISTER);
		dao.lockAlerts();
		ClinicalCatalog metadata = alertMetadata();
		Map<String, List<AlertRules.Snapshot>> before = alertSnapshots(metadata);
		SurveillanceCase value = populate(new SurveillanceCase(), request, actor, true);
		value.setCreator(actor);
		value.setDateCreated(new Date());
		dao.save(value);
		refreshCountsAfterCaseSave(value, actor);
		SurveillanceCaseResponse response = response(value);
		response.generatedAlerts = generateAlerts(value, null, before, metadata);
		return response;
	}
	
	@Override
	public List<SurveillanceCaseResponse> listCases(String patientUuid, String diagnosisConceptUuid, String onsetFrom,
	        String onsetTo, String notificationFrom, String notificationTo, Boolean fatal) {
		access.require(SurveillanceConstants.VIEW);
		Date onsetStart = date(onsetFrom), onsetEnd = date(onsetTo),
		        notificationStart = notificationBoundary(notificationFrom, false),
		        notificationEnd = notificationBoundary(notificationTo, true);
		if (onsetStart != null && onsetEnd != null && onsetEnd.before(onsetStart)
		        || notificationStart != null && notificationEnd != null && !notificationEnd.after(notificationStart))
			throw new SurveillanceException(422, "INVALID_DATE_RANGE");
		List<SurveillanceCaseResponse> result = new ArrayList<SurveillanceCaseResponse>();
		for (SurveillanceCase value : dao.surveillanceCases()) {
			String conceptUuid = value.getDiagnosis() == null || value.getDiagnosis().getDiagnosis() == null
			        || value.getDiagnosis().getDiagnosis().getCoded() == null ? null
			                : value.getDiagnosis().getDiagnosis().getCoded().getUuid();
			if (patientUuid != null && (value.getPatient() == null || !patientUuid.equals(value.getPatient().getUuid()))
			        || diagnosisConceptUuid != null && !diagnosisConceptUuid.equals(conceptUuid)
			        || onsetStart != null && (value.getOnsetDate() == null || value.getOnsetDate().before(onsetStart))
			        || onsetEnd != null && (value.getOnsetDate() == null || value.getOnsetDate().after(onsetEnd))
			        || notificationStart != null
			                && (value.getNotificationDate() == null || value.getNotificationDate().before(notificationStart))
			        || notificationEnd != null
			                && (value.getNotificationDate() == null || !value.getNotificationDate().before(notificationEnd))
			        || fatal != null && fatal.booleanValue() != (value.getPatient().getDeathDate() != null))
				continue;
			result.add(response(value));
		}
		return result;
	}
	
	@Override
	public SurveillanceCaseResponse updateDraft(String uuid, SurveillanceCaseRequest request) {
		User actor = access.require(SurveillanceConstants.EDIT);
		dao.lockAlerts();
		SurveillanceCase value = dao.byUuid(SurveillanceCase.class, uuid);
		if (value == null || value.isVoided())
			throw new SurveillanceException(404, "CASE_NOT_FOUND");
		ClinicalCatalog metadata = alertMetadata();
		Map<String, List<AlertRules.Snapshot>> before = alertSnapshots(metadata);
		AlertRules.Snapshot previous = AlertRules.Snapshot.of(value, new EpidemiologicalCalendar(metadata));
		populate(value, request, actor, false);
		value.setChangedBy(actor);
		value.setDateChanged(new Date());
		dao.save(value);
		refreshCounts(actor);
		SurveillanceCaseResponse response = response(value);
		response.generatedAlerts = generateAlerts(value, previous, before, metadata);
		return response;
	}
	
	@Override
	public SurveillanceCaseResponse getDraft(String uuid) {
		access.require(SurveillanceConstants.VIEW);
		SurveillanceCase value = dao.byUuid(SurveillanceCase.class, uuid);
		if (value == null || value.isVoided())
			throw new SurveillanceException(404, "CASE_NOT_FOUND");
		return response(value);
	}
	
	@Override
	public List<Map<String, Object>> addressChildren(String level, String parentUuid) {
		access.require(SurveillanceConstants.VIEW);
		String field = "departments".equals(level) ? "ADDRESS_1"
		        : "provinces".equals(level) ? "STATE_PROVINCE"
		                : "districts".equals(level) ? "COUNTY_DISTRICT"
		                        : "populated-centers".equals(level) ? "CITY_VILLAGE" : null;
		if (field == null)
			throw new SurveillanceException(422, "INVALID_ADDRESS_LEVEL");
		List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		for (Object[] row : dao.addressChildren(field, parentUuid)) {
			Map<String, Object> item = new LinkedHashMap<String, Object>();
			item.put("uuid", row[0]);
			item.put("display", row[1]);
			result.add(item);
		}
		return result;
	}
	
	private SurveillanceCase populate(SurveillanceCase value, SurveillanceCaseRequest request, User actor,
	        boolean creating) {
		if (request == null)
			throw new SurveillanceException(422, "REQUIRED_FIELDS");
		String[] keys = { "patientUuid", "encounterUuid", "providerUuid", "locationUuid", "diagnosisUuid", "diagnosisType",
		        "vaccinationStatus", "surveillanceType" };
		String[] values = { request.patientUuid, request.encounterUuid, request.providerUuid, request.locationUuid,
		        request.diagnosisUuid, request.diagnosisType, request.vaccinationStatus, request.surveillanceType };
		for (int i = 0; i < keys.length; i++)
			if (!ClinicalCatalogService.present(values[i]))
				throw new SurveillanceException(422, "REQUIRED_FIELDS", keys[i], "required");
		Patient patient = clinical.patient(request.patientUuid);
		Encounter encounter = clinical.encounter(request.encounterUuid);
		// Editing keeps the registering professional; the editor is recorded in changed_by.
		boolean keepProvider = !creating && value.getProvider() != null;
		Provider provider = keepProvider ? value.getProvider() : clinical.provider(request.providerUuid);
		Location location = clinical.location(request.locationUuid);
		Diagnosis diagnosis = clinical.diagnosis(request.diagnosisUuid);
		if (patient == null || patient.getVoided())
			throw new SurveillanceException(422, "INVALID_REFERENCE", "patientUuid", "selection");
		if (encounter == null || encounter.getVoided() || !patient.equals(encounter.getPatient()))
			throw new SurveillanceException(422, "INVALID_REFERENCE", "encounterUuid", "selection");
		if (keepProvider) {
			if (!provider.getUuid().equals(request.providerUuid))
				throw new SurveillanceException(422, "INVALID_REFERENCE", "providerUuid", "selection");
		} else if (provider == null || provider.getRetired() || provider.getPerson() == null
		        || !provider.getPerson().equals(actor.getPerson()))
			throw new SurveillanceException(422, "INVALID_REFERENCE", "providerUuid", "selection");
		if (location == null || location.getRetired() || !location.equals(encounter.getLocation()))
			throw new SurveillanceException(422, "INVALID_REFERENCE", "locationUuid", "selection");
		if (diagnosis == null || diagnosis.getVoided() || !encounter.equals(diagnosis.getEncounter()))
			throw new SurveillanceException(422, "INVALID_REFERENCE", "diagnosisUuid", "selection");
		if (creating)
			requireActiveDiagnosis(diagnosis);
		SurveillanceCase existing = dao.caseByDiagnosis(diagnosis);
		if (!creating && value.getPatient() != null && (!patient.equals(value.getPatient())
		        || !encounter.equals(value.getEncounter()) || !diagnosis.equals(value.getDiagnosis())))
			throw new SurveillanceException(422, "INVALID_REFERENCE");
		if (existing != null && !existing.equals(value))
			throw new SurveillanceException(409, "CASE_ALREADY_EXISTS_FOR_DIAGNOSIS",
			        Collections.singletonList("diagnosisUuid"));
		if (!Arrays.asList("CONFIRMADO", "PROBABLE", "DESCARTADO").contains(request.diagnosisType))
			throw new SurveillanceException(422, "INVALID_CLASSIFICATION", Collections.singletonList("diagnosisType"));
		request.origin = normalizeOrigin(request.origin);
		if (request.origin != null && !Arrays.asList("AUTOCTONO", "IMPORTADO_NACIONAL", "IMPORTADO_INTERNACIONAL",
		    "INDUCIDO", "INTRODUCIDO", "RECAIDA", "RECRUDESCENCIA").contains(request.origin))
			throw new SurveillanceException(422, "INVALID_CLASSIFICATION", "origin", "selection");
		if (request.vaccinationStatus != null && !Arrays.asList("SI", "NO", "IGN").contains(request.vaccinationStatus))
			throw new SurveillanceException(422, "INVALID_CLASSIFICATION", "vaccinationStatus", "selection");
		if (request.surveillanceType != null
		        && !Arrays.asList("PASIVA", "BUSQUEDA_ACTIVA").contains(request.surveillanceType))
			throw new SurveillanceException(422, "INVALID_CLASSIFICATION", "surveillanceType", "selection");
		value.setPatient(patient);
		value.setEncounter(encounter);
		value.setProvider(provider);
		value.setLocation(location);
		value.setDiagnosis(diagnosis);
		value.setDiagnosisType(request.diagnosisType);
		value.setOrigin(request.origin);
		if (!ClinicalCatalogService.present(request.habitualResidence))
			throw new SurveillanceException(422, "REQUIRED_FIELDS", "habitualResidence", "required");
		if (!Arrays.asList("1", "2", "3").contains(request.habitualResidence))
			throw new SurveillanceException(422, "INVALID_CLASSIFICATION", "habitualResidence", "selection");
		value.setHabitualResidence(request.habitualResidence);
		value.setVaccinationStatus(request.vaccinationStatus);
		value.setSurveillanceType(request.surveillanceType);
		if (ClinicalCatalogService.present(request.infectionAddressUuid)) {
			Integer addressId = dao.populatedCenterId(request.infectionAddressUuid);
			if (addressId == null)
				throw new SurveillanceException(422, "INVALID_INFECTION_ADDRESS",
				        Collections.singletonList("infectionAddressUuid"));
			value.setInfectionAddress(addressId);
		} else
			value.setInfectionAddress(null);
		Order testOrder = ClinicalCatalogService.present(request.testOrderUuid) ? clinical.order(request.testOrderUuid)
		        : null;
		if (ClinicalCatalogService.present(request.testOrderUuid) && (testOrder == null || testOrder.isVoided()))
			throw new SurveillanceException(422, "INVALID_LAB_RESULT", Collections.singletonList("testOrderUuid"));
		Obs observation = null;
		if (ClinicalCatalogService.present(request.laboratoryObservationUuid)) {
			observation = clinical.observation(request.laboratoryObservationUuid);
			if (observation == null || observation.getVoided() || !patient.equals(observation.getPerson())
			        || !encounter.equals(observation.getEncounter())
			        || testOrder != null && !testOrder.equals(observation.getOrder()))
				throw new SurveillanceException(422, "INVALID_LAB_RESULT", "laboratoryObservationUuid", "invalidLaboratory");
			testOrder = observation.getOrder();
		}
		if (testOrder != null && (testOrder.isVoided() || !patient.equals(testOrder.getPatient())))
			throw new SurveillanceException(422, "INVALID_LAB_RESULT", "laboratoryObservationUuid", "invalidLaboratory");
		value.setLaboratoryObservation(observation);
		value.setTestOrder(testOrder);
		value.setOnsetDate(date(request.onsetDate, "onsetDate"));
		if (encounter.getEncounterDatetime() == null)
			throw new SurveillanceException(422, "INVALID_DATE", "encounterUuid", "dateFormat");
		EpidemiologicalCalendar calendar = new EpidemiologicalCalendar(catalog.get());
		value.setInvestigationDate(java.sql.Date.valueOf(calendar.local(encounter.getEncounterDatetime())));
		if (request.investigationForm != null)
			value.setInvestigationForm(request.investigationForm);
		value.setNotificationDate(notificationTime(request.notificationDate));
		// Person is authoritative; never accept a case-specific date of death.
		String attribution = request.deathAttribution;
		if (patient.getDeathDate() == null || !"CONFIRMADO".equals(request.diagnosisType))
			attribution = null;
		else if (attribution == null)
			attribution = "POR_DETERMINAR";
		else if (!Arrays.asList("SI", "NO", "POR_DETERMINAR").contains(attribution))
			throw new SurveillanceException(422, "INVALID_CLASSIFICATION", "deathAttribution", "selection");
		value.setDeathAttribution(attribution);
		validateDates(value);
		replaceTravelHistory(value, request.travelHistory, actor, calendar);
		if (creating)
			capturePatient(value, patient, calendar);
		boolean eligible = "F".equalsIgnoreCase(value.getPatientSex()) && value.getPatientAge() != null
		        && value.getPatientAge() >= 10 && value.getPatientAge() <= 55;
		value.setPregnant(eligible && Boolean.TRUE.equals(request.pregnant));
		return value;
	}
	
	private void replaceTravelHistory(SurveillanceCase value, List<TravelHistoryEntry> entries, User actor,
	        EpidemiologicalCalendar calendar) {
		Map<String, SurveillanceCaseTravel> existing = new HashMap<String, SurveillanceCaseTravel>();
		for (SurveillanceCaseTravel travel : value.getTravelHistory())
			existing.put(travel.getUuid(), travel);
		Set<SurveillanceCaseTravel> replacement = new LinkedHashSet<SurveillanceCaseTravel>();
		Set<String> seen = new HashSet<String>();
		LocalDate reference = calendar
		        .local(value.getOnsetDate() == null ? value.getInvestigationDate() : value.getOnsetDate());
		if (entries != null)
			for (int i = 0; i < entries.size(); i++) {
				TravelHistoryEntry entry = entries.get(i);
				String prefix = "travelHistory[" + i + "].";
				if (entry == null || !ClinicalCatalogService.present(entry.travelAddress))
					throw new SurveillanceException(422, "REQUIRED_FIELDS", prefix + "travelAddress", "required");
				Integer address = dao.populatedCenterId(entry.travelAddress);
				if (address == null)
					throw new SurveillanceException(422, "INVALID_REFERENCE", prefix + "travelAddress", "selection");
				Date start = date(entry.startDate, prefix + "startDate"), end = date(entry.endDate, prefix + "endDate");
				if (start == null)
					throw new SurveillanceException(422, "REQUIRED_FIELDS", prefix + "startDate", "required");
				if (end == null)
					throw new SurveillanceException(422, "REQUIRED_FIELDS", prefix + "endDate", "required");
				LocalDate from = calendar.local(start), to = calendar.local(end);
				if (from.isAfter(calendar.today()))
					throw new SurveillanceException(422, "INVALID_DATE_RANGE", prefix + "startDate", "future");
				if (to.isAfter(calendar.today()))
					throw new SurveillanceException(422, "INVALID_DATE_RANGE", prefix + "endDate", "future");
				if (from.isAfter(to))
					throw new SurveillanceException(422, "INVALID_DATE_RANGE", prefix + "endDate", "endBeforeStart");
				if (to.isBefore(reference.minusDays(28)) || from.isAfter(reference))
					throw new SurveillanceException(422, "INVALID_DATE_RANGE", prefix + "startDate", "travelOutsideWindow");
				SurveillanceCaseTravel travel = null;
				if (ClinicalCatalogService.present(entry.uuid)) {
					travel = existing.get(entry.uuid);
					if (travel == null || !seen.add(entry.uuid))
						throw new SurveillanceException(422, "INVALID_REFERENCE", prefix + "uuid", "selection");
				}
				if (travel == null) {
					travel = new SurveillanceCaseTravel();
					travel.setCreator(actor);
					travel.setDateCreated(new Date());
					travel.setSurveillanceCase(value);
				} else if (!Objects.equals(address, travel.getTravelAddress()) || !start.equals(travel.getStartDate())
				        || !end.equals(travel.getEndDate())) {
					travel.setChangedBy(actor);
					travel.setDateChanged(new Date());
				}
				travel.setTravelAddress(address);
				travel.setStartDate(start);
				travel.setEndDate(end);
				replacement.add(travel);
			}
		value.getTravelHistory().retainAll(replacement);
		value.getTravelHistory().addAll(replacement);
	}

	private void capturePatient(SurveillanceCase value, Patient patient, EpidemiologicalCalendar calendar) {
		PersonName name = patient.getPersonName();
		if (name != null) {
			value.setPatientGivenNames(joinNonBlank(" ", name.getGivenName(), name.getMiddleName()));
			value.setPatientPaternalSurname(name.getFamilyName());
			value.setPatientMaternalSurname(name.getFamilyName2());
		}
		value.setPatientSex(patient.getGender());
		if (patient.getBirthdate() != null) {
			LocalDate birth = calendar.local(patient.getBirthdate());
			LocalDate investigation = calendar.local(value.getInvestigationDate());
			if (birth.isAfter(investigation))
				throw new SurveillanceException(422, "INVALID_DATE_RANGE", "investigationDate", "beforeBirth");
			value.setPatientAge(Period.between(birth, investigation).getYears());
		}
		PersonAddress address = patient.getPersonAddress();
		if (address != null && !address.getVoided())
			value.setPatientResidence(joinNonBlank(" → ", address.getAddress1(), address.getStateProvince(),
			    address.getCountyDistrict(), address.getCityVillage(), address.getAddress2()));
	}
	
	private String joinNonBlank(String separator, String... values) {
		List<String> parts = new ArrayList<String>();
		for (String part : values)
			if (ClinicalCatalogService.present(part))
				parts.add(part);
		return parts.isEmpty() ? null : String.join(separator, parts);
	}
	
	private String normalizeOrigin(String value) {
		if ("AUTOCHTHONOUS".equals(value))
			return "AUTOCTONO";
		if ("IMPORTED_NATIONAL".equals(value))
			return "IMPORTADO_NACIONAL";
		if ("IMPORTED_INTERNATIONAL".equals(value))
			return "IMPORTADO_INTERNACIONAL";
		return value;
	}
	
	private void validateDates(SurveillanceCase value) {
		EpidemiologicalCalendar calendar = new EpidemiologicalCalendar(catalog.get());
		LocalDate investigation = calendar.local(value.getInvestigationDate());
		if (investigation.isAfter(calendar.today()))
			throw new SurveillanceException(422, "INVALID_DATE_RANGE", "investigationDate", "future");
		if (value.getNotificationDate() != null && value.getNotificationDate().after(new Date()))
			throw new SurveillanceException(422, "INVALID_DATE_RANGE", "notificationDate", "future");
		if (value.getNotificationDate() != null
		        && value.getNotificationDate().before(value.getEncounter().getEncounterDatetime()))
			throw new SurveillanceException(422, "INVALID_DATE_RANGE", "notificationDate",
			        "notificationBeforeInvestigation");
		if (value.getOnsetDate() != null) {
			if (value.getOnsetDate().after(value.getInvestigationDate()))
				throw new SurveillanceException(422, "INVALID_DATE_RANGE", "onsetDate", "afterCare");
			if (value.getNotificationDate() != null && value.getOnsetDate().after(value.getNotificationDate()))
				throw new SurveillanceException(422, "INVALID_DATE_RANGE", "notificationDate", "beforeOnset");
		}
	}
	
	private Date notificationBoundary(String value, boolean end) {
		if (date(value) == null)
			return null;
		return Date.from(java.time.LocalDate.parse(value).plusDays(end ? 1 : 0).atStartOfDay()
		        .toInstant(java.time.ZoneOffset.ofHours(-5)));
	}
	
	private Date notificationTime(String value) {
		if (!ClinicalCatalogService.present(value))
			return null;
		try {
			return Date.from(java.time.OffsetDateTime.parse(value).toInstant());
		}
		catch (RuntimeException ex) {
			throw new SurveillanceException(422, "INVALID_DATE", "notificationDate", "dateTimeFormat");
		}
	}
	
	private Date date(String value) {
		return date(value, null);
	}
	
	private Date date(String value, String field) {
		if (!ClinicalCatalogService.present(value))
			return null;
		try {
			return java.sql.Date.valueOf(java.time.LocalDate.parse(value));
		}
		catch (RuntimeException ex) {
			if (field != null)
				throw new SurveillanceException(422, "INVALID_DATE", field, "dateFormat");
			throw new SurveillanceException(422, "INVALID_DATE");
		}
	}
	
	private void requireActiveDiagnosis(Diagnosis diagnosis) {
		Concept concept = diagnosis.getDiagnosis() == null ? null : diagnosis.getDiagnosis().getCoded();
		if (concept == null)
			throw new SurveillanceException(422, "INVALID_REFERENCE", "diagnosisUuid", "selection");
		NotifiableEvent event = null;
		List<NotifiableEvent> events = dao.events();
		for (NotifiableEvent candidate : events)
			if (event == null && concept.equals(candidate.getConcept()))
				event = candidate;
		for (NotifiableEvent candidate : events)
			if (event == null && covers(candidate, concept))
				event = candidate;
		if (event == null)
			throw new SurveillanceException(422, "INVALID_REFERENCE", "diagnosisUuid", "selection");
		dao.lockEvent(event);
		if (!event.isActive())
			throw new SurveillanceException(409, "EVENT_RETIRED");
	}
	
	private String residenceDisplay(String residence) {
		if (residence == null)
			return null;
		String[] parts = residence.split(" → ", -1);
		if (parts.length != 4)
			return residence;
		boolean firstIsDepartment = false;
		boolean lastIsDepartment = false;
		for (Object[] department : dao.addressChildren("ADDRESS_1", null)) {
			String name = String.valueOf(department[1]).trim();
			firstIsDepartment |= name.equalsIgnoreCase(parts[0].trim());
			lastIsDepartment |= name.equalsIgnoreCase(parts[3].trim());
		}
		// Normalize older snapshots for display without replacing their historical data.
		return lastIsDepartment && !firstIsDepartment ? joinNonBlank(" → ", parts[3], parts[0], parts[1], parts[2])
		        : residence;
	}
	
	@Override
	public Map<String, Object> weeklyRecords(int year, int week) {
		access.require(SurveillanceConstants.VIEW);
		access.require(SurveillanceConstants.REPORT);
		ClinicalCatalog metadata = catalog.get();
		EpidemiologicalCalendar calendar = new EpidemiologicalCalendar(metadata);
		LocalDate start = calendar.weekStart(year, week);
		Date from = calendar.date(start), to = calendar.date(start.plusWeeks(1));
		List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
		for (SurveillanceCase value : dao.weeklyCases(from, to)) {
			if (value.isVoided() || value.getPatient() == null || value.getPatient().getVoided()
			        || value.getEncounter() == null || value.getEncounter().getVoided() || value.getDiagnosis() == null
			        || value.getDiagnosis().getVoided() || value.getNotificationDate() == null
			        || value.getNotificationDate().before(from) || !value.getNotificationDate().before(to))
				continue;
			Map<String, Object> row = new LinkedHashMap<String, Object>();
			row.put("id", value.getUuid());
			row.put("year", year);
			row.put("week", week);
			row.put("paternalSurname", value.getPatientPaternalSurname());
			row.put("maternalSurname", value.getPatientMaternalSurname());
			row.put("givenNames", value.getPatientGivenNames());
			row.put("age", value.getPatientAge());
			row.put("sex", value.getPatientSex());
			String dni = null;
			for (PatientIdentifier identifier : value.getPatient().getIdentifiers())
				if (!identifier.isVoided() && identifier.getIdentifierType() != null
				        && "DNI".equalsIgnoreCase(identifier.getIdentifierType().getName())) {
					dni = identifier.getIdentifier();
					break;
				}
			row.put("dni", dni);
			Integer center = value.getInfectionAddress();
			row.put("province",
			    center == null ? null : dao.addressCode(dao.ancestorIdForPopulatedCenter(center, "STATE_PROVINCE")));
			row.put("district",
			    center == null ? null : dao.addressCode(dao.ancestorIdForPopulatedCenter(center, "COUNTY_DISTRICT")));
			row.put("locality", center == null ? null : dao.addressCode(center));
			String residence = residenceDisplay(value.getPatientResidence());
			String[] parts = residence == null ? new String[0] : residence.split(" → ", -1);
			row.put("residence",
			    parts.length >= 4 ? dao.residenceCode(parts[0].trim(), parts[1].trim(), parts[2].trim(), parts[3].trim())
			            : null);
			WeeklyEthnicity ethnicity = weeklyEthnicity(value.getPatient(), metadata);
			row.put("ethnicityGroup", ethnicity.group);
			row.put("ethnicity", ethnicity.ethnicity);
			row.put("habitualResidence", value.getHabitualResidence());
			Concept diagnosis = value.getDiagnosis().getDiagnosis() == null ? null
			        : value.getDiagnosis().getDiagnosis().getCoded();
			row.put("icd10", diagnosis == null ? null : ClinicalCatalogService.icd10OrNull(diagnosis, metadata));
			row.put("diagnosisType", value.getDiagnosisType());
			row.put("vaccinationStatus", value.getVaccinationStatus());
			row.put("onsetDate", weeklyDate(value.getOnsetDate(), calendar));
			row.put("deathDate", weeklyDate(value.getPatient().getDeathDate(), calendar));
			row.put("investigationDate", weeklyDate(value.getInvestigationDate(), calendar));
			row.put("notificationDate", weeklyDate(value.getNotificationDate(), calendar));
			row.put("investigationForm", value.getInvestigationForm());
			row.put("surveillanceType", value.getSurveillanceType());
			rows.add(row);
		}
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("year", year);
		result.put("week", week);
		result.put("from", start.toString());
		result.put("to", start.plusDays(6).toString());
		result.put("rows", rows);
		return result;
	}
	
	private String weeklyDate(Date value, EpidemiologicalCalendar calendar) {
		return value == null ? null : calendar.local(value).toString();
	}
	
	private SurveillanceCaseResponse response(SurveillanceCase value) {
		SurveillanceCaseResponse r = new SurveillanceCaseResponse();
		for (SurveillanceCaseTravel travel : value.getTravelHistory()) {
			TravelHistoryEntry entry = new TravelHistoryEntry();
			entry.uuid = travel.getUuid();
			entry.travelAddress = dao.addressUuid(travel.getTravelAddress());
			entry.travelAddressDisplay = dao.addressDisplay(travel.getTravelAddress());
			entry.startDate = travel.getStartDate().toString().substring(0, 10);
			entry.endDate = travel.getEndDate().toString().substring(0, 10);
			r.travelHistory.add(entry);
		}
		r.uuid = value.getUuid();
		r.patientUuid = value.getPatient().getUuid();
		for (PatientIdentifier identifier : value.getPatient().getIdentifiers())
			if (!identifier.isVoided() && ClinicalCatalogService.present(identifier.getIdentifier())) {
				r.patientIdentifier = identifier.getIdentifier();
				break;
			}
		r.patientGivenNames = value.getPatientGivenNames();
		r.patientPaternalSurname = value.getPatientPaternalSurname();
		r.patientMaternalSurname = value.getPatientMaternalSurname();
		r.patientDisplay = joinNonBlank(" ", r.patientGivenNames, r.patientPaternalSurname, r.patientMaternalSurname);
		r.patientSex = value.getPatientSex();
		r.patientAge = value.getPatientAge();
		r.patientResidence = residenceDisplay(value.getPatientResidence());
		r.pregnant = value.getPregnant();
		r.patientBirthDate = value.getPatient().getBirthdate() == null ? null : value.getPatient().getBirthdate().toString();
		r.encounterUuid = value.getEncounter().getUuid();
		r.encounterDisplay = value.getEncounter().getEncounterType() == null ? null
		        : value.getEncounter().getEncounterType().getName();
		r.encounterDate = value.getEncounter().getEncounterDatetime() == null ? null
		        : java.time.Instant.ofEpochMilli(value.getEncounter().getEncounterDatetime().getTime()).toString();
		r.providerDisplay = value.getProvider().getName();
		r.locationDisplay = value.getLocation().getName();
		r.providerUuid = value.getProvider().getUuid();
		r.locationUuid = value.getLocation().getUuid();
		r.diagnosisUuid = value.getDiagnosis().getUuid();
		r.diagnosisDisplay = value.getDiagnosis().getDiagnosis() == null
		        || value.getDiagnosis().getDiagnosis().getCoded() == null ? null
		                : value.getDiagnosis().getDiagnosis().getCoded().getDisplayString();
		r.testOrderUuid = value.getTestOrder() == null ? null : value.getTestOrder().getUuid();
		r.diagnosisIcd10 = value.getDiagnosis().getDiagnosis() == null
		        || value.getDiagnosis().getDiagnosis().getCoded() == null ? null
		                : ClinicalCatalogService.icd10OrNull(value.getDiagnosis().getDiagnosis().getCoded(), catalog.get());
		r.testOrderDisplay = value.getTestOrder() == null || value.getTestOrder().getConcept() == null ? null
		        : value.getTestOrder().getConcept().getDisplayString();
		Obs laboratory = value.getLaboratoryObservation();
		r.laboratoryObservationUuid = laboratory == null || laboratory.getVoided() ? null : laboratory.getUuid();
		r.laboratoryObservationDisplay = laboratory == null || laboratory.getVoided() ? null
		        : laboratory.getValueCoded() != null ? laboratory.getValueCoded().getDisplayString()
		                : laboratory.getValueText();
		r.origin = value.getOrigin();
		r.diagnosisType = value.getDiagnosisType();
		r.vaccinationStatus = value.getVaccinationStatus();
		r.habitualResidence = value.getHabitualResidence();
		r.surveillanceType = value.getSurveillanceType();
		r.infectionAddressUuid = value.getInfectionAddress() == null ? null : dao.addressUuid(value.getInfectionAddress());
		r.infectionAddressDisplay = value.getInfectionAddress() == null ? null
		        : dao.addressDisplay(value.getInfectionAddress());
		r.onsetDate = value.getOnsetDate() == null ? null : value.getOnsetDate().toString();
		r.investigationDate = value.getInvestigationDate() == null ? null : value.getInvestigationDate().toString();
		r.investigationForm = value.getInvestigationForm();
		r.notificationDate = value.getNotificationDate() == null ? null
		        : java.time.Instant.ofEpochMilli(value.getNotificationDate().getTime())
		                .atOffset(java.time.ZoneOffset.ofHours(-5)).toString();
		r.patientDeathDate = value.getPatient().getDeathDate() == null ? null : value.getPatient().getDeathDate().toString();
		r.deathAttribution = r.patientDeathDate == null || !"CONFIRMADO".equals(value.getDiagnosisType()) ? null
		        : value.getDeathAttribution() == null ? "POR_DETERMINAR" : value.getDeathAttribution();
		return r;
	}
	
	@Override
	public SurveillanceReport report(String eventUuid, String from, String to, String period) {
		return report(eventUuid, from, to, period, "CENTRO_POBLADO", null, "CONFIRMADO");
	}
	
	@Override
	public SurveillanceReport report(String eventUuid, String from, String to, String period, String zoneLevel,
	        String address, String diagnosisType) {
		access.require(SurveillanceConstants.REPORT);
		if (!Arrays.asList("DEPARTAMENTO", "PROVINCIA", "DISTRITO", "CENTRO_POBLADO").contains(zoneLevel))
			throw new SurveillanceException(422, "INVALID_ZONE_LEVEL");
		if (!Arrays.asList("CONFIRMADO", "PROBABLE", "TODOS").contains(diagnosisType))
			throw new SurveillanceException(422, "INVALID_DIAGNOSIS_TYPE");
		period = period == null ? "semana" : period.toLowerCase(Locale.ENGLISH);
		Integer addressId = null;
		if (address != null && !address.trim().isEmpty()) {
			addressId = dao.addressId(address, reportAddressField(zoneLevel));
			if (addressId == null)
				throw new SurveillanceException(422, "INVALID_REPORT_ADDRESS");
		}
		ClinicalCatalog m = catalog.get();
		NotifiableEvent event = dao.byUuid(NotifiableEvent.class, eventUuid);
		if (event == null)
			throw new SurveillanceException(404, "EVENT_NOT_FOUND");
		LocalDate start, end;
		try {
			start = LocalDate.parse(from);
			end = LocalDate.parse(to);
		}
		catch (Exception ex) {
			throw new SurveillanceException(422, "INVALID_DATE_RANGE");
		}
		EpidemiologicalCalendar calendar = new EpidemiologicalCalendar(m);
		calendar.number(start, period);
		if (start.getYear() != calendar.today().getYear() || end.getYear() != calendar.today().getYear())
			throw new SurveillanceException(422, "OUTSIDE_CURRENT_YEAR");
		if (end.isBefore(start) || end.isAfter(calendar.today()))
			throw new SurveillanceException(422, "INVALID_DATE_RANGE");
		if (start.isBefore(LocalDate.parse(m.surveillanceStartDate)))
			throw new SurveillanceException(422, "OUTSIDE_COVERAGE");
		boolean ancestor = "DEPARTAMENTO".equals(zoneLevel) || "PROVINCIA".equals(zoneLevel);
		String countLevel = ancestor ? "CENTRO_POBLADO" : zoneLevel;
		Integer countAddress = ancestor ? null : addressId;
		List<PeriodCaseCount> daily = dao.counts(event, "dia", start.getYear(), end.getYear(), countLevel, countAddress);
		int[] settings = dao.endemicSettings();
		m.historicalYears = settings == null ? 5 : settings[0];
		m.minimumHistoricalYears = settings == null ? 3 : settings[1];
		List<PeriodCaseCount> counts = dao.counts(event, period, start.getYear() - m.historicalYears, start.getYear() - 1,
		    countLevel, countAddress);
		if (ancestor && addressId != null) {
			Map<Integer, Integer> ancestors = new HashMap<Integer, Integer>();
			daily = filterCountsByAncestor(daily, zoneLevel, addressId, ancestors);
			counts = filterCountsByAncestor(counts, zoneLevel, addressId, ancestors);
		}
		SurveillanceReport report = new AggregateReportCalculator().calculate(eventUuid, start, end, period, countLevel,
		    countAddress, diagnosisType, daily, counts, m);
		report.zoneLevel = zoneLevel;
		report.address = addressId == null ? null : address;
		return report;
	}
	
	private static String reportAddressField(String level) {
		return "DEPARTAMENTO".equals(level) ? "ADDRESS_1"
		        : "PROVINCIA".equals(level) ? "STATE_PROVINCE"
		                : "DISTRITO".equals(level) ? "COUNTY_DISTRICT" : "CITY_VILLAGE";
	}
	
	private List<PeriodCaseCount> filterCountsByAncestor(List<PeriodCaseCount> counts, String level, Integer selected,
	        Map<Integer, Integer> ancestors) {
		List<PeriodCaseCount> result = new ArrayList<PeriodCaseCount>();
		for (PeriodCaseCount count : counts) {
			Integer center = count.getAddressHierarchyEntryId();
			if (center == null)
				continue;
			if (!ancestors.containsKey(center))
				ancestors.put(center, dao.ancestorIdForPopulatedCenter(center, reportAddressField(level)));
			if (selected.equals(ancestors.get(center)))
				result.add(count);
		}
		return result;
	}
	
	@Override
	public void refreshCounts() {
		User actor = access.require(SurveillanceConstants.MANAGE);
		refreshCounts(actor);
	}
	
	private void refreshCountsAfterCaseSave(SurveillanceCase value, User actor) {
		if ("CONFIRMADO".equals(value.getDiagnosisType()) || "PROBABLE".equals(value.getDiagnosisType()))
			refreshCounts(actor);
	}
	
	private void refreshCounts(User actor) {
		ClinicalCatalog m = catalog.get();
		EpidemiologicalCalendar calendar = new EpidemiologicalCalendar(m);
		List<SurveillanceCase> cases = dao.surveillanceCases();
		Map<Integer, Integer> districts = new HashMap<Integer, Integer>();
		for (SurveillanceCase surveillanceCase : cases)
			if (surveillanceCase.getInfectionAddress() != null)
				districts.put(surveillanceCase.getInfectionAddress(),
				    dao.districtIdForPopulatedCenter(surveillanceCase.getInfectionAddress()));
		for (NotifiableEvent event : dao.events())
			dao.replaceCounts(event,
			    calculator.aggregateSurveillanceCases(event, cases, districts, m, actor, calendar.today()));
	}
	
	@Override
	public Map<String, Object> saveEvent(Map<String, Object> body) {
		access.require(SurveillanceConstants.MANAGE);
		String uuid = body != null && body.containsKey("uuid") ? string(body, "uuid") : null;
		if (uuid != null && dao.byUuid(NotifiableEvent.class, uuid) != null)
			throw new SurveillanceException(409, "EVENT_ALREADY_EXISTS");
		return writeEvent(body, null);
	}
	
	private Map<String, Object> writeEvent(Map<String, Object> body, NotifiableEvent existing) {
		User actor = access.require(SurveillanceConstants.MANAGE);
		String uuid = body != null && body.get("uuid") instanceof String ? string(body, "uuid")
		        : UUID.randomUUID().toString();
		String periodicity = string(body, "periodicity").toUpperCase(Locale.ENGLISH);
		String referenceRegulation = string(body, "referenceRegulation");
		Concept concept = clinical.concept(string(body, "conceptUuid"));
		if (!(body.get("active") instanceof Boolean))
			throw new SurveillanceException(422, "INVALID_EVENT", "active", "selection");
		if (concept == null || concept.getRetired())
			throw new SurveillanceException(422, "INVALID_EVENT", "conceptUuid", "selection");
		if (referenceRegulation.isEmpty())
			throw new SurveillanceException(422, "INVALID_EVENT", "referenceRegulation", "required");
		if (referenceRegulation.length() > 100)
			throw new SurveillanceException(422, "INVALID_EVENT", "referenceRegulation", "referenceLength");
		if (!Arrays.asList("SEMANAL", "INMEDIATA").contains(periodicity))
			throw new SurveillanceException(422, "INVALID_EVENT", "periodicity", "selection");
		dao.lockEventConcept(concept);
		for (NotifiableEvent other : dao.events())
			if (concept.equals(other.getConcept()) && (existing == null || !existing.getUuid().equals(other.getUuid())))
				throw new SurveillanceException(409, "EVENT_ALREADY_EXISTS");
		NotifiableEvent event = existing;
		if (event == null) {
			event = new NotifiableEvent();
			event.setUuid(uuid);
			event.setValidFrom(new Date());
			event.setCreator(actor);
			event.setDateCreated(new Date());
		}
		event.setConcept(concept);
		event.setPeriodicity(periodicity);
		event.setReferenceRegulation(referenceRegulation);
		event.setActive((Boolean) body.get("active"));
		if (existing != null) {
			event.setChangedBy(actor);
			event.setDateChanged(new Date());
		}
		dao.save(event);
		return eventJson(event);
	}
	
	private String string(Map<String, Object> body, String key) {
		if (body == null || !(body.get(key) instanceof String))
			throw new SurveillanceException(422, "REQUIRED_FIELDS", key, "required");
		String value = ((String) body.get(key)).trim();
		if (key.toLowerCase(Locale.ROOT).contains("uuid")) {
			try {
				if (!UUID.fromString(value).toString().equalsIgnoreCase(value))
					throw new IllegalArgumentException();
			}
			catch (Exception ex) {
				throw new SurveillanceException(422, "INVALID_REFERENCE", key, "selection");
			}
		}
		return value;
	}
	
	private int number(Map<String, Object> body, String key) {
		Object value = body.get(key);
		if (!(value instanceof Number) || ((Number) value).doubleValue() != ((Number) value).intValue())
			throw new SurveillanceException(422, "INVALID_NUMBER");
		return ((Number) value).intValue();
	}
	
	private Map<String, Object> eventJson(NotifiableEvent event) {
		Map<String, Object> value = new LinkedHashMap<String, Object>();
		value.put("uuid", event.getUuid());
		value.put("conceptUuid", event.getConcept().getUuid());
		value.put("icd10", ClinicalCatalogService.icd10OrNull(event.getConcept(), catalog.get()));
		if (event.getConcept().getName() != null) {
			value.put("conceptDisplay", event.getConcept().getName().getName());
		} else if (event.getConcept().getDisplayString() != null) {
			value.put("conceptDisplay", event.getConcept().getDisplayString());
		}
		value.put("periodicity", event.getPeriodicity());
		value.put("deadlineDays", event.getDeadlineDays());
		value.put("referenceRegulation", event.getReferenceRegulation());
		value.put("active", event.isActive());
		value.put("diagnosisConceptUuids", diagnosisConceptUuids(event));
		return value;
	}
	
	/**
	 * The event concept and, when it is a concept set, its members; read from OpenMRS, never configured
	 * here.
	 */
	private static List<String> diagnosisConceptUuids(NotifiableEvent event) {
		List<String> uuids = new ArrayList<String>();
		uuids.add(event.getConcept().getUuid());
		for (Concept member : event.getConcept().getSetMembers())
			if (!member.getRetired() && !uuids.contains(member.getUuid()))
				uuids.add(member.getUuid());
		return uuids;
	}
}
