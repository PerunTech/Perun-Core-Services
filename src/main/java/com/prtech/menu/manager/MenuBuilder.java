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
					SvReader.getTypeIdByName(MC.PERUN_MENU), MC.MENU_CODE, rootMenuCode, svr);
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

		String menuConfStr = (String) menuDbo.getVal(MC.MENU_CONF);
		if (menuConfStr != null) {
			JsonObject confJson = JsonParser.parseString(menuConfStr).getAsJsonObject();

			// Import referenced menu if exists
			if (confJson.has(MC.IMPORT_MENU)) {
				String importCode = confJson.get(MC.IMPORT_MENU).getAsString();
				DbDataObject imported = new DbReader().searchDbObjectBySingleFilter(DbCompareOperand.EQUAL,
						SvReader.getTypeIdByName(MC.PERUN_MENU), MC.MENU_CODE, importCode, svr);
				if (imported != null)
					buildRecursive(imported, svr, visited, mergedButtons);
			}

			// Append own buttonArray
			if (confJson.has("buttonArray")) {
				JsonArray btns = confJson.getAsJsonArray("buttonArray");
				for (JsonElement btn : btns)
					mergedButtons.add(btn.deepCopy());
			}
		}
	}
}