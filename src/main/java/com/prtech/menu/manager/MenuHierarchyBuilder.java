package com.prtech.menu.manager;

import com.google.gson.*;
import com.prtech.svarog.SvReader;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;

import java.util.HashSet;
import java.util.Set;

public class MenuHierarchyBuilder {

	public static JsonObject buildFullHierarchy(DbDataObject rootMenu, SvReader svr, BuildDirectionMode mode)
			throws Exception {
		JsonArray buttonArray = new JsonArray();
		Set<Long> visited = new HashSet<Long>();
		if (mode == BuildDirectionMode.UP || mode == BuildDirectionMode.BOTH) {
			buildUpHierarchy(rootMenu, svr, buttonArray, visited);
		}
		if (mode == BuildDirectionMode.DOWN || mode == BuildDirectionMode.BOTH) {
			buildDownHierarchy(rootMenu, svr, buttonArray, visited);
		}
		JsonObject result = new JsonObject();
		result.add("buttonArray", buttonArray);
		return result;
	}

	private static void buildUpHierarchy(DbDataObject menu, SvReader svr, JsonArray array, Set<Long> visited)
			throws Exception {
		if (menu == null || visited.contains(menu.getObjectId()))
			return;
		visited.add(menu.getObjectId());
		if (menu.getVal(MC.MENU_CONF) != null) {
			JsonObject obj = JsonParser.parseString((String) menu.getVal(MC.MENU_CONF)).getAsJsonObject();
			JsonArray btns = obj.getAsJsonArray("buttonArray");
			for (JsonElement el : btns)
				array.add(el.deepCopy());
		}
		if (menu.getParentId() != 0) {
			DbDataObject parent = svr.getObjectById(menu.getParentId(), menu.getObjectType(), null);
			buildUpHierarchy(parent, svr, array, visited);
		}
	}

	private static void buildDownHierarchy(DbDataObject menu, SvReader svr, JsonArray array, Set<Long> visited)
			throws Exception {
		if (menu == null || visited.contains(menu.getObjectId()))
			return;
		visited.add(menu.getObjectId());
		if (menu.getVal(MC.MENU_CONF) != null) {
			JsonObject obj = JsonParser.parseString((String) menu.getVal(MC.MENU_CONF)).getAsJsonObject();
			JsonArray btns = obj.getAsJsonArray("buttonArray");
			for (JsonElement el : btns)
				array.add(el.deepCopy());
		}
		DbDataArray children = svr.getObjectsByParentId(menu.getObjectId(), menu.getObjectType(), null);
		for (DbDataObject child : children.getItems()) {
			buildDownHierarchy(child, svr, array, visited);
		}
	}
}