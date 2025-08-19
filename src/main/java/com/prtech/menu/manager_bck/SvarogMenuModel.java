package com.prtech.menu.manager_bck;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.annotation.*;
import javax.validation.constraints.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prtech.svarog_common.DbDataObject;

public class SvarogMenuModel {

	@Size(max = 50)
	@NotNull
	protected String menuCode;

	@Size(max = 50)
	@NotNull
	protected String menuLabelCode;

	@Size(max = 50)
	@Nullable
	@Pattern(regexp = "menu|menu-component", message = "menuType must be one of the values pre-defined in svarog MENU_TYPES codelist")
	protected String menuType;

	@Nullable
	protected String menuConf;

	@Nullable
	protected Long menuVersion;

	public SvarogMenuModel(String menuCode, String menuLabelCode, String menuType, String menuConf, Long menuVersion) {
		super();
		this.menuCode = menuCode;
		this.menuLabelCode = menuLabelCode;
		this.menuType = menuType;
		this.menuConf = menuConf;
		this.menuVersion = menuVersion;
	}

	public SvarogMenuModel(DbDataObject svMenu) {
		super();
		this.menuCode = (String) svMenu.getVal(MC.MENU_CODE);
		this.menuLabelCode = (String) svMenu.getVal(MC.LABEL_CODE);
		this.menuType = (String) svMenu.getVal(MC.MENU_TYPE);
		this.menuConf = (String) svMenu.getVal(MC.MENU_CONF);
		this.menuVersion = (Long) svMenu.getVal(MC.VERSION);
	}

	public static Set<String> extractKeysFromJson(String json) throws Exception {
		//System.out.println(json);
		ObjectMapper mapper = new ObjectMapper();
		JsonNode root = mapper.readTree(json);

		Set<String> keys = new HashSet<>();
		collectKeysRecursive(root, keys);
		return keys;
	}

	private static void collectKeysRecursive(JsonNode node, Set<String> keys) {
		if (node.isObject()) {
			Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
			while (fields.hasNext()) {
				Map.Entry<String, JsonNode> entry = fields.next();
				keys.add(entry.getKey());
				collectKeysRecursive(entry.getValue(), keys);
			}
		} else if (node.isArray()) {
			for (JsonNode item : node) {
				collectKeysRecursive(item, keys);
			}
		}
	}

}
