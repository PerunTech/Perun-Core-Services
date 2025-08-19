package com.prtech.menu.manager_bck;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.prtech.perun.services.ws.DbReader;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvSecurity;
import com.prtech.svarog.SvUtil;
import com.prtech.svarog.svCONST;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;

public class MenuAnalyzer {

	static Logger log4j = LogManager.getLogger(MenuAnalyzer.class);

	public static void analyzeButtonKeys(String sessionId) {
		Set<String> allKeys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		Map<String, Set<String>> menuKeyMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		Map<String, Set<String>> menuSignatureMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

		DbReader dbr = new DbReader();
		try (SvReader svr = new SvReader(sessionId)) {
			DbDataArray allMenus = dbr.searchObjectsBySingleFilter(DbCompareOperand.EQUAL, svCONST.OBJECT_TYPE_MENU,
					MC.MENU_TYPE, "menu", svr);

			for (DbDataObject dbo : allMenus.getItems()) {
				String menuCode = String.valueOf(dbo.getVal(MC.MENU_CODE));
				String jsonConf = String.valueOf(dbo.getVal(MC.MENU_CONF));
				Set<String> currentKeys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
				Set<String> currentSignatures = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

				try {
					JsonObject obj = JsonParser.parseString(jsonConf).getAsJsonObject();
					JsonArray buttonArray = obj.getAsJsonArray("buttonArray");
					extractButtonSignatures(buttonArray, currentSignatures);
					extractAllKeys(buttonArray, currentKeys);
				} catch (Exception e) {
					log4j.warn("Error parsing JSON for menuCode: " + menuCode, e);
				}

				allKeys.addAll(currentKeys);
				menuKeyMap.put(menuCode, currentKeys);
				menuSignatureMap.put(menuCode, currentSignatures);
			}

			exportToJson(allKeys, menuKeyMap);
			exportToCsv(menuKeyMap);
			exportSignaturesToCsv(menuSignatureMap);

		} catch (SvException e) {
			log4j.error("SVReader error", e);
		}
	}

	private static void extractAllKeys(JsonElement element, Set<String> keys) {
		if (element.isJsonObject()) {
			JsonObject obj = element.getAsJsonObject();
			keys.addAll(obj.keySet());
			if (obj.has("data") && obj.get("data").isJsonArray()) {
				extractAllKeys(obj.get("data"), keys);
			}
		} else if (element.isJsonArray()) {
			for (JsonElement item : element.getAsJsonArray()) {
				extractAllKeys(item, keys);
			}
		}
	}

	private static String normalizeJson(JsonObject obj) {
		TreeMap<String, JsonElement> sortedMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
			sortedMap.put(entry.getKey(), entry.getValue());
		}
		JsonObject normalized = new JsonObject();
		for (Map.Entry<String, JsonElement> e : sortedMap.entrySet()) {
			normalized.add(e.getKey(), e.getValue());
		}
		return normalized.toString();
	}

	private static void extractButtonSignatures(JsonElement element, Set<String> signatures) {
		if (element.isJsonObject()) {
			JsonObject obj = element.getAsJsonObject();
			if (obj.has("menu_name")) {
				String menuName = obj.get("menu_name").getAsString();
				String tableName = obj.has("TABLE_NAME") ? obj.get("TABLE_NAME").getAsString() : "";
				String signature = menuName + "|" + tableName;
				signatures.add(signature);
			}
			if (obj.has("data") && obj.get("data").isJsonArray()) {
				extractButtonSignatures(obj.get("data"), signatures);
			}
		} else if (element.isJsonArray()) {
			for (JsonElement item : element.getAsJsonArray()) {
				extractButtonSignatures(item, signatures);
			}
		}
	}

	private static void exportToJson(Set<String> allKeys, Map<String, Set<String>> menuKeyMap) {
		JsonObject report = new JsonObject();
		JsonArray keysArray = new JsonArray();
		allKeys.forEach(keysArray::add);
		report.add("all_keys", keysArray);

		JsonObject perMenu = new JsonObject();
		for (Map.Entry<String, Set<String>> entry : menuKeyMap.entrySet()) {
			JsonArray keys = new JsonArray();
			entry.getValue().forEach(keys::add);
			perMenu.add(entry.getKey(), keys);
		}
		report.add("menu_key_map", perMenu);

		try (FileWriter writer = new FileWriter("menu_analysis_report.json")) {
			writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(report));
			log4j.info("JSON report exported to menu_analysis_report.json");
		} catch (IOException e) {
			log4j.error("Error writing JSON report", e);
		}
	}

	private static void exportToCsv(Map<String, Set<String>> menuKeyMap) {
		try (FileWriter writer = new FileWriter("menu_analysis_report.csv")) {
			writer.write("menu_code,keys\n");
			for (Map.Entry<String, Set<String>> entry : menuKeyMap.entrySet()) {
				String line = entry.getKey() + "," + String.join("|", entry.getValue()) + "\n";
				writer.write(line);
			}
			log4j.info("CSV report exported to menu_analysis_report.csv");
		} catch (IOException e) {
			log4j.error("Error writing CSV report", e);
		}
	}

	private static void exportSignaturesToCsv(Map<String, Set<String>> signatureMap) {
		try (FileWriter writer = new FileWriter("menu_button_signature_matches.csv")) {
			writer.write("menu_code,menu_signature\n");
			for (Map.Entry<String, Set<String>> entry : signatureMap.entrySet()) {
				for (String signature : entry.getValue()) {
					writer.write(entry.getKey() + "," + signature + "\n");
				}
			}
			log4j.info("Signature report exported to menu_button_signature_matches.csv");
		} catch (IOException e) {
			log4j.error("Error writing signature CSV", e);
		}
	}

	private static void exportSharedButtons(Map<String, Set<String>> blockMap, Map<String, JsonElement> exampleMap) {
		JsonArray reused = new JsonArray();

		for (Map.Entry<String, Set<String>> entry : blockMap.entrySet()) {
			if (entry.getValue().size() > 1) {
				JsonObject obj = new JsonObject();
				obj.add("used_in_menus", new Gson().toJsonTree(entry.getValue()));
				obj.add("button_example", exampleMap.get(entry.getKey()));
				reused.add(obj);
			}
		}

		try (FileWriter writer = new FileWriter("shared_button_blocks.json")) {
			writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(reused));
			log4j.info("Shared blocks report saved to shared_button_blocks.json");
		} catch (IOException e) {
			log4j.error("Error writing shared block report", e);
		}
	}

	// MAIN method to test
	public static void main(String[] args) {
		String sid = null;
		try (@SuppressWarnings("deprecation")
		SvSecurity svc = new SvSecurity()) {
			sid = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			log4j.info(sid);
		} catch (Exception e) {
			log4j.error(e);
		}
		analyzeButtonKeys(sid);
	}
}
