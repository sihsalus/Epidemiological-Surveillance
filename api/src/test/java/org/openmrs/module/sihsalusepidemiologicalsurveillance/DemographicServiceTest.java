package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.Test;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.*;

public class DemographicServiceTest {

	@Test
	public void departmentAndProvinceFilterBothReportsWithoutDoubleCounting() {
		for (String level : Arrays.asList("DEPARTAMENTO", "PROVINCIA")) {
			SyntheticFixture f = new SyntheticFixture();
			String field = "DEPARTAMENTO".equals(level) ? "ADDRESS_1" : "STATE_PROVINCE";
			when(f.dao.addressId("selected", field)).thenReturn(5);
			when(f.dao.ancestorIdForPopulatedCenter(10, field)).thenReturn(5);
			when(f.dao.ancestorIdForPopulatedCenter(20, field)).thenReturn(6);
			SurveillanceCase first = record(f), second = record(f);
			first.setInfectionAddress(10);
			second.setInfectionAddress(20);
			when(f.dao.events()).thenReturn(Collections.singletonList(f.event));
			when(f.dao.surveillanceCases()).thenReturn(Arrays.asList(first, second));
			DemographicReport demographic = f.service.demographics("2026-01-01", "2026-01-31", "sex", "ageGroup", "mes",
			    f.event.getUuid(), null, "TODOS", level, "selected");
			assertEquals(1, demographic.total);
			List<PeriodCaseCount> daily = new ArrayList<PeriodCaseCount>();
			for (int center : Arrays.asList(10, 20)) {
				PeriodCaseCount count = new PeriodCaseCount();
				count.setEvent(f.event);
				count.setAddressHierarchyEntryId(center);
				count.setZoneLevel("CENTRO_POBLADO");
				count.setPeriodType("DIA");
				count.setDiagnosisType("CONFIRMADO");
				count.setStartDate(java.sql.Date.valueOf("2026-01-15"));
				count.setCaseCount(3);
				daily.add(count);
			}
			when(f.dao.counts(f.event, "dia", 2026, 2026, "CENTRO_POBLADO", null)).thenReturn(daily);
			SurveillanceReport report = f.service.report(f.event.getUuid(), "2026-01-01", "2026-01-31", "mes", level,
			    "selected", "TODOS");
			assertEquals(3, report.total);
			assertEquals(level, report.zoneLevel);
			assertEquals("selected", report.address);
			assertEquals(
			    6,
			    f.service.report(f.event.getUuid(), "2026-01-01", "2026-01-31", "mes", "CENTRO_POBLADO", null, "TODOS").total);
			assertEquals(2, f.service.demographics("2026-01-01", "2026-01-31", "sex", "ageGroup", "mes", f.event.getUuid(),
			    null, "TODOS", "CENTRO_POBLADO", null).total);
		}
	}

	@Test
	public void reportsAcceptMoreThanTwoYears() {
		SyntheticFixture f = new SyntheticFixture();
		f.m.surveillanceStartDate = "2020-01-01";
		when(f.dao.events()).thenReturn(Collections.singletonList(f.event));
		when(f.dao.surveillanceCases()).thenReturn(Collections.singletonList(record(f)));
		DemographicReport demographic = f.service.demographics("2023-09-17", "2026-10-13", "period", "sex", "dia",
		    f.event.getUuid(), null, "TODOS");
		assertEquals(1123, demographic.rows.size());
		assertEquals(1, demographic.total);
		SurveillanceReport temporal = f.service.report(f.event.getUuid(), "2023-09-17", "2026-10-13", "dia");
		assertEquals(1123, temporal.channel.size());
	}

	@Test
	public void geographyFiltersCasesBeforeCountingUndatedRecords() {
		SyntheticFixture f = new SyntheticFixture();
		SurveillanceCase first = record(f), second = record(f), undated = record(f), unknown = record(f);
		first.setInfectionAddress(10);
		second.setInfectionAddress(20);
		undated.setInfectionAddress(20);
		undated.setOnsetDate(null);
		when(f.dao.events()).thenReturn(Collections.singletonList(f.event));
		when(f.dao.surveillanceCases()).thenReturn(Arrays.asList(first, second, undated, unknown));
		when(f.dao.addressId("district", "COUNTY_DISTRICT")).thenReturn(5);
		when(f.dao.districtIdForPopulatedCenter(10)).thenReturn(5);
		when(f.dao.districtIdForPopulatedCenter(20)).thenReturn(6);
		DemographicReport result = f.service.demographics("2026-01-01", "2026-01-31", "sex", "ageGroup", "mes",
		    f.event.getUuid(), null, "TODOS", "DISTRITO", "district");
		assertEquals(1, result.total);
		assertEquals(0, result.excludedWithoutOnset);
		assertEquals("district", result.address);
		when(f.dao.addressId("center", "CITY_VILLAGE")).thenReturn(20);
		when(f.dao.addressId("center", "COUNTY_DISTRICT")).thenReturn(null);
		result = f.service.demographics("2026-01-01", "2026-01-31", "sex", "ageGroup", "mes", f.event.getUuid(), null,
		    "TODOS", "CENTRO_POBLADO", "center");
		assertEquals(1, result.total);
		assertEquals(1, result.excludedWithoutOnset);
		try {
			f.service.demographics("2026-01-01", "2026-01-31", "sex", "ageGroup", "mes", f.event.getUuid(), null, "TODOS",
			    "DISTRITO", "center");
			fail();
		}
		catch (SurveillanceException ex) {
			assertEquals("INVALID_REPORT_ADDRESS", ex.getCode());
		}
	}

	@Test
	public void periodLabelsUseConfiguredEpidemiologicalWeekAndRequestedPrecision() {
		SyntheticFixture f = new SyntheticFixture();
		EpidemiologicalCalendar calendar = new EpidemiologicalCalendar(f.m);
		java.time.LocalDate date = java.time.LocalDate.of(2026, 7, 1);
		assertEquals("01/07/2026", DemographicCalculator.periodLabel(date, "dia", calendar));
		assertEquals("2026-07", DemographicCalculator.periodLabel(date, "mes", calendar));
		assertEquals("2026-T3", DemographicCalculator.periodLabel(date, "trimestre", calendar));
		assertEquals("2026-2", DemographicCalculator.periodLabel(date, "semestre", calendar));
		assertEquals("2026", DemographicCalculator.periodLabel(date, "anio", calendar));
		assertEquals("2026-S01", DemographicCalculator.periodLabel(java.time.LocalDate.of(2026, 1, 4), "semana", calendar));
		assertEquals("2025-S53", DemographicCalculator.periodLabel(java.time.LocalDate.of(2025, 12, 28), "semana", calendar));
	}

	@Test
	public void comparisonsIncludeZeroDiseaseAndKnownEthnicityWithoutLeakingPatientNames() {
		SyntheticFixture f = new SyntheticFixture();
		SurveillanceCase value = record(f);
		NotifiableEvent other = new NotifiableEvent();
		other.setUuid(SyntheticFixture.uuid(501));
		other.setConcept(SyntheticFixture.concept(502));
		when(f.dao.events()).thenReturn(Arrays.asList(f.event, other));
		when(f.dao.surveillanceCases()).thenReturn(Collections.singletonList(value));
		org.openmrs.PersonAttributeType type = new org.openmrs.PersonAttributeType();
		type.setId(60);
		type.setUuid("synthetic-ethnicity");
		type.setFormat("java.lang.String");
		f.m.ethnicityAttributeTypeUuid = type.getUuid();
		f.patient.addAttribute(new org.openmrs.PersonAttribute(type, "Synthetic ethnicity"));
		when(f.clinical.attributeType(type.getUuid())).thenReturn(type);
		DemographicReport report = f.service.demographics("2026-01-01", "2026-01-31", "disease", "ethnicity", "mes", null,
		    f.event.getUuid() + "," + other.getUuid(), "CONFIRMADO");
		assertEquals(2, report.rows.size());
		assertEquals(Arrays.asList(1, 0), report.rowTotals);
		assertEquals("Synthetic ethnicity", report.columns.get(0).label);
	}

	@Test
	public void invalidConfigurationAndDeniedWritesDoNotPersist() {
		SyntheticFixture f = new SyntheticFixture();
		try {
			f.service.saveAgeGroups(Collections.emptyList());
			fail();
		}
		catch (SurveillanceException ex) {
			assertEquals("INVALID_AGE_GROUPS", ex.getCode());
		}
		when(f.access.require(SurveillanceConstants.MANAGE)).thenThrow(new SurveillanceException(403, "ACCESS_DENIED"));
		try {
			f.service.saveAgeGroups(DemographicCalculator.groups(null));
			fail();
		}
		catch (SurveillanceException ex) {
			assertEquals(403, ex.getStatus());
		}
		verify(f.dao, never()).save(any(AgeGroupConfiguration.class));
	}

	private SurveillanceCase record(SyntheticFixture f) {
		f.encounterDiagnosis.setDiagnosis(new org.openmrs.CodedOrFreeText(f.diagnosis, null, null));
		SurveillanceCase value = new SurveillanceCase();
		value.setPatient(f.patient);
		value.setDiagnosis(f.encounterDiagnosis);
		value.setOnsetDate(java.sql.Date.valueOf("2026-01-20"));
		value.setPatientAge(12);
		value.setPatientSex("F");
		value.setPregnant(true);
		value.setDiagnosisType("CONFIRMADO");
		return value;
	}

	@Test
	public void reportUsesCaseCopiesAndExcludesVoidedDiscardedAndOutOfRangeCases() {
		SyntheticFixture f = new SyntheticFixture();
		SurveillanceCase included = record(f), discarded = record(f), voided = record(f), old = record(f), undated = record(f);
		discarded.setDiagnosisType("DESCARTADO");
		voided.setVoided(true);
		old.setOnsetDate(java.sql.Date.valueOf("2025-12-31"));
		undated.setOnsetDate(null);
		when(f.dao.surveillanceCases()).thenReturn(Arrays.asList(included, discarded, voided, old, undated));
		when(f.dao.events()).thenReturn(Collections.singletonList(f.event));
		f.patient.setGender("M");
		DemographicReport result = f.service.demographics("2026-01-01", "2026-01-31", "sex", "ageGroup", "mes",
		    f.event.getUuid(), null, "TODOS");
		assertEquals(1, result.total);
		assertEquals(1, result.excludedWithoutOnset);
		assertEquals(Integer.valueOf(1), result.rowTotals.get(0));
		assertEquals("F", result.rows.get(0).key);
		assertEquals(Integer.valueOf(1), result.columnTotals.get(2));
		verify(f.access).require(SurveillanceConstants.REPORT);
	}

	@Test
	public void comparisonRejectsAnIndividualEventAndInvalidVariables() {
		SyntheticFixture f = new SyntheticFixture();
		try {
			f.service.demographics("2026-01-01", "2026-01-31", "disease", "sex", "mes", f.event.getUuid(),
			    f.event.getUuid(), "TODOS");
			fail();
		}
		catch (SurveillanceException ex) {
			assertEquals("INVALID_DEMOGRAPHIC_EVENTS", ex.getCode());
		}
		try {
			f.service.demographics("2026-01-01", "2026-01-31", "sex", "sex", "mes", f.event.getUuid(), null, "TODOS");
			fail();
		}
		catch (SurveillanceException ex) {
			assertEquals("INVALID_DEMOGRAPHIC_DIMENSIONS", ex.getCode());
		}
	}

	@Test
	public void savedConfigurationChangesClassificationWithoutChangingCases() {
		SyntheticFixture f = new SyntheticFixture();
		AgeGroupConfiguration config = new AgeGroupConfiguration();
		config.setLowerBounds("0,2,12,18,30,60");
		when(f.dao.byUuid(AgeGroupConfiguration.class, AgeGroupConfiguration.UUID)).thenReturn(config);
		List<AgeGroup> groups = f.service.getAgeGroups();
		groups.get(1).maxAge = 12;
		groups.get(2).minAge = 13;
		f.service.saveAgeGroups(groups);
		verify(f.access).require(SurveillanceConstants.MANAGE);
		verify(f.dao).save(config);
		assertEquals("0,2,13,18,30,60", config.getLowerBounds());
		assertEquals("CHILDREN", DemographicCalculator.ageGroup(12, f.service.getAgeGroups()));
		verify(f.dao, never()).save(any(SurveillanceCase.class));
	}

	@Test
	public void deniedReportDoesNotReadCases() {
		SyntheticFixture f = new SyntheticFixture();
		when(f.access.require(SurveillanceConstants.REPORT)).thenThrow(new SurveillanceException(403, "ACCESS_DENIED"));
		try {
			f.service.demographics("2026-01-01", "2026-01-31", "sex", "age", "mes", f.event.getUuid(), null, "TODOS");
			fail();
		}
		catch (SurveillanceException ex) {
			assertEquals(403, ex.getStatus());
		}
		verify(f.dao, never()).surveillanceCases();
	}
}
