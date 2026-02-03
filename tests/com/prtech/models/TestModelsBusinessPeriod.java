package com.prtech.models;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.Test;

import com.prtech.svarog.SvException;
import com.prtech.svarog.SvLink;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvWriter;

public class TestModelsBusinessPeriod {

	private class TestClassA extends BaseObjectModel {

		Date startDate;
		Date endDate;

		public TestClassA() {
		}

		public TestClassA(Date startDate, Date endDate) {
			this();
			this.startDate = startDate;
			this.endDate = endDate;
		}

		@Override
		public BaseObjectModel setValue(String field, Object value) {
			switch (field) {
			case "START_DATE":
				this.startDate = Date.valueOf(value.toString());
				break;
			case "END_DATE":
				this.endDate = Date.valueOf(value.toString());
				break;
			default:
				break;
			}
			return this;
		}

		@Override
		public Object getValue(String field) {
			Object o = null;
			switch (field) {
			case "START_DATE":
				o = this.startDate;
				break;
			case "END_DATE":
				o = this.endDate;
				break;
			default:
				break;
			}
			return o;
		}

		@Override
		public String getTableName() {
			return "TEST_CLASS_A";
		}

		@Override
		public List<String> onStatusChange(String newStatus, SvReader svr, SvWriter svw) throws SvException {
			return null;
		}

		@Override
		public LinkedHashMap<String, String> getDetails(String localeId, SvReader svr) throws SvException {
			return null;
		}

		@Override
		public LinkedHashMap<String, String> getSummary(String localeId, SvReader svr) throws SvException {
			return null;
		}

		@Override
		public List<String> saveObject(Long parentId, Long objectId, String localeId, SvReader svr, SvWriter svw)
				throws SvException {
			return null;
		}

		@Override
		public List<String> invalidateLink(String linkName, Long linkedObjectId, SvReader svr, SvWriter svw)
				throws SvException {
			return null;
		}

		@Override
		public List<String> saveAndLink(Long parentId, Long objectId, String linkType, Long objectToBeLinkedTo,
				String locale, SvReader svr, SvWriter svw, SvLink svl) throws SvException {
			return null;
		}

		public String getBusinessStartDate() {
			return "START_DATE";
		}

		public String getBusinessEndDate() {
			return "END_DATE";
		}
	}

	private class TestClassB extends BaseObjectModel {

		Date startDate;
		Date endDate;

		@Override
		public BaseObjectModel setValue(String field, Object value) {
			switch (field) {
			case "START_DATE":
				this.startDate = Date.valueOf(value.toString());
				break;
			case "END_DATE":
				this.endDate = Date.valueOf(value.toString());
				break;
			default:
				break;
			}
			return this;
		}

		@Override
		public Object getValue(String field) {
			Object o = null;
			switch (field) {
			case "START_DATE":
				o = this.startDate;
				break;
			case "END_DATE":
				o = this.endDate;
				break;
			default:
				break;
			}
			return o;
		}

		@Override
		public String getTableName() {
			return "TEST_CLASS_B";
		}

		@Override
		public List<String> onStatusChange(String newStatus, SvReader svr, SvWriter svw) throws SvException {
			return null;
		}

		@Override
		public LinkedHashMap<String, String> getDetails(String localeId, SvReader svr) throws SvException {
			return null;
		}

		@Override
		public LinkedHashMap<String, String> getSummary(String localeId, SvReader svr) throws SvException {
			return null;
		}

		@Override
		public List<String> saveObject(Long parentId, Long objectId, String localeId, SvReader svr, SvWriter svw)
				throws SvException {
			return null;
		}

		@Override
		public List<String> invalidateLink(String linkName, Long linkedObjectId, SvReader svr, SvWriter svw)
				throws SvException {
			return null;
		}

		@Override
		public List<String> saveAndLink(Long parentId, Long objectId, String linkType, Long objectToBeLinkedTo,
				String locale, SvReader svr, SvWriter svw, SvLink svl) throws SvException {
			return null;
		}

		@Override
		public List<String> getParents() {
			return Arrays.asList("TEST_CLASS_A");
		}

		@Override
		public String getBusinessStartDate() {
			return "START_DATE";
		}

		@Override
		public String getBusinessEndDate() {
			return "END_DATE";
		}
	}

	private class TestModelFactory extends ModelFactory {

		@Override
		public BaseObjectModel createObject(String tableName) {
			BaseObjectModel obj = null;
			if (tableName.equals("TEST_CLASS_A")) {
				obj = new TestClassA();
			} else if (tableName.equals("TEST_CLASS_B")) {
				obj = new TestClassB();
			}
			return obj;
		}

	}

	@Test
	public void testIsWithinBusinessPeriod() {
		TestClassA objA = new TestClassA(Date.valueOf(LocalDate.now().minusDays(2)),
				Date.valueOf(LocalDate.now().plusDays(2)));
		assertTrue(BaseObjectModel.isWithinBusinessPeriod(objA));
	}

	@Test
	public void testIsNotWithinBusinessPeriod() {
		TestClassA objA = new TestClassA(Date.valueOf(LocalDate.now().minusDays(10)),
				Date.valueOf(LocalDate.now().minusDays(5)));
		assertFalse(BaseObjectModel.isWithinBusinessPeriod(objA));
	}
}
