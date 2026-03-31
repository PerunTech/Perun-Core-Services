package com.prtech.perun;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Paths;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Ignore;
import org.junit.Test;

import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvSecurity;
import com.prtech.svarog.SvUtil;
import com.prtech.svarog_common.DbDataObject;

public class TestExcelToDbDataObject {
	private static final Logger log4j = LogManager.getLogger(TestExcelToDbDataObject.class);

	static String getUserSessionIdForTestPurpose() {
		String sessionId = "";
		try (@SuppressWarnings("deprecation")
		SvSecurity svc = new SvSecurity();) {
			String userName = "ADMIN";
			String password = "welcome";
			sessionId = svc.logon(userName, SvUtil.getMD5(password));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return sessionId;
	}

	@Test
	@Ignore
	public void testConvertExcelToDbDataObjects() {
		String sessionId = getUserSessionIdForTestPurpose();
		log4j.info("Session ID for test purpose: " + sessionId);

		try {
			ExcelToDbDataObjectHelper helper = new ExcelToDbDataObjectHelper(SvReader.getTypeIdByName("POPULATION"));
			List<DbDataObject> objectArr = helper.convertExcelToDbDataObjects(
					Paths.get("src", "test", "resources", "test_excel_to_db_data_obj.xls").toString());
			log4j.info("Number of DbDataObjects loaded: " + objectArr.size());
			for (DbDataObject dbo : objectArr) {
				log4j.info(dbo.toSimpleJson());
			}
			assertTrue(!objectArr.isEmpty());
		} catch (Exception e) {
			log4j.error("Error: " + e.getMessage(), e);
			fail();
		}
	}
}
