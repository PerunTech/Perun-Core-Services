package com.prtech.perun.services.ws;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.prtech.svarog.I18n;
import com.prtech.svarog.SvConf;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvReader;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbSearchCriterion;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;

public class MenuBuild {
	JsonArray menuMain = new JsonArray();
	JsonObject menuItem = new JsonObject();
	JsonArray subMenu = new JsonArray();
	JsonObject subItem = new JsonObject();
	String token="";
	String localeS = "";
	SvReader svr = null;
	String menuName = "";
	static final Logger log4j = SvConf.getLogger(MenuBuild.class);

	public MenuBuild(String componentName, SvReader svrIn) throws SvException {
		svr = svrIn;
		token = svr.getSessionId();
		menuName = componentName;
		getLocaleId(svr);
	}

	private static void debugException(Exception e) {
		if (log4j.isDebugEnabled())
			log4j.debug(e.getMessage(), e);
	}

	private void getLocaleId(SvReader svr) {
		String locale = SvConf.getDefaultLocale();
		try {
			DbDataObject dboLocale = svr.getUserLocale(svr.getInstanceUser());
			if (dboLocale != null && dboLocale.getVal("LOCALE_ID").toString() != null) {
				locale = dboLocale.getVal("LOCALE_ID").toString();
			}
		} catch (SvException e) {
			debugException(e);
		}
		localeS = locale;
	}

	/** build and return the menu array
	 * 
	 * @return
	 * @throws SvException
	 */
	public JsonArray getMenu() throws SvException {
		DbSearchCriterion dbsc = new DbSearchCriterion("CONTEXT_NAME", DbCompareOperand.EQUAL, menuName);
		DbDataArray objectss = svr.getObjects(dbsc, SvCore.getTypeIdByName("SVAROG_PERUN_PLUGIN"), null, 0, 0);
		DbDataObject menuObj = (objectss != null && !objectss.getItems().isEmpty()) ? objectss.getItems().get(0) : null;
		if (menuObj != null) {
			Gson gson = new Gson();
			String menuStr = menuObj.getVal("MENU_CONF").toString();
			JsonObject masterJson = gson.fromJson(menuStr, JsonObject.class);	
			JsonArray menuRead = masterJson.has("buttonArray") ? masterJson.get("buttonArray").getAsJsonArray() : new JsonArray();
			for (int i = 0; i < menuRead.size(); i++)
				try {
					JsonObject tmp = menuRead.get(i).getAsJsonObject();
					menuItem = new JsonObject();
					if (tmp.has("menu_name")) {
						processItem(tmp, 0);
					} else if (tmp.has("data")) {
						subMenu = new JsonArray();
						if (tmp.has("ID"))
							menuItem.addProperty("ID", tmp.get("ID").getAsString());
						if (tmp.has("label"))
							menuItem.addProperty("label", tmp.get("label").getAsString());
						JsonArray arr = (JsonArray) tmp.get("data");
						for (int j = 0; j < arr.size(); j++) {
							JsonObject tmpSub = arr.get(j).getAsJsonObject();
							if (tmpSub.has("menu_name")) {
								processItem(tmpSub, 1);
								subMenu.add(subItem);
							}
						}
						menuItem.add("data", subMenu);
					} else {
						menuItem = tmp;
					}
					menuMain.add(menuItem);
				} catch (Exception e) {
					// so one item does not block the full menu
					log4j.error(e.getMessage());
				}
			String yearS = String.valueOf(new DateTime().year().get());
			String asStr = menuMain.toString();
			asStr = asStr.replaceAll("%TOKEN%", token);
			asStr = asStr.replaceAll("\"true\"", "true"); 
			asStr = asStr.replaceAll("\"false\"", "false"); 
			asStr = asStr.replaceAll("%CURRENT_YEAR%", yearS);
			while (asStr.contains("%REPO_ID_")) {
				int start = asStr.indexOf("%REPO_ID_");
				int end = asStr.indexOf("%", start + 3);
				String tableName = asStr.substring(start + 9, end--).toUpperCase();
				asStr = asStr.replace("%REPO_ID_" + tableName + "%", SvCore.getTypeIdByName(tableName).toString());
			}
			// replace label codes
			int start=1;
			while (asStr.indexOf("label",start) >0    ) {
				int start1 = asStr.indexOf("label",start);
				int start2 = asStr.indexOf("\"",start1+6);
				int start3 = asStr.indexOf("\"",start2+2);
				String labelCode = asStr.substring(start2+1, start3--);
				String translated = I18n.getText(localeS, labelCode);
				asStr= asStr.replace(labelCode, translated);
				start = start2+1;
			}
			menuMain = gson.fromJson(asStr, JsonArray.class);
		}
		return menuMain;
	}

	/**
	 * method to process one JsonObject item
	 * 
	 * @param tmpSub JsonObject for the item as read from the menu array
	 * @param level  int level of the object i=0 for item to be put in main Array,
	 *               when i=1 we put the item in the subArray
	 * @throws SvException
	 */
	private void processItem(JsonObject tmpSub, int level) throws SvException {

		DbSearchCriterion dbsc1 = new DbSearchCriterion("CONTEXT_NAME", DbCompareOperand.EQUAL,
				tmpSub.get("menu_name").getAsString());
		DbDataArray objectss1 = svr.getObjects(dbsc1, SvCore.getTypeIdByName("SVAROG_PERUN_PLUGIN"), null, 0, 0);
		DbDataObject menuObj1 = (objectss1 != null && !objectss1.getItems().isEmpty()) ? objectss1.getItems().get(0)
				: null;
		if (menuObj1 != null && menuObj1.getVal("MENU_CONF") != null) {
			Gson gson = new Gson();
			String asStr = menuObj1.getVal("MENU_CONF").toString();
			Iterator<Entry<String, JsonElement>> it = tmpSub.entrySet().iterator();
			JsonObject toInsert = null;
			String parentObjectIdStr = "0";
			String recordIdStr ="0";
			DbDataObject dBTable = null;
			while (it.hasNext()) {
				Entry<String, JsonElement> pair = it.next();
				String toChange = "%" + pair.getKey() + "%";
				if ("%insert%".equals(toChange)) { // we want to insert some Json Object
					toInsert = pair.getValue().getAsJsonObject();
				} else {
					String changed = pair.getValue().getAsString();
					asStr = asStr.replaceAll(toChange, changed);
					if (pair.getKey().equals("TABLE_NAME")) {
						dBTable = SvCore.getDbtByName(changed);
					}
				}
			}

			asStr = asStr.replaceAll("%PARENT_OBJECT_ID%", parentObjectIdStr);
			if (asStr.contains("%CHILD_ID_OF%")) {
				DbDataArray recordArray = svr.getObjectsByParentId(Long.parseLong(parentObjectIdStr),
						dBTable.getObjectId(), null);
				if (null != recordArray && !recordArray.isEmpty()) {
					recordIdStr = recordArray.get(0).getObjectId().toString();
				}
				asStr = asStr.replaceAll("%CHILD_ID_OF%", recordIdStr);
			}

			JsonObject abab = gson.fromJson(asStr, JsonObject.class);
			if (toInsert != null) {
				abab = deepMerge(toInsert, abab);
			}
			String pass = abab.toString();
			if (dBTable != null) {
				pass = pass.replaceAll("%TABLE_NAME%", dBTable.getVal("TABLE_NAME").toString());
				pass = pass.replaceAll("%TABLE_NAME_LABEL_CODE%", dBTable.getVal("LABEL_CODE").toString());
				pass = pass.replaceAll("%TABLE_NAME_OBJECT_ID%", dBTable.getObjectId().toString());
			}
			abab = gson.fromJson(pass, JsonObject.class);
			if (level == 0)
				menuItem = abab;
			else if (level == 1)
				subItem = abab;
		}
	}

	/**
	 * Merge "source" into "target". If fields have equal name, merge them
	 * recursively. Null values in source will remove the field from the target.
	 * Override target values with source values Keys not supplied in source will
	 * remain unchanged in target
	 *
	 * @return the merged object (target).
	 */
	private static JsonObject deepMerge(JsonObject source, JsonObject target) {

		for (Map.Entry<String, JsonElement> sourceEntry : source.entrySet()) {
			String key = sourceEntry.getKey();
			JsonElement value = sourceEntry.getValue();
			if (!target.has(key)) {
				// target does not have the same key, so perhaps it should be added to target
				// well, only add if the source value is not null
				// add even if null if (!value.isJsonNull())
				target.add(key, value);
			} else {
				if (!value.isJsonNull()) {
					if (value.isJsonObject()) {
						// source value is json object, start deep merge
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

}
