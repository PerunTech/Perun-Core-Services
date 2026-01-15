package com.prtech.models;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.prtech.models.ModelAnnotations.FixedLength;
import com.prtech.models.ModelAnnotations.NonNegative;
import com.prtech.perun.services.ws.WsReactElements;
import com.prtech.svarog.I18n;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvLink;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvWorkflow;
import com.prtech.svarog.SvWriter;
import com.prtech.svarog.svCONST;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;
import com.prtech.svarog_common.DbQueryObject;
import com.prtech.svarog_common.DbSearchCriterion;
import com.prtech.svarog_common.DbSearchCriterion.DbCompareOperand;
import com.prtech.svarog_common.DbSearchExpression;

/**
 * Abstract base class for all data models (tables) in the application.
 */
public abstract class BaseObjectModel {
	static final Logger log4j = LogManager.getLogger(BaseObjectModel.class.getName());

	public static final Integer ROW_LIMIT = 100;
	public static final int COMMIT_COUNT = 100;

	protected Long pkid = 0L;
	protected Long objectId = 0L;
	protected DateTime dtInsert;
	protected DateTime dtDelete;
	protected Long parentId = 0L;
	protected Long objectType = 0L;
	protected String status = svCONST.STATUS_VALID;
	protected Long userId = 0L;
	/**
	 * Should we skip on save validations check hooks
	 */
	protected Boolean skipCheck;

	/**
	 * Map that holds the label code for the fields in the table
	 */
	protected Map<String, String> fieldsLabels;
	/**
	 * List of not null fields in the table
	 */
	protected List<String> mandatoryFields;

	/**
	 * Map of all fields in the table
	 */
	protected Map<String, DbDataObject> tableFields;

	/**
	 * Constructs a new BaseObjectModel instance. Initializes field labels map,
	 * mandatory fields list, tableFields map, and sets the object type.
	 */
	public BaseObjectModel() {
		fieldsLabels = new HashMap<String, String>();
		mandatoryFields = new ArrayList<>();
		tableFields = new HashMap<String, DbDataObject>();
		try {
			this.objectType = SvReader.getTypeIdByName(getTableName());
			initFields();
		} catch (SvException e) {
			log4j.error("An error occured while initializing BaseObjectModel for {}: {}", getTableName(),
					e.getMessage(), e);
		}
	}

	/**
	 * @return The primary key value
	 */
	public Long getPkid() {
		return pkid;
	}

	/**
	 * @return The object identifier value
	 */
	public Long getObjectId() {
		return objectId;
	}

	/**
	 * @return The creation DateTime
	 */
	public DateTime getDtInsert() {
		return dtInsert;
	}

	/**
	 * @return The deletion DateTime
	 */
	public DateTime getDtDelete() {
		return dtDelete;
	}

	/**
	 * @return The parent object's ID
	 */
	public Long getParentId() {
		return parentId;
	}

	/**
	 * @return The type identifier
	 */
	public Long getObjectType() {
		return objectType;
	}

	/**
	 * @return The status value
	 */
	public String getStatus() {
		return status;
	}

	/**
	 * @return The user ID
	 */
	public Long getUserId() {
		return userId;
	}

	/**
	 * @param pkid The primary key value
	 */
	public void setPkid(Long pkid) {
		this.pkid = pkid;
	}

	/**
	 * @param objectId The object identifier value
	 */
	public void setObjectId(Long objectId) {
		this.objectId = objectId;
	}

	/**
	 * @param dtInsert The creation DateTime
	 */
	public void setDtInsert(DateTime dtInsert) {
		this.dtInsert = dtInsert;
	}

	/**
	 * @param dtDelete The deletion DateTime
	 */
	public void setDtDelete(DateTime dtDelete) {
		this.dtDelete = dtDelete;
	}

	/**
	 * @param parentId The parent object's ID
	 */
	public void setParentId(Long parentId) {
		this.parentId = parentId;
	}

	/**
	 * @param objectType The type identifier
	 */
	public void setObjectType(Long objectType) {
		this.objectType = objectType;
	}

	/**
	 * @param status The status value (e.g. VALID)
	 */
	public void setStatus(String status) {
		this.status = status;
	}

	/**
	 * @param userId The user ID
	 */
	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public Boolean getSkipCheck() {
		return skipCheck;
	}

	public void setSkipCheck(Boolean skipCheck) {
		this.skipCheck = skipCheck;
	}

	/**
	 * Abstract method to set a field value by name
	 * 
	 * @param field The name of the field to set
	 * @param value The value to assign to the field
	 * @return The current model instance
	 */
	public abstract BaseObjectModel setValue(String field, Object value);

	/**
	 * Abstract method to get a field value by name
	 * 
	 * @param field The name of the field to retrieve
	 * @return The current value of the specified field
	 */
	public abstract Object getValue(String field);

	/**
	 * Abstract method to get the name of the table for this model
	 * 
	 * @return Name of the table
	 */
	public abstract String getTableName();

	public DbDataObject setValues(DbDataObject obj) {
		obj.setPkid(this.pkid);
		obj.setStatus(this.status);
		obj.setDtInsert(this.dtInsert);
		obj.setDtDelete(this.dtDelete);
		obj.setObjectId(this.objectId);
		obj.setObjectType(this.objectType);
		obj.setUserId(this.userId);
		obj.setParentId(this.parentId);
		obj.setVal(CC.SKIP_CHECK, this.skipCheck);
		return obj;
	}

	public DbDataObject setValues(DbDataObject obj, DbDataObject otherDbObj) {
		obj.setPkid(otherDbObj.getPkid());
		obj.setStatus(otherDbObj.getStatus());
		obj.setDtInsert(otherDbObj.getDtInsert());
		obj.setDtDelete(otherDbObj.getDtDelete());
		obj.setObjectId(otherDbObj.getObjectId());
		obj.setObjectType(otherDbObj.getObjectType());
		obj.setUserId(otherDbObj.getUserId());
		obj.setParentId(otherDbObj.getParentId());
		return obj;
	};

	public DbDataObject getDbObj() {
		DbDataObject obj = new DbDataObject();
		this.setValues(obj);
		return obj;
	}

	/**
	 * Get a list of all mandatory fields for the model
	 * 
	 * @return List of mandatory fields for this model
	 */
	public List<String> getMandatoryFields() {
		return mandatoryFields;
	}

	/**
	 * Get the map of all fields in the table
	 * 
	 * @return Map of all fields for this model
	 */
	public Map<String, DbDataObject> getTableFields() {
		return tableFields;
	}

	/**
	 * Check if the mandatory fields are not null or empty
	 * 
	 * @param localeId locale identifier of the user to return the error(s) in the
	 *                 proper language
	 * @return List of errors if any, otherwise empty list
	 */
	protected List<String> validateMandatoryFields(String localeId) {
		List<String> errors = new ArrayList<>();
		for (String field : getMandatoryFields()) {
			if (getValue(field) == null) {
				errors.add(I18n.getText(localeId, "perun.error.missingMandatoryField") + " "
						+ I18n.getText(localeId, this.fieldsLabels.get(field)));
			} else {
				validateNotEmpty(field, errors, localeId);
			}
		}
		return errors;
	}

	/**
	 * Method that retrieves a list of field names from the specified table that are
	 * associated with predefined code list
	 * 
	 * @param tableName
	 * @param svr
	 * @return
	 */
	public List<String> getFieldsWithCodeList(SvReader svr) {
		List<String> fieldsWithCodeList = new ArrayList<>();
		try {
			DbDataArray fieldsDba = svr.getObjectsByParentId(SvReader.getTypeIdByName(getTableName()),
					svCONST.OBJECT_TYPE_FIELD, null);
			for (DbDataObject field : fieldsDba.getItems()) {
				if (field.getVal(CC.CODE_LIST_ID) != null) {
					fieldsWithCodeList.add(field.getVal(CC.FIELD_NAME).toString());
				}
			}
		} catch (Exception e) {
			log4j.error("Error retrieving fields for {}: {}", getTableName(), e.getMessage(), e);
		}
		return fieldsWithCodeList;
	}

	/**
	 * Populates this instance with values from a DbDataObject.
	 * 
	 * @param obj The DbDataObject containing the values to copy to this model
	 */
	public void from(DbDataObject obj) {
		this.pkid = obj.getPkid();
		this.status = obj.getStatus();
		this.dtInsert = obj.getDtInsert();
		this.dtDelete = obj.getDtDelete();
		this.objectId = obj.getObjectId();
		this.objectType = obj.getObjectType();
		this.userId = obj.getUserId();
		this.parentId = obj.getParentId();
	}

	/**
	 * Loads this instance with data from the database using the specified object
	 * ID.
	 * 
	 * @param objectId The ID of the object to load from the database
	 * @param svr      SvReader instance
	 * @return true if the object was found and loaded successfully, false otherwise
	 * @throws SvException if an error occurs during database operations
	 */
	public boolean from(Long objectId, SvReader svr) throws SvException {
		return from(objectId, true, svr);
	}

	/**
	 * Loads this instance with data from the database using the specified object
	 * ID.
	 * 
	 * @param objectId The ID of the object to load from the database
	 * @param svr      SvReader instance
	 * @param useCache True if you want to use the cache to retrieve the object
	 * @return true if the object was found and loaded successfully, false otherwise
	 * @throws SvException if an error occurs during database operations
	 */
	public boolean from(Long objectId, Boolean useCache, SvReader svr) throws SvException {
		DbDataObject obj;
		if (useCache) {
			obj = svr.getObjectById(objectId, SvReader.getTypeIdByName(getTableName()), null);
		} else {
			obj = svr.getObjectById(objectId, SvReader.getTypeIdByName(getTableName()), new DateTime());
		}
		if (obj != null) {
			this.from(obj);
			return true;
		}
		return false;
	}

	/**
	 * Changes the status of a database object.
	 * 
	 * @param obj        The database object whose status should be changed
	 * @param sessionId  The session ID for the workflow operation
	 * @param newStatus  The new status to assign to the object
	 * @param autoCommit Whether to automatically commit
	 * @return true if the status change was successful, false otherwise
	 */
	protected Boolean changeStatus(DbDataObject obj, String sessionId, String newStatus, Boolean autoCommit) {
		Boolean result = false;
		try (SvWorkflow svw = new SvWorkflow(sessionId)) {
			svw.moveObject(obj, newStatus, autoCommit);
			result = true;
		} catch (SvException e) {
			log4j.error("Error while changing object status for {}: {}", obj.getObjectId().toString(), e.getMessage(),
					e);
		}
		return result;
	}

	/**
	 * Changes the status of this object with auto-commit enabled.
	 * 
	 * @param newStatus The new status to assign
	 * @param svr       SvReader instance
	 * @param svw       SvWriter instance
	 * @param sww       SvWorkflow instance
	 * @return List of validation errors, empty if successful
	 * @throws SvException if an error occurs during the status change
	 */
	public List<String> changeStatus(String newStatus, SvReader svr, SvWriter svw, SvWorkflow sww) throws SvException {
		return changeStatus(newStatus, true, svr, svw, sww);
	}

	/**
	 * Changes the status of this object.
	 * 
	 * @param newStatus  The new status to assign
	 * @param autoCommit Whether to automatically commit
	 * @param svr        SvReader instance
	 * @param svw        SvWriter instance
	 * @param sww        SvWorkflow instance
	 * @return List of validation errors, empty if successful
	 * @throws SvException if an error occurs during the status change
	 */
	public List<String> changeStatus(String newStatus, Boolean autoCommit, SvReader svr, SvWriter svw, SvWorkflow sww)
			throws SvException {
		List<String> errorsList = new ArrayList<String>();

		if (this.getStatus().equals(newStatus)) {
			errorsList.add(I18n.getText(svr.getUserLocaleId(svr.getInstanceUser()), "error.transition_same_status"));
			return errorsList;
		}

		DbDataObject dbo = getDbObj();
		errorsList = onStatusChange(newStatus, svr, svw);
		if (errorsList.isEmpty()) {
			sww.moveObject(dbo, newStatus, autoCommit);
		}

		return errorsList;
	}

	/**
	 * Changes the status of multiple objects identified by their IDs.
	 * 
	 * @param newStatus The new status to assign to all objects
	 * @param objectIds Array of object IDs whose status should be changed
	 * @param svr       SvReader instance
	 * @param svw       SvWriter instance
	 * @param sww       SvWorkflow instance
	 * @return List of validation errors from all status change operations
	 * @throws SvException if an error occurs during any status change
	 */
	public List<String> changeStatus(String newStatus, String[] objectIds, SvReader svr, SvWriter svw, SvWorkflow sww)
			throws SvException {
		List<String> errors = new ArrayList<String>(0);
		for (String objId : objectIds) {
			this.from(Long.valueOf(objId), svr);
			errors.addAll(this.changeStatus(newStatus, false, svr, svw, sww));
		}

		return errors;
	}

	/**
	 * Retrieves a specific version of an object from the database.
	 * 
	 * @param objectId  The ID of the object to retrieve
	 * @param tableName The name of the table containing the object
	 * @param useCache  Whether to use cached version of the object in Svarog or
	 *                  fetch fresh data
	 * @param svr       SvReader instance
	 * @return The DbDataObject representing the requested object version
	 * @throws SvException if an error occurs during database operations
	 */
	public static DbDataObject getObjectVersion(Long objectId, String tableName, Boolean useCache, SvReader svr)
			throws SvException {
		DbDataObject obj = null;
		if (useCache) {
			obj = svr.getObjectById(objectId, SvReader.getTypeIdByName(tableName), null);
		} else {
			obj = svr.getObjectById(objectId, SvReader.getTypeIdByName(tableName), new DateTime());
		}
		return obj;
	}

	/**
	 * Generates a JSON schema for the table associated with this model.
	 * 
	 * @param locale The locale for internationalization
	 * @param svr    SvReader instance
	 * @return JSON schema as a string
	 * @throws SvException if an error occurs during schema generation
	 */
	public JsonObject getTableJsonSchema(String locale, SvReader svr) throws SvException {
		WsReactElements ws = new WsReactElements();
		Response response = ws.getTableJSONSchema(svr.getSessionId(), getTableName(), null);
		String responseStr = response.getEntity().toString();
		JsonObject responseJson = new Gson().fromJson(responseStr, JsonObject.class);
		return responseJson;
	}

	/**
	 * Generates a UI schema for the table associated with this model.
	 * 
	 * @param locale The locale for internationalization
	 * @param svr    SvReader instance
	 * @return UI schema as a string
	 * @throws SvException if an error occurs during schema generation
	 */
	public JsonObject getTableUiSchema(String locale, SvReader svr) throws SvException {
		WsReactElements ws = new WsReactElements();
		Response response = ws.getTableUISchema(svr.getSessionId(), getTableName(), null);
		String responseStr = response.getEntity().toString();
		JsonObject responseJson = new Gson().fromJson(responseStr, JsonObject.class);
		return responseJson;
	}

	/**
	 * Return a list of all statuses that this object can have. Override in child
	 * classes.
	 * 
	 * @return List of all valid statuses for this object
	 */
	public List<String> getObjectStatusList() {
		return Arrays.asList(CC.VALID);
	}

	/**
	 * Method called before a status change occurs. Implementations should perform
	 * validation and return any errors that prevent the status change.
	 * 
	 * @param newStatus the new status that we want to change the object to
	 * @param svr       SvReader instance
	 * @param svw       SvWriter instance
	 * @return List of errors if any, otherwise empty list
	 * @throws SvException if an error occurs during validation
	 */
	public abstract List<String> onStatusChange(String newStatus, SvReader svr, SvWriter svw) throws SvException;

	/**
	 * Returns detailed information about this model instance as key-value pairs.
	 * The Map contains fields and values.
	 * 
	 * @param svr      SvReader instance for database operations
	 * @param localeId The locale for internationalization
	 * @return LinkedHashMap containing detailed information about the object
	 * @throws SvException if an error occurs during data retrieval
	 */
	public abstract LinkedHashMap<String, String> getDetails(String localeId, SvReader svr) throws SvException;

	/**
	 * Returns short information about this model instance as key-value pairs. The
	 * Map contains fields and values.
	 * 
	 * @param svr      SvReader instance for database operations
	 * @param localeId The locale for internationalization
	 * @return LinkedHashMap containing detailed information about the object
	 * @throws SvException if an error occurs during data retrieval
	 */
	public abstract LinkedHashMap<String, String> getSummary(String localeId, SvReader svr) throws SvException;

	/**
	 * Saves this object to the database.
	 * 
	 * @param parentId The ID of the parent object, if any
	 * @param objectId The ID of this object (for updates) or 0L (for new objects)
	 * @param localeId The locale for error messages
	 * @param svr      SvReader instance
	 * @param svw      SvWriter instance
	 * @return List of validation errors, empty if successful
	 * @throws SvException if an error occurs during the save operation
	 */
	public abstract List<String> saveObject(Long parentId, Long objectId, String localeId, SvReader svr, SvWriter svw)
			throws SvException;

	public abstract List<String> invalidateLink(String linkName, Long linkedObjectId, SvReader svr, SvWriter svw)
			throws SvException;

	public abstract List<String> saveAndLink(Long parentId, Long objectId, String linkType, Long objectToBeLinkedTo,
			String locale, SvReader svr, SvWriter svw, SvLink svl) throws SvException;

	public Map<String, DbDataObject> getLinkTypes() throws SvException {
		return new HashMap<>();
	}

	/**
	 * Validates that a field is not null or empty and adds an error message if it
	 * is.
	 * 
	 * @param fieldName The name of the field to validate
	 * @param errors    The list to add error messages to
	 * @param localeId  The locale for error message internationalization
	 */
	public void validateNotEmpty(String fieldName, List<String> errors, String localeId) {
		if (Objects.isNull(this.getValue(fieldName)) || this.getValue(fieldName).toString().isBlank()) {
			errors.add(I18n.getText(localeId, "perun.error.blankField") + " "
					+ I18n.getText(localeId, this.fieldsLabels.get(fieldName)));
		}
	}

	/**
	 * Retrieves objects that are children of this object and have a specific
	 * status.
	 * 
	 * @param status     The status to filter by
	 * @param objectType The type of objects to retrieve
	 * @param svr        SvReader instance
	 * @return DbDataArray containing the matching objects
	 * @throws SvException if an error occurs during the database query
	 */
	public DbDataArray getObjectsByParentAndStatus(String status, Long objectType, SvReader svr) throws SvException {
		DbSearchCriterion dbc1 = new DbSearchCriterion(CC.PARENT_ID, DbCompareOperand.EQUAL, this.objectId);
		DbSearchCriterion dbc2 = new DbSearchCriterion(CC.STATUS, DbCompareOperand.EQUAL, status);
		DbSearchExpression dbse = new DbSearchExpression().addDbSearchItem(dbc1).addDbSearchItem(dbc2);
		DbDataArray dbArr = svr.getObjects(dbse, objectType, null, null, null);
		return dbArr;
	}

	/**
	 * Counts the number of child objects with a specific status.
	 * 
	 * @param status     The status to filter by
	 * @param objectType The type of objects to count
	 * @param svr        SvReader instance
	 * @return The count of matching objects
	 * @throws SvException if an error occurs during the database query
	 */
	public Integer getObjectCountByParentAndStatus(String status, Long objectType, SvReader svr) throws SvException {
		Integer result = 0;
		DbDataArray dbArr = getObjectsByParentAndStatus(status, objectType, svr);
		if (dbArr != null) {
			result = dbArr.size();
		}
		return result;
	}

	/**
	 * Retrieves objects matching specific search criteria.
	 * 
	 * @param field    The field name to search on
	 * @param operand  The comparison operator to use
	 * @param value    The value to compare against
	 * @param useCache Whether to use cached objects from Svarog
	 * @param svr      SvReader instance
	 * @return DbDataArray containing the matching objects
	 * @throws SvException if an error occurs during the database query
	 */
	public DbDataArray getObjectsByCriteria(String field, DbCompareOperand operand, Object value, Boolean useCache,
			SvReader svr) throws SvException {
		DbDataArray dbArr = null;
		if (value != null) {
			dbArr = svr.getObjects(new DbSearchCriterion(field, operand, value), objectType,
					useCache ? null : new DateTime(), null, null);
		}
		return dbArr == null ? new DbDataArray() : dbArr;
	}

	/**
	 * Retrieves objects matching specific search criteria using cache by default.
	 * 
	 * @param field   The field name to search on
	 * @param operand The comparison operator to use
	 * @param value   The value to compare against
	 * @param svr     SvReader instance
	 * @return DbDataArray containing the matching objects
	 * @throws SvException if an error occurs during the database query
	 */
	public DbDataArray getObjectsByCriteria(String field, DbCompareOperand operand, Object value, SvReader svr)
			throws SvException {
		return this.getObjectsByCriteria(field, operand, value, true, svr);
	}

	/**
	 * Retrieves the first object matching specific search criteria.
	 * 
	 * @param field   The field name to search on
	 * @param operand The comparison operator to use
	 * @param value   The value to compare against
	 * @param svr     SvReader instance
	 * @return The first matching DbDataObject, or null if none found
	 * @throws SvException if an error occurs during the database query
	 */
	public DbDataObject getObjectByCriteria(String field, DbCompareOperand operand, Object value, SvReader svr)
			throws SvException {
		DbDataObject result = null;
		DbDataArray dbArr = this.getObjectsByCriteria(field, operand, value, svr);
		if (!dbArr.isEmpty())
			result = dbArr.get(0);
		return result;
	}

	/**
	 * Method for initializing the fieldsLabels map and the mandatoryFields list
	 * 
	 * @throws SvException if an error occurs during field initialization
	 */
	public void initFields() throws SvException {
		String fieldName;
		String labelCode;
		DbDataArray dbArr = SvReader.getFields(SvReader.getTypeIdByName(getTableName()));
		for (DbDataObject dbo : dbArr.getItems()) {
			fieldName = dbo.getAsString(CC.FIELD_NAME);
			labelCode = dbo.getAsString(CC.LABEL_CODE);
			if (fieldName != null) {
				if (!fieldName.equals(CC.PKID)) {
					tableFields.put(fieldName, dbo);
				}
				fieldsLabels.put(fieldName, labelCode);
			}
			if (!fieldName.equals(CC.PKID) && dbo.getVal(CC.IS_NULL) != null && !dbo.getAsBoolean(CC.IS_NULL)) {
				mandatoryFields.add(fieldName);
			}
		}
	}

	/**
	 * Check if the report fields in the object are updated
	 * 
	 * @param oldDbo
	 * @return
	 */
	public Boolean isObjectChanged(DbDataObject oldDbo) {
		Boolean result = false;
		String val;
		String oldVal;
		for (String field : getReportFields()) {
			val = this.getValue(field) == null ? null : this.getValue(field).toString();
			oldVal = oldDbo.getVal(field) == null ? null : oldDbo.getVal(field).toString();
			if ((val != null && oldVal != null && !val.equals(oldVal)) || (val != null && oldVal == null)
					|| (val == null && oldVal != null)) {
				result = true;
				break;
			}
		}
		return result;
	}

	protected static class SearchField {
		private String fieldName;
		private boolean ignoreCase;
		private int minLength;
		private boolean includePercent;

		public SearchField(String fieldName, boolean ignoreCase, int minLength, boolean includePercent) {
			this.fieldName = fieldName;
			this.ignoreCase = ignoreCase;
			this.minLength = minLength;
			this.includePercent = includePercent;
		}

		public String getFieldName() {
			return fieldName;
		}

		public void setFieldName(String fieldName) {
			this.fieldName = fieldName;
		}

		public boolean isIgnoreCase() {
			return ignoreCase;
		}

		public void setIgnoreCase(boolean ignoreCase) {
			this.ignoreCase = ignoreCase;
		}

		public int getMinLength() {
			return minLength;
		}

		public void setMinLength(int minLength) {
			this.minLength = minLength;
		}

		public boolean getIncludePercent() {
			return includePercent;
		}

		public void setIncludePercent(boolean includePercent) {
			this.includePercent = includePercent;
		}
	}

	/**
	 * Get list of all fields you can search for. Kept for backward compatibility.
	 * 
	 * @return List of all search fields
	 */
	public List<String> getSearchFields() {
		return new ArrayList<String>();
	}

	/**
	 * Get list of all fields you can search for
	 * 
	 * @return List of all search fields
	 */
	public List<SearchField> getSearchableFields() {
		return new ArrayList<SearchField>();
	}

	/**
	 * Searches for objects in the database based on the provided search parameters.
	 * 
	 * If the search only contains these combinations of field/s the method will
	 * raise an exception: PARENT_ID, STATUS, PARENT_ID + STATUS. You should use the
	 * existing methods getObjectsByParentId and getObjectsByLinkedId for such
	 * cases.
	 * 
	 * @param searchParams JSON object containing search criteria and optional
	 *                     pagination/sorting parameters
	 * @param svr          SvReader instance used for database operations
	 * @return DbDataArray containing objects that match the search criteria, or
	 *         empty array if no criteria provided
	 * @throws SvException
	 */
	public DbDataArray searchObjects(JsonObject searchParams, SvReader svr) throws SvException {
		DbDataArray result = new DbDataArray();
		DbSearchExpression dbse = new DbSearchExpression();
		Boolean hasCrit = false;
		Set<String> searchFieldsPresent = new HashSet<>();

		for (SearchField field : getSearchableFields()) {
			boolean added = addSearchCriterion(searchParams, field, dbse) || hasCrit;
			if (added) {
				searchFieldsPresent.add(field.getFieldName().toUpperCase());
			}
			hasCrit = added || hasCrit;
		}

		if (hasCrit) {
			validateSearchCombination(searchFieldsPresent, svr);
			Integer rowLimit = null;
			Integer offset = null;
			String sortByField = CC.PKID;
			String sortOrder = "DESC";
			try {
				if (searchParams.has(CC.ROW_LIMIT)) {
					rowLimit = Integer.valueOf(searchParams.get(CC.ROW_LIMIT).getAsInt());
				}
			} catch (NumberFormatException e) {
			}
			if (rowLimit == null) {
				rowLimit = ROW_LIMIT;
			}
			if (searchParams.has(CC.SORT_FIELD)) {
				sortByField = searchParams.get(CC.SORT_FIELD).getAsString();
			}
			if (searchParams.has(CC.SORT_ORDER)) {
				sortOrder = searchParams.get(CC.SORT_ORDER).getAsString();
			}

			DbQueryObject query = new DbQueryObject(SvReader.getDbtByName(getTableName()), dbse, null, null);
			ArrayList<String> orderBy = new ArrayList<String>();
			orderBy.add(sortByField + " " + sortOrder);
			query.setOrderByFields(orderBy);
			result = svr.getObjects(query, rowLimit, offset);
		}
		return result;
	}

	/**
	 * Validates that search fields does not contain these combinations:<br>
	 * - PARENT_ID only<br>
	 * - STATUS only<br>
	 * - PARENT_ID + STATUS<br>
	 * 
	 * @param searchFields Set of search field names that are present
	 * @throws SvException
	 */
	private void validateSearchCombination(Set<String> searchFields, SvReader svr) throws SvException {
		// Define allowed combinations
		Set<String> parentIdOnly = new HashSet<>(Arrays.asList("PARENT_ID"));
		Set<String> statusOnly = new HashSet<>(Arrays.asList("STATUS"));
		Set<String> parentIdAndStatus = new HashSet<>(Arrays.asList("PARENT_ID", "STATUS"));

		boolean isValid = searchFields.equals(parentIdOnly) || searchFields.equals(statusOnly)
				|| searchFields.equals(parentIdAndStatus);

		if (!isValid) {
			throw new SvException("Invalid search field combination. Invalid combinations are: "
					+ "PARENT_ID, STATUS, or PARENT_ID + STATUS. " + "Provided fields: "
					+ String.join(", ", searchFields), svr.getInstanceUser());
		}
	}

	/**
	 * Get list of all fields included in the report for this model
	 * 
	 * @return List of all fields that will be visible in the report
	 */
	public List<String> getReportFields() {
		return new ArrayList<String>();
	}

	/**
	 * Get fields for specific form contexts
	 * 
	 * @param formContext
	 * @return
	 */
	public List<String> getFormFields(String formContext) {
		return getReportFields();
	}

	/**
	 * @return JsonObject representation for this object
	 */
	public JsonObject getJsonRepresentation() {
		DbDataObject dbo = this.getDbObj();
		return dbo.toSimpleJson();
	}

	/**
	 * Checks if fields in the model are annotated and validates them
	 * 
	 * @param localeId
	 * @return
	 */
	public List<String> checkValidData(String localeId) {
		List<String> errors = new ArrayList<String>();

		Class<?> clazz = this.getClass();
		Field[] fields = clazz.getDeclaredFields();

		for (Field field : fields) {
			field.setAccessible(true);

			try {
				Object value = field.get(this);
				if (field.isAnnotationPresent(NonNegative.class)) {
					if (value != null && value instanceof Number) {
						Number numValue = (Number) value;
						if (numValue.doubleValue() < 0) {
							errors.add(I18n.getText(localeId, "perun.error.fieldNegativeValue") + ": " + I18n
									.getText(localeId, fieldsLabels.get(camelCaseToConstantCase(field.getName()))));
						}
					}
				}

				if (field.isAnnotationPresent(FixedLength.class)) {
					FixedLength annotation = field.getAnnotation(FixedLength.class);
					if (value != null && value instanceof String) {
						String strVal = (String) value;
						if (strVal.length() != annotation.length()) {
							errors.add(I18n.getText(localeId, "perun.error.fieldInvalidLength")
									.replace("{field_name}",
											I18n.getText(localeId,
													fieldsLabels.get(camelCaseToConstantCase(field.getName()))))
									.replace("{length}", Integer.toString(annotation.length())));
						}
					}
				}
			} catch (Exception e) {
				log4j.debug("Error in checkValidData - BaseObjectModel", e);
			}
		}

		return errors;
	}

	/**
	 * Convert a string for camelCase to CONSTANT_CASE (screaming case)
	 * 
	 * @param ccString
	 * @return
	 */
	private Object camelCaseToConstantCase(String ccString) {
		if (ccString == null || ccString.isEmpty()) {
			return ccString;
		}

		StringBuilder result = new StringBuilder();
		result.append(Character.toUpperCase(ccString.charAt(0)));

		for (int i = 1; i < ccString.length(); i++) {
			char c = ccString.charAt(i);
			if (Character.isUpperCase(c)) {
				result.append("_").append(c);
			} else {
				result.append(Character.toUpperCase(c));
			}
		}
		return result.toString();
	}

	private Boolean addSearchCriterion(JsonObject searchParams, SearchField field, DbSearchExpression dbse)
			throws SvException {
		Object value = null;
		DbCompareOperand operand = null;
		JsonElement element = searchParams.has(field.getFieldName())
				&& !searchParams.get(field.getFieldName()).isJsonNull() ? searchParams.get(field.getFieldName()) : null;

		if (element != null && !element.isJsonNull()) {
			JsonPrimitive primitive = element.getAsJsonPrimitive();
			if (primitive.isBoolean()) {
				operand = DbCompareOperand.EQUAL;
				value = primitive.getAsBoolean();
			} else if (primitive.isNumber()) {
				operand = DbCompareOperand.EQUAL;
				value = primitive.getAsLong();
			} else if (primitive.isString()) {
				if (field.isIgnoreCase()) {
					operand = DbCompareOperand.ILIKE;
				} else {
					operand = DbCompareOperand.LIKE;
				}
				value = primitive.getAsString();
				if (field.getIncludePercent()) {
					value = CC.PERCENT_OPERATOR + primitive.getAsString() + CC.PERCENT_OPERATOR;
				}
			}
		}

		if (value != null && operand != null) {
			DbSearchCriterion dbc = new DbSearchCriterion(field.getFieldName(), operand, value);
			dbse.addDbSearchItem(dbc);
			return true;
		}
		return false;
	}
}
