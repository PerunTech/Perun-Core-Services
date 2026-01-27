package com.prtech.sequence.manager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;
import com.prtech.menu.manager.CC;
import com.prtech.sequence.manager.SeqPatternExceptions.SeqPatternDuplicateError;
import com.prtech.sequence.manager.SeqPatternExceptions.SeqPatternError;
import com.prtech.sequence.manager.SeqPatternExceptions.SeqPatternValidationError;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvSequence;
import com.prtech.svarog.SvWriter;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbSearchCriterion;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;
import com.prtech.svarog_common.DbSearchExpression;

public class SeqPatternService {

	private static final Logger log4j = LogManager.getLogger(SeqPatternService.class);

	/**
	 * Set SV_ID_SEQ_PATTERN object values
	 * 
	 * @param seqPatternDbo database object to set values to
	 * @param seqPatternId
	 * @param confTable     name of the target table
	 * @param destField     column name in confTable
	 * @param seqPattern    pattern string
	 * @param isDefault
	 * @param condField     field in confTable used for condition matching
	 * @param condOperator  condition operator
	 * @param condValue     condition value(s)
	 * @return The new DbDataObject
	 * @throws SvException
	 */
	static void setSeqPatternObjectValues(DbDataObject seqPatternDbo, String seqPatternId, String confTable,
			String destField, String seqPattern, boolean isDefault, String condField, String condOperator,
			String condValue) throws SvException {
		seqPatternDbo.setVal(CC.SEQ_PATTERN_ID, seqPatternId);
		seqPatternDbo.setVal(CC.CONF_TABLE, confTable);
		seqPatternDbo.setVal(CC.DEST_FIELD, destField);
		seqPatternDbo.setVal(CC.SEQ_PATTERN, seqPattern);
		seqPatternDbo.setVal(CC.IS_DEFAULT, isDefault);
		if (!isDefault) {
			seqPatternDbo.setVal(CC.COND_FIELD, condField);
			seqPatternDbo.setVal(CC.COND_OPERATOR, condOperator);
			seqPatternDbo.setVal(CC.COND_VALUE, condValue);
		} else {
			seqPatternDbo.setVal(CC.COND_FIELD, null);
			seqPatternDbo.setVal(CC.COND_OPERATOR, null);
			seqPatternDbo.setVal(CC.COND_VALUE, null);
		}
		Long objectType = SvReader.getTypeIdByName(confTable);
		seqPatternDbo.setParentId(objectType);
	}

	/**
	 * Set SV_ID_SEQ_PATTERN object values from the JSON data
	 * 
	 * @param seqPatternDbo database object to set values to
	 * @param requestData   JSON object containing the database object data
	 * @return
	 * @throws SvException
	 */
	static void setSeqPatternObjectValues(DbDataObject seqPatternDbo, JsonObject requestData) throws SvException {
		String seqPatternId = requestData.has(CC.SEQ_PATTERN_ID) ? requestData.get(CC.SEQ_PATTERN_ID).getAsString()
				: null;
		String confTable = requestData.has(CC.CONF_TABLE) ? requestData.get(CC.CONF_TABLE).getAsString() : null;
		String destField = requestData.has(CC.DEST_FIELD) ? requestData.get(CC.DEST_FIELD).getAsString() : null;
		String seqPattern = requestData.has(CC.SEQ_PATTERN) ? requestData.get(CC.SEQ_PATTERN).getAsString() : null;
		boolean isDefault = requestData.has(CC.IS_DEFAULT) && requestData.get(CC.IS_DEFAULT).getAsBoolean();
		String condField = requestData.has(CC.COND_FIELD) ? requestData.get(CC.COND_FIELD).getAsString() : null;
		String condOperator = requestData.has(CC.COND_OPERATOR) ? requestData.get(CC.COND_OPERATOR).getAsString()
				: null;
		String condValue = requestData.has(CC.COND_VALUE) ? requestData.get(CC.COND_VALUE).getAsString() : null;
		setSeqPatternObjectValues(seqPatternDbo, seqPatternId, confTable, destField, seqPattern, isDefault, condField,
				condOperator, condValue);
	}

	/**
	 * Save SV_ID_SEQ_PATTERN object from JSON data with validation
	 * 
	 * @param requestData JSON object containing the pattern info
	 * @param svr         Svarog reader
	 * @param svw         Svarog writer
	 * @return the saved DbDataObject
	 * @throws SvException
	 * @throws SeqPatternValidationError
	 * @throws SeqPatternDuplicateError
	 */
	public static DbDataObject saveSeqPatternHelper(JsonObject requestData, SvReader svr, SvWriter svw)
			throws SvException, SeqPatternValidationError, SeqPatternDuplicateError {

		Long objectType = SvReader.getTypeIdByName(CC.SV_ID_SEQ_PATTERN);
		List<String> requiredKeys = Arrays.asList(CC.SEQ_PATTERN_ID, CC.CONF_TABLE, CC.DEST_FIELD, CC.SEQ_PATTERN);
		for (String key : requiredKeys) {
			if (!requestData.has(key) || requestData.get(key).isJsonNull()
					|| requestData.get(key).getAsString().isBlank()) {
				log4j.error("Missing required key: " + key);
				throw new SeqPatternExceptions.SeqPatternValidationError("Missing required key: " + key);
			}
		}
		String seqPatternId = requestData.get(CC.SEQ_PATTERN_ID).getAsString();
		DbSearchCriterion criteria = new DbSearchCriterion(CC.SEQ_PATTERN_ID, DbCompareOperand.EQUAL, seqPatternId);
		DbSearchExpression expression = new DbSearchExpression().addDbSearchItem(criteria);
		DbDataArray array = svr.getObjects(expression, objectType, null, null, null);
		if (array != null && !array.getItems().isEmpty()) {
			throw new SeqPatternExceptions.SeqPatternDuplicateError("Unique Pattern ID already exists.");
		}
		String seqPattern = requestData.get(CC.SEQ_PATTERN).getAsString();
		validateSeqPattern(seqPattern);
		String confTable = requestData.get(CC.CONF_TABLE).getAsString();
		String destField = requestData.get(CC.DEST_FIELD).getAsString();
		DbDataArray fieldsArray = SvReader.getFields(SvReader.getTypeIdByName(confTable));
		boolean destFieldExists = false;
		for (DbDataObject fieldObj : fieldsArray.getItems()) {
			String fieldName = fieldObj.getAsString(CC.FIELD_NAME);
			if (fieldName != null && fieldName.equals(destField)) {
				destFieldExists = true;
				break;
			}
		}
		if (!destFieldExists) {
			log4j.error("FIELD: " + destField + " does not exist in table " + confTable);
			throw new SeqPatternExceptions.SeqPatternValidationError(
					"FIELD :" + destField + " does not exist in table " + confTable);
		}
		boolean isDefault = requestData.has(CC.IS_DEFAULT) && requestData.get(CC.IS_DEFAULT).getAsBoolean();
		List<String> condFieldsList = Arrays.asList(CC.COND_FIELD, CC.COND_OPERATOR, CC.COND_VALUE);
		if (isDefault) {
			for (String key : condFieldsList) {
				if (requestData.has(key) && !requestData.get(key).isJsonNull()
						&& !requestData.get(key).getAsString().isBlank()) {
					log4j.error("Default pattern cannot have " + key);
					throw new SeqPatternExceptions.SeqPatternValidationError("Default pattern cannot have " + key);
				}
			}
			DbSearchCriterion filter1 = new DbSearchCriterion(CC.CONF_TABLE, DbCompareOperand.EQUAL, confTable);
			DbSearchCriterion filter2 = new DbSearchCriterion(CC.DEST_FIELD, DbCompareOperand.EQUAL, destField);
			DbSearchCriterion filter3 = new DbSearchCriterion(CC.IS_DEFAULT, DbCompareOperand.EQUAL, true);
			DbSearchExpression dbexpr = new DbSearchExpression().addDbSearchItem(filter1).addDbSearchItem(filter2)
					.addDbSearchItem(filter3);
			DbDataArray dbArray = svr.getObjects(dbexpr, objectType, null, null, null);
			if (dbArray != null && !dbArray.getItems().isEmpty()) {
				throw new SeqPatternExceptions.SeqPatternDuplicateError(
						"Only one default row is allowed per table and destination");
			}

		} else {
			for (String key : condFieldsList) {
				if (!requestData.has(key) || requestData.get(key).isJsonNull()
						|| requestData.get(key).getAsString().isBlank()) {
					log4j.error("Non-default pattern must have " + key);
					throw new SeqPatternExceptions.SeqPatternValidationError("Non-default pattern must have " + key);
				}
			}
			String condOperator = requestData.get(CC.COND_OPERATOR).getAsString();
			ArrayList<String> allowedOperators = getCodesFromCodelist(CC.SV_ID_SEQ_PATTERN, CC.COND_OPERATOR, svr);
			if (condOperator != null && !allowedOperators.contains(condOperator)) {
				log4j.error("Invalid COND_OPERATOR: " + condOperator);
				throw new SeqPatternExceptions.SeqPatternValidationError(
						"Invalid COND_OPERATOR: " + condOperator + ". Allowed values: " + allowedOperators);
			}
			String condField = requestData.get(CC.COND_FIELD).getAsString();
			String condValue = requestData.get(CC.COND_VALUE).getAsString();
			DbSearchCriterion dbc1 = new DbSearchCriterion(CC.CONF_TABLE, DbCompareOperand.EQUAL, confTable);
			DbSearchCriterion dbc2 = new DbSearchCriterion(CC.DEST_FIELD, DbCompareOperand.EQUAL, destField);
			DbSearchCriterion dbc3 = new DbSearchCriterion(CC.COND_FIELD, DbCompareOperand.EQUAL, condField);
			DbSearchCriterion dbc4 = new DbSearchCriterion(CC.COND_OPERATOR, DbCompareOperand.EQUAL, condOperator);
			DbSearchCriterion dbc5 = new DbSearchCriterion(CC.COND_VALUE, DbCompareOperand.EQUAL, condValue);
			DbSearchExpression dbse = new DbSearchExpression().addDbSearchItem(dbc1).addDbSearchItem(dbc2)
					.addDbSearchItem(dbc3).addDbSearchItem(dbc4).addDbSearchItem(dbc5);
			DbDataArray dbArr = svr.getObjects(dbse, objectType, null, null, null);
			if (dbArr != null && !dbArr.getItems().isEmpty()) {
				throw new SeqPatternExceptions.SeqPatternDuplicateError(
						"Duplicate conditional rule for same table and destination is not allowed");
			}
		}
		DbDataObject seqPatternDbo;
		Long objectId = requestData.has(CC.OBJECT_ID) ? requestData.get(CC.OBJECT_ID).getAsLong() : -1L;

		if (objectId != null && objectId > 0) {
			seqPatternDbo = svr.getObjectById(objectId, SvReader.getTypeIdByName(CC.SV_ID_SEQ_PATTERN), null);
			if (seqPatternDbo == null) {
				log4j.error("SV_ID_SEQ_PATTERN object with object_id " + objectId
						+ " not found, creating new one instead.");
				seqPatternDbo = new DbDataObject();
				seqPatternDbo.setObjectType(SvReader.getTypeIdByName(CC.SV_ID_SEQ_PATTERN));
			}
		} else {
			seqPatternDbo = new DbDataObject();
			seqPatternDbo.setObjectType(SvReader.getTypeIdByName(CC.SV_ID_SEQ_PATTERN));
		}
		setSeqPatternObjectValues(seqPatternDbo, requestData);
		svw.saveObject(seqPatternDbo, true);
		return seqPatternDbo;
	}

	/**
	 * Validates the SEQ_PATTERN string according to business rules: 1. Must end
	 * with {SvSeq} 2. Must contain at least one constant in quotes before {SvSeq}
	 *
	 * @param seqPattern the sequence pattern string to validate
	 * @throws SeqPatternValidationError if validation fails
	 */
	public static void validateSeqPattern(String seqPattern) throws SeqPatternValidationError {
		if (seqPattern == null || seqPattern.isBlank()) {
			throw new SeqPatternValidationError("SEQ_PATTERN cannot be null or empty");
		}
		if (!seqPattern.endsWith("{SvSeq}")) {
			log4j.error("SEQ_PATTERN must end with {SvSeq}");
			throw new SeqPatternValidationError("SEQ_PATTERN must end with {SvSeq}");
		}
		Pattern constCheck = Pattern.compile("\".+\".*\\{SvSeq\\}$");
		Matcher matcher = constCheck.matcher(seqPattern);
		if (!matcher.find()) {
			log4j.error("SEQ_PATTERN must have at least one constant in quotes before {SvSeq}");
			throw new SeqPatternValidationError("SEQ_PATTERN must have at least one constant in quotes before {SvSeq}");
		}
	}

	/**
	 * Method that gets all code values from a codelist
	 * 
	 * @param tableName
	 * @param fieldName
	 * @param svr
	 * @return
	 */
	public static ArrayList<String> getCodesFromCodelist(String tableName, String fieldName, SvReader svr) {
		ArrayList<String> result = new ArrayList<>();
		String code = CC.EMPTY_STRING;
		try {
			DbDataObject tempField = SvReader.getFieldByName(tableName, fieldName);
			Long codeListId = tempField.getAsLong("CODE_LIST_ID");
			DbDataArray codelistItems = svr.getObjectsByParentId(codeListId, SvReader.getTypeIdByName("SVAROG_CODES"),
					null);
			for (DbDataObject codeItem : codelistItems.getItems()) {
				code = codeItem.getAsString("CODE_VALUE");
				result.add(code);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	/**
	 * Retrieve a SV_ID_SEQ_PATTERN object by its unique SEQ_PATTERN_ID.
	 * 
	 * This method searches for a sequence pattern configuration using the business
	 * key SEQ_PATTERN_ID. Since SEQ_PATTERN_ID is expected to be unique, the method
	 * returns the first matching object if found, or null otherwise.
	 * 
	 * @param seqPatternId unique identifier of the sequence pattern
	 * @param svr
	 * @return DbDataObject of type SV_ID_SEQ_PATTERN if found, otherwise null
	 */
	public static DbDataObject getBySeqencePatternId(String seqPatternId, SvReader svr) throws SvException {
		Long objectType = SvReader.getTypeIdByName(CC.SV_ID_SEQ_PATTERN);
		DbSearchCriterion crit = new DbSearchCriterion(CC.SEQ_PATTERN_ID, DbCompareOperand.EQUAL, seqPatternId);
		DbSearchExpression expr = new DbSearchExpression().addDbSearchItem(crit);
		DbDataArray arr = svr.getObjects(expr, objectType, null, null, null);
		if (arr != null && !arr.getItems().isEmpty()) {
			return arr.getItems().get(0);
		}
		return null;
	}

	/**
	 * Retrieve all SV_ID_SEQ_PATTERN objects for given target configuration table
	 * and destination field
	 * 
	 * This method searches for a sequence pattern configuration using the business
	 * key SEQ_PATTERN_ID. Since SEQ_PATTERN_ID is expected to be unique, the method
	 * returns the first matching object if found, or null otherwise.
	 * 
	 * @param confTable name of the configuration (target) table
	 * @param destField destination column name within the configuration table
	 * @param svr
	 * @return DbDataArray containing SV_ID_SEQ_PATTERN objects matching the
	 *         criteria
	 * @throws SvException
	 */
	public static DbDataArray getByTableAndDestinationField(String confTable, String destField, SvReader svr)
			throws SvException {
		Long objectType = SvReader.getTypeIdByName(CC.SV_ID_SEQ_PATTERN);
		DbSearchExpression expr = new DbSearchExpression()
				.addDbSearchItem(new DbSearchCriterion(CC.CONF_TABLE, DbCompareOperand.EQUAL, confTable))
				.addDbSearchItem(new DbSearchCriterion(CC.DEST_FIELD, DbCompareOperand.EQUAL, destField));
		return svr.getObjects(expr, objectType, null, null, null);
	}

	/**
	 * Delete from SV_ID_SEQ_PATTERN configuration identified by its unique
	 * SEQ_PATTERN_ID
	 * 
	 * @param seqPatternId unique mnemonic code for pattern row
	 * @param svr
	 * @param svw
	 * @return The deleted SV_ID_SEQ_PATTERN DbDataObject.
	 * @throws SvException
	 * @throws SeqPatternError if no SV_ID_SEQ_PATTERN exists with the given
	 *                         SEQ_PATTERN_ID
	 * 
	 */
	public static DbDataObject deleteByUniqueSeqPatternID(String seqPatternId, SvReader svr, SvWriter svw)
			throws SvException, SeqPatternError {
		Long objectType = SvReader.getTypeIdByName(CC.SV_ID_SEQ_PATTERN);
		DbSearchCriterion dbc = new DbSearchCriterion(CC.SEQ_PATTERN_ID, DbCompareOperand.EQUAL, seqPatternId);
		DbSearchExpression dbse = new DbSearchExpression().addDbSearchItem(dbc);
		DbDataArray dba = svr.getObjects(dbse, objectType, null, null, null);
		if (dba == null || dba.getItems().isEmpty()) {
			throw new SeqPatternError("SV_ID_SEQ_PATTERN with SEQ_PATTERN_ID='" + seqPatternId + "' not found");
		}
		DbDataObject patternDbo = dba.getItems().get(0);
		svw.deleteObject(patternDbo);
		return patternDbo;
	}

	/**
	 * @param seq_pattern Pattern string
	 * @param svr
	 * @param svw
	 * @return
	 * @throws SvException
	 * @throws SeqPatternError
	 */
	public static List<DbDataObject> deleteBySequencePattern(String seq_pattern, SvReader svr, SvWriter svw)
			throws SvException, SeqPatternError {
		Long objectType = SvReader.getTypeIdByName(CC.SV_ID_SEQ_PATTERN);
		DbSearchCriterion dbc = new DbSearchCriterion(CC.SEQ_PATTERN, DbCompareOperand.EQUAL, seq_pattern);
		DbSearchExpression dbse = new DbSearchExpression().addDbSearchItem(dbc);
		DbDataArray dba = svr.getObjects(dbse, objectType, null, null, null);
		if (dba == null || dba.getItems().isEmpty()) {
			throw new SeqPatternError("SV_ID_SEQ_PATTERN objects with SEQ_PATTERN='" + seq_pattern + "' not found");
		}
		List<DbDataObject> deleted = new ArrayList<>();
		for (DbDataObject patternDbo : dba.getItems()) {
			svw.deleteObject(patternDbo);
			deleted.add(patternDbo);
		}
		return deleted;
	}

	/**
	 * Updates the SEQ_PATTERN value of an existing SV_ID_SEQ_PATTERN configuration.
	 * 
	 * @param seqPatternId  Unique mnemonic code identifying the pattern row to
	 *                      update
	 * @param newSeqPattern New sequence pattern template string to set
	 * @param svr
	 * @param svw
	 * @return The newly created DbDataObject representing the updated active
	 *         SV_ID_SEQ_PATTERN row.
	 * @throws SvException
	 * @throws SeqPatternError If no existing row with {@code seqPatternId} is
	 *                         found.
	 */
	public static DbDataObject updateSeqPatternValue(String seqPatternId, String newSeqPattern, SvReader svr,
			SvWriter svw) throws SvException, SeqPatternError {
		Long objectType = SvReader.getTypeIdByName(CC.SV_ID_SEQ_PATTERN);
		DbSearchCriterion dbc = new DbSearchCriterion(CC.SEQ_PATTERN_ID, DbCompareOperand.EQUAL, seqPatternId);
		DbSearchExpression dbse = new DbSearchExpression().addDbSearchItem(dbc);
		DbDataArray dba = svr.getObjects(dbse, objectType, null, null, null);
		if (dba == null || dba.getItems().isEmpty()) {
			throw new SeqPatternError("SV_ID_SEQ_PATTERN with SEQ_PATTERN_ID= '" + seqPatternId + "' not found");
		}
		DbDataObject oldDbo = dba.getItems().get(0);
		validateSeqPattern(newSeqPattern);
		svw.deleteObject(oldDbo);
		DbDataObject newDbo = new DbDataObject();
		newDbo.setObject_type(objectType);
		newDbo.setVal(CC.SEQ_PATTERN_ID, oldDbo.getVal(CC.SEQ_PATTERN_ID));
		newDbo.setVal(CC.CONF_TABLE, oldDbo.getVal(CC.CONF_TABLE));
		newDbo.setVal(CC.DEST_FIELD, oldDbo.getVal(CC.DEST_FIELD));
		newDbo.setVal(CC.IS_DEFAULT, oldDbo.getVal(CC.IS_DEFAULT));
		newDbo.setVal(CC.COND_FIELD, oldDbo.getVal(CC.COND_FIELD));
		newDbo.setVal(CC.COND_OPERATOR, oldDbo.getVal(CC.COND_OPERATOR));
		newDbo.setVal(CC.COND_VALUE, oldDbo.getVal(CC.COND_VALUE));
		newDbo.setParentId(oldDbo.getParentId());
		newDbo.setVal(CC.SEQ_PATTERN, newSeqPattern);
		svw.saveObject(newDbo, true);
		return newDbo;
	}

	/**
	 * Generate an identifier value for a business object based on the
	 * SV_ID_SEQ_PATTERN configuration
	 * 
	 * @param row       business table row for which the identifier is generated
	 * @param confTable name of the target business table
	 * @param destField column name in {@code confTable} where the generated value
	 *                  will be written
	 * @param svr       Svarog reader
	 * @return Generated identifier value based on the resolved sequence pattern
	 * @throws SvException
	 * @throws SeqPatternError
	 */
	public static String generateId(DbDataObject row, String confTable, String destField, SvReader svr)
			throws SvException, SeqPatternError {
		Long patternType = SvReader.getTypeIdByName(CC.SV_ID_SEQ_PATTERN);
		DbSearchCriterion filterTable = new DbSearchCriterion(CC.CONF_TABLE, DbCompareOperand.EQUAL, confTable);
		DbSearchCriterion filterDest = new DbSearchCriterion(CC.DEST_FIELD, DbCompareOperand.EQUAL, destField);
		DbSearchExpression expr = new DbSearchExpression().addDbSearchItem(filterTable).addDbSearchItem(filterDest);
		DbDataArray patterns = svr.getObjects(expr, patternType, null, null, null);
		if (patterns == null || patterns.getItems().isEmpty()) {
			throw new SeqPatternError("No patterns found for table " + confTable + " and dest field " + destField);
		}
		DbDataObject selectedPattern = null;
		for (DbDataObject patternObj : patterns.getItems()) {
			boolean isDefault = Boolean.TRUE.equals(patternObj.getVal(CC.IS_DEFAULT));
			if (!isDefault) {
				String condField = patternObj.getVal(CC.COND_FIELD) != null
						? patternObj.getVal(CC.COND_FIELD).toString()
						: null;
				String condOperator = patternObj.getVal(CC.COND_OPERATOR) != null
						? patternObj.getVal(CC.COND_OPERATOR).toString()
						: null;
				String condValue = patternObj.getVal(CC.COND_VALUE) != null
						? patternObj.getVal(CC.COND_VALUE).toString()
						: null;
				if (condField != null && condOperator != null && condValue != null) {
					Object rowValue = row.getVal(condField);
					if (rowValue != null) {
						boolean matches = false;
						if (DbCompareOperand.EQUAL.toString().equalsIgnoreCase(condOperator)) {
							matches = rowValue.toString().equals(condValue);
						} else if ("IN".equalsIgnoreCase(condOperator)) {
							matches = Arrays.asList(condValue.split(",")).contains(rowValue.toString());
						} else if (DbCompareOperand.LIKE.toString().equalsIgnoreCase(condOperator)) {
							matches = rowValue.toString().contains(condValue);
						} else {
							throw new SvException("Unsupported operator: " + condOperator, null);
						}
						if (matches) {
							selectedPattern = patternObj;
							break;
						}
					}
				}
			}
		}
		if (selectedPattern == null) {
			for (DbDataObject patternObj : patterns.getItems()) {
				boolean isDefault = Boolean.TRUE.equals(patternObj.getVal(CC.IS_DEFAULT));
				if (isDefault) {
					selectedPattern = patternObj;
					break;
				}
			}
		}
		if (selectedPattern == null) {
			throw new SeqPatternError(
					"No default pattern found for table " + confTable + " and dest field " + destField);
		}
		String seqPattern = selectedPattern.getVal(CC.SEQ_PATTERN).toString();
		seqPattern = seqPattern.replace("{currYear}", String.valueOf(LocalDate.now().getYear()));
		Pattern placeholderPattern = Pattern.compile("\\{([A-Z_]+)\\}");
		Matcher matcher = placeholderPattern.matcher(seqPattern);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			String placeholder = matcher.group(1);
			if ("SvSeq".equals(placeholder)) {
				matcher.appendReplacement(sb, "{SvSeq}");
				continue;
			}
			Object value = row.getVal(placeholder);
			if (value == null) {
				throw new SeqPatternError("Missing column '" + placeholder + "' in row for generating ID");
			}
			matcher.appendReplacement(sb, value.toString());
		}
		matcher.appendTail(sb);
		seqPattern = sb.toString();
		String sequenceKey = seqPattern.replace("{SvSeq}", "");
		Long nextSeq = SvSequence.getSeqNextVal(sequenceKey, svr);
		String generatedId = seqPattern.replace("{SvSeq}", String.valueOf(nextSeq));
		return generatedId;
	}
}
