package com.prtech.menu.manager;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.List;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.logging.log4j.Logger;
import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.prtech.menu.manager.MenuExceptions.MenuSaveError;
import com.prtech.menu.manager.MenuExceptions.UserNotAuthorizedError;
import com.prtech.perun.services.ws.Rc;
import com.prtech.svarog.SvConf;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvSecurity;
import com.prtech.svarog.SvUtil;
import com.prtech.svarog.SvWriter;
import com.prtech.svarog_common.DbDataObject;

public class TestMenu {
	static final Logger log4j = SvConf.getLogger(TestMenu.class);
	static String menuConfValid = "{\"buttonArray\":[{\"menu_name\":\"test_menu\"}]}";
	static String menuConfValidImport = "{\"buttonArray\":[{\"menu_name\":\"test_menu\"}]}";

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

	private MultivaluedMap<String, String> createBasicMenuFormValues(String menuCode, String labelCode,
			String menuConf) {
		MultivaluedMap<String, String> formVals = new MultivaluedHashMap<>();
		formVals.add(Rc.OBJECT_ID, "0");
		formVals.add("MENU_CODE", menuCode);
		formVals.add("LABEL_CODE", labelCode);
		formVals.add("MENU_TYPE", "menu");
		formVals.add("VERSION", "1");
		if (menuConf != null) {
			formVals.add("MENU_CONF", menuConf);
		}
		return formVals;
	}

	private void setAutoCommit(SvReader svr, SvWriter svw) throws SvException {
		svr.setAutoCommit(false);
		svr.dbSetAutoCommit(false);
		svw.setAutoCommit(false);
		svw.dbSetAutoCommit(false);
	}

	private DbDataObject saveMenuAndAssert(MultivaluedMap<String, String> formVals, SvReader svr, SvWriter svw)
			throws SvException, UserNotAuthorizedError, MenuSaveError {
		DbDataObject menuDbo = MenuHelper.saveMenuHelper(formVals, svr);
		svw.saveObject(menuDbo);
		assertTrue(menuDbo.getObjectId() != 0);
		return menuDbo;
	}

	@Test(expected = MenuSaveError.class)
	public void testAddMenuInvalidNoMenuConf() throws UserNotAuthorizedError, MenuSaveError {
		String sessionId = getUserSessionIdForTestPurpose();
		assertTrue(sessionId != null && !sessionId.isBlank());

		MultivaluedMap<String, String> formVals = createBasicMenuFormValues("test_menu", "test_menu", null);

		try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
			MenuHelper.saveMenuHelper(formVals, svr);
		} catch (SvException e) {
			fail(e.getMessage());
		}
	}

	@Test(expected = MenuSaveError.class)
	public void testAddMenuInvalidBadConf() throws UserNotAuthorizedError, MenuSaveError {
		String sessionId = getUserSessionIdForTestPurpose();
		assertTrue(sessionId != null && !sessionId.isBlank());

		MultivaluedMap<String, String> formVals = createBasicMenuFormValues("test_menu", "test_menu",
				"{\"menu_name\": \"test_menu\"}");

		try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
			MenuHelper.saveMenuHelper(formVals, svr);
		} catch (SvException e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testAddMenuValid() {
		String sessionId = getUserSessionIdForTestPurpose();
		assertTrue(sessionId != null && !sessionId.isBlank());

		MultivaluedMap<String, String> formVals = createBasicMenuFormValues("test_menu", "test_menu", menuConfValid);

		try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
			setAutoCommit(svr, svw);
			saveMenuAndAssert(formVals, svr, svw);
		} catch (SvException | UserNotAuthorizedError | MenuSaveError e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testAddMenuValidWithImport() {
		String sessionId = getUserSessionIdForTestPurpose();
		assertTrue(sessionId != null && !sessionId.isBlank());

		MultivaluedMap<String, String> formVals = createBasicMenuFormValues("test_menu", "test_menu", menuConfValid);
		MultivaluedMap<String, String> formVals2 = createBasicMenuFormValues("test_menu2", "test_menu2",
				menuConfValidImport);

		try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
			setAutoCommit(svr, svw);
			saveMenuAndAssert(formVals, svr, svw);
			saveMenuAndAssert(formVals2, svr, svw);
		} catch (SvException | UserNotAuthorizedError | MenuSaveError e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testDeleteMenuValid() {
		String sessionId = getUserSessionIdForTestPurpose();
		assertTrue(sessionId != null && !sessionId.isBlank());

		MultivaluedMap<String, String> formVals = createBasicMenuFormValues("test_menu", "test_menu", menuConfValid);

		try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
			setAutoCommit(svr, svw);
			saveMenuAndAssert(formVals, svr, svw);

			List<String> errors = MenuHelper.deleteMenuHelper("test_menu", svr);
			assertTrue(errors.isEmpty());
		} catch (SvException | UserNotAuthorizedError | MenuSaveError e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testGenerateMenuValid() {
		String sessionId = getUserSessionIdForTestPurpose();
		assertTrue(sessionId != null && !sessionId.isBlank());

		MultivaluedMap<String, String> formVals = createBasicMenuFormValues("test_menu", "test_menu", menuConfValid);
		MultivaluedMap<String, String> formVals2 = createBasicMenuFormValues("test_menu2", "test_menu2",
				menuConfValidImport);

		try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
			setAutoCommit(svr, svw);
			saveMenuAndAssert(formVals, svr, svw);
			DbDataObject menuDbo2 = saveMenuAndAssert(formVals2, svr, svw);

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
