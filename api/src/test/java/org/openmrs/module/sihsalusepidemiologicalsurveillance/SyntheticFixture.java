package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.*;

import org.openmrs.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.db.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.impl.SurveillanceServiceImpl;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.*;

class SyntheticFixture {
	
	final ClinicalCatalog m = new ClinicalCatalog();
	
	final ClinicalData clinical = mock(ClinicalData.class);
	
	final SurveillanceDao dao = mock(SurveillanceDao.class);
	
	final ClinicalCatalogService resolver = mock(ClinicalCatalogService.class);
	
	final SurveillanceAccess access = mock(SurveillanceAccess.class);
	
	final SurveillanceServiceImpl service = new SurveillanceServiceImpl();
	
	final User actor = new User(1);
	
	final Patient patient = new Patient(2);
	
	final Provider provider = new Provider(3);
	
	final Location location = new Location(4);
	
	final Encounter source = new Encounter(5);
	
	final EncounterType type = new EncounterType();
	
	final NotifiableEvent event = new NotifiableEvent();
	
	final Concept diagnosis = concept(6);
	
	final Diagnosis encounterDiagnosis = new Diagnosis();
	
	final SurveillanceCaseRequest surveillanceRequest = new SurveillanceCaseRequest();
	
	static String uuid(int number) {
		return String.format("00000000-0000-4000-8000-%012d", number);
	}
	
	static Concept concept(int id) {
		Concept c = new Concept(id);
		c.setUuid(uuid(id));
		c.setRetired(false);
		return c;
	}
	
	SyntheticFixture() {
		m.surveillanceStartDate = "2020-01-01";
		m.trueConceptUuid = uuid(80);
		m.falseConceptUuid = uuid(81);
		when(clinical.trueConcept()).thenReturn(concept(80));
		when(clinical.falseConcept()).thenReturn(concept(81));
		patient.setUuid(uuid(2));
		patient.setBirthdate(new EpidemiologicalCalendar(m).date(LocalDate.of(1990, 1, 1)));
		patient.setGender("F");
		patient.setVoided(false);
		actor.setPerson(new Person(1));
		actor.getPerson().setUuid(uuid(1));
		provider.setUuid(uuid(3));
		provider.setPerson(actor.getPerson());
		provider.setRetired(false);
		location.setUuid(uuid(4));
		location.setRetired(false);
		Visit visit = new Visit();
		visit.setUuid(uuid(10));
		visit.setPatient(patient);
		visit.setVoided(false);
		source.setUuid(uuid(5));
		source.setPatient(patient);
		source.setVisit(visit);
		source.setLocation(location);
		source.setVoided(false);
		source.setEncounterDatetime(new EpidemiologicalCalendar(m).date(LocalDate.of(2026, 1, 20)));
		type.setUuid(uuid(11));
		source.setEncounterType(type);
		m.icd10SourceUuid = uuid(13);
		ConceptSource codeSource = new ConceptSource();
		codeSource.setUuid(m.icd10SourceUuid);
		ConceptReferenceTerm term = new ConceptReferenceTerm();
		term.setConceptSource(codeSource);
		term.setCode("A90");
		term.setRetired(false);
		ConceptMap map = new ConceptMap();
		map.setConceptReferenceTerm(term);
		diagnosis.setConceptMappings(new HashSet<ConceptMap>(Arrays.asList(map)));
		event.setId(1);
		event.setUuid(uuid(14));
		event.setConcept(concept(15));
		// Each notifiable event covers its configured diagnosis (here as a concept set member).
		event.getConcept().addSetMember(diagnosis);
		event.setPeriodicity("SEMANAL");
		event.setReferenceRegulation("Synthetic regulation");
		event.setValidFrom(new EpidemiologicalCalendar(m).date(LocalDate.of(2020, 1, 1)));
		when(clinical.patient(patient.getUuid())).thenReturn(patient);
		when(clinical.encounter(source.getUuid())).thenReturn(source);
		when(clinical.provider(provider.getUuid())).thenReturn(provider);
		when(clinical.location(location.getUuid())).thenReturn(location);
		encounterDiagnosis.setUuid(uuid(91));
		encounterDiagnosis.setPatient(patient);
		encounterDiagnosis.setEncounter(source);
		encounterDiagnosis.setVoided(false);
		encounterDiagnosis.setDiagnosis(new org.openmrs.CodedOrFreeText(diagnosis, null, null));
		when(clinical.diagnosis(encounterDiagnosis.getUuid())).thenReturn(encounterDiagnosis);
		when(clinical.concept(diagnosis.getUuid())).thenReturn(diagnosis);
		when(dao.events()).thenReturn(Arrays.asList(event));
		when(dao.byUuid(NotifiableEvent.class, event.getUuid())).thenReturn(event);
		when(dao.counts(any(), anyString(), anyInt(), anyInt())).thenReturn(Collections.<PeriodCaseCount> emptyList());
		when(resolver.get()).thenReturn(m);
		when(access.require(anyString())).thenReturn(actor);
		service.setClinical(clinical);
		service.setDao(dao);
		service.setCatalog(resolver);
		service.setAccess(access);
		surveillanceRequest.patientUuid = patient.getUuid();
		surveillanceRequest.encounterUuid = source.getUuid();
		surveillanceRequest.providerUuid = provider.getUuid();
		surveillanceRequest.locationUuid = location.getUuid();
		surveillanceRequest.diagnosisUuid = encounterDiagnosis.getUuid();
		surveillanceRequest.diagnosisType = "PROBABLE";
		surveillanceRequest.origin = "AUTOCTONO";
		surveillanceRequest.onsetDate = "2026-01-19";
		surveillanceRequest.vaccinationStatus = "IGN";
		surveillanceRequest.investigationDate = "2026-01-20";
		surveillanceRequest.notificationDate = "2026-01-21T12:00:00-05:00";
		surveillanceRequest.habitualResidence = "1";
		surveillanceRequest.deathDate = "2026-01-22";
		surveillanceRequest.surveillanceType = "PASIVA";
	}
	
	Obs lab(boolean positive) {
		Concept test = concept(70), question = concept(71), answer = concept(positive ? 72 : 73);
		TestOrder order = new TestOrder();
		order.setConcept(test);
		order.setPatient(patient);
		order.setEncounter(source);
		Obs obs = new Obs(patient, question, source.getEncounterDatetime(), location);
		obs.setUuid(uuid(74));
		obs.setOrder(order);
		obs.setEncounter(source);
		obs.setValueCoded(answer);
		when(clinical.observation(obs.getUuid())).thenReturn(obs);
		return obs;
	}
}
