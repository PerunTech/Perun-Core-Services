package com.prtech.perun_core.ws;

import static org.junit.Assert.fail;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.prtech.perun_core.ws.AdminConsole;
import com.prtech.perun_core.ws.DbReader;
import com.prtech.perun_core.ws.PublicWs;
import com.prtech.perun_core.ws.WsConf;
import com.prtech.perun_core.ws.WsReactElements;
import com.prtech.perun_core.ws.WsReporting;
import com.prtech.perun_core.ws.WsSecurityActions;
import com.prtech.svarog.I18n;
import com.prtech.svarog.SvConf;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvExecManager;
import com.prtech.svarog.SvLink;
import com.prtech.svarog.SvParameter;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvSecurity;
import com.prtech.svarog.SvUtil;
import com.prtech.svarog.SvWriter;
import com.prtech.svarog.svCONST;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbQuery;
import com.prtech.svarog_common.DbQueryExpression;
import com.prtech.svarog_common.DbQueryObject;
import com.prtech.svarog_common.DbSearchCriterion;
import com.prtech.svarog_common.DbSearchExpression;
import com.prtech.svarog_common.ResponseHandler;
import com.prtech.svarog_common.DbDataField.DbFieldType;
import com.prtech.svarog_common.DbQueryObject.DbJoinType;
import com.prtech.svarog_common.DbQueryObject.LinkType;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;
import com.prtech.svarog_common.ResponseHandler.MessageType;

public class Perun_core_tests {

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

	@Test
	public void defaultGroup() {
		SvSecurity svc = null;
		AdminConsole adm = new AdminConsole();
		try {
			svc = new SvSecurity();
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			HttpServletRequest httpRequest = null;
			Response test = adm.addDefaultUserGroup(sessionId, 23028908L, 23028908L, "true", httpRequest);
			System.out.print(test);
		} catch (SvException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

//	@Test
//	public void accesGroupPermission() {
//		SvSecurity svc = null;
//		SvReader svr = null;
//		Utils WsConf = new Utils();
//		try {
//			svc = new SvSecurity();
//			String sessionId = svc.logon("FIC", SvUtil.getMD5("welcome"));
//			svr = new SvReader(sessionId);
//			DbDataObject result = generateScenario(svr, "REFUSED");
//			System.out.print(result);
//		} catch (SvException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//	}

	@Test
	public void getQueryReport() throws SvException {
		String token = getUserSessionIdForTestPurpose();
		try (SvReader svr = new SvReader(token)) {
			String strIn = "[{\"field_name\":\"ANIMAL.HOLDING_ID\",\"operator\":\"equal\",\"value\":\"104602131\"},"
					+ "{\"field_name\":\"ANIMAL.HOLDING_LOCATION\",\"operator\":\"equal\"}]";
			Gson gson = new Gson();
			JsonArray json = gson.fromJson(strIn, JsonArray.class);
			WsReporting w = new WsReporting();
			//DbDataArray data = (w).getData(json, svr);
			HashMap<String, String> fieldNames = new LinkedHashMap<>();
			ArrayList<DbFieldType> types = new ArrayList<>();
			HashMap<String, String> tablePrefixMap = new LinkedHashMap<>();
			DbQuery q = w.generateQuery(json, tablePrefixMap);
			w.buildFieldList(json, tablePrefixMap, types, fieldNames);
			DbDataArray res = svr.getObjects(q, null, null);
			FileOutputStream fout;
			try {
				fout = new FileOutputStream("./test.xls");
				w.generateXls(res, fieldNames, types, fout);
				fout.close();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				fail("Exception raised");
			}

		}
	}

	@Test
	public void getTables() {
		String strIn = "[{\"field_name\":\"ANIMAL.HOLDING_ID\",\"operator\":\"equal\",\"value\":\"vvvv\"}]";
		Gson gson = new Gson();
		JsonArray json = gson.fromJson(strIn, JsonArray.class);
		WsReporting w = new WsReporting();
		ArrayList<DbDataObject> t = w.extractTableNames(json);

		if (!t.get(0).getVal("TABLE_NAME").equals("ANIMAL"))
			fail("no animal table is found");
	}

	@Test
	public void getFields() {
		String strIn = "[{\"field_name\":\"ANIMAL.HOLDING_ID\",\"operator\":\"equal\",\"value\":\"vvvv\"}]";
		Gson gson = new Gson();
		JsonArray json = gson.fromJson(strIn, JsonArray.class);
		WsReporting w = new WsReporting();
		ArrayList<String> t = w.extractFieldNames(json, "ANIMAL");
		if (!t.contains("HOLDING_ID"))
			fail("no animal table is found");
	}

	@Test
	public void canAccess() {
		SvSecurity svc = null;
		SvReader svr = null;
		DbReader dbadm = new DbReader();
		try {
			svc = new SvSecurity();
			String sessionId = svc.logon("FIC", SvUtil.getMD5("welcome"));
			svr = new SvReader(sessionId);
			DbDataObject dboUserGroup = svr.getDefaultUserGroup();
			Boolean test = dbadm.canAccess(dboUserGroup, "card.edbar", svr);
			System.out.print(test);
		} catch (SvException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Test
	public void getUserInfoIACS() {
		SvSecurity svc = null;
		SvReader svr = null;
		try {
			svc = new SvSecurity();
			String sessionId = svc.logon("FIC", SvUtil.getMD5("welcome1"));
			svr = new SvReader(sessionId);
			DbDataObject dbUser = svr.getInstanceUser();
			JsonObject takeDataForUser = searchPerson(svr, dbUser);
			System.out.print(takeDataForUser);
		} catch (SvException e) {
			e.printStackTrace();
			System.out.print(e.getJsonMessage());
		}
	}

	@Test
	public void poaLinkUserFarm() {
		SvSecurity svc = null;
		SvReader svr = null;
		SvLink svl = null;
		DbDataObject dbU = null;
		DbDataObject dbP = null;
		try {
			svc = new SvSecurity();
			String sessionId = svc.logon("FIC", SvUtil.getMD5("$$$$"));
			svr = new SvReader(sessionId);
			dbP = svr.getObjectById(23207818L, SvCore.getTypeIdByName("PERSON"), null);
			dbU = svr.getObjectById(23207821L, SvCore.getTypeIdByName("SVAROG_USERS"), null);
			svl = new SvLink(svr);
			svc.empowerUser(dbU, dbP, svl);
			DbDataArray empowerments = svc.getPOAObjects(dbU.getObjectId(), "PERSON");
			if (!empowerments.getItems().isEmpty() && empowerments.getItems().get(0) != null) {
				DbDataObject personObj = empowerments.getItems().get(0);
				if (personObj.getVal("ID_NO") != null) {
					String id_no = personObj.getVal("ID_NO").toString();
					System.out.print(id_no);
				}
			}
		} catch (SvException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public JsonObject searchPerson(SvReader svr, DbDataObject dbUser) {
		ResponseHandler jrh = new ResponseHandler();
		JsonArray finalJsonArray = new JsonArray();
		SvSecurity svc = null;
		try {
			if (dbUser != null) {
				svc = new SvSecurity();
				Gson gson = new Gson();
				DbDataArray personData = svc.getPOAObjects(dbUser.getObjectId(), "PERSON");
				DbSearchCriterion crit1 = new DbSearchCriterion("PERSON_OBJECT_ID", DbCompareOperand.EQUAL,
						personData.get(0).getObjectId());
				DbSearchCriterion crit2 = new DbSearchCriterion("PERSON_ID", DbCompareOperand.EQUAL,
						personData.get(0).getObjectId());
				int tablesusedCount = 1;
				DbDataArray farmData = svr.getObjects(crit1, SvCore.getTypeIdByName("FARM"), null, 0, 0);
				DbDataArray ipardAplicantData = svr.getObjects(crit2, SvCore.getTypeIdByName("IPARD_APPLICANT"), null,
						0, 0);

				String[] tablesUsedArray = new String[tablesusedCount];
				Boolean[] tableShowArray = new Boolean[tablesusedCount];
				tablesUsedArray[0] = ("PERSON");
				tableShowArray[0] = true;
				String jsonStr = WsReactElements.prapareTableQueryData(personData, tablesUsedArray, tableShowArray,
						tablesusedCount, true, svr);
				JsonArray tmpJson = gson.fromJson(jsonStr, JsonArray.class);
				finalJsonArray.add(tmpJson.get(0));
				tablesUsedArray[0] = ("FARM");
				tableShowArray[0] = true;
				jsonStr = WsReactElements.prapareTableQueryData(farmData, tablesUsedArray, tableShowArray,
						tablesusedCount, true, svr);
				tmpJson = gson.fromJson(jsonStr, JsonArray.class);
				finalJsonArray.add(tmpJson.get(0));
				tablesUsedArray[0] = ("IPARD_APPLICANT");
				tableShowArray[0] = true;
				jsonStr = WsReactElements.prapareTableQueryData(ipardAplicantData, tablesUsedArray, tableShowArray,
						tablesusedCount, true, svr);
				tmpJson = gson.fromJson(jsonStr, JsonArray.class);
				finalJsonArray.add(tmpJson.get(0));

				jrh.create(MessageType.SUCCESS, I18n.getText("farmer.data.found"), I18n.getText("data.read"),
						finalJsonArray);
			}
		} catch (JsonSyntaxException e) {
			jrh.create(MessageType.ERROR, I18n.getText("error.loading.farmer.data"),
					I18n.getText("error.loading.farmer.data"), finalJsonArray);
		} catch (SvException e) {
			jrh = ResponseHandler.responseHandlerByException(e);
		} finally {
			if (svr != null)
				svr.release();
		}
		return jrh.getAll();
	}

	@Test
	public void defaultGroupPermission() {
		SvSecurity svc = null;
		SvReader svr = null;
		DbDataObject dbG = null;
		try {
			dbG = new DbDataObject();
			svc = new SvSecurity();
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome13"));
			svr = new SvReader(sessionId);
			// 222222L - objectId na grupa
			dbG = svr.getObjectById(660497263L, SvCore.getTypeIdByName("SVAROG_USER_GROUPS"), null);
			svc = new SvSecurity(svr);
			String groupAccess = "%.FULL";
			DbDataArray permissions = svc.getPermissions(groupAccess);
			for (DbDataObject perm : permissions.getItems()) {
				svc.grantPermission(dbG, (String) perm.getVal("LABEL_CODE"));
			}
		} catch (SvException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Test
	public void checkUserAccess() {
		SvSecurity svc;
		WsSecurityActions wsc = new WsSecurityActions();
		try {
			svc = new SvSecurity();
			/* hardcided examples from 9.72 */
//			JsonObject sessionId = wsc.doLogin("00005316698", SvUtil.getMD5("welcome"));
//			JsonObject sessionId1 = wsc.doLogin("I.MILOSHEVSKI", SvUtil.getMD5("welcome"));
//			JsonObject sessionId2 = wsc.doLogin("admin", SvUtil.getMD5("welcome"));
//			JsonObject sessionId3 = wsc.doLogin("34511865740", SvUtil.getMD5("welcome13"));
//			System.out.print("finish" + sessionId +  sessionId1 + sessionId2 + sessionId3);
		} catch (SvException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Test
	public void getSiteKey() {
		PublicWs pws = new PublicWs();
		String siteKey = pws.getSiteKey();
		System.out.print(siteKey);
	}

	/* get public notifications for login comp f.r */
	@Test
	public void getPublicNotifications() {
		PublicWs pws = new PublicWs();
		String token = "192.168.99.28";
		Response notifications = pws.getNotifications(token, null);
		System.out.print(notifications);
	}

	/* get public app info for login comp f.r */
	@Test
	public void getAppInfo() {
		PublicWs pws = new PublicWs();
		String token = "192.168.99.28";
		Response notifications = pws.getApplications(token, "4002991112356", "76697", "TAX_NO", null);
		System.out.print(notifications);
	}

	@Test
	public void kibsCheck() {
		String strUrl = "";
		String embBankCombo = "combo";
		try {
			String servicesUrl = "http://192.168.10.21:8080/kibs-crosscheck/kibs_check.jsp";
			// SvConf.getParam("kibs_check.url");
			strUrl = servicesUrl + "?val2check=" + embBankCombo;
			URL url = new URL(strUrl);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "text/html");
			String svcResponse = "";
			if (conn.getResponseCode() != 200)
				svcResponse = IOUtils.toString(conn.getErrorStream(), "UTF-8").trim();
			else
				svcResponse = IOUtils.toString(conn.getInputStream(), "UTF-8").trim();
			conn.disconnect();
			if (!svcResponse.substring(0, 2).equals("OK"))
				System.out.print("applicant.bank_account_verification_failed");
		} catch (Exception e) {
			System.out.print(I18n.getText("applicant.bank_account_verification_failed") + strUrl);
		}
	}

	@Test
	public void invalidUser() {
		SvSecurity svc;
		AdminConsole wsc = new AdminConsole();
		try {
			svc = new SvSecurity();
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("notValid"));
			Response test = wsc.changeUserStatus(sessionId, 2078873L, "VALID", null);
			System.out.print("finish");
		} catch (SvException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Test
	public void getLinkedUsers() {
		SvSecurity svc;
		AdminConsole wsc = new AdminConsole();
		try {
			svc = new SvSecurity();
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("notValid"));
			Response test2 = wsc.getLinkedUsers(sessionId, 20522590L, null);
			System.out.print("finish");
		} catch (SvException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Test
	public void getLinkedGroups() {
		SvSecurity svc;
		AdminConsole wsc = new AdminConsole();
		try {
			svc = new SvSecurity();
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("notvalid"));
			Response test3 = wsc.getLinkedGroups(sessionId, 20570491L, null);
			System.out.print(test3);
		} catch (SvException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Test
	public void assigneUserToGroup() {
		SvSecurity svc;
		SvReader svr;
		try {
			svc = new SvSecurity();
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("notvalid"));
			svr = new SvReader(sessionId);
			svc = new SvSecurity(svr);
			DbDataObject user = svc.getUser("user");
			DbDataObject userGroupa = svr.getObjectByUnqConfId("IACS_RANK_EXTRACTION", "SVAROG_USER_GROUPS");
			svc.addUserToGroup(user, userGroupa, false);
			System.out.print("finish");
		} catch (SvException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Test
	public void getTableJSONSchema() {
		WsReactElements wsc = new WsReactElements();
		SvSecurity svc;
		try {
			svc = new SvSecurity();
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("notvalid"));
			Response test = wsc.getTableJSONSchema(sessionId, "LAND_USE_PLAN", null);
			System.out.print(test);
		} catch (SvException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Test
	public void changePass() {
		WsSecurityActions wsc = new WsSecurityActions();
		// Response test = wsc.changePassword("ADMIN", "2211221122112", "WELCOME",
		// "WELCOME", null);
		// System.out.print(test);
	}

	// @Test
	// public void getModule() {
	// WsSecurityActions wsa = new WsSecurityActions();
	// SvSecurity svc;
	// try {
	// svc = new SvSecurity();
	// String sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
	// Response test = wsa.getCofnigModules(sessionId);
	// } catch (SvException e) {
	// e.printStackTrace();
	// }
	//
	// }

	// @Test
	// public void sendActivationMail() {
	// WsSecurityActions wsa = new WsSecurityActions();
	// MultivaluedMap<String, String> formVals = new MultivaluedHashMap<>();
	// try {
	//
	// formVals.add("username", "00000258550");
	// formVals.add("password", SvUtil.getMD5("welcome"));
	// formVals.add("idNo", "1604957450101");
	// Response test = wsa.sendActivationLink(formVals, httpRequest);
	// System.out.print(test);
	// } catch (SvException e) {
	// // TODO Auto-generated catch block
	// e.printStackTrace();
	// }
	// }

	@Test
	public void getUserGroups() {
		WsSecurityActions wsa = new WsSecurityActions();
		SvSecurity svc;
		try {
			svc = new SvSecurity();
			String sessionId = svc.logon("fic", SvUtil.getMD5("notvalid"));
			JsonObject test = wsa.getCofnigModules(sessionId);
			System.out.print(test);
		} catch (SvException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Test
	public void setInitialCard() {
		SvSecurity svc = null;
		SvWriter svw = null;
		SvReader svr = null;
		DbDataObject saveFormConf = null;
		try {
			svc = new SvSecurity();
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("notvalid"));
			svr = new SvReader(sessionId);
			svw = new SvWriter(sessionId);

			saveFormConf = new DbDataObject();
			saveFormConf.setVal("CARD_ID", "user_manager");
			saveFormConf.setVal("TITLE", "Системски поставки");
			saveFormConf.setObjectType(SvCore.getTypeIdByName("CARD_CONF"));
			saveFormConf.setVal("DESCR", "(корисници, кориснички групи и системски табели)");
			saveFormConf.setStatus("VALID");
			saveFormConf.setVal("IMG_PATH", "systemsettings.jpg");
			saveFormConf.setVal("USER_GROUP_ID", -6l);
			svw.saveObject(saveFormConf, false);
			// svw.dbCommit();
		} catch (SvException e) {
			e.printStackTrace();
		} finally {
			if (svr != null)
				svr.release();
			if (svw != null)
				svw.release();
		}
		System.out.print("created system_settings");
	}

	@Test
	public void createAllParamsForMailActivation() {
//		setParamsForMailActivation("frontend.services_host", "http://192.168.9.46:8080/triglav_rest/");
		setParamsForMailActivation("frontend.gui_host", "https://e-subvencii.ipardpa.gov.mk/perun/index.html");
		setParamsForMailActivation("mail.from", "afpzrr.ipard@ipardpa.gov.mk");
		setParamsForMailActivation("mail.username", "afpzrr.ipard@ipardpa.gov.mk");
		setParamsForMailActivation("mail.password", "Afs@rd2019sub");
		setParamsForMailActivation("mail.host", "mail.ipardpa.gov.mk");
		setParamsForMailActivation("mail.smtp.auth", "true");
		setParamsForMailActivation("mail.smtp.starttls.enable", "true");
		setParamsForMailActivation("mail.smtp.port", "25");
		setParamsForMailActivation("mail.format", "text/html; charset=UTF-8");
	}

	public void setParamsForMailActivation(String label_code, String value_element) {
		SvReader svr = null;
		SvWriter svw = null;
		SvParameter svp = null;
		SvSecurity svc = null;
		try {
			svc = new SvSecurity();
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("notvalid"));
			svr = new SvReader(sessionId);
			svw = new SvWriter(svr);
			svw.setAutoCommit(true);
			svp = new SvParameter(svw);

			DbDataObject dboParamType = svp.getParamType(label_code);
			// if it doesn't exist, than it will create new type of parameter
			if (dboParamType == null) {
				svp.createParamType(label_code, true);
				dboParamType = svp.getParamType(label_code);
			}

			if (dboParamType != null) {
				DbDataObject securityDbt = SvCore.getDbt(svCONST.OBJECT_TYPE_SECURITY_LOG);
				svp.setParamString(securityDbt, label_code, value_element);
			}
		} catch (SvException ex) {
			ex.printStackTrace();
			fail(ex.getFormattedMessage());
		} finally {
			if (svr != null)
				svr.release();
			if (svw != null)
				svw.release();
			if (svp != null)
				svp.release();
		}
	}

	public DbDataObject searchForObject(long objToSearch, String columnToSearch, String valueToSearch,
			SvReader svReader) {
		DbDataArray reg_office = new DbDataArray();
		DbDataObject result = null;
		try {
			com.prtech.svarog_common.DbSearchExpression expr = new com.prtech.svarog_common.DbSearchExpression();
			expr.addDbSearchItem(new com.prtech.svarog_common.DbSearchCriterion(columnToSearch, DbCompareOperand.EQUAL,
					valueToSearch));
			/*
			 * expr.addDbSearchItem( new DbSearchCriterion("PARENT_CODE_VALUE",
			 * DbCompareOperand.EQUAL, "POPULATED_AREAS_IPARD"));
			 */
			reg_office = svReader.getObjects(expr, objToSearch, null, 0, 0);
			ArrayList<DbDataObject> list = reg_office.getItems();
			if (list.size() > 0) {
				result = list.get(0);
			}

		} catch (Exception ex) {
			System.out.println("Error in it.getSQLExpression()");
			ex.printStackTrace();
		}
		return result;

	}

	public void createParamType(Long parentId, String labelCode, String dataType, String inputType, String defaultValue,
			Long codeListId, String isMultipleValueOfEntry, String guiMetadata, Long paramOrder,
			String paramTypeLabelText, String labelLocaleId, SvReader svr, SvWriter svw) throws SvException {

		// check if paramTypeObject already exists
		DbDataObject paramTypeObj = searchForObject(svCONST.OBJECT_TYPE_PARAM_TYPE, "LABEL_CODE", labelCode, svr);
		// if not exist then create it
		if (paramTypeObj == null) {
			// prepare paramTypeObject and save it
			svw.saveObject(createParamTypeObj(parentId, labelCode, dataType, inputType, defaultValue, codeListId,
					isMultipleValueOfEntry, guiMetadata, paramOrder), false);
			// save label for paramTypeObject
			createLabel(labelCode, paramTypeLabelText, labelLocaleId, svw);
			System.out.println("ParamTypeObject with labelCode:" + labelCode + " saved with autoCommit:false.");
		} else {
			System.out.println("ParamTypeObject with labelCode:" + labelCode + " already exists.");

		}

	}

	public DbDataObject createParamTypeObj(Long parentId, String labelCode, String dataType, String inputType,
			String defaultValue, Long codeListId, String isMultipleValueOfEntry, String guiMetadata, Long paramOrder) {
		DbDataObject paramTypeObj = new DbDataObject();
		paramTypeObj.setObject_type(svCONST.OBJECT_TYPE_PARAM_TYPE);
		paramTypeObj.setStatus("VALID");
		paramTypeObj.setParent_id(parentId);
		paramTypeObj.setDt_insert(new DateTime());
		paramTypeObj.setDt_delete(SvConf.MAX_DATE);
		if (labelCode != null)
			paramTypeObj.setVal("LABEL_CODE", labelCode);
		if (dataType != null)
			paramTypeObj.setVal("DATA_TYPE", dataType);
		if (inputType != null)
			paramTypeObj.setVal("INPUT_TYPE", inputType);
		if (defaultValue != null)
			paramTypeObj.setVal("DEFAULT_VALUE", defaultValue);
		if (codeListId != null)
			paramTypeObj.setVal("CODE_LIST_ID", codeListId);
		if (isMultipleValueOfEntry != null)
			paramTypeObj.setVal("IS_MULTIPLE_VALUE_OF_ENTRY", isMultipleValueOfEntry);
		if (guiMetadata != null)
			paramTypeObj.setVal("GUI_METADATA", guiMetadata);
		if (paramOrder != null)
			paramTypeObj.setVal("PARAM_ORDER", paramOrder);

		return paramTypeObj;
	}

	public void createLabel(String labelCode, String labelDescr, String localeId, SvWriter svWriter)
			throws SvException {

		SvReader svReader = new SvReader(svWriter);
		try {
			DbDataObject labelObj = getLabel(labelCode, localeId, svReader);
			if (labelObj == null) {
				labelObj = new DbDataObject();
				labelObj.setStatus("VALID");
				labelObj.setDt_insert(new DateTime());
				labelObj.setDt_delete(SvConf.MAX_DATE);
				labelObj.setVal("label_code", labelCode);
				labelObj.setVal("label_text", labelDescr);
				labelObj.setObject_type(svCONST.OBJECT_TYPE_LABEL);
				labelObj.setVal("locale_id", localeId);
				svWriter.saveObject(labelObj, false);
			} else {
				String currLabelDescr = labelObj.getVal("LABEL_TEXT").toString();
				if (!currLabelDescr.equals(labelDescr)) {
					labelObj.setVal("LABEL_TEXT", labelDescr);
					svWriter.saveObject(labelObj, false);
					System.out.println("Object of type LABEL updated with label_code" + labelCode);
				} else {
					System.out.println("Object of type LABEL with label_code:" + labelCode + " already exists.");
				}
			}

		} finally {
			if (svReader != null) {
				svReader.release();
			}
		}
	}

	public DbDataObject getLabel(String labelCode, String localeId, SvReader svReader) throws SvException {

		DbDataObject result = null;

		DbSearchExpression getFormType = new DbSearchExpression();
		DbSearchCriterion filterByLabel = new DbSearchCriterion("LABEL_CODE", DbCompareOperand.EQUAL, labelCode);
		DbSearchCriterion filterByLocaleId = new DbSearchCriterion("LOCALE_ID", DbCompareOperand.EQUAL, localeId);
		getFormType.addDbSearchItem(filterByLabel);
		getFormType.addDbSearchItem(filterByLocaleId);
		DbDataArray searchResult = svReader.getObjects(getFormType, svCONST.OBJECT_TYPE_LABEL, null, 0, 0);

		if (searchResult.getItems().size() > 0) {
			result = searchResult.getItems().get(0);
		}
		return result;
	}

	@Test
	public void testCreateCustomPermissionAcl() throws Exception {
		SvReader svr = null;
		SvWriter svw = null;
		SvSecurity svc = null;

		try {
			svc = new SvSecurity();
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("notvalid"));
			svr = new SvReader(sessionId);
			svr.setAutoCommit(false);
			svw = new SvWriter(svr);
			/* card access codes f.r */
			List<String> customPermissionCodes = Arrays.asList("card.edbar", "card.edbar");

			for (Integer i = 0; i < customPermissionCodes.size(); i++) {
				System.out.println(customPermissionCodes.get(i));
				DbDataObject dboAcl = new DbDataObject(svCONST.OBJECT_TYPE_ACL);
				dboAcl.setVal("ACCESS_TYPE", "READ");
				dboAcl.setVal("ACL_OBJECT_ID", 0L);
				dboAcl.setVal("ACL_OBJECT_TYPE", 50L);
				dboAcl.setVal("ACL_CONFIG_UNQ", customPermissionCodes.get(i));
				dboAcl.setVal("LABEL_CODE", customPermissionCodes.get(i));
				svw.saveObject(dboAcl, false);
				svw.dbCommit();
			}
		} catch (SvException e) {
			e.printStackTrace();
			fail();
		} finally {
			if (svr != null)
				svr.release();
			svc.release();
			svw.release();
		}
	}

	/* test to grant permissions f.r */
	@Test
	public void testGrantOrRevokePermission() throws Exception {
		SvSecurity svc = null;
		SvReader svr = null;
		try {
			svc = new SvSecurity();
			String sessionId = svc.logon("ADMIN", SvUtil.getMD5("nopass"));
			svr = new SvReader(sessionId);
			svc = new SvSecurity(svr);
//			SvExecManager svx = new SvExecManager(svr);
//			svx.
			// 664500342L- prod

			/* group object_id */
			DbDataObject group = svr.getObjectById(659984644L, SvCore.getTypeIdByName("SVAROG_USER_GROUPS"), null);

//			List<String> executorsAclCodes = Arrays.asList("CALCULATOR.app_type.2019.edbar",
//					"CALCULATOR.app_type.2019.insurance", "CALCULATOR.MASTER", "CALCULATOR.app_type.2020.edbar",
//					"CALCULATOR.app_type.2019.milk_1", "CALCULATOR.app_type.2019.laying_hens_1",
//					"CALCULATOR.app_type.2019.laying_hens_3", "CALCULATOR.app_type.2019.laying_hens_2",
//					"CALCULATOR.app_type.2018.laying_hens_3", "CALCULATOR.app_type.2019.organic_prod",
//					"CALCULATOR.app_type.2019.milk_3", "CALCULATOR.app_type.2020.laying_hens_1",
//					"CALCULATOR.app_type.2020.laying_hens_2", "CALCULATOR.app_type.2020.poultry_3",
//					"CALCULATOR.app_type.2020.insurance", "CALCULATOR.app_type.2019.apple",
//					"CALCULATOR.app_type.2019.grapes", "CALCULATOR.app_type.2019.lamb", "CALCULATOR.app_type.2019.rice",
//					"CALCULATOR.app_type.2019.processors", "CALCULATOR.app_type.2019.milk_2");

//			/* Edbar 2021 executros f.r */
//			List<String> executorsAclCodes = Arrays.asList("SVAROG_SDI_USE.READ", "SVAROG_TASK_TYPE.READ",
//					"SVAROG_UI_STRUCT_TYPE.READ", "SUPPORT_CLAIM.READ", "ANIMAL_TYPE.READ", "CALC.READ",
//					"SVAROG_CLC.READ", "PAYMENT_CLAIM.FULL", "PAYMENT_MEASURE_TYPE.FULL", "SVAROG_JOB.FULL",
//					"SVAROG_RE.FULL", "SVAROG_RESULTS.FULL", "SVAROG_SDI.FULL", "SVAROG_SID_ACL.FULL",
//					"SVAROG_PARAM.READ", "HOLDING_MEMBER.READ", "ANIMAL_AUTOCHT.FULL", "LAND_USE_CODE.FULL"
//					// ,"ANIMAL_CONTROL.FULL"
//					// ,"HOLIDAY.FULL"
//					, "SVAROG_FORM.FULL", "SVAROG_FILES.FULL", "SVAROG_RULES.FULL", "SVAROG_PARAM_TYPE.FULL",
//					"SVAROG_LINK.FULL", "HOLDING_MEMBER.FULL", "SUPPORT_CLAIM.FULL", "CALC_RESULT.FULL",
//					"CALC_PENALTY.FULL", "CALC_SCALAR_GROUP.FULL", "CALC_DETAILS.FULL", "FORM.UI_SCHEMA",
//					"FORM.JSON_SCHEMA", "ANIMAL_EDBAR.FIELD_LIST", "APP_MEASURE_MENU_EDBAR2021.INPROG18",
//					"SVAROG_ACL.FULL", "SVAROG_UI_STRUCT_SOURCE.FULL", "SVAROG_LOCALES.FULL", "SVAROG_SDI_UNITS.FULL",
//					"SVAROG_JOB_OBJECT.FULL", "SVAROG_FORM_FIELD.FULL", "SUPPORT_TYPE.FULL", "SUPPORT_TYPE_STATUS.FULL",
//					"LPIS_PARCEL.FULL", "CALC_ITEM.FULL", "TRANSITION.SUBMIT", "TRANSITION.AMMEND",
//					"SVAROG_RENDER_ENGINE.FULL", "SVAROG_CODES.FULL", "SVAROG_USER_GROUPS.FULL", "ANIMAL.FULL",
//					"LAND_USE_PLAN.FULL", "APPLICATION.FULL", "APPLICATION_TYPE.FULL", "ANIMAL_TYPE.FULL",
//					"LINE_ITEMS.FULL", "ARCH_NO_PRINTOUT_TYPE.FULL", "FARMER.FULL", "card.edbar",
//					"APPLICATION_MENU.INPROG18", "APP_MEASURE_MENU_EDBAR2021.CHANGED", "SVAROG_EXEC_PACK.FULL",
//					"SVAROG_SDI_USE.FULL", "SVAROG_TABLES.FULL", "SVAROG_EXECUTIONS.FULL", "SVAROG_NOTES.FULL",
//					"SVAROG_LABELS.FULL", "ARCH_NO_PRINTOUT.FULL", "SUPPLIERS.FULL", "CALC.FULL",
//					"CLAIM_EDBAR.SAVE_DATA", "SVAROG_EXECUTOR_GROUP.FULL", "SVAROG_TASK.FULL",
//					"SVAROG_PARAM_VALUE.FULL", "SVAROG_FORM_FIELD_TYPE.FULL", "SVAROG_PARAM.FULL",
//					"SVAROG_SDI_COVER.FULL", "CALC_PENALTY_GROUP.FULL", "SVAROG_UI_STRUCT_TYPE.FULL", "CAD_PARCEL.FULL",
//					"APPLICATION_MENU.DETAILS", "APPLICATION_MENU.SUBMITTED", "TRANSITION.INPROGRESS",
//					"FORM_EDBAR2021.UI_SCHEMA_PER_MEASURE", "APP_MEASURE_MENU_EDBAR2021.SUBMITTED",
//					"PRINT.SUBMIT_PRINT", "SVAROG_SDI_BOUNDS.FULL", "SVAROG_FIELDS.FULL", "SVAROG_JOB_TASK.FULL",
//					"SVAROG.FULL", "SVAROG_LINK_TYPE.FULL", "SVAROG_ORG_UNITS.FULL", "FORM_EDBAR2021.SAVE_DATA",
//					"APP_MEASURE_MENU_EDBAR2021.INPROGRESS", "SVAROG_EXPCK_ITEMS.FULL", "SVAROG_USERS.FULL",
//					"SVAROG_JOB_TYPE.FULL", "SVAROG_WORKFLOW.FULL", "SVAROG_SECURITY_LOG.FULL",
//					"SVAROG_TASK_DETAIL.FULL", "CALC_TYPE.FULL", "SVAROG_CLC.FULL", "SVAROG_ACTIONS.FULL",
//					"SVAROG_TASK_TYPE.FULL", "CALC_SCALAR.FULL", "FORM.GET_DATA", "TRANSITION.INPROG18",
//					"SVAROG_EXECUTORS.FULL", "SVAROG_FORM_FIELD.READ", "SVAROG_FORM_TYPE.FULL", "SVAROG_SEQUENCE.FULL",
//					"REGIONAL_OFFICE.FULL", "APPLICATION_MENU.INPROGRESS", "PARCEL_EDBAR.DATA", "ANIMAL_EDBAR.DATA",
//					"PARCEL_EDBAR2021.FIELD_LIST", "SVAROG_EXECUTIONS.READ", "SEARCH.FARMER", "system.null_geometry",
//					"IMPORTER.SDI", "SUBMIT_CHECKS.app_type.2021.edbar","ANIMAL_EDBAR_2021.FILTER","IMPORTER.FARM_DATA");

			/* Edbar 2021 executros READ f.r */
			List<String> executorsAclCodes = Arrays.asList("SVAROG_SDI_USE.READ", "SVAROG_TASK_TYPE.READ",
					"SVAROG_UI_STRUCT_TYPE.READ", "SUPPORT_CLAIM.READ", "ANIMAL_TYPE.READ", "CALC.READ",
					"SVAROG_CLC.READ", "PAYMENT_CLAIM.READ", "PAYMENT_MEASURE_TYPE.READ", "SVAROG_JOB.READ",
					"SVAROG_RE.READ", "SVAROG_RESULTS.READ", "SVAROG_SDI.READ", "SVAROG_SID_ACL.READ",
					"SVAROG_PARAM.READ", "HOLDING_MEMBER.READ", "ANIMAL_AUTOCHT.READ", "LAND_USE_CODE.READ"
					// ,"ANIMAL_CONTROL.READ"
					// ,"HOLIDAY.READ"
					, "SVAROG_FORM.READ", "SVAROG_FILES.READ", "SVAROG_RULES.READ", "SVAROG_PARAM_TYPE.READ",
					"SVAROG_LINK.READ", "HOLDING_MEMBER.READ", "SUPPORT_CLAIM.READ", "CALC_RESULT.READ",
					"CALC_PENALTY.READ", "CALC_SCALAR_GROUP.READ", "CALC_DETAILS.READ", "FORM.UI_SCHEMA",
					"FORM.JSON_SCHEMA", "ANIMAL_EDBAR.FIELD_LIST", "APP_MEASURE_MENU_EDBAR2021.INPROG18",
					"SVAROG_ACL.READ", "SVAROG_UI_STRUCT_SOURCE.READ", "SVAROG_LOCALES.READ", "SVAROG_SDI_UNITS.READ",
					"SVAROG_JOB_OBJECT.READ", "SVAROG_FORM_FIELD.READ", "SUPPORT_TYPE.READ", "SUPPORT_TYPE_STATUS.READ",
					"LPIS_PARCEL.READ", "CALC_ITEM.READ", "TRANSITION.SUBMIT", "TRANSITION.AMMEND",
					"SVAROG_RENDER_ENGINE.READ", "SVAROG_CODES.READ", "SVAROG_USER_GROUPS.READ", "ANIMAL.READ",
					"LAND_USE_PLAN.READ", "APPLICATION.READ", "APPLICATION_TYPE.READ", "ANIMAL_TYPE.READ",
					"LINE_ITEMS.READ", "ARCH_NO_PRINTOUT_TYPE.READ", "FARMER.READ", "card.edbar",
					"APPLICATION_MENU.INPROG18", "APP_MEASURE_MENU_EDBAR2021.CHANGED", "SVAROG_EXEC_PACK.READ",
					"SVAROG_SDI_USE.READ", "SVAROG_TABLES.READ", "SVAROG_EXECUTIONS.READ", "SVAROG_NOTES.READ",
					"SVAROG_LABELS.READ", "ARCH_NO_PRINTOUT.READ", "SUPPLIERS.READ", "CALC.READ",
					"CLAIM_EDBAR.SAVE_DATA", "SVAROG_EXECUTOR_GROUP.READ", "SVAROG_TASK.READ",
					"SVAROG_PARAM_VALUE.READ", "SVAROG_FORM_FIELD_TYPE.READ", "SVAROG_PARAM.READ",
					"SVAROG_SDI_COVER.READ", "CALC_PENALTY_GROUP.READ", "SVAROG_UI_STRUCT_TYPE.READ", "CAD_PARCEL.READ",
					"APPLICATION_MENU.DETAILS", "APPLICATION_MENU.SUBMITTED", "TRANSITION.INPROGRESS",
					"FORM_EDBAR2021.UI_SCHEMA_PER_MEASURE", "APP_MEASURE_MENU_EDBAR2021.SUBMITTED",
					"PRINT.SUBMIT_PRINT", "SVAROG_SDI_BOUNDS.READ", "SVAROG_FIELDS.READ", "SVAROG_JOB_TASK.READ",
					"SVAROG.READ", "SVAROG_LINK_TYPE.READ", "SVAROG_ORG_UNITS.READ", "FORM_EDBAR2021.SAVE_DATA",
					"APP_MEASURE_MENU_EDBAR2021.INPROGRESS", "SVAROG_EXPCK_ITEMS.READ", "SVAROG_USERS.READ",
					"SVAROG_JOB_TYPE.READ", "SVAROG_WORKFLOW.READ", "SVAROG_SECURITY_LOG.READ",
					"SVAROG_TASK_DETAIL.READ", "CALC_TYPE.READ", "SVAROG_CLC.READ", "SVAROG_ACTIONS.READ",
					"SVAROG_TASK_TYPE.READ", "CALC_SCALAR.READ", "FORM.GET_DATA", "TRANSITION.INPROG18",
					"SVAROG_EXECUTORS.READ", "SVAROG_FORM_FIELD.READ", "SVAROG_FORM_TYPE.READ", "SVAROG_SEQUENCE.READ",
					"REGIONAL_OFFICE.READ", "APPLICATION_MENU.INPROGRESS", "PARCEL_EDBAR.DATA", "ANIMAL_EDBAR.DATA",
					"PARCEL_EDBAR2021.FIELD_LIST", "SVAROG_EXECUTIONS.READ", "SEARCH.FARMER", "system.null_geometry",
					"IMPORTER.SDI", "SUBMIT_CHECKS.app_type.2021.edbar", "ANIMAL_EDBAR_2021.FILTER",
					"IMPORTER.FARM_DATA", "SUBMIT_CHECKS.app_type.2021.insurance",
					"CLONE_MEASURES.app_type.2021.insurance", "APP_MENU_INSURANCE2021.AMMENDED", "TRANSITION.CHANGED",
					"APP_MENU_INSURANCE2021.CHANGED", "APP_MENU_INSURANCE2021.ADM_CTRL",
					"APP_MEASURE_MENU_INSURANCE2021.CORR_DCL", "APP_MENU_INSURANCE2021.CORR_DCL",
					"APP_MENU_INSURANCE2021.SUBMITTED", "APP_MENU_INSURANCE2021.INPROGRESS",
					"APP_MEASURE_MENU_INSURANCE2021.ADM_CTRL", "APP_MEASURE_MENU_INSURANCE2021.CHANGED",
					"APP_MEASURE_MENU_INSURANCE2021.SUBMITTED", "APP_MEASURE_MENU_INSURANCE2021.INPROGRESS",
					"FORM_INSURANCE2021.DATA_INCOMPLETE", "SUMMARY_VIEW_INSURANCE2021.FIELD_LIST",
					"FORM_INSURANCE2021.SAVE_DATA", "FORM_INSURANCE2021.UI_SCHEMA_PER_MEASURE",
					"SUMMARY_VIEW_INSURANCE2021.DATA", "TRANSITION.REPLACE", "TRANSITION.CHANGE", "TRANSITION.RESCIND",
					"TRANSITION.AMMEND", "TRANSITION.REPLACE", "TOP_MENU.TOP_MENU_DOC", "TOP_MENU.TOP_MENU_APPENDIX",
					"");

			/* Batch executors - 01.03.2021 f.r */
//			List<String> executorsAclCodes = Arrays.asList("MILKIMPORT.SELECTOR", "MILKIMPORT.PROCESSOR",
//					"ADMAPPROVER.EDBAR", "CALCULATOR.MASTER", "FILE.TODO-DOING");

			/* Batch - Payment executors - 01.03.2021 f.r */
//			List<String> executorsAclCodes = Arrays.asList("PAYMENT.FILTER_SELECTION_APPLICATION", 
//					"PAYMENT.PROCESSOR_SELECTION_APPLICATION", "PAYMENT.PROCESS_PAYMENT", "system.sudo");

			/* Batch - Refusal executors - 01.03.2021 f.r */
//			List<String> executorsAclCodes = Arrays.asList("PARTIAL_REJECTION.PROCESSOR_CREATE_REJECTION", 
//					"PARTIAL_REJECTION.APPLICATION", "COMPLETELY_REJECTION.PROCESSOR_CREATE_REJECTION", 
//					"COMPLETELY_REJECTION.APPLICATION", "system.sudo");

			/* Rank and extraction executors - 01.03.2021 f.r */
//			List<String> executorsAclCodes = Arrays.asList("PROCESSOR_2019.SELECTION_VEGETABLES_ENTERPRISE",
//					"PROCESSOR_2019.SAMPLE_SELECTION_FRUITS_ENTERPRISE",
//					"PROCESSOR_2019.SELECTION_VEGETABLES_APPLICATION", "APPLE_2019.SAMPLE_SELECTION_ENTERPRISE",
//					"GRAPES_2019.SAMPLE_SELECTION_APPLICATION", "EDBAR_2020.SAMPLE_SELECTION_ANIMAL",
//					"APPLE_2019.SELECTION_ENTERPRISE", "APPLE_2019.SAMPLE_SELECTION_APPLICATION",
//					"PROCESSOR_2019.SELECTION_FRUITS_ENTERPRISE", "PROCESSOR_2019.SAMPLE_SELECTION_VEG_ENTERPRISE",
//					"PROCESSOR_2019.SELECTION_FRUITS_APPLICATION", "PROCESSOR_2019.SAMPLE_SELECTION_VEG_APPLICATION",
//					"PROCESSOR_2019.SAMPLE_SELECTION_FRUITS_APPLICATION", "GRAPES_2019.SELECTION_ENTERPRISE",
//					"GRAPES_2019.SAMPLE_SELECTION_ENTERPRISE", "AD_HOC.SELECTION", "APPLE_2019.SELECTION_APPLICATION",
//					"GRAPES_2019.SELECTION_APPLICATION", "MILK_2020.SELECT_SAMPLE_APPLICATION_SHEEP_GOATS",
//					"MILK_2020.SELECT_SAMPLE_ENTERPRISE", "MILK_2020.SELECT_SCORE_APPLICATION",
//					"GRAPES_2020.SELECTION_ENTERPRISE", "GRAPES_2020.SAMPLE_SELECTION_ENTERPRISE",
//					"GRAPES_2020.SELECTION_APPLICATION", "MILK_2020.SELECT_SAMPLE_APPLICATION_COWS",
//					"PROCESSOR_2020.SELECTION_VEGETABLES_ENTERPRISE", "PROCESSOR_2020.SELECTION_FRUITS_ENTERPRISE",
//					"PROCESSOR_2020.SAMPLE_SELECTION_VEG_ENTERPRISE", "PROCESSOR_2020.SELECTION_VEGETABLES_APPLICATION",
//					"PROCESSOR_2020.SAMPLE_SELECTION_VEG_APPLICATION", "APPLE2019.SAMPLE_APPLICATION",
//					"PROCESSOR_2019.SCORE_VEG_ENTERPRISE", "PROCESSOR_2019.SCORE_FRUITS_ENTERPRISE",
//					"PROCESSOR_2019.SAMPLE_ENTERPRISE", "PROCESSOR_2019.SCORE_FRUITS_APPLICATION",
//					"PROCESSOR_2019.SCORE_VEGETBLES_APPLICATION", "PROCESSOR_2019.SAMPLE_VEGETABLES_APPLICATION",
//					"PROCESSOR_2019.SAMPLE_FRUITS_APPLICATION", "GRAPES_2019.SCORE_ENTERPRISE",
//					"GRAPES_2019.SAMPLE_ENTERPRISE", "GRAPES_2019.SCORE_APPLICATION", "GRAPES_2019.SAMPLE_APPLICATION",
//					"SLAUGHTERHOUSE_2020.SAMPLE_SELECTION_APPLICATION_FATTENS", "EDBAR_2020.SAMPLE_ANIMAL",
//					"GRAPES_2020.SCORE_ENTERPRISE", "GRAPES_2020.SAMPLE_ENTERPRISE",
//					"PROCESSOR_2020.SCORE_VEG_ENTERPRISE", "SLAUGHTERHOUSE_2020.SCORE_ENTERPRISE_FATTENS",
//					"SLAUGHTERHOUSE_2020.SAMPLE_ENTERPRISE", "AD_HOC.PROCESSING", "EDBAR_2020.RANKING_LAND",
//					"PROCESSOR_2020.SAMPLE_ENTERPRISE", "PROCESSOR_2020.SCORE_VEGETBLES_APPLICATION",
//					"APPLE2019.SAMPLE_ENTERPRISE", "APPLE2019.SCORE_ENTERPRISE", "MILK_2020.SAMPLE_ENTERPRISE",
//					"SLAUGHTERHOUSE_2020.ENTERPRISE_SLAUGHTERHOUSE_FATTENS", "MILK_2020.SAMPLE_APPLICATION_COWS",
//					"MILK_2020.SAMPLE_APPLICATION_SHEEP_GOATS", "PROCESSOR_2020.SAMPLE_VEGETABLES_APPLICATION",
//					"SLAUGHTERHOUSE_2020.SAMPLE_SELECTION_ENTERPRISE_FATTENS",
//					"SLAUGHTERHOUSE_2020.SELECTION_APPLICATION_FATTENS",
//					"SLAUGHTERHOUSE_2020.SCORE_APPLICATION_FATTENS", "SLAUGHTERHOUSE_2020.SAMPLE_APPLICATION_FATTENS",
//					"GRAPES_2020.SCORE_APPLICATION", "MILK_2020.SCORE_APPLICATION", "EDBAR_2020.RANKING_ANIMALS",
//					"APPLE2019.SCORE_APPLICATION", "SLAUGHTERHOUSE_2020.SAMPLE_SELECTION_ENTERPRISE_CATTLE", 
//					"AD_HOC.SELECTION_ENTERPRISES","GRAPES_2020.SAMPLE_SELECTION_APPLICATION_ADHOC",
//					"GRAPES_2020.SAMPLE_APPLICATION_ADHOC",	"GRAPES_2020.APPLICATION_DELIVERED_QUANTITY_ADHOC",
//					"GRAPES_2020.SCORE_APPLICATION_ADHOC","GRAPES_2020.SELECTION_APPLICATION_ADHOC",
//					"AD_HOC.PROCESSING", "AD_HOC.SELECTION", "AD_HOC.PROCESSING_ENTERPRISES", 
//					"AD_HOC.SELECTION_ENTERPRISES");

			for (Integer i = 0; i < executorsAclCodes.size(); i++) {
				System.out.println(executorsAclCodes.get(i));
				svc.grantPermission(group, executorsAclCodes.get(i));
//			if (group != null)
//				svc.grantPermission(group, "card.edbar");

				// svc.revokePermission(group, "card.batch_access");
				// svc.grantPermission(group, "card.rankExtraction_access");
			}
		} catch (SvException e) {
			fail();
			e.printStackTrace();
		} finally {
			if (svc != null)
				svc.release();
		}
	}

	// @Test
	// public void testSetPermissionToUserOrUserGroup() throws Exception {
	// SvReader svr = null;
	// SvWriter svw = null;
	// SvSecurity svs = null;
	//
	// try {
	// svs = new SvSecurity();
	// String sessionId = svs.logon("ADMIN", SvUtil.getMD5("welcome"));
	// svr = new SvReader(sessionId);
	// svr.dbSetAutoCommit(false);
	// svw = new SvWriter(svr);
	//
	// DbDataObject dboAdmins = svr.getObjectById(-6L,
	// svCONST.OBJECT_TYPE_GROUP, null);
	// processPermissionsToUserOrGroup("ASSIGN", dboAdmins,
	// getSvarogCoreTablePermission(svr),
	// getSvarogCustomTablePermission("FULL", svr), true, svr, svw, svs);
	//
	// } catch (SvException e) {
	// // TODO Auto-generated catch block
	// e.printStackTrace();
	// } finally {
	// if (svr != null)
	// svr.release();
	// if (svw != null)
	// svw.release();
	// }
	// }
	//
	// /**
	// * Help method for attaching already extracted permissions on a group
	// *
	// * @param groupName
	// * @param customTablePermissions
	// * @param svr
	// * @param svw
	// * @throws SvException
	// */
	// public void processPermissionsToUserOrGroup(String processType,
	// DbDataObject userOrGroupObj,
	// ArrayList<String> coreTablePermissions, ArrayList<String>
	// customTablePermissions, Boolean shouldRevoke,
	// SvReader svr, SvWriter svw, SvSecurity svs) throws SvException {
	// String permissionAction = "GRANT";
	// if (shouldRevoke) {
	// revokeAllCurrentPermission(userOrGroupObj, svr, svw, svs);
	// svw.dbCommit();
	// svs.dbCommit();
	// }
	// if (processType.equals("UNASSIGN")) {
	// permissionAction = "REVOKE";
	// }
	// // identify if user or group
	// String userOrGroupName = "USER_NAME";
	// if (userOrGroupObj.getObjectType().equals(svCONST.OBJECT_TYPE_GROUP)) {
	// userOrGroupName = "GROUP_NAME";
	// }
	//
	// if (coreTablePermissions != null && !coreTablePermissions.isEmpty()) {
	// setSidPermission(userOrGroupObj.getVal(userOrGroupName).toString(),
	// userOrGroupObj.getObjectType(),
	// coreTablePermissions, permissionAction, svr.getSessionId());
	// svw.dbCommit();
	// svs.dbCommit();
	// }
	// if (customTablePermissions != null && !customTablePermissions.isEmpty())
	// {
	// setSidPermission(userOrGroupObj.getVal(userOrGroupName).toString(),
	// userOrGroupObj.getObjectType(),
	// customTablePermissions, permissionAction, svr.getSessionId());
	// svw.dbCommit();
	// svs.dbCommit();
	// }
	// }
	//
	// public boolean setSidPermission(String sidName, Long sidType,
	// ArrayList<String> permissionKeys, String operation
	// ) {
	// SvSecurity svs = null;
	// SvReader svr = null;
	// DbDataObject sid = null;
	// try {
	// svr = new SvReader(token);
	// // svr.switchUser("ADMIN");
	// svs = new SvSecurity(svr);
	// svs.setAutoCommit(false);
	// sid = svs.getSid(sidName, sidType);
	// for (String permissionKey : permissionKeys)
	// if (operation.equals("GRANT"))
	// svs.grantPermission(sid, permissionKey.trim());
	// else if (operation.equals("REVOKE"))
	// svs.revokePermission(sid, permissionKey.trim());
	// else
	// System.out.println("Operation must be either GRANT or REVOKE, Wrong
	// operation:" + operation);
	// svs.dbCommit();
	// } catch (SvException e) {
	// return false;
	// } finally {
	// if (svs != null)
	// svs.release();
	// if (svr != null)
	// svr.release();
	// }
	// return true;
	// }
	//
	// /**
	// * Help method for revoking already extracted permissions on a group
	// *
	// * @param groupObj
	// * @param svr
	// * @param svw
	// * @throws SvException
	// */
	// public void revokeAllCurrentPermission(DbDataObject
	// objToGetPermissionsFrom, SvReader svr, SvWriter svw,
	// SvSecurity svs) throws SvException {
	// if
	// (objToGetPermissionsFrom.getObjectType().equals(svCONST.OBJECT_TYPE_GROUP))
	// {
	// DbDataObject groupObj = objToGetPermissionsFrom;
	// if (groupObj.getVal("GROUP_NAME") != null) {
	// ArrayList<String> currentPermissions = getCurrentUserPermissions(
	// groupObj.getVal("GROUP_NAME").toString(), svr);
	// if (setSidPermission(groupObj.getVal("GROUP_NAME").toString(),
	// svCONST.OBJECT_TYPE_GROUP,
	// currentPermissions, "REVOKE" ))
	// System.out.print("All permissions for:" +
	// groupObj.getVal("GROUP_NAME").toString() + " revoked.");
	// else
	// System.out.print("NONE permission for:" +
	// groupObj.getVal("GROUP_NAME").toString() + " revoked.");
	// }
	// }
	// if
	// (objToGetPermissionsFrom.getObjectType().equals(svCONST.OBJECT_TYPE_USER))
	// {
	// DbDataObject userObj = objToGetPermissionsFrom;
	// if (userObj.getVal("USER_NAME") != null) {
	// ArrayList<String> currentPermissions =
	// getCurrentUserPermissions(userObj.getVal("USER_NAME").toString(),
	// svr);
	// if (setSidPermission(userObj.getVal("USER_NAME").toString(),
	// svCONST.OBJECT_TYPE_USER,
	// currentPermissions, "REVOKE"))
	// System.out.print("All permissions for:" +
	// userObj.getVal("USER_NAME").toString() + " revoked.");
	// else
	// System.out.print("NONE permission for:" +
	// userObj.getVal("USER_NAME").toString() + " revoked.");
	// }
	// }
	//
	// }
	//
	// public ArrayList<String> getCurrentUserPermissions(String groupName,
	// SvReader svr) {
	// DbDataArray currentGroupPrmissions = new DbDataArray();
	// ArrayList<String> result = new ArrayList<>();
	// try {
	// DbDataObject sid = getGroupSid(groupName, svr);
	// if (sid != null) {
	// currentGroupPrmissions = svr.getPermissions(sid, svr);
	// if (currentGroupPrmissions != null &&
	// !currentGroupPrmissions.getItems().isEmpty()) {
	// for (DbDataObject tempDbo : currentGroupPrmissions.getItems()) {
	// if (tempDbo.getVal(Tc.LABEL_CODE) != null)
	// result.add(tempDbo.getVal(Tc.LABEL_CODE).toString());
	// }
	// }
	// }
	// } catch (SvException e) {
	// log4j.error(e.getFormattedMessage(), e);
	// }
	//
	// return result;
	// }
}
