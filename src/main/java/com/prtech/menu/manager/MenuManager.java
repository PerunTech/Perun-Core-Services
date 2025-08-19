package com.prtech.menu.manager;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
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
import com.prtech.svarog.svCONST;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;

@Path("/menu")
public class MenuManager {

	static final Logger log4j = LogManager.getLogger(DbReader.class.getName());

	@GET
	@Path("/extended/{sid}/{menuCode}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getExtendedMenu(@PathParam("sid") String sessionId, @PathParam("menuCode") String menuCode,
			@QueryParam("mode") String modeParam) {
		try (SvReader svr = new SvReader(sessionId)) {
			BuildDirectionMode mode = BuildDirectionMode.fromString(modeParam);
			DbDataObject menuRoot = new DbReader().searchDbObjectBySingleFilter(DbCompareOperand.EQUAL,
					svCONST.OBJECT_TYPE_MENU, "menu_code", menuCode, svr);
			if (menuRoot == null) {
				return Response.status(Response.Status.NOT_FOUND).entity("Menu not found").build();
			}
			JsonObject resultJson = MenuHierarchyBuilder.buildFullHierarchy(menuRoot, svr, mode);
			return Response.ok(resultJson).build();
		} catch (Exception e) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		}
	}

	public static void main(String[] args) {
		String sid = null;
		try (SvSecurity svc = new SvSecurity()) {
			sid = svc.logon("ADMIN", SvUtil.getMD5("welcome"));
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}

		if (sid == null)
			return;

		String targetMenuCode = "pharmacies_registry_menu_t"; // Change if needed
		BuildDirectionMode directionMode = BuildDirectionMode.fromString("UP");

		try (SvReader svr = new SvReader(sid)) {
			DbDataObject menuRoot = new DbReader().searchDbObjectBySingleFilter(DbCompareOperand.EQUAL,
					svCONST.OBJECT_TYPE_MENU, "menu_code", targetMenuCode, svr);
			if (menuRoot == null) {
				log4j.info("Menu not found: " + targetMenuCode);
				return;
			}

			JsonObject resultJson = MenuHierarchyBuilder.buildFullHierarchy(menuRoot, svr, directionMode);
			log4j.info("=== Full Merged Menu JSON ===");
			System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(resultJson));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}