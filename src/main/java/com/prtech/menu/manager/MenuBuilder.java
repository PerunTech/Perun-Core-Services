package com.prtech.menu.manager;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.prtech.perun.services.ws.DbReader;
import com.prtech.svarog.SvComplexCache;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvRelationCache;
import com.prtech.svarog.SvSecurity;
import com.prtech.svarog.SvUtil;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbSearchCriterion;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;

/**
 * MenuBuilder class for generating merged menu definitions. Uses PERUN_MENU as
 * source table, and supports recursive merging based on IMPORT_MENU as KEY
 * field in MENU_CONF JSON.
 */
@Path("/Menu")
public class MenuBuilder {

	private static final Logger log4j = LogManager.getLogger(MenuBuilder.class);

	/**
	 * Web service endpoint to generate full merged menu config
	 * 
	 * @param sessionId    Session ID
	 * @param rootMenuCode Root menu code
	 * @return JSON with merged buttonArray content
	 */
	@GET
	@Path("/generate/{sid}/{rootMenuCode}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response generateMenu(@PathParam("sid") String sessionId, @PathParam("rootMenuCode") String rootMenuCode) {
		try (SvReader svr = new SvReader(sessionId)) {
			DbDataObject menuRoot = new DbReader().searchDbObjectBySingleFilter(DbCompareOperand.EQUAL,
					SvReader.getTypeIdByName(CC.PERUN_MENU), CC.MENU_CODE, rootMenuCode, svr);
			if (menuRoot == null)
				return Response.status(Response.Status.NOT_FOUND).entity("Menu not found").build();
			JsonObject resultJson = buildFullHierarchy(menuRoot, svr, new HashSet<>());
			return Response.ok(resultJson.toString(), MediaType.APPLICATION_JSON).build();
		} catch (Exception e) {
			log4j.error("Error generating menu: ", e);
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		}
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
	public static JsonObject buildFullHierarchy(DbDataObject menuDbo, SvReader svr, Set<Long> visited)
			throws Exception {
		JsonArray mergedButtons = new JsonArray();
		// buildRecursive(menuDbo, svr, visited, mergedButtons);
		buildRecursiveWithSvCache(menuDbo, svr, visited, mergedButtons);
		JsonObject result = new JsonObject();
		result.add("buttonArray", mergedButtons);
		return result;
	}

	private static void buildRecursive(DbDataObject menuDbo, SvReader svr, Set<Long> visited, JsonArray mergedButtons)
			throws Exception {
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

	private static void buildRecursiveWithSvCache(DbDataObject menuDbo, SvReader svr, Set<Long> visited,
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
				/*
				 * DbDataObject importedMenu = new
				 * DbReader().searchDbObjectBySingleFilter(DbCompareOperand.EQUAL,
				 * SvReader.getTypeIdByName(CC.PERUN_MENU), CC.MENU_CODE, importCode, svr);
				 */
				DbDataObject importedMenu = findObjectUsingSvCache(CC.MENU_CODE, importCode, CC.PERUN_MENU, "PM", svr);
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
	public static DbDataArray findObjectsUsingSvCache(String columnName, String columnValue, String tableName,
			String cacheAlias, SvReader svr) throws SvException {
		String uniqueCacheId = columnValue;
		DbDataArray result = SvComplexCache.getData(uniqueCacheId, svr);
		if (result != null) {
			return result;
		}
		DbSearchCriterion search = new DbSearchCriterion(columnName, DbCompareOperand.EQUAL, columnValue);
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
	public static DbDataObject findObjectUsingSvCache(String columnName, String columnValue, String tableName,
			String cacheAlias, SvReader svr) throws SvException {
		DbDataArray dbArr = findObjectsUsingSvCache(columnName, columnValue, tableName, cacheAlias, svr);
		if (!dbArr.isEmpty())
			return dbArr.get(0);
		else
			return null;
	}

	/**
	 * Console test for merging a full menu tree from a given menu code.
	 */
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

		String targetMenuCode = "pharmacies_registry_menu_tt";

		try (SvReader svr = new SvReader(sid)) {
			DbDataObject targetMenu = findMenuByCode(targetMenuCode, svr);
			if (targetMenu == null) {
				log4j.info("Menu not found: " + targetMenuCode);
				return;
			}

			JsonObject fullHierarchyJson = buildFullHierarchy(targetMenu, svr, new HashSet<>());
			log4j.info("!!! Full merged menu in JSON: !!!");
			System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(fullHierarchyJson));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static DbDataObject findMenuByCode(String menuCode, SvReader svr) throws Exception {
		DbSearchCriterion filter = new DbSearchCriterion(CC.MENU_CODE, DbCompareOperand.EQUAL, menuCode);
		DbDataArray result = svr.getObjects(filter, SvReader.getTypeIdByName(CC.PERUN_MENU), null, 0, 0);
		return result != null && !result.getItems().isEmpty() ? result.get(0) : null;
	}
}