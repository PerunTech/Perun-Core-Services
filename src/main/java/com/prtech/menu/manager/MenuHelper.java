package com.prtech.menu.manager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.prtech.menu.manager.MenuExceptions.ImportedMenuNotFoundError;
import com.prtech.menu.manager.MenuExceptions.MenuDeleteConstraintError;
import com.prtech.menu.manager.MenuExceptions.MenuError;
import com.prtech.menu.manager.MenuExceptions.MenuInvalidConfigError;
import com.prtech.menu.manager.MenuExceptions.MenuNotFoundError;
import com.prtech.menu.manager.MenuExceptions.MenuSaveError;
import com.prtech.menu.manager.MenuExceptions.UserNotAuthorizedError;
import com.prtech.perun.services.ws.DbReader;
import com.prtech.svarog.SvComplexCache;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvRelationCache;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbSearchCriterion;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;

final class MenuHelper {
	private static final Logger log4j = LogManager.getLogger(MenuHelper.class);

	private MenuHelper() {
	}

	/**
	 * Recursively builds full menu config based on IMPORT_MENU json key. Maintains
	 * top-down ordering of buttonArray elements.
	 * 
	 * @param menuDbo Initial menu object
	 * @param svr     SvReader instance
	 * @param visited Set of visited menu object IDs to prevent loop
	 * @return Merged JsonObject with buttonArray
	 */
	static JsonObject buildFullHierarchy(DbDataObject menuDbo, SvReader svr, Set<Long> visited) throws Exception {
		JsonArray mergedButtons = new JsonArray();
		buildRecursiveWithSvCache(menuDbo, svr, visited, mergedButtons);
		JsonObject result = new JsonObject();
		result.add("buttonArray", mergedButtons);
		return result;
	}

	static void buildRecursive(DbDataObject menuDbo, SvReader svr, Set<Long> visited, JsonArray mergedButtons)
			throws Exception {
		if (menuDbo == null || !visited.add(menuDbo.getObjectId()))
			return;
		String menuConfStr = (String) menuDbo.getVal(CC.MENU_CONF);
		if (menuConfStr == null)
			return;
		JsonObject confJson = JsonParser.parseString(menuConfStr).getAsJsonObject();
		if (!confJson.has("buttonArray"))
			return;
		JsonArray btns = confJson.getAsJsonArray("buttonArray");
		for (JsonElement btn : btns) {
			if (btn.isJsonObject() && btn.getAsJsonObject().has(CC.IMPORT_MENU)) {
				String importCode = btn.getAsJsonObject().get(CC.IMPORT_MENU).getAsString();
				DbDataObject importedMenu = new DbReader().searchDbObjectBySingleFilter(DbCompareOperand.EQUAL,
						SvReader.getTypeIdByName(CC.PERUN_MENU), CC.MENU_CODE, importCode, svr);
				if (importedMenu != null) {
					buildRecursive(importedMenu, svr, visited, mergedButtons);
				} else {
					log4j.warn("IMPORT_MENU: Menu code not found: " + importCode);
				}
			} else {
				mergedButtons.add(btn.deepCopy());
			}
		}
	}

	static void buildRecursiveWithSvCache(DbDataObject menuDbo, SvReader svr, Set<Long> visited,
			JsonArray mergedButtons) throws Exception {
		if (menuDbo == null || visited.contains(menuDbo.getObjectId()))
			return;
		visited.add(menuDbo.getObjectId());
		String menuConfStr = (String) menuDbo.getVal(CC.MENU_CONF);
		if (menuConfStr == null)
			return;
		JsonObject confJson = JsonParser.parseString(menuConfStr).getAsJsonObject();
		if (!confJson.has("buttonArray"))
			return;
		JsonArray btns = confJson.getAsJsonArray("buttonArray");
		for (JsonElement btn : btns) {
			if (btn.isJsonObject() && btn.getAsJsonObject().has(CC.IMPORT_MENU)) {
				String importCode = btn.getAsJsonObject().get(CC.IMPORT_MENU).getAsString();
				DbDataObject importedMenu = findObjectUsingSvCache(CC.MENU_CODE, importCode, CC.PERUN_MENU, "PM", svr);
				if (importedMenu != null) {
					buildRecursiveWithSvCache(importedMenu, svr, visited, mergedButtons);
				} else {
					log4j.warn("IMPORT_MENU: Menu code not found: " + importCode);
				}
			} else {
				mergedButtons.add(btn.deepCopy());
			}
		}
	}

	/**
	 * Finds and returns a DbDataArray from a specific table using a Svarog cache.
	 * The method checks the `SvComplexCache` for entries matching the given column
	 * value (used as cache key), and falls back to database retrieval if not
	 * cached. Once retrieved, the result is stored in the cache for future access.
	 *
	 * @param columnName  The name of the column to filter on (e.g., "MENU_CODE").
	 * @param columnValue The value of the column to search for (also used as cache
	 *                    key).
	 * @param tableName   The data object descriptor table name in Svarog (e.g.,
	 *                    "PERUN_MENU").
	 * @param cacheAlias  The alias used for the table in cache dbSearch (e.g.,
	 *                    "PM").
	 * @param svr         An active instance of SvReader used for DB access if
	 *                    needed.
	 * @return A DbDataArray containing all matching objects (may be empty but never
	 *         null).
	 * @throws SvException if any error occurs during DB or cache operations.
	 */
	static DbDataArray findObjectsUsingSvCache(String columnName, String columnValue, String tableName,
			String cacheAlias, SvReader svr) throws SvException {
		String uniqueCacheId = CC.PERUN_MENU;
		DbDataArray result = SvComplexCache.getData(uniqueCacheId, svr);
		if (result != null) {
			return result;
		}
		DbSearchCriterion search = new DbSearchCriterion(CC.STATUS, DbCompareOperand.EQUAL, CC.VALID);
		SvRelationCache src = new SvRelationCache(SvCore.getDbtByName(tableName), search, cacheAlias, null, null, null,
				null);
		SvComplexCache.addRelationCache(uniqueCacheId, src, false);
		return SvComplexCache.getData(uniqueCacheId, svr);
	}

	/**
	 * Finds and returns a single `DbDataObject` from a specific table using a
	 * SVAROG cache strategy. Internally relies on `findObjectsUsingSvCache` and
	 * returns the first object if any are found.
	 *
	 * @param columnName  The name of the column to filter on (e.g., "MENU_CODE").
	 * @param columnValue The value of the column to search for.
	 * @param tableName   The logical table name in Svarog (e.g., "PERUN_MENU").
	 * @param cacheAlias  The alias used for the table in cache dbSearch (e.g.,
	 *                    "PM").
	 * @param svr         An active instance of `SvReader` used for DB access if
	 *                    needed.
	 * @return A DbDataObject if found, or NULL if no match is found.
	 * @throws SvException if any error occurs during DB or cache operations.
	 */
	static DbDataObject findObjectUsingSvCache(String columnName, String columnValue, String tableName,
			String cacheAlias, SvReader svr) throws SvException {
		DbDataArray dbArr = findObjectsUsingSvCache(columnName, columnValue, tableName, cacheAlias, svr);
		if (!dbArr.isEmpty()) {
			DbDataObject dbo = dbArr.getItemByIdx(columnValue);
			if (dbo == null) {
				dbArr.rebuildIndex(columnName, true);
			}
			return dbArr.getItemByIdx(columnValue);
		} else
			return null;
	}

	static DbDataObject findMenuByCode(String menuCode, SvReader svr) throws Exception {
		DbSearchCriterion filter = new DbSearchCriterion(CC.MENU_CODE, DbCompareOperand.EQUAL, menuCode);
		DbDataArray result = svr.getObjects(filter, SvReader.getTypeIdByName(CC.PERUN_MENU), null, 0, 0);
		return result != null && !result.getItems().isEmpty() ? result.get(0) : null;
	}

	/**
	 * Set PERUN_MENU object values
	 * 
	 * @param perunMenuDbo    database object to set values to
	 * @param menuCode        unique code for the menu
	 * @param labelCode       label code of the menu
	 * @param menuType        type of the menu (ex. menu, menu-component)
	 * @param menuConf        JSON configuration for the menu
	 * @param parentTableName for what table is this menu configuration
	 * @param svarogCdlCat    code_value of svarog codelist item
	 * @param svarogAclLbl    permission for the menu
	 * @param internalCat     internal category
	 * @param version
	 * @return The new DbDataObject
	 * @throws SvException
	 */
	static void setPerunMenuObjectValues(DbDataObject perunMenuDbo, Long parentId, String menuCode, String labelCode,
			String menuType, String menuConf, String parentTableName, String svarogCdlCat, String svarogAclLbl,
			String internalCat, Long version) throws SvException {
		perunMenuDbo.setParentId(parentId);
		perunMenuDbo.setVal(CC.MENU_CODE, menuCode);
		perunMenuDbo.setVal(CC.LABEL_CODE, labelCode);
		perunMenuDbo.setVal(CC.MENU_TYPE, menuType);
		perunMenuDbo.setVal(CC.MENU_CONF, menuConf);
		perunMenuDbo.setVal(CC.PARENT_TABLE_NAME, parentTableName);
		perunMenuDbo.setVal(CC.SVAROG_CDL_CAT, svarogCdlCat);
		perunMenuDbo.setVal(CC.SVAROG_ACL_LBL, svarogAclLbl);
		perunMenuDbo.setVal(CC.INTERNAL_CAT, internalCat);
		perunMenuDbo.setVal(CC.VERSION, version);
	}

	/**
	 * Set PERUN_MENU object values from the JSON data
	 * 
	 * @param perunMenuDbo database object to set values to
	 * @param requestData  JSON object containing the database object data
	 * @return
	 * @throws SvException
	 */
	static void setPerunMenuObjectValues(DbDataObject perunMenuDbo, JsonObject requestData) throws SvException {
		String menuCode = requestData.has(CC.MENU_CODE) ? requestData.get(CC.MENU_CODE).getAsString() : null;
		String labelCode = requestData.has(CC.LABEL_CODE) ? requestData.get(CC.LABEL_CODE).getAsString() : null;
		String menuType = requestData.has(CC.MENU_TYPE) ? requestData.get(CC.MENU_TYPE).getAsString() : null;
		String menuConf = requestData.has(CC.MENU_CONF) && requestData.get(CC.MENU_CONF).isJsonObject()
				? requestData.getAsJsonObject(CC.MENU_CONF).toString()
				: null;
		String parentTableName = requestData.has(CC.PARENT_TABLE_NAME)
				? requestData.get(CC.PARENT_TABLE_NAME).getAsString()
				: null;
		String svarogCdlCat = requestData.has(CC.SVAROG_CDL_CAT) ? requestData.get(CC.SVAROG_CDL_CAT).getAsString()
				: null;
		String svarogAclLbl = requestData.has(CC.SVAROG_ACL_LBL) ? requestData.get(CC.SVAROG_ACL_LBL).getAsString()
				: null;
		String internalCat = requestData.has(CC.INTERNAL_CAT) ? requestData.get(CC.INTERNAL_CAT).getAsString() : null;
		Long version = requestData.has(CC.VERSION) ? requestData.get(CC.VERSION).getAsLong() : 1;
		Long parentId = requestData.has(CC.PARENT_ID) ? requestData.get(CC.PARENT_ID).getAsLong() : 0;

		setPerunMenuObjectValues(perunMenuDbo, parentId, menuCode, labelCode, menuType, menuConf, parentTableName,
				svarogCdlCat, svarogAclLbl, internalCat, version);
	}

	/**
	 * Helper method for deleting a PERUN_MENU object. If the menu is referenced by
	 * other menus it will block the deletion.
	 * 
	 * @param menuCode
	 * @param svr
	 * @return
	 * @throws SvException
	 */
	static void deleteMenuHelper(String menuCode, SvReader svr) throws SvException, MenuError {
		Gson gson = new Gson();
		List<String> usedBy = new ArrayList<String>();
		DbDataArray allMenuItems = svr.getObjectsByTypeId(SvReader.getTypeIdByName(CC.PERUN_MENU), null, 0, 0);

		for (DbDataObject dbo : allMenuItems.getItems()) {
			String menuConfStr = (String) dbo.getVal(CC.MENU_CONF);
			if (menuConfStr == null)
				continue;
			JsonObject confJson = gson.fromJson(menuConfStr, JsonObject.class);
			if (!confJson.has("buttonArray"))
				continue;
			JsonArray btns = confJson.getAsJsonArray("buttonArray");
			for (JsonElement btn : btns) {
				if (btn.isJsonObject() && btn.getAsJsonObject().has(CC.IMPORT_MENU)) {
					String importMenuCode = btn.getAsJsonObject().get(CC.IMPORT_MENU).getAsString();
					if (importMenuCode.equals(menuCode)) {
						usedBy.add(dbo.getVal(CC.MENU_CODE).toString());
						break;
					}
				}
			}
		}
		if (!usedBy.isEmpty()) {
			throw new MenuDeleteConstraintError(
					"Can't delete menu item because it's used by these items: " + usedBy.toString(), usedBy);
		}
	}

	/**
	 * Check if the user has the appropriate svarog acl permission for the given
	 * menu object.
	 * 
	 * @param menuDbo
	 * @param accessType
	 * @param svr
	 * @return
	 * @throws SvException
	 */
	static boolean checkUserHasPermission(DbDataObject menuDbo, List<String> accessType, SvReader svr)
			throws SvException {
		String aclLabelCode = menuDbo.getVal(CC.SVAROG_ACL_LBL) == null ? null
				: menuDbo.getVal(CC.SVAROG_ACL_LBL).toString();
		if (aclLabelCode == null) {
			return true;
		} else {
			return new DbReader().canAccess(aclLabelCode, accessType, svr);
		}
	}

	/**
	 * Helper method for creating and validating a PERUN_MENU object for saving.
	 * 
	 * @param requestData
	 * @param svr
	 * @return
	 * @throws SvException
	 * @throws UserNotAuthorizedError
	 * @throws MenuSaveError
	 */
	static DbDataObject saveMenuHelper(JsonObject requestData, SvReader svr)
			throws SvException, UserNotAuthorizedError, MenuError {
		DbDataObject menuDbo;
		Long objectId = requestData.get(CC.OBJECT_ID).getAsLong();
		if (objectId == 0) {
			menuDbo = new DbDataObject();
			menuDbo.setObjectType(SvReader.getTypeIdByName(CC.PERUN_MENU));
		} else {
			menuDbo = svr.getObjectById(objectId, SvReader.getTypeIdByName(CC.PERUN_MENU), null);
			if (menuDbo == null) {
				throw new MenuNotFoundError(String.format("The menu with object_id: %d was not found", objectId));
			}

			if (!checkUserHasPermission(menuDbo, Arrays.asList("FULL", "WRITE"), svr)) {
				throw new UserNotAuthorizedError("User does not have permission to edit this menu");
			}
		}

		setPerunMenuObjectValues(menuDbo, requestData);
		String menuConfStr = (String) menuDbo.getVal(CC.MENU_CONF);
		if (menuConfStr == null || menuConfStr.isBlank()) {
			throw new MenuInvalidConfigError("The menu must have valid configuration");
		}

		JsonObject confJson = JsonParser.parseString(menuConfStr).getAsJsonObject();
		if (!confJson.has("buttonArray")) {
			throw new MenuInvalidConfigError("Menu configuration is missing required buttonArray key");
		}

		JsonArray btns = confJson.getAsJsonArray("buttonArray");
		List<String> missingMenuCodes = new ArrayList<String>();
		for (JsonElement btn : btns) {
			if (btn.isJsonObject() && btn.getAsJsonObject().has(CC.IMPORT_MENU)) {
				String importCode = btn.getAsJsonObject().get(CC.IMPORT_MENU).getAsString();
				DbDataObject importedMenu = findObjectUsingSvCache(CC.MENU_CODE, importCode, CC.PERUN_MENU, "PM", svr);
				if (importedMenu == null) {
					missingMenuCodes.add(importCode);
				}
			}
		}
		if (!missingMenuCodes.isEmpty()) {
			throw new ImportedMenuNotFoundError(
					"The following imported menus are missing: " + missingMenuCodes.toString(), missingMenuCodes);
		}

		return menuDbo;
	}

	/**
	 * Method that checks if JSON data has all required keys that are given as an
	 * argument
	 * 
	 * @param requestData    JSON data that will be checked
	 * @param requiredFields List of all fields that need to be present in the JSON
	 *                       data
	 * @return
	 */
	public static List<String> checkAndReturnMissingKeys(JsonObject requestData, List<String> requiredFields) {
		List<String> missing = new ArrayList<String>();
		for (String field : requiredFields) {
			if (!requestData.has(field)) {
				missing.add(field);
			}
		}
		return missing;
	}
}
