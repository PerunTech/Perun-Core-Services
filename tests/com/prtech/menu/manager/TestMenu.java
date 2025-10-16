package com.prtech.menu.manager;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashSet;

import org.apache.logging.log4j.Logger;
import org.junit.Ignore;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.prtech.menu.manager.MenuExceptions.MenuError;
import com.prtech.menu.manager.MenuExceptions.MenuInvalidConfigError;
import com.prtech.menu.manager.MenuExceptions.UserNotAuthorizedError;
import com.prtech.perun.services.ws.Rc;
import com.prtech.svarog.SvConf;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvSecurity;
import com.prtech.svarog.SvUtil;
import com.prtech.svarog.SvWriter;
import com.prtech.svarog_common.DbDataObject;

@Ignore
public class TestMenu {
	static final Logger log4j = SvConf.getLogger(TestMenu.class);
	static String menuConfValid = "{\"buttonArray\":[{\"menu_name\":\"test_menu\"}]}";
	static String menuConfValidImport = "{\"buttonArray\":[{\"IMPORT_MENU\":\"test_menu\"},{\"menu_name\":\"test_menu2\"}]}";

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

	private JsonObject createBasicMenuFormValues(String menuCode, String labelCode, String menuConf) {
		JsonObject data = new JsonObject();
		Gson gson = new Gson();
		data.addProperty(Rc.OBJECT_ID, 0l);
		data.addProperty("MENU_CODE", menuCode);
		data.addProperty("LABEL_CODE", labelCode);
		data.addProperty("MENU_TYPE", "menu");
		data.addProperty("VERSION", 1);
		if (menuConf != null) {
			data.add("MENU_CONF", gson.fromJson(menuConf, JsonObject.class));
		}
		return data;
	}

	private void setAutoCommit(SvReader svr, SvWriter svw) throws SvException {
		svr.setAutoCommit(false);
		svr.dbSetAutoCommit(false);
		svw.setAutoCommit(false);
		svw.dbSetAutoCommit(false);
	}

	private DbDataObject saveMenuAndAssert(JsonObject data, SvReader svr, SvWriter svw)
			throws SvException, UserNotAuthorizedError, MenuError {
		DbDataObject menuDbo = MenuHelper.saveMenuHelper(data, svr);
		svw.saveObject(menuDbo);
		assertTrue(menuDbo.getObjectId() != 0);
		return menuDbo;
	}

	@Test(expected = MenuInvalidConfigError.class)
	public void testAddMenuInvalidNoMenuConf() throws UserNotAuthorizedError, MenuError {
		String sessionId = getUserSessionIdForTestPurpose();
		assertTrue(sessionId != null && !sessionId.isBlank());

		JsonObject data = createBasicMenuFormValues("test_menu", "test_menu", null);

		try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
			MenuHelper.saveMenuHelper(data, svr);
		} catch (SvException e) {
			fail(e.getMessage());
		}
	}

	@Test(expected = MenuInvalidConfigError.class)
	public void testAddMenuInvalidBadConf() throws UserNotAuthorizedError, MenuError {
		String sessionId = getUserSessionIdForTestPurpose();
		assertTrue(sessionId != null && !sessionId.isBlank());

		JsonObject data = createBasicMenuFormValues("test_menu", "test_menu", "{\"menu_name\": \"test_menu\"}");

		try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
			MenuHelper.saveMenuHelper(data, svr);
		} catch (SvException e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testAddMenuValid() {
		String sessionId = getUserSessionIdForTestPurpose();
		assertTrue(sessionId != null && !sessionId.isBlank());

		JsonObject data = createBasicMenuFormValues("test_menu", "test_menu", menuConfValid);

		try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
			setAutoCommit(svr, svw);
			saveMenuAndAssert(data, svr, svw);
		} catch (SvException | UserNotAuthorizedError | MenuError e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testAddMenuValidWithImport() {
		String sessionId = getUserSessionIdForTestPurpose();
		assertTrue(sessionId != null && !sessionId.isBlank());

		JsonObject data = createBasicMenuFormValues("test_menu", "test_menu", menuConfValid);
		JsonObject data2 = createBasicMenuFormValues("test_menu2", "test_menu2", menuConfValidImport);

		try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
			setAutoCommit(svr, svw);
			saveMenuAndAssert(data, svr, svw);
			saveMenuAndAssert(data2, svr, svw);
		} catch (SvException | UserNotAuthorizedError | MenuError e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testDeleteMenuValid() {
		String sessionId = getUserSessionIdForTestPurpose();
		assertTrue(sessionId != null && !sessionId.isBlank());

		JsonObject data = createBasicMenuFormValues("test_menu", "test_menu", menuConfValid);

		try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
			setAutoCommit(svr, svw);
			saveMenuAndAssert(data, svr, svw);

			MenuHelper.deleteMenuHelper("test_menu", svr);
		} catch (SvException | UserNotAuthorizedError | MenuError e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testGenerateMenuValid() {
		String sessionId = getUserSessionIdForTestPurpose();
		assertTrue(sessionId != null && !sessionId.isBlank());

		JsonObject data = createBasicMenuFormValues("test_menu", "test_menu", menuConfValid);
		JsonObject data2 = createBasicMenuFormValues("test_menu2", "test_menu2", menuConfValidImport);

		try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
			setAutoCommit(svr, svw);
			saveMenuAndAssert(data, svr, svw);
			DbDataObject menuDbo2 = saveMenuAndAssert(data2, svr, svw);

			JsonObject resultJson = MenuHelper.buildFullHierarchy(menuDbo2, svr, new HashSet<>());
			assertTrue(resultJson != null);
			assertTrue(resultJson.has("buttonArray"));

			boolean hasImportMenu = false;
			JsonArray buttonArray = resultJson.getAsJsonArray("buttonArray");
			for (JsonElement elem : buttonArray) {
				if (elem.isJsonObject() && elem.getAsJsonObject().has("menu_name")
						&& elem.getAsJsonObject().get("menu_name").getAsString().equals("test_menu")) {
					hasImportMenu = true;
					break;
				}
			}
			assertTrue(hasImportMenu);
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}
}
