package com.prtech.form;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.prtech.svarog.SvCore;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvReader;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbSearchCriterion;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;

// Shared utility methods for form handling
public class Utils {
	static final Logger log4j = LogManager.getLogger(Utils.class.getName());

	/**
	 * Merge "source" into "target". If fields have equal name, merge them
	 * recursively. Null values in source will remove the field from the target.
	 * Override target values with source values Keys not supplied in source will
	 * remain unchanged in target
	 *
	 * @return the merged object (target).
	 */
	public static JsonObject deepMerge(JsonObject source, JsonObject target) {

		for (Map.Entry<String, JsonElement> sourceEntry : source.entrySet()) {
			String key = sourceEntry.getKey();
			JsonElement value = sourceEntry.getValue();
			if (!target.has(key)) {
				target.add(key, value);
			} else {
				if (!value.isJsonNull()) {
					if (value.isJsonObject()) {
						deepMerge(value.getAsJsonObject(), target.get(key).getAsJsonObject());
					} else {
						target.add(key, value);
					}
				} else {
					target.remove(key);
				}
			}
		}
		return target;
	}

	public static DbDataObject findField(String tableName, String fieldName) throws SvException {
		DbDataObject dbField = null;
		DbDataObject tableObject = SvCore.getDbtByName(tableName);

		DbDataArray dboFieldsPerTable = SvCore.getFields(tableObject.getObjectId());
		for (DbDataObject dbo : dboFieldsPerTable.getItems()) {
			if (dbo.getVal(CC.FIELD_NAME).toString().equals(fieldName)) {
				dbField = dbo;
				break;
			}
		}
		return dbField;
	}

	public static DbDataObject getDbDataObjectFromDenormalizedField(String tableName, String fieldName,
			Object denormalizedId, SvReader svr) throws SvException {
		DbDataObject dbo = null;

		if (denormalizedId != null) {
			if (fieldName.equals(CC.OBJECT_ID)) {
				dbo = svr.getObjectById(Long.valueOf(denormalizedId.toString()),
						SvReader.getDbtByName(tableName.toUpperCase()), null);
			} else {
				DbSearchCriterion crit = new DbSearchCriterion(fieldName.toUpperCase(), DbCompareOperand.EQUAL,
						denormalizedId);
				DbDataArray dba = svr.getObjects(crit, SvReader.getTypeIdByName(tableName), null, 0, 0);
				if (!dba.isEmpty()) {
					dbo = dba.get(0);
				}
			}
		}
		return dbo;
	}
}