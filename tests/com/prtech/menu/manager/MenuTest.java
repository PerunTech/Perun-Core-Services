package com.prtech.menu.manager;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import com.prtech.menu.manager_bck.MC;
import com.prtech.menu.manager_bck.SvarogMenuModel;
import com.prtech.perun.services.ws.DbReader;
import com.prtech.perun.services.ws.WsReactElements;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvSecurity;
import com.prtech.svarog.SvUtil;
import com.prtech.svarog.SvWriter;
import com.prtech.svarog.svCONST;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbSearchCriterion;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MenuTest {

	static final Logger log4j = LogManager.getLogger(WsReactElements.class.getName());

	@Test
	public void testSvarogMenuModelStringStringStringStringLong() {
		fail("Not yet implemented");
	}

	@Test
	public void testSvarogMenuModelDbDataObject() throws JsonProcessingException {
		String sessionId = null;
		DbReader dbr = new DbReader();
		ObjectMapper mapper = new ObjectMapper();
		try (@SuppressWarnings("deprecation")
		SvSecurity svc = new SvSecurity()) {
			sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			System.out.println(sessionId);
			log4j.info(sessionId);
		} catch (Exception e) {
			log4j.error(e);
		}
		try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
			DbDataObject db_svmm = dbr.searchDbObjectBySingleFilter(svCONST.OBJECT_TYPE_MENU, MC.MENU_CODE,
					"animal-registry-menu", svr);
			SvarogMenuModel svmm = new SvarogMenuModel(db_svmm);
			String menuJson = (String) db_svmm.getVal(MC.MENU_CONF);
			JsonNode cleanMenuJson = mapper.readTree(menuJson);
			System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(cleanMenuJson));
		} catch (SvException e) {
			log4j.error(e);
		}
		// fail("Not yet implemented");
	}

	@Test
	public void testGetAllExistingMenuKeys() throws Exception {
		String sessionId = null;
		DbReader dbr = new DbReader();
		List<String> allMenus = new ArrayList<>();
		ObjectMapper mapper = new ObjectMapper();
		try (@SuppressWarnings("deprecation")
		SvSecurity svc = new SvSecurity()) {
			sessionId = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			System.out.println(sessionId);
			log4j.info(sessionId);
		} catch (Exception e) {
			log4j.error(e);
		}
		try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
			DbSearchCriterion cr1 = new DbSearchCriterion(MC.MENU_TYPE, DbCompareOperand.EQUAL, "menu");
			DbDataArray db_svmm = svr.getObjects(cr1, svCONST.OBJECT_TYPE_MENU, null, 0, 0);
			for (DbDataObject tempSvarogMenuObject : db_svmm.getItems()) {
				if (tempSvarogMenuObject.getVal(MC.MENU_CONF) != null) {
					String menuJson = (String) tempSvarogMenuObject.getVal(MC.MENU_CONF);
					allMenus.add(menuJson);
				}
			}
			Set<String> allKeys = new TreeSet<>();
			for (String json : allMenus) {
				allKeys.addAll(SvarogMenuModel.extractKeysFromJson(json));
			}

			// System.out.println("Unique keys found:");
			allKeys.forEach(System.out::println);

			Map<String, Set<String>> keyValueMap = new HashMap<>();
			for (String json : allMenus) {
				JsonNode node = mapper.readTree(json);
				collectKeyValues(node, keyValueMap);
			}

			Map<String, Set<String>> sortedMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			sortedMap.putAll(keyValueMap);

			// Optional: sort by actual key value (not just comparator), and preserve case
			// This will sort lexicographically but case-insensitively while keeping
			// original case
			sortedMap = new TreeMap<>((a, b) -> a.compareToIgnoreCase(b));
			sortedMap.putAll(keyValueMap);

			// Print the sorted map
			sortedMap.forEach((key, values) -> {
				System.out.println(key + ":::");
				for (String value : values) {
					System.out.println("  --- " + value);
				}
			});

		} catch (SvException e) {
			log4j.error(e);
		}
	}

	private static void collectKeyValues(JsonNode node, Map<String, Set<String>> result) {
		if (node.isObject()) {
			node.fields().forEachRemaining(entry -> {
				String key = entry.getKey();
				if (key == null || key.trim().isEmpty())
					return; // Skip blanks

				JsonNode value = entry.getValue();
				String valStr = value.isContainerNode() ? value.toString() : value.asText();

				System.out.println("DEBUG adding key: " + key);
				result.computeIfAbsent(key, k -> new TreeSet<>()).add(valStr);

				if (value.isObject() || value.isArray()) {
					collectKeyValues(value, result);
				}
			});
		} else if (node.isArray()) {
			for (JsonNode item : node) {
				collectKeyValues(item, result);
			}
		}
	}

}
