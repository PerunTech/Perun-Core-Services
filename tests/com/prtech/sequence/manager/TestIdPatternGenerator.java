package com.prtech.sequence.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import com.prtech.menu.manager.CC;
import com.prtech.sequence.manager.SeqPatternExceptions.SeqPatternValidationError;

import org.apache.logging.log4j.Logger;
import com.google.gson.JsonObject;
import com.prtech.svarog.SvConf;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvSecurity;
import com.prtech.svarog.SvUtil;
import com.prtech.svarog.SvWriter;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbSearchCriterion;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;
import com.prtech.svarog_common.DbSearchExpression;

@Ignore
public class TestIdPatternGenerator {

	static final Logger log4j = SvConf.getLogger(TestIdPatternGenerator.class);

	static String getUserSessionIdForTestPurpose() {
		String sessionId = "";
		try (@SuppressWarnings("deprecation")
		SvSecurity svc = new SvSecurity();) {
			String userName = "ADMIN";
			String password = "welcome";
			if (SvConf.getParam("conn.string").equals("jdbc:oracle:thin:@192.168.9.60:1521:orclte19")) {
				password = "welcome";
			}
			sessionId = svc.logon(userName, SvUtil.getMD5(password));
		} catch (Exception e) {
			e.printStackTrace();
		}

		return sessionId;
	}

	private void setAutoCommit(List<SvCore> svcList, Boolean autoCommit) throws SvException {
		for (SvCore svc : svcList) {
			svc.setAutoCommit(autoCommit);
			svc.dbSetAutoCommit(autoCommit);
		}
	};

	private long getMaxSequenceValue(String sequenceKey, SvReader svr) throws SvException {
		Long seqTypeId = SvReader.getTypeIdByName("SVAROG_SEQUENCE");
		DbSearchCriterion keyCrit = new DbSearchCriterion("SEQUENCE_KEY", DbCompareOperand.EQUAL, sequenceKey);
		DbSearchExpression expr = new DbSearchExpression().addDbSearchItem(keyCrit);
		DbDataArray seqArr = svr.getObjects(expr, seqTypeId, null, null, null);
		assertNotNull("SVAROG_SEQUENCE query returned null", seqArr);
		if (seqArr.getItems().isEmpty()) {
			return 0;
		}
		return seqArr.getItems().stream().map(o -> o.getAsLong("SEQUENCE_VALUE")).max(Long::compareTo).orElse(0L);
	}

	/**
	 * Tests creation of a default sequence pattern. Ensures the pattern is saved
	 * and retrieved with correct configuration.
	 */
	@Test
	public void testCreateAndReadDefaultSeqPattern() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				setAutoCommit(Arrays.asList(svr, svw), false);
				JsonObject data = new JsonObject();
				data.addProperty(CC.SEQ_PATTERN_ID, "HLD-26");
				data.addProperty(CC.CONF_TABLE, "HOLDING");
				data.addProperty(CC.DEST_FIELD, "PIC");
				data.addProperty(CC.SEQ_PATTERN, "\"HLD\"--{currYear}-{currMonth}-{SvSeq}");
				data.addProperty(CC.IS_DEFAULT, true);
				DbDataObject created = SeqPatternService.saveSeqPatternHelper(data, svr, svw);
				assertNotNull(created);
				assertTrue(created.getObjectId() > 0);
				System.out.println(String.format("Created SEQ_PATTERN: objectId=%d, seqPatternId=%s",
						created.getObjectId(), created.getVal(CC.SEQ_PATTERN_ID)));
				DbDataObject read = SeqPatternService.getBySeqencePatternId("HLD-26", svr);
				assertNotNull(read);
				assertEquals("HOLDING", read.getVal(CC.CONF_TABLE));
				assertEquals("PIC", read.getVal(CC.DEST_FIELD));
				assertNull(read.getVal(CC.COND_FIELD));
				assertNull(read.getVal(CC.COND_OPERATOR));
				assertNull(read.getVal(CC.COND_VALUE));
				System.out.println(String.format(
						"SEQ_PATTERN_ID=%s, CONF_TABLE=%s, DEST_FIELD=%s, SEQ_PATTERN=%s, IS_DEFAULT=%s, COND_FIELD=%s, COND_OPERATOR=%s, COND_VALUE=%s",
						read.getVal(CC.SEQ_PATTERN_ID), read.getVal(CC.CONF_TABLE), read.getVal(CC.DEST_FIELD),
						read.getVal(CC.SEQ_PATTERN), read.getVal(CC.IS_DEFAULT), read.getVal(CC.COND_FIELD),
						read.getVal(CC.COND_OPERATOR), read.getVal(CC.COND_VALUE)));
			}
		}
	}

	/**
	 * Default pattern validation test: verifies that default patterns cannot
	 * contain values for COND_* fields
	 * 
	 * @throws Exception
	 */
	@Test
	public void testDefaultSeqPattern_conditionsNotAllowed() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				setAutoCommit(Arrays.asList(svr, svw), false);
				JsonObject data = new JsonObject();
				data.addProperty(CC.SEQ_PATTERN_ID, "HLD-26-INV");
				data.addProperty(CC.CONF_TABLE, "HOLDING");
				data.addProperty(CC.DEST_FIELD, "PIC");
				data.addProperty(CC.SEQ_PATTERN, "\"HLD\"-{currYear}-{SvSeq}");
				data.addProperty(CC.IS_DEFAULT, true);
				data.addProperty(CC.COND_FIELD, "NAME");
				SeqPatternValidationError ex = assertThrows(SeqPatternValidationError.class,
						() -> SeqPatternService.saveSeqPatternHelper(data, svr, svw));
				assertTrue(ex.getMessage().contains("Default pattern cannot have"));
			}
		}
	}

	/**
	 * Test creation of a non-default sequence pattern. Ensures the pattern is saved
	 * and retrieved with correct configuration.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testCreateAndReadNonDefaultSeqPattern() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				setAutoCommit(Arrays.asList(svr, svw), false);
				JsonObject data = new JsonObject();
				data.addProperty(CC.SEQ_PATTERN_ID, "ID-HLD-26");
				data.addProperty(CC.CONF_TABLE, "HOLDING");
				data.addProperty(CC.DEST_FIELD, "PIC");
				data.addProperty(CC.SEQ_PATTERN, "\"HLD\"--{MUNIC_CODE}--{currYear}-{SvSeq}");
				data.addProperty(CC.IS_DEFAULT, false);
				data.addProperty(CC.COND_FIELD, "NAME");
				data.addProperty(CC.COND_OPERATOR, "EQUAL");
				data.addProperty(CC.COND_VALUE, "PERUN PERUN");
				DbDataObject created = SeqPatternService.saveSeqPatternHelper(data, svr, svw);
				assertNotNull(created);
				assertTrue(created.getObjectId() > 0);
				System.out.println(String.format("Created SEQ_PATTERN: objectId=%d, seqPatternId=%s",
						created.getObjectId(), created.getVal(CC.SEQ_PATTERN_ID)));
				DbDataObject read = SeqPatternService.getBySeqencePatternId("ID-HLD-26", svr);
				assertNotNull(read);
				assertEquals("HOLDING", read.getVal(CC.CONF_TABLE));
				assertEquals("PIC", read.getVal(CC.DEST_FIELD));
				System.out.println(String.format(
						"SEQ_PATTERN_ID=%s, CONF_TABLE=%s, DEST_FIELD=%s, SEQ_PATTERN=%s, IS_DEFAULT=%s, COND_FIELD=%s, COND_OPERATOR=%s, COND_VALUE=%s",
						read.getVal(CC.SEQ_PATTERN_ID), read.getVal(CC.CONF_TABLE), read.getVal(CC.DEST_FIELD),
						read.getVal(CC.SEQ_PATTERN), read.getVal(CC.IS_DEFAULT), read.getVal(CC.COND_FIELD),
						read.getVal(CC.COND_OPERATOR), read.getVal(CC.COND_VALUE)));
			}
		}
	}

	/**
	 * Pattern format validation test verify rejection of invalid sequence patterns
	 */
	@Test
	public void testSeqPattern_invalidSeqPattern() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				setAutoCommit(Arrays.asList(svr, svw), false);
				JsonObject data = new JsonObject();
				data.addProperty(CC.SEQ_PATTERN_ID, "INVALID_NO_SVSEQ");
				data.addProperty(CC.CONF_TABLE, "HOLDING");
				data.addProperty(CC.DEST_FIELD, "PIC");
				data.addProperty(CC.SEQ_PATTERN, "\"HLD\"--{MUNIC_CODE}--{currYear}");
				data.addProperty(CC.IS_DEFAULT, false);
				data.addProperty(CC.COND_FIELD, "NAME");
				data.addProperty(CC.COND_OPERATOR, "EQUAL");
				data.addProperty(CC.COND_VALUE, "PERUN PERUN");
				SeqPatternValidationError ex = assertThrows(SeqPatternValidationError.class,
						() -> SeqPatternService.saveSeqPatternHelper(data, svr, svw));
				assertEquals("SEQ_PATTERN must end with SvSeq}", ex.getMessage());
			}
		}
	}

	/**
	 * Pattern format validation test verify rejection of invalid sequence patterns
	 */
	@Test
	public void testSeqPattern_requiresSvSeqSuffix() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				setAutoCommit(Arrays.asList(svr, svw), false);
				JsonObject data = new JsonObject();
				data.addProperty(CC.SEQ_PATTERN_ID, "INVALID_NO_SVSEQ");
				data.addProperty(CC.CONF_TABLE, "HOLDING");
				data.addProperty(CC.DEST_FIELD, "PIC");
				data.addProperty(CC.SEQ_PATTERN, "\"HLD\"--{MUNIC_CODE}--{currYear}");
				data.addProperty(CC.IS_DEFAULT, false);
				data.addProperty(CC.COND_FIELD, "NAME");
				data.addProperty(CC.COND_OPERATOR, "EQUAL");
				data.addProperty(CC.COND_VALUE, "PERUN PERUN");
				try {
					SeqPatternService.saveSeqPatternHelper(data, svr, svw);
					fail("Expected SeqPatternValidationError due to missing {SvSeq}");
				} catch (SeqPatternValidationError ex) {
					assertEquals("SEQ_PATTERN must end with SvSeq}", ex.getMessage());
				}
			}
		}

	}

	/**
	 * Duplicate default protection test: duplicate default insertion is rejected
	 * with an exception SeqPatternDuplicateError
	 */
	@Test
	public void testDefaultSeqPattern_duplicateDefaultRejected() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				setAutoCommit(Arrays.asList(svr, svw), false);
				JsonObject firstDefault = new JsonObject();
				firstDefault.addProperty(CC.SEQ_PATTERN_ID, "DEF_DUP_1");
				firstDefault.addProperty(CC.CONF_TABLE, "HOLDING");
				firstDefault.addProperty(CC.DEST_FIELD, "PIC");
				firstDefault.addProperty(CC.SEQ_PATTERN, "\"HLD\"-{currYear}-{SvSeq}");
				firstDefault.addProperty(CC.IS_DEFAULT, true);
				SeqPatternService.saveSeqPatternHelper(firstDefault, svr, svw);
				JsonObject secondDefault = new JsonObject();
				secondDefault.addProperty(CC.SEQ_PATTERN_ID, "DEF_DUP_2");
				secondDefault.addProperty(CC.CONF_TABLE, "HOLDING");
				secondDefault.addProperty(CC.DEST_FIELD, "PIC");
				secondDefault.addProperty(CC.SEQ_PATTERN, "\"NEW_PATTERN_HLD\"-{currYear}-{SvSeq}");
				secondDefault.addProperty(CC.IS_DEFAULT, true);
				assertEquals("Only one default row is allowed per table and destination",
						assertThrows(SeqPatternExceptions.SeqPatternDuplicateError.class,
								() -> SeqPatternService.saveSeqPatternHelper(secondDefault, svr, svw)).getMessage());
			}
		}
	}

	/**
	 * 
	 * Returns the correct matching pattern
	 * 
	 * @throws Exception
	 */
	@Test
	public void testFindMatchingPattern() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				DbDataObject row = new DbDataObject();
				row.setObjectType(SvReader.getTypeIdByName("ANIMAL"));
				DbDataObject matchingPattern = SeqPatternService.findMatchingPattern(row, "ANIMAL", "PRODUCTION_TYPE",
						svr);
				String seqPattern = matchingPattern.getVal(CC.SEQ_PATTERN).toString();
				System.out.println("The matching pattern is: " + seqPattern);

			}
		}
	}

	@Test
	public void testGenerateNonDefaultId() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				DbDataObject row = new DbDataObject();
				row.setObjectType(SvReader.getTypeIdByName("ANIMAL"));
				row.setVal("GENDER", "F");
				row.setVal("ANIMAL_CLASS", "01");
				row.setVal("ANIMAL_RACE", "01001");
				String generatedId = SeqPatternService.generateSequenceId(row, "ANIMAL", "PRODUCTION_TYPE", svr);
				System.out.println("Generated non-default ID: " + generatedId);
				assertNotNull(generatedId);
				assertFalse(generatedId.isBlank());
				assertTrue("Generated ID should start with AN", generatedId.startsWith("\"AN\""));
				assertTrue("Generated ID should containt business key animal class", generatedId.contains("--01--"));
				assertTrue(generatedId.contains(String.valueOf(LocalDate.now().getYear())));
			} catch (SvException e) {
				log4j.error("Error generating non-default ID", e);
				throw e;
			}
		}
	}

	/**
	 * 
	 * This test returns all patterns for given CONF_TABLE + DEST_FIELD
	 * 
	 * @throws Exception
	 */
	@Test
	public void testGetByConfTableDestField() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				setAutoCommit(Arrays.asList(svr, svw), false);
				JsonObject data = new JsonObject();
				data.addProperty(CC.SEQ_PATTERN_ID, "HLD-26");
				data.addProperty(CC.CONF_TABLE, "HOLDING");
				data.addProperty(CC.DEST_FIELD, "PIC");
				data.addProperty(CC.SEQ_PATTERN, "\"HLD\"--{currYear}-{SvSeq}");
				data.addProperty(CC.IS_DEFAULT, true);
				DbDataObject created = SeqPatternService.saveSeqPatternHelper(data, svr, svw);
				assertNotNull(created);
				assertTrue(created.getObjectId() > 0);
				System.out.println(String.format("Created SEQ_PATTERN: objectId=%d, seqPatternId=%s",
						created.getObjectId(), created.getVal(CC.SEQ_PATTERN_ID)));
				DbDataArray read = SeqPatternService.getByTableAndDestinationField("HOLDING", "PIC", svr);
				for (DbDataObject obj : read.getItems()) {
					System.out.println(String.format(
							"Object: SEQ_PATTERN_ID=%s, CONF_TABLE=%s, DEST_FIELD=%s, SEQ_PATTERN=%s, IS_DEFAULT=%s, COND_FIELD=%s, COND_OPERATOR=%s, COND_VALUE=%s",
							obj.getVal(CC.SEQ_PATTERN_ID), obj.getVal(CC.CONF_TABLE), obj.getVal(CC.DEST_FIELD),
							obj.getVal(CC.SEQ_PATTERN), obj.getVal(CC.IS_DEFAULT), obj.getVal(CC.COND_FIELD),
							obj.getVal(CC.COND_OPERATOR), obj.getVal(CC.COND_VALUE)));
					assertNotNull(read);
					assertEquals("HOLDING", obj.getVal(CC.CONF_TABLE));
					assertEquals("PIC", obj.getVal(CC.DEST_FIELD));
				}
			} catch (SvException e) {
				log4j.error("Error in testGetByConfTableDestField", e);
				throw e;
			}
		}
	}

	@Test
	public void testGenerateNonDefaultIdWithOfficeCode() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				DbDataObject row = new DbDataObject();
				row.setObjectType(SvReader.getTypeIdByName("HOLDING"));
				Long orgUnitTypeId = SvReader.getTypeIdByName("SVAROG_ORG_UNITS");
				DbSearchCriterion crit = new DbSearchCriterion("OBJECT_ID", DbCompareOperand.EQUAL, 49);
				DbSearchExpression expr = new DbSearchExpression().addDbSearchItem(crit);
				DbDataArray orgUnits = svr.getObjects(expr, orgUnitTypeId, null, null, null);
				assertFalse("ORG_UNIT 'HEADQUARTER' must exist", orgUnits.getItems().isEmpty());
				String orgUnitName = orgUnits.getItems().get(0).getVal("NAME").toString();
				row.setVal("OFFICE_CODE", orgUnitName);
				row.setVal("NAME", "PERUN PERUN");
				String generatedId = SeqPatternService.generateSequenceId(row, "HOLDING", "PIC", svr);
				System.out.println("Generated non-default ID: " + generatedId);
				assertNotNull(generatedId);
				assertFalse(generatedId.isBlank());
				assertTrue("Generated ID should start with AN", generatedId.startsWith("\"HLD\""));
				assertTrue(generatedId.contains(String.valueOf(LocalDate.now().getYear())));
			} catch (SvException e) {
				log4j.error("Error generating non-default ID", e);
				throw e;
			}
		}
	}

	/**
	 * Deletes pattern by SEQ_PATTERN_ID
	 * 
	 * @throws Exception
	 */
	@Test
	public void testDeleteBySeqPatternId() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				setAutoCommit(Arrays.asList(svr, svw), false);
				String seq_pattern_id = "UNQ_ID_HOLDING";
				DbDataObject deleted = SeqPatternService.deleteByUniqueSeqPatternID(seq_pattern_id, svr, svw);
				assertEquals(seq_pattern_id, deleted.getVal(CC.SEQ_PATTERN_ID));
				System.out.println("Deleted SV_ID_SEQ_PATTERN with seq_pattern_id = " + seq_pattern_id);
			} catch (SvException e) {
				log4j.error("Error while deleting the object", e);
				throw e;
			}
		}
	}

	/**
	 * This test updates existing pattern value for SEQ_PATTERN_ID with new pattern
	 * value
	 * 
	 * @throws Exception
	 */
	@Test
	public void testUpdateSeqPatternValue() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				setAutoCommit(Arrays.asList(svr, svw), false);
				String seqPatternId = "UNQ_ID_ANIMAL";
				String newSeqPattern = "\"AN\"--{ANIMAL_CLASS}--{ANIMAL_RACE}--{currYear}--{SvSeq}";
				DbDataObject updatedObj = SeqPatternService.updateSeqPatternValue(seqPatternId, newSeqPattern, svr,
						svw);
				assertNotNull(updatedObj);
				assertEquals(seqPatternId, updatedObj.getVal(CC.SEQ_PATTERN_ID));
				assertEquals(newSeqPattern, updatedObj.getVal(CC.SEQ_PATTERN));
				System.out.println("Updated SEQ_PATTERN for ID=" + seqPatternId + "' to '" + newSeqPattern + "'");

			} catch (SvException e) {
				log4j.error("Error in testUpdateSeqPatternValue", e);
				throw e;
			}
		}
	}

	@Test
	public void testGenerateDefaultPatternwithDateIdCheckSvSeq() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				setAutoCommit(Arrays.asList(svr, svw), false);
				DbDataObject row = new DbDataObject();
				row.setObjectType(SvReader.getTypeIdByName("HOLDING"));
				String sequenceKey = "\"HLD\"--" + LocalDate.now().getYear() + "-"
						+ String.format("%02d", LocalDate.now().getMonthValue()) + "-";
				long maxSeqValue = getMaxSequenceValue(sequenceKey, svr);
				long expectedNextSeq = maxSeqValue + 1;
				String generatedId = SeqPatternService.generateSequenceId(row, "HOLDING", "PIC", svr);
				System.out.println("Generated default ID: " + generatedId);
				assertNotNull(generatedId);
				assertFalse(generatedId.isBlank());
				assertTrue(generatedId.startsWith("\"HLD\""));
				assertTrue("Expected ID to end with sequence value " + expectedNextSeq + " but was: " + generatedId,
						generatedId.endsWith(String.valueOf(expectedNextSeq)));
			}
		}
	}

	/**
	 * Tests creation of a non-default sequence pattern. Ensures the pattern is
	 * saved and retrieved with correct configuration.
	 */
	@Test
	public void testSaveAndGetNonDefaultSeqPattern() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				setAutoCommit(Arrays.asList(svr, svw), false);
				JsonObject data = new JsonObject();
				data.addProperty(CC.SEQ_PATTERN_ID, "ID_HOLDING_SLAUGHTERHOUSE");
				data.addProperty(CC.CONF_TABLE, "HOLDING");
				data.addProperty(CC.DEST_FIELD, "PIC");
				data.addProperty(CC.SEQ_PATTERN, "\"HLD\"--{OFFICE_CODE}--{currYear}/{currMonth}/{currDay}-{SvSeq}");
				data.addProperty(CC.IS_DEFAULT, false);
				data.addProperty(CC.COND_FIELD, "NAME");
				data.addProperty(CC.COND_OPERATOR, "EQUAL");
				data.addProperty(CC.COND_VALUE, "PERUN PERUN");
				DbDataObject created = SeqPatternService.saveSeqPatternHelper(data, svr, svw);
				assertNotNull(created);
				assertTrue(created.getObjectId() > 0);
				System.out.println(String.format("Created SEQ_PATTERN: objectId=%d, seqPatternId=%s",
						created.getObjectId(), created.getVal(CC.SEQ_PATTERN_ID)));
				DbDataObject read = SeqPatternService.getBySeqencePatternId("ID_HOLDING_SLAUGHTERHOUSE", svr);
				assertNotNull(read);
				assertEquals("HOLDING", read.getVal(CC.CONF_TABLE));
				assertEquals("PIC", read.getVal(CC.DEST_FIELD));
				System.out.println(String.format(
						"SEQ_PATTERN_ID=%s, CONF_TABLE=%s, DEST_FIELD=%s, SEQ_PATTERN=%s, IS_DEFAULT=%s, COND_FIELD=%s, COND_OPERATOR=%s, COND_VALUE=%s",
						read.getVal(CC.SEQ_PATTERN_ID), read.getVal(CC.CONF_TABLE), read.getVal(CC.DEST_FIELD),
						read.getVal(CC.SEQ_PATTERN), read.getVal(CC.IS_DEFAULT), read.getVal(CC.COND_FIELD),
						read.getVal(CC.COND_OPERATOR), read.getVal(CC.COND_VALUE)));
			}
		}
	}

	@Test
	public void testGenerateDefaultIdCheckSvSeq() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				setAutoCommit(Arrays.asList(svr, svw), false);
				DbDataObject row = new DbDataObject();
				row.setObjectType(SvReader.getTypeIdByName("ANIMAL"));
				String sequenceKey = "\"ANIMAL\"--";
				long maxSeqValue = getMaxSequenceValue(sequenceKey, svr);
				long expectedNextSeq = maxSeqValue + 1;
				String generatedId = SeqPatternService.generateSequenceId(row, "ANIMAL", "PRODUCTION_TYPE", svr);
				System.out.println("Generated default ID: " + generatedId);
				assertNotNull(generatedId);
				assertFalse(generatedId.isBlank());
				assertTrue(generatedId.startsWith("\"ANIMAL\""));
				assertTrue("Expected ID to end with sequence value " + expectedNextSeq + " but was: " + generatedId,
						generatedId.endsWith(String.valueOf(expectedNextSeq)));
			}
		}
	}

	@Test
	public void testGenerateNonDefaultIdcheckSvSeq() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				setAutoCommit(Arrays.asList(svr, svw), false);
				DbDataObject row = new DbDataObject();
				row.setObjectType(SvReader.getTypeIdByName("ANIMAL"));
				row.setVal("GENDER", "F");
				row.setVal("ANIMAL_CLASS", "01");
				row.setVal("ANIMAL_RACE", "01001");
				String sequenceKey = "\"AN\"--01--01001--" + LocalDate.now().getYear() + "--";
				long maxSeqValue = getMaxSequenceValue(sequenceKey, svr);
				long expectedNextSeq = maxSeqValue + 1;
				String generatedId = SeqPatternService.generateSequenceId(row, "ANIMAL", "PRODUCTION_TYPE", svr);
				System.out.println("Generated non-default ID: " + generatedId);
				assertNotNull(generatedId);
				assertFalse(generatedId.isBlank());
				assertTrue(generatedId.startsWith("\"AN\""));
				assertTrue(generatedId.contains("--01--"));
				assertTrue("Expected ID to end with sequence value " + expectedNextSeq + " but was: " + generatedId,
						generatedId.endsWith(String.valueOf(expectedNextSeq)));
			}
		}
	}

	@Test
	public void testGetDestFieldsByConfTable() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId)) {
				List<String> destFields = SeqPatternService.getDestFieldsByConfTableNoDuplicates("HOLDING", svr);
				assertNotNull(destFields);
				assertFalse(destFields.isEmpty());
				for (String field : destFields) {
					assertNotNull(field);
					assertFalse(field.trim().isEmpty());
					System.out.println("DEST_FIELD = " + field);
				}
			}
		}
	}

	/**
	 * Tests saving of a conditional, non-default sequence pattern for LabSample.
	 * Ensures the pattern is correctly persisted and retrieved with its condition.
	 */
	@Test
	public void testSaveNonDefaultSeqPatternforLabSample() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				setAutoCommit(Arrays.asList(svr, svw), false);
				JsonObject data = new JsonObject();
				data.addProperty(CC.SEQ_PATTERN_ID, "LAB_SAMPLE_SV_SEQ");
				data.addProperty(CC.CONF_TABLE, "LAB_SAMPLE");
				data.addProperty(CC.DEST_FIELD, "SAMPLE_ID");
				data.addProperty(CC.SEQ_PATTERN, "\"TEST-LAB\"-{currYear}-{SvSeq}");
				data.addProperty(CC.IS_DEFAULT, false);
				data.addProperty(CC.COND_FIELD, "SAMPLE_TYPE");
				data.addProperty(CC.COND_OPERATOR, "EQUAL");
				data.addProperty(CC.COND_VALUE, "URINE");
				DbDataObject created = SeqPatternService.saveSeqPatternHelper(data, svr, svw);
				assertNotNull(created);
				assertTrue(created.getObjectId() > 0);
				System.out.println(String.format("Created SEQ_PATTERN: objectId=%d, seqPatternId=%s",
						created.getObjectId(), created.getVal(CC.SEQ_PATTERN_ID)));
				DbDataObject read = SeqPatternService.getBySeqencePatternId("LAB_SAMPLE_SV_SEQ", svr);
				assertNotNull(read);
				assertEquals("LAB_SAMPLE", read.getVal(CC.CONF_TABLE));
				assertEquals("SAMPLE_ID", read.getVal(CC.DEST_FIELD));
				System.out.println(String.format(
						"SEQ_PATTERN_ID=%s, CONF_TABLE=%s, DEST_FIELD=%s, SEQ_PATTERN=%s, IS_DEFAULT=%s, COND_FIELD=%s, COND_OPERATOR=%s, COND_VALUE=%s",
						read.getVal(CC.SEQ_PATTERN_ID), read.getVal(CC.CONF_TABLE), read.getVal(CC.DEST_FIELD),
						read.getVal(CC.SEQ_PATTERN), read.getVal(CC.IS_DEFAULT), read.getVal(CC.COND_FIELD),
						read.getVal(CC.COND_OPERATOR), read.getVal(CC.COND_VALUE)));
			}
		}
	}

	/**
	 * Tests creation of a default 5-digit sequence pattern for LabSample. Ensures
	 * the pattern is saved and retrieved with correct configuration.
	 */
	@Test
	public void testCreateDefaultSeqPatternForLabSample() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				setAutoCommit(Arrays.asList(svr, svw), false);
				JsonObject data = new JsonObject();
				data.addProperty(CC.SEQ_PATTERN_ID, "LAB_SAMPLE_ID");
				data.addProperty(CC.CONF_TABLE, "LAB_SAMPLE");
				data.addProperty(CC.DEST_FIELD, "SAMPLE_ID");
				data.addProperty(CC.SEQ_PATTERN, "LAB-{currYear2}-{5SvSeq}");
				data.addProperty(CC.IS_DEFAULT, true);
				DbDataObject created = SeqPatternService.saveSeqPatternHelper(data, svr, svw);
				assertNotNull(created);
				assertTrue(created.getObjectId() > 0);
				System.out.println(String.format("Created SEQ_PATTERN: objectId=%d, seqPatternId=%s",
						created.getObjectId(), created.getVal(CC.SEQ_PATTERN_ID)));
				DbDataObject read = SeqPatternService.getBySeqencePatternId("LAB_SAMPLE_ID", svr);
				assertNotNull(read);
				assertEquals("LAB_SAMPLE", read.getVal(CC.CONF_TABLE));
				assertEquals("SAMPLE_ID", read.getVal(CC.DEST_FIELD));
				assertNull(read.getVal(CC.COND_FIELD));
				assertNull(read.getVal(CC.COND_OPERATOR));
				assertNull(read.getVal(CC.COND_VALUE));
				System.out.println(String.format(
						"SEQ_PATTERN_ID=%s, CONF_TABLE=%s, DEST_FIELD=%s, SEQ_PATTERN=%s, IS_DEFAULT=%s, COND_FIELD=%s, COND_OPERATOR=%s, COND_VALUE=%s",
						read.getVal(CC.SEQ_PATTERN_ID), read.getVal(CC.CONF_TABLE), read.getVal(CC.DEST_FIELD),
						read.getVal(CC.SEQ_PATTERN), read.getVal(CC.IS_DEFAULT), read.getVal(CC.COND_FIELD),
						read.getVal(CC.COND_OPERATOR), read.getVal(CC.COND_VALUE)));
			}
		}
	}

	/**
	 * Tests creation of a default 5-digit sequence pattern for SampleEvent. Ensures
	 * the pattern is saved and retrieved with correct configuration.
	 */
	@Test
	public void testCreateDefaultSeqPatternForSampleEvent() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				setAutoCommit(Arrays.asList(svr, svw), false);
				JsonObject data = new JsonObject();
				data.addProperty(CC.SEQ_PATTERN_ID, "SAMPLE_EVENT_ID_SQ");
				data.addProperty(CC.CONF_TABLE, "SAMPLE_EVENT");
				data.addProperty(CC.DEST_FIELD, "EVENT_ID");
				data.addProperty(CC.SEQ_PATTERN, "SE-{5SvSeq}");
				data.addProperty(CC.IS_DEFAULT, true);
				DbDataObject created = SeqPatternService.saveSeqPatternHelper(data, svr, svw);
				assertNotNull(created);
				assertTrue(created.getObjectId() > 0);
				System.out.println(String.format("Created SEQ_PATTERN: objectId=%d, seqPatternId=%s",
						created.getObjectId(), created.getVal(CC.SEQ_PATTERN_ID)));
				DbDataObject read = SeqPatternService.getBySeqencePatternId("SAMPLE_EVENT_ID_SQ", svr);
				assertNotNull(read);
				assertEquals("SAMPLE_EVENT", read.getVal(CC.CONF_TABLE));
				assertEquals("EVENT_ID", read.getVal(CC.DEST_FIELD));
				assertNull(read.getVal(CC.COND_FIELD));
				assertNull(read.getVal(CC.COND_OPERATOR));
				assertNull(read.getVal(CC.COND_VALUE));
				System.out.println(String.format(
						"SEQ_PATTERN_ID=%s, CONF_TABLE=%s, DEST_FIELD=%s, SEQ_PATTERN=%s, IS_DEFAULT=%s, COND_FIELD=%s, COND_OPERATOR=%s, COND_VALUE=%s",
						read.getVal(CC.SEQ_PATTERN_ID), read.getVal(CC.CONF_TABLE), read.getVal(CC.DEST_FIELD),
						read.getVal(CC.SEQ_PATTERN), read.getVal(CC.IS_DEFAULT), read.getVal(CC.COND_FIELD),
						read.getVal(CC.COND_OPERATOR), read.getVal(CC.COND_VALUE)));
			}
		}
	}

	/**
	 * Tests creation of a default 3-digit sequence pattern for EpiCase. Ensures the
	 * pattern is saved and retrieved with correct configuration.
	 */
	@Test
	public void testCreateDefaultSeqPatternForEpiCase() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				setAutoCommit(Arrays.asList(svr, svw), false);
				JsonObject data = new JsonObject();
				data.addProperty(CC.SEQ_PATTERN_ID, "EPI_CASE_ID_SQ");
				data.addProperty(CC.CONF_TABLE, "EPI_CASE");
				data.addProperty(CC.DEST_FIELD, "EPI_CASE_ID");
				data.addProperty(CC.SEQ_PATTERN, "{currYear}{currMonth}{currDay}/{3SvSeq}");
				data.addProperty(CC.IS_DEFAULT, true);
				DbDataObject created = SeqPatternService.saveSeqPatternHelper(data, svr, svw);
				assertNotNull(created);
				assertTrue(created.getObjectId() > 0);
				System.out.println(String.format("Created SEQ_PATTERN: objectId=%d, seqPatternId=%s",
						created.getObjectId(), created.getVal(CC.SEQ_PATTERN_ID)));
				DbDataObject read = SeqPatternService.getBySeqencePatternId("EPI_CASE_ID_SQ", svr);
				assertNotNull(read);
				assertEquals("EPI_CASE", read.getVal(CC.CONF_TABLE));
				assertEquals("EPI_CASE_ID", read.getVal(CC.DEST_FIELD));
				assertNull(read.getVal(CC.COND_FIELD));
				assertNull(read.getVal(CC.COND_OPERATOR));
				assertNull(read.getVal(CC.COND_VALUE));
				System.out.println(String.format(
						"SEQ_PATTERN_ID=%s, CONF_TABLE=%s, DEST_FIELD=%s, SEQ_PATTERN=%s, IS_DEFAULT=%s, COND_FIELD=%s, COND_OPERATOR=%s, COND_VALUE=%s",
						read.getVal(CC.SEQ_PATTERN_ID), read.getVal(CC.CONF_TABLE), read.getVal(CC.DEST_FIELD),
						read.getVal(CC.SEQ_PATTERN), read.getVal(CC.IS_DEFAULT), read.getVal(CC.COND_FIELD),
						read.getVal(CC.COND_OPERATOR), read.getVal(CC.COND_VALUE)));
			}
		}
	}

	/**
	 * Tests creation of a default 3-digit sequence pattern for EpiNotification.
	 * Ensures the pattern is saved and retrieved with correct configuration.
	 */
	@Test
	public void testCreateDefaultSeqPatternForEpiNotification() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				setAutoCommit(Arrays.asList(svr, svw), false);
				JsonObject data = new JsonObject();
				data.addProperty(CC.SEQ_PATTERN_ID, "EPI_NOTF_ID_SQ");
				data.addProperty(CC.CONF_TABLE, "EPI_NOTIFICATION");
				data.addProperty(CC.DEST_FIELD, "REFERENCE_NUMBER");
				data.addProperty(CC.SEQ_PATTERN, "{currYear}{currMonth}{currDay}/000/{3SvSeq}");
				data.addProperty(CC.IS_DEFAULT, true);
				DbDataObject created = SeqPatternService.saveSeqPatternHelper(data, svr, svw);
				assertNotNull(created);
				assertTrue(created.getObjectId() > 0);
				System.out.println(String.format("Created SEQ_PATTERN: objectId=%d, seqPatternId=%s",
						created.getObjectId(), created.getVal(CC.SEQ_PATTERN_ID)));
				DbDataObject read = SeqPatternService.getBySeqencePatternId("EPI_NOTF_ID_SQ", svr);
				assertNotNull(read);
				assertEquals("EPI_NOTIFICATION", read.getVal(CC.CONF_TABLE));
				assertEquals("REFERENCE_NUMBER", read.getVal(CC.DEST_FIELD));
				assertNull(read.getVal(CC.COND_FIELD));
				assertNull(read.getVal(CC.COND_OPERATOR));
				assertNull(read.getVal(CC.COND_VALUE));
				System.out.println(String.format(
						"SEQ_PATTERN_ID=%s, CONF_TABLE=%s, DEST_FIELD=%s, SEQ_PATTERN=%s, IS_DEFAULT=%s, COND_FIELD=%s, COND_OPERATOR=%s, COND_VALUE=%s",
						read.getVal(CC.SEQ_PATTERN_ID), read.getVal(CC.CONF_TABLE), read.getVal(CC.DEST_FIELD),
						read.getVal(CC.SEQ_PATTERN), read.getVal(CC.IS_DEFAULT), read.getVal(CC.COND_FIELD),
						read.getVal(CC.COND_OPERATOR), read.getVal(CC.COND_VALUE)));
			}
		}
	}

	/**
	 * Tests creation of a default 5-digit sequence pattern for Documents. Ensures
	 * the pattern is saved and retrieved with correct configuration.
	 */
	@Test
	public void testCreateDefaultSeqPatternForFarmRegDocuments() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				setAutoCommit(Arrays.asList(svr, svw), false);
				JsonObject data = new JsonObject();
				data.addProperty(CC.SEQ_PATTERN_ID, "DOC_ID_SQ");
				data.addProperty(CC.CONF_TABLE, "DOCUMENTS");
				data.addProperty(CC.DEST_FIELD, "NOTE");
				data.addProperty(CC.SEQ_PATTERN, "DOC-{5SvSeq}");
				data.addProperty(CC.IS_DEFAULT, true);
				DbDataObject created = SeqPatternService.saveSeqPatternHelper(data, svr, svw);
				assertNotNull(created);
				assertTrue(created.getObjectId() > 0);
				System.out.println(String.format("Created SEQ_PATTERN: objectId=%d, seqPatternId=%s",
						created.getObjectId(), created.getVal(CC.SEQ_PATTERN_ID)));
				DbDataObject read = SeqPatternService.getBySeqencePatternId("DOC_ID_SQ", svr);
				assertNotNull(read);
				assertEquals("DOCUMENTS", read.getVal(CC.CONF_TABLE));
				assertEquals("NOTE", read.getVal(CC.DEST_FIELD));
				assertNull(read.getVal(CC.COND_FIELD));
				assertNull(read.getVal(CC.COND_OPERATOR));
				assertNull(read.getVal(CC.COND_VALUE));
				System.out.println(String.format(
						"SEQ_PATTERN_ID=%s, CONF_TABLE=%s, DEST_FIELD=%s, SEQ_PATTERN=%s, IS_DEFAULT=%s, COND_FIELD=%s, COND_OPERATOR=%s, COND_VALUE=%s",
						read.getVal(CC.SEQ_PATTERN_ID), read.getVal(CC.CONF_TABLE), read.getVal(CC.DEST_FIELD),
						read.getVal(CC.SEQ_PATTERN), read.getVal(CC.IS_DEFAULT), read.getVal(CC.COND_FIELD),
						read.getVal(CC.COND_OPERATOR), read.getVal(CC.COND_VALUE)));
			}
		}
	}
}
