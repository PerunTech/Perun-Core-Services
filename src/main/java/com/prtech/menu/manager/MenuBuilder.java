package com.prtech.menu.manager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvSecurity;
import com.prtech.svarog.SvUtil;
import com.prtech.svarog.svCONST;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbSearchCriterion;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;

@Path("/Menu")
public class MenuBuilder {
	private static final Logger log4j = LogManager.getLogger(MenuBuilder.class);

	/**
	 * Web service for building full menu hierarchies (parent and child menus) into
	 * a single merged JSON array under "buttonArray".
	 */
	@GET
	@Path("/generate/{sid}/{menuCode}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response generateMenu(@PathParam("sid") String sessionId, @PathParam("menuCode") String menuCode) {
		try (SvReader svr = new SvReader(sessionId)) {
			DbDataObject menuRoot = new DbReader().searchDbObjectBySingleFilter(DbCompareOperand.EQUAL,
					svCONST.OBJECT_TYPE_MENU, "menu_code", menuCode, svr);
			if (menuRoot == null) {
				return Response.status(Response.Status.NOT_FOUND).entity("Menu not found").build();
			}
			JsonObject resultJson = buildFullHierarchy(menuRoot, svr);
			return Response.ok(resultJson).build();
		} catch (Exception e) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
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

		String targetMenuCode = "pharmacies_registry_menu_t"; // Change if needed

		try (SvReader svr = new SvReader(sid)) {
			DbDataObject targetMenu = findMenuByCode(targetMenuCode, svr);
			if (targetMenu == null) {
				log4j.info("Menu not found: " + targetMenuCode);
				return;
			}

			JsonObject fullHierarchyJson = buildFullHierarchy(targetMenu, svr);
			log4j.info("!!! Full merged menu in JSON: !!!");
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

		// Collect upwards
		collectParents(menuDbo, svr, visited, hierarchyMenus);

		// Add self if not already added
		if (!visited.contains(menuDbo.getObjectId())) {
			hierarchyMenus.add(menuDbo);
			visited.add(menuDbo.getObjectId());
		}

		// Collect downwards
		collectChildren(menuDbo, svr, visited, hierarchyMenus);

		// Merge all buttonArrays
		JsonArray mergedButtons = new JsonArray();
		for (DbDataObject dbo : hierarchyMenus) {
			String confStr = (String) dbo.getVal(MC.MENU_CONF);
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
			collectParents(parent, svr, visited, accumulator); // recursive
			accumulator.add(parent); // add after recursion to keep top-down order
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
				collectChildren(child, svr, visited, accumulator); // recursive
			}
		}
	}
}
