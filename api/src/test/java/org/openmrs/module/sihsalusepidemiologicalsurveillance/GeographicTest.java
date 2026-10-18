package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.*;

import org.junit.Test;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.*;

public class GeographicTest {
	
	@Test
	public void geographicColumnsReuseDemographicDomainsAndPreserveTotals() {
		DemographicCalculator.Record a = new DemographicCalculator.Record();
		a.center = "42";
		a.age = 12;
		a.sex = "F";
		a.pregnant = false;
		a.ethnicity = "Synthetic ethnicity";
		a.origin = "AUTOCTONO";
		a.date = LocalDate.of(2026, 1, 1);
		DemographicCalculator.Record b = new DemographicCalculator.Record();
		b.center = "43";
		b.date = LocalDate.of(2026, 1, 2);
		DemographicCalculator calculator = new DemographicCalculator();
		EpidemiologicalCalendar calendar = new EpidemiologicalCalendar(new ClinicalCatalog());
		for (String column : Arrays.asList("age", "sex", "ageGroup", "ethnicity", "pregnancy", "period", "origin")) {
			DemographicReport table = calculator.geography(Arrays.asList(a, b), column, "dia", a.date, b.date,
			    DemographicCalculator.groups(null), calendar);
			assertEquals(2, table.total);
			assertEquals(Arrays.asList(1, 1), table.rowTotals);
			String expected = "age".equals(column) ? "12"
			        : "sex".equals(column) ? "F"
			                : "ageGroup".equals(column) ? "ADOLESCENTS"
			                        : "ethnicity".equals(column) ? a.ethnicity
			                                : "pregnancy".equals(column) ? "NO"
			                                        : "period".equals(column) ? "2026-01-01" : "AUTOCTONO";
			int index = -1;
			for (int i = 0; i < table.columns.size(); i++)
				if (expected.equals(table.columns.get(i).key))
					index = i;
			assertTrue(index >= 0);
			assertEquals(Integer.valueOf(1), table.values.get(0).get(index));
			assertEquals(2, table.columnTotals.stream().mapToInt(Integer::intValue).sum());
		}
		for (String invalid : Arrays.asList("disease", "center", "invalid")) {
			try {
				calculator.geography(Arrays.asList(a), invalid, "mes", a.date, b.date, DemographicCalculator.groups(null),
				    calendar);
				fail("Invalid geographic column accepted");
			}
			catch (SurveillanceException expected) {
				assertEquals("INVALID_DEMOGRAPHIC_DIMENSIONS", expected.getCode());
			}
		}
	}

	private GeographicCalculator.Record record(int center, String date, String origin) {
		GeographicCalculator.Record value = new GeographicCalculator.Record();
		value.center = center;
		value.date = LocalDate.parse(date);
		value.origin = origin;
		return value;
	}
	
	@Test
	public void classificationUsesCalendarYearsAndHistoryOutsideTheSelectedWindow() {
		List<GeographicCalculator.Record> records = new ArrayList<GeographicCalculator.Record>();
		for (int center = 1; center <= 6; center++)
			records.add(record(center, "2026-03-01", "INTRODUCIDO"));
		records.add(record(1, "2026-01-01", "AUTOCTONO"));
		records.add(record(2, "2025-12-31", "AUTOCTONO"));
		records.add(record(3, "2023-01-01", "AUTOCTONO"));
		records.add(record(4, "2022-12-31", "AUTOCTONO"));
		records.add(record(5, "2027-01-01", "AUTOCTONO"));
		records.add(record(7, "2026-01-01", "AUTOCTONO")); // No case in the displayed interval.
		GeographicReport report = new GeographicCalculator().calculate(records, LocalDate.parse("2026-03-01"),
		    LocalDate.parse("2026-03-31"), new int[] { 10, 20, 50 });
		assertEquals(6, report.total);
		assertEquals(6, report.foci.size());
		assertEquals("ACTIVE", report.foci.get(0).classification);
		assertEquals("RESIDUAL", report.foci.get(1).classification);
		assertEquals("RESIDUAL", report.foci.get(2).classification);
		assertEquals("ELIMINATED", report.foci.get(3).classification);
		assertEquals("ELIMINATED", report.foci.get(4).classification);
		assertEquals("ELIMINATED", report.foci.get(5).classification);
	}
	
	@Test
	public void riskBoundariesAndAllNonLocalOrigins() {
		int[] counts = { 1, 9, 10, 19, 20, 49, 50 };
		String[] risks = { "LOW", "LOW", "MEDIUM", "MEDIUM", "HIGH", "HIGH", "VERY_HIGH" };
		List<GeographicCalculator.Record> records = new ArrayList<GeographicCalculator.Record>();
		for (int i = 0; i < counts.length; i++)
			for (int n = 0; n < counts[i]; n++)
				records.add(record(i, "2026-01-01", GeographicCalculator.ORIGINS.get(1 + n % 6)));
		GeographicReport report = new GeographicCalculator().calculate(records, LocalDate.parse("2026-01-01"),
		    LocalDate.parse("2026-01-01"), GeographicCalculator.thresholds(null));
		for (int i = 0; i < counts.length; i++) {
			assertEquals(risks[i], report.foci.get(i).risk);
			assertEquals("ELIMINATED", report.foci.get(i).classification);
		}
		for (String invalid : Arrays.asList("1,20,50", "10,10,50", "10,20,19", "10.5,20,50", "10,20,2147483648")) {
			try {
				GeographicCalculator.thresholds(invalid);
				fail();
			}
			catch (SurveillanceException expected) {
				assertEquals("INVALID_RISK_LEVELS", expected.getCode());
			}
		}
	}
	
	@Test
	public void csvIsValidatedCompletelyBeforeAnyWritesAndPreservesCodes() {
		String header = "address_hierarchy_entry_id,latitude,longitude\n";
		assertTrue(GeographicCalculator.parseCsv("\uFEFF" + header + "00123,-90,180\r\n").containsKey("00123"));
		for (String invalid : Arrays.asList("00123,NaN,1", "00123,91,1", "00123,1,181", "00123,,1",
		    "00123,1,2\n00123,3,4")) {
			try {
				GeographicCalculator.parseCsv(header + invalid);
				fail();
			}
			catch (SurveillanceException expected) {
				assertEquals("INVALID_COORDINATES_CSV", expected.getCode());
			}
		}
		SyntheticFixture f = new SyntheticFixture();
		when(f.dao.populatedCenterCodes()).thenReturn(Collections.singletonList(new Object[] { 42, "00123" }));
		Map<String, Integer> result = f.service.importCoordinates(header + "00123,-3,-73\n99999,-4,-74");
		assertEquals(Integer.valueOf(2), result.get("imported"));
		assertEquals(Integer.valueOf(1), result.get("matched"));
		assertEquals(Integer.valueOf(1), result.get("pending"));
		verify(f.dao, never()).saveCoordinates(anyInt(), anyDouble(), anyDouble());
		verify(f.dao).saveCoordinateCode("00123", -3, -73);
		verify(f.dao).saveCoordinateCode("99999", -4, -74);
		when(f.dao.populatedCenterCodes())
		        .thenReturn(Arrays.asList(new Object[] { 42, "00123" }, new Object[] { 43, "00123" }));
		assertEquals(Integer.valueOf(1), f.service.importCoordinates(header + "00123,-3,-73").get("pending"));
		reset(f.dao);
		try {
			f.service.importCoordinates(header + "00123,-3,-73\n99999,NaN,-74");
			fail();
		}
		catch (SurveillanceException expected) {
			assertEquals("INVALID_COORDINATES_CSV", expected.getCode());
		}
		verify(f.dao, never()).saveCoordinateCode(anyString(), anyDouble(), anyDouble());
	}
	
	@Test
	public void serviceFiltersDiseaseStatusGeographyAndAttachesCoordinates() {
		SyntheticFixture f = new SyntheticFixture();
		f.encounterDiagnosis.setDiagnosis(new org.openmrs.CodedOrFreeText(f.diagnosis, null, null));
		SurveillanceCase confirmed = new SurveillanceCase(), probable = new SurveillanceCase(),
		        elsewhere = new SurveillanceCase();
		for (SurveillanceCase value : Arrays.asList(confirmed, probable, elsewhere)) {
			value.setDiagnosis(f.encounterDiagnosis);
			value.setDiagnosisType("CONFIRMADO");
			value.setOrigin("AUTOCTONO");
			value.setInfectionAddress(42);
			value.setOnsetDate(new EpidemiologicalCalendar(f.m).date(LocalDate.parse("2026-01-01")));
		}
		probable.setDiagnosisType("PROBABLE");
		confirmed.setPatientSex("F");
		probable.setPatientSex("M");
		elsewhere.setInfectionAddress(43);
		when(f.dao.events()).thenReturn(Collections.singletonList(f.event));
		when(f.dao.surveillanceCases()).thenReturn(Arrays.asList(confirmed, probable, elsewhere));
		when(f.dao.addressId("province", "STATE_PROVINCE")).thenReturn(2);
		when(f.dao.ancestorIdForPopulatedCenter(42, "STATE_PROVINCE")).thenReturn(2);
		when(f.dao.ancestorIdForPopulatedCenter(43, "STATE_PROVINCE")).thenReturn(3);
		when(f.dao.centerCoordinates()).thenReturn(Collections.singletonList(new Object[] { 42, -3.0, -73.0 }));
		GeographicReport report = f.service.geography(f.event.getUuid(), "2026-01-01", "2026-01-31", "PROVINCIA",
		    "province");
		assertEquals(1, report.total);
		assertEquals(0, report.unmappedCases);
		assertEquals(-3.0, report.foci.get(0).latitude, 0);
		GeographicReport bySex = f.service.geography(f.event.getUuid(), "2026-01-01", "2026-01-31", "PROVINCIA", "province",
		    "TODOS", "sex", "mes");
		assertEquals(2, bySex.table.total);
		assertEquals(bySex.total, bySex.table.total);
		assertEquals(Arrays.asList(1, 1, 0, 0), bySex.table.values.get(0));
		assertEquals(1,
		    f.service.geography(f.event.getUuid(), "2026-01-01", "2026-01-31", "PROVINCIA", "province", "PROBABLE").total);
		assertEquals(2,
		    f.service.geography(f.event.getUuid(), "2026-01-01", "2026-01-31", "PROVINCIA", "province", "TODOS").total);
		probable.setDiagnosisType("DESCARTADO");
		assertEquals(1,
		    f.service.geography(f.event.getUuid(), "2026-01-01", "2026-01-31", "PROVINCIA", "province", "TODOS").total);
		try {
			f.service.geography(f.event.getUuid(), "2026-01-01", "2026-01-31", "PROVINCIA", "province", "DESCARTADO");
			fail("Unsupported diagnosis filter accepted");
		}
		catch (SurveillanceException expected) {
			assertEquals("INVALID_DIAGNOSIS_TYPE", expected.getCode());
		}
		when(f.dao.centerCoordinates()).thenReturn(Collections.emptyList());
		assertEquals(1,
		    f.service.geography(f.event.getUuid(), "2026-01-01", "2026-01-31", "PROVINCIA", "province").unmappedCases);
	}
	
	@Test
	public void rejectsCrossYearRangesAndDeniedAccess() {
		SyntheticFixture f = new SyntheticFixture();
		try {
			f.service.geography(f.event.getUuid(), "2025-12-31", "2026-01-01", "DEPARTAMENTO", null);
			fail();
		}
		catch (SurveillanceException expected) {
			assertEquals("INVALID_GEOGRAPHIC_RANGE", expected.getCode());
		}
		when(f.access.require(SurveillanceConstants.REPORT)).thenThrow(new SurveillanceException(403, "ACCESS_DENIED"));
		try {
			f.service.geography(f.event.getUuid(), "2026-01-01", "2026-01-31", "DEPARTAMENTO", null);
			fail();
		}
		catch (SurveillanceException expected) {
			assertEquals(403, expected.getStatus());
		}
		verify(f.dao, never()).surveillanceCases();
		when(f.access.require(SurveillanceConstants.MANAGE)).thenThrow(new SurveillanceException(403, "ACCESS_DENIED"));
		try {
			f.service.importCoordinates("anything");
			fail();
		}
		catch (SurveillanceException expected) {
			assertEquals(403, expected.getStatus());
		}
		verify(f.dao, never()).saveCoordinates(anyInt(), anyDouble(), anyDouble());
	}
}
