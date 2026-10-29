package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Date;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.openmrs.User;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.OutbreakRuleSettings;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.*;

public class ConfigurationAuditTest {
	
	@Test
	public void outbreakRuleConfigurationPreservesCreationAndRecordsEditor() {
		SyntheticFixture f = new SyntheticFixture();
		Date before = new Date();
		f.service.saveOutbreakRules(OutbreakRuleSettings.defaults());
		ArgumentCaptor<OutbreakRuleConfiguration> saved = ArgumentCaptor.forClass(OutbreakRuleConfiguration.class);
		verify(f.dao).save(saved.capture());
		OutbreakRuleConfiguration config = saved.getValue();
		assertSame(f.actor, config.getCreator());
		assertNotNull(config.getDateCreated());
		assertFalse(config.getDateCreated().before(before));
		assertFalse(config.getDateCreated().after(new Date()));
		assertNull(config.getChangedBy());
		assertNull(config.getDateChanged());
		
		Date created = config.getDateCreated();
		User editor = new User(99);
		when(f.access.require(SurveillanceConstants.MANAGE)).thenReturn(editor);
		when(f.dao.byUuid(OutbreakRuleConfiguration.class, OutbreakRuleConfiguration.UUID)).thenReturn(config);
		f.service.saveOutbreakRules(OutbreakRuleSettings.defaults());
		assertSame(f.actor, config.getCreator());
		assertEquals(created, config.getDateCreated());
		assertSame(editor, config.getChangedBy());
		assertNotNull(config.getDateChanged());
		assertFalse(config.getDateChanged().before(created));
		verify(f.dao, times(2)).save(config);
	}
	
	@Test
	public void outbreakRuleConfigurationDoesNotInventLegacyCreation() {
		SyntheticFixture f = new SyntheticFixture();
		OutbreakRuleConfiguration config = new OutbreakRuleConfiguration();
		config.setId(1);
		when(f.dao.byUuid(OutbreakRuleConfiguration.class, OutbreakRuleConfiguration.UUID)).thenReturn(config);
		f.service.saveOutbreakRules(OutbreakRuleSettings.defaults());
		assertNull(config.getCreator());
		assertNull(config.getDateCreated());
		assertSame(f.actor, config.getChangedBy());
		assertNotNull(config.getDateChanged());
		verify(f.dao).save(config);
	}
	
	@Test
	public void ageGroupConfigurationPreservesCreationAndRecordsEditor() {
		SyntheticFixture f = new SyntheticFixture();
		Date before = new Date();
		f.service.saveAgeGroups(f.service.getAgeGroups());
		ArgumentCaptor<AgeGroupConfiguration> saved = ArgumentCaptor.forClass(AgeGroupConfiguration.class);
		verify(f.dao).save(saved.capture());
		AgeGroupConfiguration config = saved.getValue();
		assertSame(f.actor, config.getCreator());
		assertNotNull(config.getDateCreated());
		assertFalse(config.getDateCreated().before(before));
		assertFalse(config.getDateCreated().after(new Date()));
		assertNull(config.getChangedBy());
		assertNull(config.getDateChanged());
		
		Date created = config.getDateCreated();
		User editor = new User(99);
		when(f.access.require(SurveillanceConstants.MANAGE)).thenReturn(editor);
		when(f.dao.byUuid(AgeGroupConfiguration.class, AgeGroupConfiguration.UUID)).thenReturn(config);
		f.service.saveAgeGroups(f.service.getAgeGroups());
		assertSame(f.actor, config.getCreator());
		assertEquals(created, config.getDateCreated());
		assertSame(editor, config.getChangedBy());
		assertNotNull(config.getDateChanged());
		assertFalse(config.getDateChanged().before(created));
		verify(f.dao, times(2)).save(config);
	}
	
	@Test
	public void ageGroupConfigurationDoesNotInventLegacyCreation() {
		SyntheticFixture f = new SyntheticFixture();
		AgeGroupConfiguration config = new AgeGroupConfiguration();
		config.setId(1);
		when(f.dao.byUuid(AgeGroupConfiguration.class, AgeGroupConfiguration.UUID)).thenReturn(config);
		f.service.saveAgeGroups(f.service.getAgeGroups());
		assertNull(config.getCreator());
		assertNull(config.getDateCreated());
		assertSame(f.actor, config.getChangedBy());
		assertNotNull(config.getDateChanged());
		verify(f.dao).save(config);
	}
}
