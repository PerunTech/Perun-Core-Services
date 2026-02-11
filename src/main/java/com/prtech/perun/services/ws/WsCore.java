package com.prtech.perun.services.ws;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.gson.JsonObject;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvReader;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.ResponseHandler;
import com.prtech.svarog_common.ResponseHandler.MessageType;

@Path("/WsCore")
public class WsCore {

	@Path("/children/{sessionId}/{parentId}/{objectName}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getChildObjectsAsCleanJson(@PathParam("sessionId") String sessionId,
			@PathParam("parentId") Long parentId, @PathParam("objectName") String objectName) {
		ResponseHandler jrh = new ResponseHandler();
		DbDataArray children = new DbDataArray();
		try (SvReader svr = new SvReader(sessionId)) {
			if (parentId == null || parentId <= 0)
				return Response.status(400).entity("{\"error\":\"Invalid parentId\"}").build();
			if (objectName == null || objectName.isBlank())
				return Response.status(400).entity("{\"error\":\"Invalid objectName\"}").build();
			Long objectTypeId = WsReactElements.findTableType(objectName);
			children = svr.getObjectsByParentId(parentId, objectTypeId, null, 0, 0);
		} catch (SvException e) {
			jrh.create(MessageType.ERROR, "Error fetching children", null, new JsonObject());
			return Response.ok(jrh.getAll().toString()).build();
		}
		return Response.ok(children.toJson().toString()).build();
	}

	@Path("/linkedObjects/{sessionId}/{objectId}/{table_name}/{linkName}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getObjectsByLinkPerStatuses(@PathParam("sessionId") String sessionId,
			@PathParam("objectId") Long objectId, @PathParam("table_name") String tableName,
			@PathParam("linkName") String linkName) {
		ResponseHandler jrh = new ResponseHandler();
		try (SvReader svr = new SvReader(sessionId)) {
			WsReactElements ws = new WsReactElements();
			if (objectId == null || objectId <= 0)
				return Response.status(400).entity("{\"error\":\"Invalid objectId\"}").build();
			jrh.create(MessageType.ERROR, "{\"error\":\"Invalid objectId\"}", null, new JsonObject());
			if (tableName == null || tableName.isBlank())
				return Response.status(400).entity("{\"error\":\"Invalid table_name\"}").build();
			if (linkName == null || linkName.isBlank())
				return Response.status(400).entity("{\"error\":\"Invalid linkName\"}").build();
			DbDataArray linkedObjects = ws.getObjectsByLink(sessionId, objectId, tableName, linkName);
			return Response.ok(linkedObjects.toJson().toString()).build();
		} catch (Exception e) {
			jrh.create(MessageType.ERROR, "Error getting Objects By Link Per Statuses", null, new JsonObject());
			return Response.ok(jrh.getAll().toString()).build();
		}
	}

}
