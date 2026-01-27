package com.prtech.sequence.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;
import static org.junit.Assert.assertTrue;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import com.prtech.menu.manager.CC;
import com.prtech.sequence.manager.SeqPatternExceptions.SeqPatternError;

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

	@Test
	public void testSaveDefaultSeqPattern() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				setAutoCommit(Arrays.asList(svr, svw), false);
				JsonObject data = new JsonObject();
				data.addProperty(CC.SEQ_PATTERN_ID, "ID_DEFAULT_HODLING");
				data.addProperty(CC.CONF_TABLE, "HOLDING");
				data.addProperty(CC.DEST_FIELD, "PIC");
				data.addProperty(CC.SEQ_PATTERN, "\"HLD\"--{currYear}--{SvSeq}");
				data.addProperty(CC.IS_DEFAULT, true);
				DbDataObject dbo = SeqPatternService.saveSeqPatternHelper(data, svr, svw);
				assertNotNull(dbo);
				assertTrue(dbo.getObjectId() > 0);
				assertEquals(true, dbo.getAsBoolean(CC.IS_DEFAULT));
				assertNull(dbo.getVal(CC.COND_FIELD));
				assertNull(dbo.getVal(CC.COND_OPERATOR));
				assertNull(dbo.getVal(CC.COND_VALUE));

				System.out.println("Saved default SEQ_PATTERN object_id = " + dbo.getObjectId());
			} catch (SvException e) {
				log4j.error("Couldn't save SV_ID_SEQ_PATTERN", e);
			}
		} catch (Exception e) {
			log4j.error("Error", e);
		}
	}

	@Test
	public void testSaveNOTDefaultSeqPattern() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				setAutoCommit(Arrays.asList(svr, svw), false);
				JsonObject data = new JsonObject();
				data.addProperty(CC.SEQ_PATTERN_ID, "UNQ_ID_ANIMAL");
				data.addProperty(CC.CONF_TABLE, "ANIMAL");
				data.addProperty(CC.DEST_FIELD, "PRODUCTION_TYPE");
				data.addProperty(CC.SEQ_PATTERN, "\"AN\"{ANIMAL_CLASS}--{ANIMAL_RACE}--{currYear}--{SvSeq}");
				data.addProperty(CC.IS_DEFAULT, false);
				data.addProperty(CC.COND_FIELD, "GENDER");
				data.addProperty(CC.COND_OPERATOR, "EQUAL");
				data.addProperty(CC.COND_VALUE, "F");
				DbDataObject dbo = SeqPatternService.saveSeqPatternHelper(data, svr, svw);
				assertNotNull(dbo);
				assertTrue(dbo.getObjectId() > 0);
				System.out.println("Saved default SEQ_PATTERN object_id = " + dbo.getObjectId());

			} catch (SvException e) {
				log4j.error("Couldn't save SV_ID_SEQ_PATTERN", e);
			}
		} catch (Exception e) {
			log4j.error("Error", e);
		}
	}

	@Test
	public void testGetBySeqPatternId() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId);) {
				DbDataObject readObj = SeqPatternService.getBySeqencePatternId("ID_DEFAULT_HODLING", svr);
				assertNotNull(readObj);
				assertEquals("ID_DEFAULT_HODLING", readObj.getVal(CC.SEQ_PATTERN_ID));
				assertEquals("HOLDING", readObj.getVal(CC.CONF_TABLE));
				assertEquals("PIC", readObj.getVal(CC.DEST_FIELD));
				System.out.println(String.format(
						"SEQ_PATTERN_ID=%s, CONF_TABLE=%s, DEST_FIELD=%s, SEQ_PATTERN=%s, IS_DEFAULT=%s, COND_FIELD=%s, COND_OPERATOR=%s, COND_VALUE=%s",
						readObj.getVal(CC.SEQ_PATTERN_ID), readObj.getVal(CC.CONF_TABLE), readObj.getVal(CC.DEST_FIELD),
						readObj.getVal(CC.SEQ_PATTERN), readObj.getVal(CC.IS_DEFAULT), readObj.getVal(CC.COND_FIELD),
						readObj.getVal(CC.COND_OPERATOR), readObj.getVal(CC.COND_VALUE)));
			} catch (SvException e) {
				log4j.error("Error in testGetBySeqPatternId", e);
				throw e;
			}
		}
	}

	@Test
	public void testGetByConfTableDestField() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId)) {
				DbDataArray readObjs = SeqPatternService.getByTableAndDestinationField("HOLDING", "PIC", svr);
				for (DbDataObject obj : readObjs.getItems()) {
					System.out.println(String.format(
							"Object: SEQ_PATTERN_ID=%s, CONF_TABLE=%s, DEST_FIELD=%s, SEQ_PATTERN=%s, IS_DEFAULT=%s, COND_FIELD=%s, COND_OPERATOR=%s, COND_VALUE=%s",
							obj.getVal(CC.SEQ_PATTERN_ID), obj.getVal(CC.CONF_TABLE), obj.getVal(CC.DEST_FIELD),
							obj.getVal(CC.SEQ_PATTERN), obj.getVal(CC.IS_DEFAULT), obj.getVal(CC.COND_FIELD),
							obj.getVal(CC.COND_OPERATOR), obj.getVal(CC.COND_VALUE)));

					assertNotNull(readObjs);
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
	public void testGenerateNonDefaultIdForAnimal() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				DbDataObject row = new DbDataObject();
				row.setObjectType(SvReader.getTypeIdByName("ANIMAL"));
				row.setVal("GENDER", "F");
				row.setVal("ANIMAL_CLASS", "01");
				row.setVal("ANIMAL_RACE", "01001");
				String generatedId = SeqPatternService.generateId(row, "ANIMAL", "PRODUCTION_TYPE", svr);
				System.out.println("Generated non-default ID: " + generatedId);
				assertNotNull(generatedId);
				assertTrue(generatedId.contains(String.valueOf(LocalDate.now().getYear())));
			} catch (SvException e) {
				log4j.error("Error generating non-default ID", e);
				throw e;
			}
		}
	}

	@Test
	public void testGenerateDefaultId() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				DbDataObject row = new DbDataObject();
				row.setObjectType(SvReader.getTypeIdByName("HOLDING"));
				String generatedId = SeqPatternService.generateId(row, "HOLDING", "PIC", svr);
				System.out.println("Generated default ID: " + generatedId);
				assertNotNull(generatedId);
			} catch (SvException e) {
				log4j.error("Error generating non-default ID", e);
				throw e;
			}
		}
	}

	@Test
	public void testGenerateNonExistingPatternId() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				DbDataObject row = new DbDataObject();
				row.setObjectType(SvReader.getTypeIdByName("HOLDING"));
				row.setVal("STATUS", "VERIFIED");
				String generatedId = SeqPatternService.generateId(row, "HOLDING", "ANIMAL_CODE", svr);
				System.out.println("Generated non-default ID: " + generatedId);
				assertNotNull(generatedId);
			} catch (SvException e) {
				log4j.error("Error generating non-default ID", e);
				throw e;
			}
		}
	}

	@Test
	public void saveDefaultSeqPattern() throws Exception {
		try (SvSecurity svc = new SvSecurity()) {
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
				setAutoCommit(Arrays.asList(svr, svw), false);
				JsonObject data = new JsonObject();
				data.addProperty(CC.SEQ_PATTERN_ID, "HOL_ID_UNIQUE");
				data.addProperty(CC.CONF_TABLE, "HOLDING");
				data.addProperty(CC.DEST_FIELD, "TYPE");
				data.addProperty(CC.SEQ_PATTERN, "\"HOL\"--{SvSeq}");
				data.addProperty(CC.IS_DEFAULT, true);
				DbDataObject dbo = SeqPatternService.saveSeqPatternHelper(data, svr, svw);
				assertNotNull(dbo);
				assertTrue(dbo.getObjectId() > 0);
				assertEquals(true, dbo.getAsBoolean(CC.IS_DEFAULT));
				assertNull(dbo.getVal(CC.COND_FIELD));
				assertNull(dbo.getVal(CC.COND_OPERATOR));
				assertNull(dbo.getVal(CC.COND_VALUE));
				System.out.println("Saved default SEQ_PATTERN object_id = " + dbo.getObjectId());
			} catch (SeqPatternError e) {
				log4j.error("Couldn't save SV_ID_SEQ_PATTERN", e);
			}
		} catch (Exception e) {
			log4j.error("Error", e);
		}
	}

}
