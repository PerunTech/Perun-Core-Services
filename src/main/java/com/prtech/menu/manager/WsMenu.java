package com.prtech.menu.manager;

import java.util.HashSet;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.prtech.perun.services.ws.DbReader;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvSecurity;
import com.prtech.svarog.SvUtil;
import com.prtech.svarog.SvWriter;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;

/**
 * WsMenu class for generating merged menu definitions. Uses PERUN_MENU as
 * source table, and supports recursive merging based on IMPORT_MENU as KEY
 * field in MENU_CONF JSON.
 */
@Path("/Menu")
public class WsMenu {

	private static final Logger log4j = LogManager.getLogger(WsMenu.class);

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
			JsonObject resultJson = MenuHelper.buildFullHierarchy(menuRoot, svr, new HashSet<>());
			return Response.ok(resultJson.toString(), MediaType.APPLICATION_JSON).build();
		} catch (Exception e) {
			log4j.error("Error generating menu: ", e);
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		}
	}

	/**
	 * Web service for removing a menu from the system by it's menu code
	 * 
	 * @param sessionId    Session ID
	 * @param rootMenuCode Root menu code
	 * @return
	 */
	@GET
	@Path("/remove/{sid}/{rootMenuCode}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response removeMenu(@PathParam("sid") String sessionId, @PathParam("rootMenuCode") String rootMenuCode) {
		try (SvReader svr = new SvReader(sessionId); SvWriter svw = new SvWriter(svr)) {
			DbDataObject menuRoot = new DbReader().searchDbObjectBySingleFilter(DbCompareOperand.EQUAL,
					SvReader.getTypeIdByName(CC.PERUN_MENU), CC.MENU_CODE, rootMenuCode, svr);
			if (menuRoot == null) {
				return Response.status(Response.Status.NOT_FOUND).entity("Menu not found").build();
			}
			if (!MenuHelper.checkUserHasPermission(menuRoot, svr)) {
				return Response.status(Response.Status.UNAUTHORIZED)
						.entity("User does not have permission to delete this menu").build();
			}
			List<String> deleteErrors = MenuHelper.deleteMenuHelper(rootMenuCode, svr);
			if (!deleteErrors.isEmpty()) {
				return Response.status(Response.Status.NOT_FOUND)
						.entity("The item couldn't be deleted. Please check the list below for details. "
								+ deleteErrors.toString())
						.build();
			}
			String responseObj = menuRoot.toSimpleJson().toString();
			svw.deleteObject(menuRoot);
			return Response.ok(responseObj, MediaType.APPLICATION_JSON).build();
		} catch (Exception e) {
			log4j.error("Error generating menu: ", e);
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

		String targetMenuCode = "pharmacies_registry_menu_tt";

		try (SvReader svr = new SvReader(sid)) {
			DbDataObject targetMenu = MenuHelper.findMenuByCode(targetMenuCode, svr);
			if (targetMenu == null) {
				log4j.info("Menu not found: " + targetMenuCode);
				return;
			}

			JsonObject fullHierarchyJson = MenuHelper.buildFullHierarchy(targetMenu, svr, new HashSet<>());
			log4j.info("!!! Full merged menu in JSON: !!!");
			System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(fullHierarchyJson));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}