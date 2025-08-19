package com.prtech.menu.manager_bck;

import java.util.*;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.*;
import com.prtech.perun.services.ws.DbReader;
import com.prtech.svarog.*;
import com.prtech.svarog_common.*;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;

@Path("/menu")
public class MenuManager {

	static final Logger log4j = LogManager.getLogger(DbReader.class.getName());

	private static final Long MENU_OBJECT_TYPE = 112L;

	@GET
	@Path("/extended/{sid}/{menuCode}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getExtendedMenu(@PathParam("sid") String sessionId, @PathParam("menuCode") String menuCode) {
		try (SvReader svr = new SvReader(sessionId)) {
			RecursiveMenuBuilder builder = new RecursiveMenuBuilder();
			JsonObject extendedMenu = builder.buildRecursiveMenu(menuCode, svr);
			return Response.ok().entity(extendedMenu.toString()).build();
		} catch (Exception e) {
			log4j.error("Failed to load extended menu: " + e.getMessage(), e);
			return Response.status(500).entity("Error: " + e.getMessage()).build();
		}
	}

	// Main method for quick test/debug without JAX-RS
	public static void main(String[] args) {
		String sid = null;
		try (@SuppressWarnings("deprecation")
		SvSecurity svc = new SvSecurity()) {
			sid = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
			log4j.info(sid);
		} catch (Exception e) {
			log4j.error(e);
		}
		String menuCode = "warehouse_registry_menu"; // pharmacies_registry_menu
		menuCode = "pharmacies_registry_menu_t";
		try (SvReader svr = new SvReader(sid)) {
			RecursiveMenuBuilder builder = new RecursiveMenuBuilder();
			JsonObject result = builder.buildRecursiveMenu(menuCode, svr);
			System.out.println("Extended menu:");
			System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(result));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	static class RecursiveMenuBuilder {

		private Map<Long, JsonObject> menuCache = new HashMap<>();

		public JsonObject buildRecursiveMenu(String menuCode, SvReader svr) throws SvException {
			DbDataObject rootMenu = searchDbObjectBySingleFilter(DbCompareOperand.EQUAL, MENU_OBJECT_TYPE, "menu_code",
					menuCode, svr);
			if (rootMenu == null)
				return null;

			JsonObject rootMenuConf = parseMenuConf(rootMenu);
			if (rootMenuConf == null)
				return null;

			Set<Long> visited = new HashSet<>();
			visited.add(rootMenu.getObjectId());

			enrichWithChildren(rootMenu.getObjectId(), rootMenuConf, svr, visited);
			return rootMenuConf;
		}

		private void enrichWithChildren(Long parentId, JsonObject parentConf, SvReader svr, Set<Long> visited)
				throws SvException {
			DbSearchCriterion criterion = new DbSearchCriterion("parent_id", DbCompareOperand.EQUAL, parentId);
			DbDataArray children = svr.getObjects(criterion, MENU_OBJECT_TYPE, null, 0, 0);

			JsonArray parentButtons = parentConf.has("buttonArray") ? parentConf.getAsJsonArray("buttonArray")
					: new JsonArray();

			for (DbDataObject child : children.getItems()) {
				if (visited.contains(child.getObjectId()))
					continue; // Avoid cycles
				visited.add(child.getObjectId());

				JsonObject childConf = menuCache.computeIfAbsent(child.getObjectId(), id -> parseMenuConf(child));
				if (childConf != null && childConf.has("buttonArray")) {
					JsonArray childButtons = childConf.getAsJsonArray("buttonArray");
					for (JsonElement btn : childButtons) {
						parentButtons.add(btn);
					}
				}
			}

			parentConf.add("buttonArray", parentButtons);
		}

		private JsonObject parseMenuConf(DbDataObject dbo) {
			try {
				String confStr = dbo.getVal("menu_conf").toString();
				return JsonParser.parseString(confStr).getAsJsonObject();
			} catch (Exception e) {
				log4j.error("Failed to parse menu_conf: " + e.getMessage());
				return null;
			}
		}

		public DbDataObject searchDbObjectBySingleFilter(DbCompareOperand operand, Long objectType, String columnName,
				Object value, SvReader svr) {
			try {
				DbSearchCriterion cr1 = new DbSearchCriterion(columnName, operand, value);
				DbDataArray found = svr.getObjects(cr1, objectType, null, 1, 0);
				if (!found.isEmpty()) {
					return found.get(0);
				}
			} catch (SvException e) {
				log4j.error("Error searching object: " + e.getMessage());
			}
			return null;
		}
	}
}
