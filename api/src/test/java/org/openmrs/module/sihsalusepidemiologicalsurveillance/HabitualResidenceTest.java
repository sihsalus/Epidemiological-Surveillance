package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.SurveillanceException;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.model.SurveillanceCase;

public class HabitualResidenceTest {
	
	@Test
	public void persistsAllCodesAndAllowsEditing() {
		for (String code : new String[] { "1", "2", "3" }) {
			SyntheticFixture f = new SyntheticFixture();
			f.surveillanceRequest.habitualResidence = code;
			assertEquals(code, f.service.createDraft(f.surveillanceRequest).habitualResidence);
			ArgumentCaptor<SurveillanceCase> capture = ArgumentCaptor.forClass(SurveillanceCase.class);
			verify(f.dao).save(capture.capture());
			SurveillanceCase value = capture.getValue();
			assertEquals(code, value.getHabitualResidence());
			when(f.dao.byUuid(SurveillanceCase.class, value.getUuid())).thenReturn(value);
			f.surveillanceRequest.habitualResidence = "2";
			assertEquals("2", f.service.updateDraft(value.getUuid(), f.surveillanceRequest).habitualResidence);
		}
	}
	
	@Test
	public void rejectsMissingAndUnknownCodes() {
		for (String code : new String[] { null, "", "0", "4", "Urbana" }) {
			SyntheticFixture f = new SyntheticFixture();
			f.surveillanceRequest.habitualResidence = code;
			try {
				f.service.createDraft(f.surveillanceRequest);
				fail();
			}
			catch (SurveillanceException ex) {
				assertEquals(422, ex.getStatus());
			}
			verify(f.dao, never()).save(any(SurveillanceCase.class));
		}
	}
}
