package com.prtech.menu.manager_bck;

import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.*;
import com.prtech.svarog.*;
import com.prtech.svarog_common.*;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;

public class MenuBuilder {

	private static final Logger log4j = LogManager.getLogger(MenuBuilder.class);

	public static void main(String[] args) {
		String sid = null;
		try (SvSecurity svc = new SvSecurity()) {
			sid = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			log4j.info("Logged in with session ID: " + sid);
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}

		if (sid == null)
			return;

		String targetMenuCode = "pharmacies_registry_menu_t"; // Change if needed

		try (SvReader svr = new SvReader(sid)) {
			DbDataObject targetMenu = findMenuByCode(targetMenuCode, svr);
			if (targetMenu == null) {
				log4j.info("Menu not found: " + targetMenuCode);
				return;
			}

			JsonObject fullHierarchyJson = buildFullHierarchy(targetMenu, svr);
			log4j.info("=== Full Merged Menu JSON ===");
			System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(fullHierarchyJson));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static DbDataObject findMenuByCode(String menuCode, SvReader svr) throws Exception {
		DbSearchCriterion filter = new DbSearchCriterion(MC.MENU_CODE, DbCompareOperand.EQUAL, menuCode);
		DbDataArray result = svr.getObjects(filter, svCONST.OBJECT_TYPE_MENU, null, 0, 0);
		return result != null && !result.getItems().isEmpty() ? result.get(0) : null;
	}

	public static JsonObject buildFullHierarchy(DbDataObject menuDbo, SvReader svr) throws Exception {
		Set<Long> visited = new HashSet<>();
		List<DbDataObject> hierarchyMenus = new ArrayList<>();

		// Collect upward
		collectParents(menuDbo, svr, visited, hierarchyMenus);

		// Add self if not already added
		if (!visited.contains(menuDbo.getObjectId())) {
			hierarchyMenus.add(menuDbo);
			visited.add(menuDbo.getObjectId());
		}

		// Collect downward
		collectChildren(menuDbo, svr, visited, hierarchyMenus);

		// Merge all buttonArrays
		JsonArray mergedButtons = new JsonArray();
		for (DbDataObject dbo : hierarchyMenus) {
			String confStr = (String) dbo.getVal("MENU_CONF");
			if (confStr != null) {
				JsonObject confJson = JsonParser.parseString(confStr).getAsJsonObject();
				if (confJson.has("buttonArray")) {
					JsonArray btnArr = confJson.getAsJsonArray("buttonArray");
					for (JsonElement btn : btnArr) {
						mergedButtons.add(btn);
					}
				}
			}
		}

		JsonObject finalJson = new JsonObject();
		finalJson.add("buttonArray", mergedButtons);
		return finalJson;
	}

	private static void collectParents(DbDataObject menuDbo, SvReader svr, Set<Long> visited,
			List<DbDataObject> accumulator) throws Exception {
		if (menuDbo.getParentId() == null || menuDbo.getParentId() == 0 || visited.contains(menuDbo.getParentId()))
			return;

		DbDataObject parent = svr.getObjectById(menuDbo.getParentId(), svCONST.OBJECT_TYPE_MENU, null);
		if (parent != null && !visited.contains(parent.getObjectId())) {
			collectParents(parent, svr, visited, accumulator); // Recursive
			accumulator.add(parent); // Add after recursion to keep top-down order
			visited.add(parent.getObjectId());
		}
	}

	private static void collectChildren(DbDataObject menuDbo, SvReader svr, Set<Long> visited,
			List<DbDataObject> accumulator) throws Exception {
		if (visited.contains(menuDbo.getObjectId()))
			return;

		DbDataArray children = svr.getObjectsByParentId(menuDbo.getObjectId(), svCONST.OBJECT_TYPE_MENU, null);
		for (DbDataObject child : children.getItems()) {
			if (!visited.contains(child.getObjectId())) {
				accumulator.add(child);
				visited.add(child.getObjectId());
				collectChildren(child, svr, visited, accumulator); // Recursive
			}
		}
	}
}
