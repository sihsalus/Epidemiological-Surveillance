package org.openmrs.module.sihsalusepidemiologicalsurveillance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.Test;
import org.openmrs.module.sihsalusepidemiologicalsurveillance.api.*;

public class EndemicSettingsTest {
	
	@Test
	public void defaultsValidationAndPermissions() {
		SyntheticFixture f = new SyntheticFixture();
		assertArrayEquals(new int[] { 5, 3 }, f.service.getEndemicSettings());
		assertArrayEquals(new int[] { 8, 4 }, f.service.saveEndemicSettings(Arrays.asList(8, 4)));
		verify(f.dao).saveEndemicSettings(8, 4);
		when(f.dao.endemicSettings()).thenReturn(new int[] { 8, 4 });
		assertArrayEquals(new int[] { 8, 4 }, f.service.getEndemicSettings());
		for (List<Integer> invalid : Arrays.asList(Arrays.asList(0, 1), Arrays.asList(2, 3), Arrays.asList(5, 0),
		    Arrays.asList(5), Arrays.asList(9999, 1))) {
			try {
				f.service.saveEndemicSettings(invalid);
				fail();
			}
			catch (SurveillanceException ex) {
				assertEquals("INVALID_ENDEMIC_SETTINGS", ex.getCode());
			}
		}
		verify(f.dao, times(1)).saveEndemicSettings(anyInt(), anyInt());
		when(f.access.require(SurveillanceConstants.MANAGE)).thenThrow(new SurveillanceException(403, "ACCESS_DENIED"));
		try {
			f.service.saveEndemicSettings(Arrays.asList(8, 4));
			fail();
		}
		catch (SurveillanceException ex) {
			assertEquals(403, ex.getStatus());
		}
	}
}
