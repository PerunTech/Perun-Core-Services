package com.prtech.perun.services.ws;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.google.gson.JsonObject;
import com.prtech.svarog.SvCore.SvAccess;
import com.prtech.svarog.svCONST;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;

public class TestPermissionMatrix {
	@Test
	public void highestGrantWinsAndNoneDoesNotDeny() {
		JsonObject result = matrix(array(table(10L, "ANIMAL")), array(
				acl(1L, 10L, SvAccess.READ, null), acl(2L, 10L, SvAccess.NONE, null),
				acl(3L, 10L, SvAccess.FULL, null), acl(4L, 10L, SvAccess.WRITE, null)), false);
		JsonObject row = row(result, 0);
		assertEquals("FULL", row.get("effectiveAccess").getAsString());
		assertTrue(row.get("FULL").getAsBoolean());
		assertFalse(row.get("READ").getAsBoolean());
		assertFalse(row.get("NONE").getAsBoolean());
		assertExactlyOneMark(row);
	}

	@Test
	public void includesTablesWithoutGrantsAndSortsByName() {
		JsonObject result = matrix(array(table(10L, "ZEBRA"), table(11L, "ANIMAL")), new DbDataArray(), false);
		assertEquals(2, result.getAsJsonArray("rows").size());
		assertEquals("ANIMAL", row(result, 0).get("tableName").getAsString());
		assertEquals(11L, row(result, 0).get("tableId").getAsLong());
		assertTrue(row(result, 0).get("NONE").getAsBoolean());
		assertEquals(0, row(result, 0).getAsJsonArray("grants").size());
		assertExactlyOneMark(row(result, 0));
	}

	@Test
	public void scopedAndNonTableGrantsDoNotBecomeWholeTableAccess() {
		DbDataObject nonTable = acl(3L, 10L, SvAccess.FULL, null);
		nonTable.setVal("ACL_OBJECT_TYPE", svCONST.OBJECT_TYPE_USER);
		JsonObject result = matrix(array(table(10L, "SVAROG_EXECUTORS")), array(
				acl(1L, 10L, SvAccess.EXECUTE, "LOGIN_USER"),
				acl(2L, 10L, SvAccess.FULL, "ONE_CONFIG"), nonTable,
				acl(4L, 0L, SvAccess.EXECUTE, "CUSTOM_ACTION")), false);
		JsonObject row = row(result, 0);
		assertEquals("NONE", row.get("effectiveAccess").getAsString());
		assertTrue(row.get("hasScopedPermissions").getAsBoolean());
		assertEquals(2, row.getAsJsonArray("grants").size());
		assertEquals(4, result.getAsJsonArray("otherPermissions").size());
		assertEquals("LOGIN_USER", row.getAsJsonArray("grants").get(0).getAsJsonObject()
				.get("configKey").getAsString());
	}

	@Test
	public void deduplicatesAclAndPreservesSourceGroups() {
		DbDataObject grant = acl(1L, 10L, SvAccess.READ, null);
		Map<Long, Set<String>> groups = new HashMap<>();
		groups.put(1L, new HashSet<>(Arrays.asList("WMS_USER", "AUTHORIZED_VETERINARIAN")));
		JsonObject result = WsAdminConsole.buildPermissionMatrix(array(table(10L, "ANIMAL")),
				array(grant, grant), groups, false);
		JsonObject row = row(result, 0);
		assertEquals(1, row.getAsJsonArray("grants").size());
		assertEquals(2, row.getAsJsonArray("sourceGroups").size());
		assertEquals(2, row.getAsJsonArray("grants").get(0).getAsJsonObject()
				.getAsJsonArray("sourceGroups").size());
	}

	@Test
	public void respectsExecuteAndModifyOrdering() {
		JsonObject result = matrix(array(table(10L, "ANIMAL")), array(
				acl(1L, 10L, SvAccess.WRITE, null), acl(2L, 10L, SvAccess.EXECUTE, null),
				acl(3L, 10L, SvAccess.MODIFY, null)), false);
		assertEquals("MODIFY", row(result, 0).get("effectiveAccess").getAsString());
		assertExactlyOneMark(row(result, 0));
	}

	@Test
	public void administratorBypassIsFullEvenWithoutGrants() {
		JsonObject result = matrix(array(table(10L, "ANIMAL")),
				array(acl(1L, 10L, SvAccess.NONE, null)), true);
		assertEquals("FULL", row(result, 0).get("effectiveAccess").getAsString());
		assertExactlyOneMark(row(result, 0));
	}

	private static JsonObject matrix(DbDataArray tables, DbDataArray permissions, boolean bypass) {
		return WsAdminConsole.buildPermissionMatrix(tables, permissions, Collections.emptyMap(), bypass);
	}

	private static JsonObject row(JsonObject result, int index) {
		return result.getAsJsonArray("rows").get(index).getAsJsonObject();
	}

	private static void assertExactlyOneMark(JsonObject row) {
		int marked = 0;
		for (SvAccess access : SvAccess.values())
			if (row.get(access.name()).getAsBoolean())
				marked++;
		assertEquals(1, marked);
	}

	private static DbDataArray array(DbDataObject... objects) {
		DbDataArray array = new DbDataArray();
		for (DbDataObject object : objects)
			array.addDataItem(object);
		return array;
	}

	private static DbDataObject table(Long id, String name) {
		DbDataObject table = new DbDataObject(svCONST.OBJECT_TYPE_TABLE);
		table.setObjectId(id);
		table.setVal("TABLE_NAME", name);
		table.setVal("LABEL_CODE", name.toLowerCase(java.util.Locale.ROOT));
		return table;
	}

	private static DbDataObject acl(Long id, Long tableId, SvAccess access, String configKey) {
		DbDataObject acl = new DbDataObject(svCONST.OBJECT_TYPE_ACL);
		acl.setObjectId(id);
		acl.setVal("ACL_OBJECT_ID", tableId);
		acl.setVal("ACL_OBJECT_TYPE", svCONST.OBJECT_TYPE_TABLE);
		acl.setVal("ACCESS_TYPE", access);
		acl.setVal("ACL_CONFIG_UNQ", configKey);
		acl.setVal("LABEL_CODE", "permission." + id);
		return acl;
	}
}
