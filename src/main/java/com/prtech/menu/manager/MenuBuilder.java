package com.prtech.menu.manager;

import java.util.*;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.*;
import com.prtech.perun.services.ws.DbReader;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvSecurity;
import com.prtech.svarog.SvUtil;
import com.prtech.svarog_common.*;
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
		buildRecursive(menuDbo, svr, visited, mergedButtons);
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