package com.prtech.perun.services.ws;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.gson.JsonObject;
import com.prtech.perun.PerunUtil;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvReader;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.ResponseHandler;
import com.prtech.svarog_common.ResponseHandler.MessageType;

@Path("/WsCore")
public class WsCore {

	@Path("/object/{sessionId}/{objectId}/{objectName}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getObjectAsCleanJson(@PathParam("sessionId") String sessionId, @PathParam("objectId") Long objectId,
			@PathParam("objectName") String objectName) {
		ResponseHandler jrh = new ResponseHandler();
		DbDataObject object = new DbDataObject();
		try (SvReader svr = new SvReader(sessionId)) {
			if (objectId == null || objectId <= 0)
				throw new SvException("perun.core.error.invalid_object", null);
			if (objectName == null || objectName.isBlank())
				throw new SvException("perun.core.error.invalid_object_type", null);
			Long objectTypeId = WsReactElements.findTableType(objectName);
			object = svr.getObjectById(objectId, objectTypeId, null);
		} catch (SvException e) {
			// TODO Auto-generated catch block
			return PerunUtil.handleException(e, "Error fetching children");
		}
		return Response.ok(object.toJson().toString()).build();
	}

	@Path("/children/{sessionId}/{parentId}/{objectName}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getChildObjectsAsCleanJson(@PathParam("sessionId") String sessionId,
			@PathParam("parentId") Long parentId, @PathParam("objectName") String objectName) {
		DbDataArray children = new DbDataArray();
		try (SvReader svr = new SvReader(sessionId)) {
			if (parentId == null || parentId <= 0)
				throw new SvException("perun.core.error.invalid_parent", null);
			if (objectName == null || objectName.isBlank())
				throw new SvException("perun.core.error.invalid_object_type", null);
			Long objectTypeId = WsReactElements.findTableType(objectName);
			children = svr.getObjectsByParentId(parentId, objectTypeId, null, 0, 0);
		} catch (SvException e) {
			// TODO Auto-generated catch block
			return PerunUtil.handleException(e, "Error fetching children");
		}
		return Response.ok(children.toJson().toString()).build();
	}

	@Path("/linkedObjects/{sessionId}/{objectId}/{table_name}/{linkName}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getObjectsByLinkPerStatuses(@PathParam("sessionId") String sessionId,
			@PathParam("objectId") Long objectId, @PathParam("table_name") String tableName,
			@PathParam("linkName") String linkName) {
		try (SvReader svr = new SvReader(sessionId)) {
			WsReactElements ws = new WsReactElements();

			if (objectId == null || objectId <= 0)
				throw new SvException("perun.core.error.invalid_object", null);
			if (tableName == null || tableName.isBlank())
				throw new SvException("perun.core.error.invalid_object_type", null);
			if (linkName == null || linkName.isBlank())
				throw new SvException("perun.core.error.invalid_link_type", null);

			DbDataArray linkedObjects = ws.getObjectsByLink(sessionId, objectId, tableName, linkName);
			return Response.ok(linkedObjects.toJson().toString()).build();
		} catch (Exception e) {
			return PerunUtil.handleException(e, "Error getting Objects By Link");
		}
	}

}
