package com.prtech.menu.manager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

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
import com.prtech.svarog.I18n;
import com.prtech.svarog.SvComplexCache;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvRelationCache;
import com.prtech.svarog.svCONST;
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
		buildRecursiveWithSvCache(menuDbo, svr, visited, mergedButtons, null);
		JsonObject result = new JsonObject();

		String year = String.valueOf(new DateTime().year().get());
		String mergedButtonsStr = mergedButtons.toString();
		mergedButtonsStr = mergedButtonsStr.replaceAll("%TOKEN%", svr.getSessionId());
		mergedButtonsStr = mergedButtonsStr.replaceAll("\"true\"", "true");
		mergedButtonsStr = mergedButtonsStr.replaceAll("\"false\"", "false");
		mergedButtonsStr = mergedButtonsStr.replaceAll("%CURRENT_YEAR%", year);
		Pattern pattern = Pattern.compile("%REPO_ID_(\\w+)%");
		Matcher matcher = pattern.matcher(mergedButtonsStr);

		while (matcher.find()) {
			String tableName = matcher.group(1).toUpperCase();
			String placeholder = matcher.group(0);
			String replacement = SvCore.getTypeIdByName(tableName).toString();
			mergedButtonsStr = mergedButtonsStr.replace(placeholder, replacement);
		}

		JsonArray buttonArray = new Gson().fromJson(mergedButtonsStr, JsonArray.class);
		result.add("buttonArray", buttonArray);
		return result;
	}

	static void buildRecursive(DbDataObject menuDbo, SvReader svr, Set<Long> visited, JsonArray mergedButtons,
			JsonObject configData) throws Exception {
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
			processMenuItem(btn, svr, visited, mergedButtons, configData);
		}
	}

	static void buildRecursive(JsonObject obj, SvReader svr, Set<Long> visited, JsonArray mergedButtons,
			JsonObject configData) throws Exception {
		processMenuItem(obj, svr, visited, mergedButtons, configData);
	}

	private static void processMenuItem(JsonElement item, SvReader svr, Set<Long> visited, JsonArray mergedButtons,
			JsonObject configData) throws Exception {
		if (!item.isJsonObject())
			return;

		JsonObject obj = item.getAsJsonObject();
		String localeId = svr.getUserLocaleId(svr.getInstanceUser());

		if (obj.has(CC.IMPORT_MENU)) {
			String importCode = obj.get(CC.IMPORT_MENU).getAsString();
			DbDataObject importedMenu = new DbReader().searchDbObjectBySingleFilter(DbCompareOperand.EQUAL,
					SvReader.getTypeIdByName(CC.PERUN_MENU), CC.MENU_CODE, importCode, svr);

			if (importedMenu != null) {
				buildRecursive(importedMenu, svr, visited, mergedButtons, obj);
			} else {
				log4j.warn("IMPORT_MENU: Menu code not found: " + importCode);
			}
			return;
		}

		if (obj.has(CC.DATA) && obj.get(CC.DATA).isJsonArray()) {
			JsonArray dataArray = new JsonArray();
			for (JsonElement dataElem : obj.getAsJsonArray(CC.DATA)) {
				processMenuItem(dataElem, svr, visited, dataArray, dataElem.getAsJsonObject());
			}
			obj.add(CC.DATA, dataArray);
		}

		if (configData != null) {
			if (configData.has(CC.INSERT) && configData.get(CC.INSERT).isJsonObject()) {
				deepMerge(configData.getAsJsonObject(CC.INSERT), obj);
			}

			String menuStr = obj.toString();
			DbDataObject dboTable = null;
			if (configData.has(CC.TABLE_NAME)) {
				dboTable = SvReader.getDbtByName(configData.get(CC.TABLE_NAME).getAsString());
			}

			if (menuStr.contains("%CHILD_ID_OF%")) {
				String recordIdStr = "0";
				DbDataArray recordArray = svr.getObjectsByParentId(0l, dboTable.getObjectId(), null);
				if (null != recordArray && !recordArray.isEmpty()) {
					recordIdStr = recordArray.get(0).getObjectId().toString();
				}
				menuStr = menuStr.replaceAll("%CHILD_ID_OF%", recordIdStr);
			}

			if (dboTable != null) {
				menuStr = menuStr.replaceAll("%TABLE_NAME%", dboTable.getVal("TABLE_NAME").toString());
				menuStr = menuStr.replaceAll("%TABLE_NAME_LABEL_CODE%", dboTable.getVal("LABEL_CODE").toString());
				menuStr = menuStr.replaceAll("%TABLE_NAME_OBJECT_ID%", dboTable.getObjectId().toString());
			}

			for (String key : configData.keySet()) {
				if (Arrays.asList(CC.TABLE_NAME, CC.INSERT, CC.IMPORT_MENU).contains(key)) {
					continue;
				}

				if (!configData.get(key).isJsonNull()) {
					String toReplace = "%" + key + "%";
					menuStr = menuStr.replaceAll(toReplace, configData.get(key).getAsString());
				}
			}

			obj = new Gson().fromJson(menuStr, JsonObject.class);
			cleanMenuItem(obj);
		}

		if (obj.has(CC.LABEL)) {
			String labelCode = obj.get(CC.LABEL).getAsString();
			String labelText = I18n.getText(localeId, labelCode);
			obj.addProperty(CC.LABEL, labelText);
		}

		mergedButtons.add(obj.deepCopy());
	}

	static void buildRecursiveWithSvCache(DbDataObject menuDbo, SvReader svr, Set<Long> visited,
			JsonArray mergedButtons, JsonObject configData) throws Exception {
		if (menuDbo == null)
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
			processMenuItemWithSvCache(btn, svr, visited, mergedButtons, configData);
		}
	}

	static void buildRecursiveWithSvCache(JsonObject obj, SvReader svr, Set<Long> visited, JsonArray mergedButtons,
			JsonObject configData) throws Exception {
		processMenuItemWithSvCache(obj, svr, visited, mergedButtons, configData);
	}

	private static void processMenuItemWithSvCache(JsonElement item, SvReader svr, Set<Long> visited,
			JsonArray mergedButtons, JsonObject configData) throws Exception {
		if (!item.isJsonObject())
			return;

		JsonObject obj = item.getAsJsonObject();
		String localeId = svr.getUserLocaleId(svr.getInstanceUser());

		if (obj.has(CC.IMPORT_MENU)) {
			String importCode = obj.get(CC.IMPORT_MENU).getAsString();
			DbDataObject importedMenu = findObjectUsingSvCache(CC.MENU_CODE, importCode, CC.PERUN_MENU, CC.PM, svr);

			if (importedMenu != null) {
				buildRecursiveWithSvCache(importedMenu, svr, visited, mergedButtons, obj);
			} else {
				log4j.warn("IMPORT_MENU: Menu code not found: " + importCode);
			}
			return;
		}

		if (obj.has(CC.DATA) && obj.get(CC.DATA).isJsonArray()) {
			JsonArray dataArray = new JsonArray();
			for (JsonElement dataElem : obj.getAsJsonArray(CC.DATA)) {
				processMenuItemWithSvCache(dataElem, svr, visited, dataArray, dataElem.getAsJsonObject());
			}
			obj.add(CC.DATA, dataArray);
		}

		if (configData != null) {
			if (configData.has(CC.INSERT) && configData.get(CC.INSERT).isJsonObject()) {
				deepMerge(configData.getAsJsonObject(CC.INSERT), obj);
			}

			String menuStr = obj.toString();
			DbDataObject dboTable = null;
			if (configData.has(CC.TABLE_NAME)) {
				dboTable = SvReader.getDbtByName(configData.get(CC.TABLE_NAME).getAsString());
			}

			if (menuStr.contains("%CHILD_ID_OF%")) {
				String recordIdStr = "0";
				DbDataArray recordArray = svr.getObjectsByParentId(0l, dboTable.getObjectId(), null);
				if (null != recordArray && !recordArray.isEmpty()) {
					recordIdStr = recordArray.get(0).getObjectId().toString();
				}
				menuStr = menuStr.replaceAll("%CHILD_ID_OF%", recordIdStr);
			}

			if (dboTable != null) {
				menuStr = menuStr.replaceAll("%TABLE_NAME%", dboTable.getVal("TABLE_NAME").toString());
				menuStr = menuStr.replaceAll("%TABLE_NAME_LABEL_CODE%", dboTable.getVal("LABEL_CODE").toString());
				menuStr = menuStr.replaceAll("%TABLE_NAME_OBJECT_ID%", dboTable.getObjectId().toString());
			}

			for (String key : configData.keySet()) {
				if (Arrays.asList(CC.TABLE_NAME, CC.INSERT, CC.IMPORT_MENU).contains(key)) {
					continue;
				}

				if (!configData.get(key).isJsonNull()) {
					String toReplace = "%" + key + "%";
					menuStr = menuStr.replaceAll(toReplace, configData.get(key).getAsString());
				}
			}

			obj = new Gson().fromJson(menuStr, JsonObject.class);
			cleanMenuItem(obj);
		}

		if (obj.has(CC.LABEL)) {
			String labelCode = obj.get(CC.LABEL).getAsString();
			String labelText = I18n.getText(localeId, labelCode);
			obj.addProperty(CC.LABEL, labelText);
		}

		mergedButtons.add(obj.deepCopy());
	}

	private static void cleanMenuItem(JsonObject item) {
		boolean removeInitialData = false;
		JsonObject objectConfigurationJsonObject = item.get("objectConfiguration") != null
				? item.get("objectConfiguration").getAsJsonObject()
				: null;
		JsonObject initialData = (objectConfigurationJsonObject != null
				&& objectConfigurationJsonObject.get("initialData") != null)
						? objectConfigurationJsonObject.get("initialData").getAsJsonObject()
						: null;

		if (initialData != null && initialData.has("initialDataWs") && (initialData.get("initialDataWs").isJsonNull()
				|| initialData.get("initialDataWs").getAsString().contains("%INITIAL_DATA_WS%")))
			removeInitialData = true;

		if (initialData != null && initialData.has("type") && (initialData.get("type").isJsonNull()
				|| initialData.get("type").getAsString().contains("%INITIAL_DATA_SUBMIT_TYPE%")))
			removeInitialData = true;

		if (removeInitialData) {
			objectConfigurationJsonObject.remove("initialData");
			item.add("objectConfiguration", objectConfigurationJsonObject);
		}
	}

	private static JsonObject deepMerge(JsonObject source, JsonObject target) {
		for (Map.Entry<String, JsonElement> sourceEntry : source.entrySet()) {
			String key = sourceEntry.getKey();
			JsonElement value = sourceEntry.getValue();
			if (!target.has(key)) {
				target.add(key, value);
			} else {
				if (!value.isJsonNull()) {
					if (value.isJsonObject()) {
						deepMerge(value.getAsJsonObject(), target.get(key).getAsJsonObject());
					} else {
						target.add(key, value);
					}
				} else {
					target.remove(key);
				}
			}
		}
		return target;
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
		updateCache(tableName, cacheAlias, uniqueCacheId);
		return SvComplexCache.getData(uniqueCacheId, svr);
	}

	static void updateCache(String tableName, String cacheAlias, String uniqueCacheId) throws SvException {
		DbSearchCriterion search = new DbSearchCriterion(CC.STATUS, DbCompareOperand.EQUAL, CC.VALID);
		SvRelationCache src = new SvRelationCache(SvCore.getDbtByName(tableName), search, cacheAlias, null, null, null,
				null);
		SvComplexCache.addRelationCache(uniqueCacheId, src, true);
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

	static DbDataObject findMenuByCode(String menuCode, SvReader svr) throws SvException {
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
		Long version = perunMenuDbo.getVal(CC.VERSION) == null ? 1 : perunMenuDbo.getAsLong(CC.VERSION) + 1;
		Long parentId = requestData.has(CC.PARENT_ID) ? requestData.get(CC.PARENT_ID).getAsLong() : 0;

		setPerunMenuObjectValues(perunMenuDbo, parentId, menuCode, labelCode, menuType, menuConf, parentTableName,
				svarogCdlCat, svarogAclLbl, internalCat, version);
	}

	public static DbDataObject createPerunMenuConfObj(Long parentId, String tableName, String fieldName,
			String refTableName, String regFieldName, String cdlName, String cdlItemName, String menuCode)
			throws SvException {
		DbDataObject perunMenuConfDbo = new DbDataObject();
		perunMenuConfDbo.setObjectType(SvReader.getTypeIdByName(CC.PERUN_MENU_CONF));
		perunMenuConfDbo.setParentId(parentId);
		perunMenuConfDbo.setVal(CC.TABLE_NAME, tableName);
		perunMenuConfDbo.setVal(CC.FIELD_NAME, fieldName);
		perunMenuConfDbo.setVal(CC.REF_TABLE_NAME, refTableName);
		perunMenuConfDbo.setVal(CC.REF_FIELD_NAME, regFieldName);
		perunMenuConfDbo.setVal(CC.CDL_NAME, cdlName);
		perunMenuConfDbo.setVal(CC.CDL_ITEM_NAME, cdlItemName);
		perunMenuConfDbo.setVal(CC.MENU_CODE, menuCode);

		return perunMenuConfDbo;
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
	 * @throws MenuError
	 * @throws UserNotAuthorizedError
	 * @throws Exception
	 */
	static DbDataObject saveMenuHelper(JsonObject requestData, SvReader svr)
			throws SvException, MenuError, UserNotAuthorizedError {
		DbDataObject menuDbo;
		Long objectId = -1l;

		String menuCodeStr = requestData.has(CC.MENU_CODE) ? requestData.get(CC.MENU_CODE).getAsString() : null;
		if (menuCodeStr == null || menuCodeStr.isBlank()) {
			throw new MenuSaveError("Missing required key MENU_CODE");
		}

		if (requestData.has(CC.OBJECT_ID)) {
			objectId = requestData.get(CC.OBJECT_ID).getAsLong();
		}
		if (objectId == 0) {
			menuDbo = new DbDataObject();
			menuDbo.setObjectType(SvReader.getTypeIdByName(CC.PERUN_MENU));
		} else if (objectId > 0) {
			menuDbo = svr.getObjectById(objectId, SvReader.getTypeIdByName(CC.PERUN_MENU), null);
			if (menuDbo == null) {
				throw new MenuNotFoundError(String.format("The menu with object_id: %d was not found", objectId));
			}
		} else {
			menuDbo = findMenuByCode(requestData.get(CC.MENU_CODE).getAsString(), svr);
			if (menuDbo == null) {
				menuDbo = new DbDataObject();
				menuDbo.setObjectType(SvReader.getTypeIdByName(CC.PERUN_MENU));
			}
		}

		if (menuDbo != null && menuDbo.getObjectId() > 0) {
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
				DbDataObject importedMenu = findObjectUsingSvCache(CC.MENU_CODE, importCode, CC.PERUN_MENU, CC.PM, svr);
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

	public static JsonObject prepareMenuJsonForDownload(DbDataObject menuDbo, Boolean resolveImports, SvReader svr)
			throws Exception {
		JsonObject menuConf;
		JsonObject menuJson = menuDbo.toSimpleJson();

		if (resolveImports) {
			menuConf = buildFullHierarchy(menuDbo, svr, new HashSet<Long>());
		} else {
			menuConf = new Gson().fromJson(menuDbo.getVal(CC.MENU_CONF).toString(), JsonObject.class);
		}
		menuJson.add(CC.MENU_CONF, menuConf);

		for (char[] repoField : DbDataObject.repoFieldNames) {
			String repoFieldStr = new String(repoField);
			menuJson.remove(repoFieldStr.toLowerCase());
		}

		return menuJson;
	}

	/**
	 * Find the appropriate perun_menu object by the object in the JSON request data
	 * 
	 * @param requestData - JSON object containing information about the object
	 * @param svr         - SvReader instance for database operations
	 * @return DbDataObject of type perun_menu that hold information about what menu
	 *         to generate
	 * @throws SvException
	 */
	public static DbDataObject findMenuCodeForObject(JsonObject requestData, SvReader svr) throws SvException {
		DbDataObject result = null;
		DbDataObject perunMenuConfDbo = null;
		DbDataArray perunMenuConfArr = new DbDataArray();
		String tableName = CC.EMPTY_STRING;
		Long objectType = 0l;
		String menuCode = CC.EMPTY_STRING;
		for (String key : requestData.keySet()) {
			if (key.contains(CC.OBJECT_TYPE)) {
				objectType = requestData.get(key).getAsLong();
			}
		}

		if (objectType != 0l) {
			DbDataObject tableDbo = svr.getObjectById(objectType, svCONST.OBJECT_TYPE_TABLE, null);
			tableName = tableDbo.getAsString(CC.TABLE_NAME);
		}

		if (!tableName.equals(CC.EMPTY_STRING)) {
			DbSearchCriterion dbc = new DbSearchCriterion(CC.TABLE_NAME, DbCompareOperand.EQUAL, tableName);
			perunMenuConfArr = svr.getObjects(dbc, SvReader.getTypeIdByName(CC.PERUN_MENU_CONF), null, 0, 0);
		}

		for (DbDataObject dbo : perunMenuConfArr.getItems()) {
			if ((dbo.getVal(CC.CDL_NAME) != null && checkObjectByCdlItemName(requestData, dbo))
					|| (dbo.getVal(CC.REF_TABLE_NAME) != null && checkObjectByRefField(requestData, dbo, svr))) {
				perunMenuConfDbo = dbo;
				break;
			}
		}

		if (perunMenuConfDbo != null) {
			menuCode = perunMenuConfDbo.getAsString(CC.MENU_CODE);
		}

		if (!menuCode.equals(CC.EMPTY_STRING)) {
			result = findMenuByCode(menuCode, svr);
		}

		return result;
	}

	private static String getValueFromRequestData(JsonObject requestData, DbDataObject dbo) {
		String fieldNamePerunMenuConf = dbo.getAsString(CC.FIELD_NAME);
		String value = CC.EMPTY_STRING;
		for (String key : requestData.keySet()) {
			if (key.contains(fieldNamePerunMenuConf)) {
				value = requestData.get(key).getAsString();
			}
		}
		return value;
	}

	private static boolean checkObjectByCdlItemName(JsonObject requestData, DbDataObject dbo) {
		String value = getValueFromRequestData(requestData, dbo);
		if (!value.equals(CC.EMPTY_STRING) && dbo.getVal(CC.CDL_ITEM_NAME) != null
				&& dbo.getVal(CC.CDL_ITEM_NAME).toString().equals(value)) {
			return true;
		}
		return false;
	}

	private static boolean checkObjectByRefField(JsonObject requestData, DbDataObject dbo, SvReader svr)
			throws SvException {
		String value = getValueFromRequestData(requestData, dbo);
		String refTableName = dbo.getAsString(CC.REF_TABLE_NAME);
		String refFieldName = dbo.getAsString(CC.REF_FIELD_NAME);

		DbDataObject dboRef = svr.getObjectById(Long.valueOf(value), SvReader.getTypeIdByName(refTableName), null);

		if (dboRef != null && dbo.getVal(CC.CDL_ITEM_NAME) != null && dbo.getVal(CC.CDL_ITEM_NAME) != null
				&& dboRef.getVal(refFieldName) != null
				&& dbo.getVal(CC.CDL_ITEM_NAME).equals(dboRef.getVal(refFieldName).toString())) {
			return true;
		}

		return false;
	}

	/**
	 * Replaces all placeholders within a source JSON object string representation
	 * with corresponding values from a data JSON object.
	 *
	 * Placeholders are defined as keys surrounded by percent signs (e.g., "%KEY%").
	 * The replacement occurs on the string representation of the source JSON, which
	 * is then parsed back into a new JsonObject.
	 *
	 * Note: If parsing the modified string back into a JsonObject fails, the method
	 * logs the error and returns the original requestData JsonObject as a fallback.
	 *
	 * @param json The source JsonObject containing strings with placeholders to be
	 *             replaced.
	 * @param data The JsonObject containing the key-value pairs used for
	 *             replacement.
	 * @return A new JsonObject with the placeholders replaced, or the original
	 *         JsonObject if an error occurred during the final JSON parsing step.
	 */
	static JsonObject applyDataToObject(JsonObject json, JsonObject data) {
		JsonObject result;
		String jsonStr = json.toString();
		String objectId = "0";
		for (String key : data.keySet()) {
			String fieldName = key.replaceFirst("\\w+\\.", CC.EMPTY_STRING);
			if (fieldName.equals(CC.OBJECT_ID)) {
				objectId = data.get(key).toString();
			}
		}
		for (String key : data.keySet()) {
			String fieldName = key.replaceFirst("\\w+\\.", CC.EMPTY_STRING);
			String toReplace = "%" + fieldName + "%";
			if (!data.get(key).isJsonNull() && jsonStr.contains(toReplace)) {
				String replacement = data.get(key).getAsString();
				if (fieldName.equals(CC.PARENT_ID) && data.get(key).toString().equals("0")) {
					replacement = objectId;
				}
				jsonStr = jsonStr.replaceAll(toReplace, replacement);
			}
		}
		try {
			result = new Gson().fromJson(jsonStr, JsonObject.class);
		} catch (Exception e) {
			log4j.error(e.getMessage(), e);
			result = data;
		}
		return result;
	}

	/**
	 * Finds all placeholders within the string representation of a JsonObject
	 *
	 * @param resultJson The JsonObject to be scanned for placeholders
	 * @return A List containing the content (the key) of all placeholders found
	 */
	static Set<String> findPlaceholders(JsonObject resultJson) {
		Set<String> placeholders = new HashSet<String>();
		Pattern pattern = Pattern.compile("\\%(\\w+)\\%");
		Matcher match = pattern.matcher(resultJson.toString());

		while (match.find()) {
			placeholders.add(match.group(1));
		}

		return placeholders;
	}
}
