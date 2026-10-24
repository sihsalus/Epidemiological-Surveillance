package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.Test;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.*;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.model.OutbreakRule;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.OutbreakRuleConfiguration;

public class OutbreakRuleSettingsTest {

	@Test
	public void savesDefaultsEditedValuesAndAdditionalRulesWithPermissions() {
		SyntheticFixture f = new SyntheticFixture();
		List<OutbreakRule> rules = f.service.getOutbreakRules();
		assertEquals(3, rules.size());
		rules.get(0).percentile = 90.0;
		rules.get(1).weeks = 4;
		rules.get(2).minimumCases = 2;
		OutbreakRule extra = OutbreakRuleSettings.defaults().get(0);
		extra.id = "new-rule";
		rules.add(extra);
		OutbreakRuleConfiguration config = new OutbreakRuleConfiguration();
		when(f.dao.byUuid(OutbreakRuleConfiguration.class, OutbreakRuleConfiguration.UUID)).thenReturn(config);
		assertEquals(4, f.service.saveOutbreakRules(rules).size());
		verify(f.dao).lockAlerts();
		verify(f.dao).save(config);
		assertEquals(Double.valueOf(90), f.service.getOutbreakRules().get(0).percentile);
		assertEquals(Integer.valueOf(4), f.service.getOutbreakRules().get(1).weeks);
		assertEquals(Integer.valueOf(2), f.service.getOutbreakRules().get(2).minimumCases);
		when(f.access.require(SurveillanceConstants.MANAGE)).thenThrow(new SurveillanceException(403, "ACCESS_DENIED"));
		try {
			f.service.saveOutbreakRules(rules);
			fail();
		}
		catch (SurveillanceException ex) {
			assertEquals(403, ex.getStatus());
		}
		try {
			f.service.getOutbreakRules();
			fail();
		}
		catch (SurveillanceException ex) {
			assertEquals(403, ex.getStatus());
		}
		verify(f.dao, times(1)).save(config);
	}

	@Test
	public void rejectsInvalidParametersBeforeWriting() {
		for (int scenario = 0; scenario < 10; scenario++) {
			SyntheticFixture f = new SyntheticFixture();
			List<OutbreakRule> rules = OutbreakRuleSettings.defaults();
			switch (scenario) {
				case 0:
					rules.get(0).percentile = Double.NaN;
					break;
				case 1:
					rules.get(0).percentile = 101.0;
					break;
				case 2:
					rules.get(1).weeks = 0;
					break;
				case 3:
					rules.get(1).weeks = 54;
					break;
				case 4:
					rules.get(2).minimumCases = -1;
					break;
				case 5:
					rules.get(2).origin = "UNKNOWN";
					break;
				case 6:
					rules.get(2).focus = "UNKNOWN";
					break;
				case 7:
					rules.get(1).id = rules.get(0).id;
					break;
				case 8:
					rules.get(0).type = "UNKNOWN";
					break;
				case 9:
					rules.clear();
					break;
			}
			try {
				f.service.saveOutbreakRules(rules);
				fail();
			}
			catch (SurveillanceException ex) {
				assertEquals("INVALID_OUTBREAK_RULES", ex.getCode());
			}
			verify(f.dao, never()).save(any());
		}
	}
}
