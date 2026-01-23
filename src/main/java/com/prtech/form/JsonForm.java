package com.prtech.form;

import static java.util.Map.entry;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.prtech.models.BaseObjectModel;
import com.prtech.models.ModelFactory;
import com.prtech.perun.services.ws.Rc;
import com.prtech.svarog.I18n;
import com.prtech.svarog.SvException;
import com.prtech.svarog.SvReader;
import com.prtech.svarog.SvWriter;
import com.prtech.svarog_common.DbDataArray;
import com.prtech.svarog_common.DbDataObject;

/**
 * Abstract base class for building JSON Schema forms with header, central, and
 * footer sections. This class provides the framework for creating dynamic forms
 * with configurable sections, visibility controls, and internationalization
 * support.
 */
public abstract class JsonForm implements Authorizable {
	protected static final Logger log4j = LogManager.getLogger(JsonForm.class.getName());

	protected String formName;
	protected Map<String, String> formParams;
	protected String title;
	protected String description;
	protected String localeId;
	protected ModelFactory factory;

	/**
	 * Constructs a new JsonForm with the specified parameters.
	 *
	 * @param formName    the unique name identifier for this form
	 * @param formParams  map of form parameters for configuration, usually this
	 *                    will come from the front-end
	 * @param title       the title of the form - label
	 * @param description the description of the form - label
	 * @param localeId    user locale identifier
	 */
	public JsonForm(String formName, Map<String, String> formParams, String title, String description, String localeId,
			ModelFactory factory) {
		super();
		this.formName = formName;
		this.formParams = formParams;
		this.title = title;
		this.description = description;
		this.localeId = localeId;
		this.factory = factory;
	}

	/**
	 * Returns the header section of the form.
	 *
	 * @return the header FormSection, or null if no header is needed
	 */
	public abstract FormSection getHeaderSection();

	/**
	 * Returns the central sections of the form.
	 *
	 * @return a list of FormSection objects representing the main content areas
	 */
	public abstract List<FormSection> getCentralSection();

	/**
	 * Returns the footer section of the form.
	 *
	 * @return the footer FormSection, or null if no footer is needed
	 */
	public abstract FormSection getFooterSection();

	/**
	 * Builds the JSON Schema representation of the form based on user permissions.
	 * Iterates through all sections (header, central, footer) and includes only
	 * those visible to the current user.
	 *
	 * @param svr SvReader instance for database operations
	 * @return a JsonObject containing the complete JSON Schema for the form
	 * @throws SvException        if an error occurs during schema generation
	 * @throws AuthorizationError if user does not have access to this form
	 */
	public JsonObject buildSchema(SvReader svr) throws SvException, AuthorizationError {
		if (!AuthorizationHelper.canRead(this, svr)) {
			throw new AuthorizationError("User does not have read access for the form: " + this.formName);
		}

		JsonObject schema = new JsonObject();
		JsonArray allOfArr = new JsonArray();
		JsonArray sectionAllOfArr;
		FormBuilder builder = new FormBuilder(factory);

		if (getHeaderSection() != null
				&& AuthorizationHelper.checkUserGroupAnyMembership(svr, getHeaderSection().getVisibleFor())) {
			addSectionIfVisible(schema, getHeaderSection(), svr);
			sectionAllOfArr = builder.buildSchemaDependencies(getHeaderSection(), schema, localeId, svr);
			if (sectionAllOfArr != null) {
				allOfArr.addAll(sectionAllOfArr);
			}
		}

		for (FormSection section : getCentralSection()) {
			if (section != null && AuthorizationHelper.checkUserGroupAnyMembership(svr, section.getVisibleFor())) {
				addSectionIfVisible(schema, section, svr);
				sectionAllOfArr = builder.buildSchemaDependencies(section, schema, localeId, svr);
				if (sectionAllOfArr != null) {
					allOfArr.addAll(sectionAllOfArr);
				}
			}
		}

		if (getFooterSection() != null
				&& AuthorizationHelper.checkUserGroupAnyMembership(svr, getFooterSection().getVisibleFor())) {
			addSectionIfVisible(schema, getFooterSection(), svr);
			sectionAllOfArr = builder.buildSchemaDependencies(getFooterSection(), schema, localeId, svr);
			if (sectionAllOfArr != null) {
				allOfArr.addAll(sectionAllOfArr);
			}
		}

		if (!allOfArr.isEmpty()) {
			schema.add("allOf", allOfArr);
		}
		this.setTitleAndDescription(schema);

		return schema;
	}

	/**
	 * Adds a section to the schema if it is visible to the current user. Checks
	 * user group membership and merges the section schema into the main schema.
	 *
	 * @param schema  the main schema object to merge into
	 * @param section the section to add
	 * @param svr     SvReader instance for database operations
	 * @throws SvException if an error occurs during visibility check or schema
	 *                     generation
	 */
	private void addSectionIfVisible(JsonObject schema, FormSection section, SvReader svr) throws SvException {
		if (section != null && AuthorizationHelper.checkUserGroupAnyMembership(svr, section.getVisibleFor())) {
			try {
				JsonObject sectionSchema = new FormBuilder(factory).prepareSectionSchema(section, this.localeId, svr);
				Utils.deepMerge(sectionSchema, schema);
			} catch (Exception e) {
				log4j.error(String.format("Failed to generate section schema for %s form, table name (%s)",
						this.formName, section.getTableName()), e);
			}
		}
	}

	/**
	 * Builds the UI Schema representation of the form based on user permissions. UI
	 * Schema controls the visual presentation and behavior of form fields.
	 *
	 * @param svr SvReader instance for database operations
	 * @return a JsonObject containing the UI Schema for the form
	 * @throws SvException        if an error occurs during UI schema generation
	 * @throws AuthorizationError if user does not have access to this form
	 */
	public JsonObject buildUiSchema(SvReader svr) throws SvException, AuthorizationError {
		if (!AuthorizationHelper.canRead(this, svr)) {
			throw new AuthorizationError("User does not have read access for the form: " + this.formName);
		}

		JsonObject uiSchema = new JsonObject();

		addSectionIfVisibleUi(uiSchema, getHeaderSection(), svr);

		for (FormSection section : getCentralSection()) {
			addSectionIfVisibleUi(uiSchema, section, svr);
		}

		addSectionIfVisibleUi(uiSchema, getFooterSection(), svr);

		return uiSchema;
	}

	/**
	 * Adds a section's UI schema if it is visible to the current user.
	 *
	 * @param schema  the main UI schema object to merge into
	 * @param section the section whose UI schema should be added
	 * @param svr     SvReader instance for database operations
	 * @throws SvException if an error occurs during visibility check or UI schema
	 *                     generation
	 */
	private void addSectionIfVisibleUi(JsonObject schema, FormSection section, SvReader svr) throws SvException {
		if (section != null && AuthorizationHelper.checkUserGroupAnyMembership(svr, section.getVisibleFor())) {
			try {
				JsonObject sectionUiSchema = new FormBuilder(factory).prepareSectionUiSchema(section, this.localeId);
				Utils.deepMerge(sectionUiSchema, schema);
			} catch (Exception e) {
				log4j.error(String.format("Failed to generate section UI schema for %s form, table name (%s)",
						this.formName, section.getTableName()), e);
			}
		}
	}

	/**
	 * Builds the form data object.
	 *
	 * @return a JsonObject containing the default form data
	 * @throws AuthorizationError if user does not have access to this form
	 * @throws SvException        if an error occurs during form data generation
	 */
	public JsonObject buildFormData(SvReader svr) throws SvException, AuthorizationError {
		if (!AuthorizationHelper.canRead(this, svr)) {
			throw new AuthorizationError("User does not have read access for the form: " + this.formName);
		}

		Map<String, List<BaseObjectModel>> loadedObjects = loadObjectsBySaveOrder(svr);
		afterLoadObjects(loadedObjects, svr);
		JsonObject formData = convertObjectsToFormData(loadedObjects, svr);

		return formData;
	}

	/**
	 * Build a map of all objects starting from the first entry in the save order
	 * list
	 * 
	 * @param svr SvReader instance for database operations
	 * @return Map of all loaded objects where the key is the table name
	 * @throws SvException if an error occurs during data retrieving
	 */
	protected Map<String, List<BaseObjectModel>> loadObjectsBySaveOrder(SvReader svr) throws SvException {
		Map<String, List<BaseObjectModel>> loadedObjects = new HashMap<>();
		List<SaveOrderEntry> saveOrder = getSaveOrder();
		Long objectId = 0L;

		if (formParams != null && formParams.containsKey(CC.OBJECT_ID)) {
			objectId = Long.valueOf(formParams.get(CC.OBJECT_ID));
		}

		if (objectId == 0L || saveOrder.isEmpty()) {
			return loadedObjects;
		}

		SaveOrderEntry rootEntry = saveOrder.get(0);
		BaseObjectModel rootObject = factory.createObject(rootEntry.getTableName());
		rootObject.from(objectId, svr);
		loadedObjects.put(rootEntry.getTableName(), Arrays.asList(rootObject));

		for (int i = 1; i < saveOrder.size(); i++) {
			SaveOrderEntry entry = saveOrder.get(i);
			String parentTableName = entry.getParentTableName();

			if (parentTableName == null || loadedObjects.get(parentTableName) == null
					|| !loadedObjects.get(parentTableName).isEmpty()) {
				continue;
			}

			BaseObjectModel parentObject = loadedObjects.get(parentTableName).get(0);
			DbDataArray dbArr = svr.getObjectsByParentId(parentObject.getObjectId(),
					SvReader.getTypeIdByName(entry.getTableName()), null);
			if (dbArr == null || dbArr.isEmpty()) {
				continue;
			}

			List<BaseObjectModel> currList = new ArrayList<>();
			for (DbDataObject childDbo : dbArr.getItems()) {
				BaseObjectModel childObject = factory.createObject(entry.getTableName());
				childObject.from(childDbo);
				currList.add(childObject);
			}

			if (!currList.isEmpty()) {
				loadedObjects.put(entry.getTableName(), currList);
			}
		}

		return loadedObjects;
	}

	/**
	 * Hook for child classes to modify the loaded objects for the form data
	 * 
	 * @param loadedObjects The map of objects which were loaded for this form
	 * @param svr           SvReader instance for database operations
	 * @throws SvException
	 */
	protected abstract void afterLoadObjects(Map<String, List<BaseObjectModel>> loadedObjects, SvReader svr)
			throws SvException;

	/**
	 * Converts loaded objects to form data structure matching the schema. Maps
	 * objects to their corresponding sections and groups.
	 * 
	 * @param loadedObjects map of table name to loaded objects
	 * @param svr           SvReader instance
	 * @return JsonObject in form data format
	 * @throws SvException if error occurs
	 */
	protected JsonObject convertObjectsToFormData(Map<String, List<BaseObjectModel>> loadedObjects, SvReader svr)
			throws SvException {
		JsonObject formData = new JsonObject();

		if (getHeaderSection() != null) {
			FormSection section = getHeaderSection();
			List<BaseObjectModel> objects = loadedObjects.get(section.getTableName());
			if (objects != null && !objects.isEmpty()) {
				JsonObject sectionData = convertSectionToFormData(section, objects.get(0), svr);
				formData = Utils.deepMerge(formData, sectionData);
			}
		}

		for (FormSection section : getCentralSection()) {
			List<BaseObjectModel> objects = loadedObjects.get(section.getTableName());

			if (section.isArray()) {
				JsonArray arrayData = new JsonArray();
				if (objects != null) {
					for (BaseObjectModel obj : objects) {
						JsonObject itemData = convertSectionToFormData(section, obj, svr);
						arrayData.add(itemData);
					}
				}
				formData.add(section.getTableName().toLowerCase(), arrayData);
			} else {
				if (objects != null && !objects.isEmpty()) {
					JsonObject sectionData = convertSectionToFormData(section, objects.get(0), svr);
					formData = Utils.deepMerge(formData, sectionData);
				}
			}
		}

		if (getFooterSection() != null) {
			FormSection section = getFooterSection();
			List<BaseObjectModel> objects = loadedObjects.get(section.getTableName());
			if (objects != null && !objects.isEmpty()) {
				JsonObject sectionData = convertSectionToFormData(section, objects.get(0), svr);
				formData = Utils.deepMerge(formData, sectionData);
			}
		}

		return formData;
	}

	/**
	 * Converts a section's object to form data structure with proper grouping.
	 * 
	 * @param section the form section
	 * @param obj     the object to convert
	 * @return JsonObject with fields organized by group paths
	 */
	protected JsonObject convertSectionToFormData(FormSection section, BaseObjectModel obj, SvReader svr) {
		JsonObject sectionData = new JsonObject();
		Map<String, JsonObject> groupData = new HashMap<>();

		for (String fieldName : section.getFieldNames()) {
			if (obj.getTableFields().containsKey(fieldName)) {
				DbDataObject dboField = obj.getTableFields().get(fieldName);
				Object value = obj.getValue(fieldName);

				if (value != null) {
					String groupPath = null;
					JsonObject guiMetadata = null;
					JsonObject jsonReactGui = null;
					if (dboField.getVal(CC.GUI_METADATA) != null) {
						guiMetadata = new Gson().fromJson(dboField.getVal(CC.GUI_METADATA).toString(),
								JsonObject.class);
						if (guiMetadata.has(CC.REACT)) {
							jsonReactGui = guiMetadata.getAsJsonObject(CC.REACT);
							if (jsonReactGui.has(CC.GROUPPATH)) {
								groupPath = jsonReactGui.get(CC.GROUPPATH).getAsString();
							}
						}
					}

					addToSection(sectionData, groupData, fieldName, dboField, value, groupPath);

					try {
						if (dboField.getVal(Rc.REFERENTIAL_TABLE) != null
								&& dboField.getVal(Rc.REFERENTIAL_FIELD) != null && jsonReactGui != null
								&& jsonReactGui.has(Rc.DENORMALIZED_MNEMONIC)) {
							DbDataObject denormalizedField = Utils.findField(
									dboField.getVal(Rc.REFERENTIAL_TABLE).toString(),
									jsonReactGui.get(Rc.DENORMALIZED_MNEMONIC).getAsString());
							DbDataObject denormalizedData = Utils.getDbDataObjectFromDenormalizedField(
									dboField.getVal(Rc.REFERENTIAL_TABLE).toString(),
									dboField.getVal(Rc.REFERENTIAL_FIELD).toString(), obj.getValue(fieldName), svr);
							if (denormalizedField != null && denormalizedData != null) {
								DbDataObject tempField = new DbDataObject();
								tempField.fromSimpleJson(denormalizedField.toSimpleJson());
								tempField.setVal(Rc.FIELD_NAME,
										fieldName + "." + denormalizedField.getVal(Rc.FIELD_NAME).toString());
								if (guiMetadata != null) {
									tempField.setVal(Rc.GUI_METADATA, guiMetadata.toString());
								}

								addToSection(sectionData, groupData,
										fieldName + "." + denormalizedField.getVal(Rc.FIELD_NAME).toString(), tempField,
										denormalizedData.getVal(
												jsonReactGui.get(Rc.DENORMALIZED_MNEMONIC).getAsString()),
										groupPath);
							}
						}
					} catch (SvException e) {
						log4j.error(e.getMessage(), e);
					}
				}
			}
		}

		for (Map.Entry<String, JsonObject> entry : groupData.entrySet()) {
			sectionData.add(entry.getKey(), entry.getValue());
		}

		return sectionData;
	}

	private void addToSection(JsonObject sectionData, Map<String, JsonObject> groupData, String fieldName,
			DbDataObject dboField, Object value, String groupPath) {
		JsonElement jsonValue = convertToJsonElement(value, dboField);

		if (groupPath != null) {
			JsonObject group = groupData.computeIfAbsent(groupPath, k -> new JsonObject());
			group.add(fieldName, jsonValue);
		} else {
			sectionData.add(fieldName, jsonValue);
		}
	}

	/**
	 * Converts database value to appropriate JSON element.
	 */
	private JsonElement convertToJsonElement(Object value, DbDataObject dboField) {
		String fieldType = dboField.getVal(CC.FIELD_TYPE).toString();

		switch (fieldType) {
		case CC.NUMERIC:
			Long scale = (Long) dboField.getVal(CC.FIELD_SCALE);
			if (scale != null && scale > 0) {
				return new JsonPrimitive(((Number) value).doubleValue());
			} else {
				return new JsonPrimitive(((Number) value).longValue());
			}
		case CC.BOOLEAN:
			return new JsonPrimitive((Boolean) value);
		case CC.DATE:
		case CC.TIMESTAMP:
		case CC.DATETIME:
			if (value instanceof Date) {
				SimpleDateFormat sdf = fieldType.equals(CC.DATE) ? new SimpleDateFormat("yyyy-MM-dd")
						: new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
				return new JsonPrimitive(sdf.format((Date) value));
			}
			return new JsonPrimitive(value.toString());
		default:
			return new JsonPrimitive(value.toString());
		}
	}

	/**
	 * Method for saving the form. It will prepare all objects in the form and then
	 * try to save them with.
	 * 
	 * @param formData form data that needs to be saved
	 * @param localeId user locale identifier
	 * @param svr      SvReader instance for database operations
	 * @param svw      SvWriter instance for database operations
	 * @return
	 * @throws FormSaveException
	 * @throws AuthorizationError
	 * @throws SvException
	 */
	public Map<String, List<BaseObjectModel>> saveForm(JsonObject formData, String localeId, SvReader svr, SvWriter svw)
			throws FormSaveException, AuthorizationError, SvException {
		if (!AuthorizationHelper.canWrite(this, svr)) {
			throw new AuthorizationError("User does not have write access for the form: " + this.formName);
		}

		Map<String, List<BaseObjectModel>> sectionObjects = new HashMap<>();
		List<String> errors = new ArrayList<String>();

		sectionObjects.putAll(prepareSectionObjects(getHeaderSection(), formData, svr));

		List<FormSection> centralSections = getCentralSection();
		for (int i = 0; i < centralSections.size(); i++) {
			sectionObjects.putAll(prepareSectionObjects(centralSections.get(i), formData, svr));
		}

		sectionObjects.putAll(prepareSectionObjects(getFooterSection(), formData, svr));

		this.beforeSave(sectionObjects, svr, svw);

		for (SaveOrderEntry entry : getSaveOrder()) {
			List<BaseObjectModel> objList = sectionObjects.get(entry.getTableName());
			if (objList != null && !objList.isEmpty()) {
				Long parentId = null;
				if (entry.getParentTableName() != null) {
					if (sectionObjects.get(entry.getParentTableName()) != null
							&& !sectionObjects.get(entry.getParentTableName()).isEmpty()) {
						parentId = sectionObjects.get(entry.getParentTableName()).get(0).getObjectId();
					}
				}

				for (BaseObjectModel obj : objList) {
					try {
						List<String> saveErrors = obj.saveObject(parentId, obj.getObjectId(), localeId, svr, svw);
						if (!saveErrors.isEmpty()) {
							errors.addAll(saveErrors);
						}
					} catch (SvException e) {
						log4j.error("Error saving table: " + entry.getTableName(), e);
						errors.add(I18n.getText(localeId, "epi.error.savingSection") + ": " + entry.getTableName());
					}
				}
			}
		}

		if (!errors.isEmpty()) {
			throw new FormSaveException("Error while saving the form " + this.formName, errors);
		}

		return sectionObjects;
	}

	/**
	 * Fill BaseObjectModel objects in each sections with the form data
	 * 
	 * @param section  the FormSection that will be processed
	 * @param formData form data that needs to be saved
	 * @return
	 * @throws SvException
	 */
	private Map<String, List<BaseObjectModel>> prepareSectionObjects(FormSection section, JsonObject formData,
			SvReader svr) throws SvException {
		Map<String, List<BaseObjectModel>> result = new HashMap<>();
		if (section == null || !AuthorizationHelper.checkUserGroupAnyMembership(svr, section.getVisibleFor()))
			return result;

		List<BaseObjectModel> objList = new ArrayList<>();
		if (section.isArray()) {
			if (formData.has(section.getTableName().toLowerCase())) {
				JsonArray itemsArr = formData.get(section.getTableName().toLowerCase()).getAsJsonArray();
				for (JsonElement item : itemsArr) {
					prepareSectionObjectFromData(section, item.getAsJsonObject(), objList);
				}
			}
		} else {
			prepareSectionObjectFromData(section, formData, objList);
		}

		result.put(section.getTableName(), objList);

		return result;
	}

	private void prepareSectionObjectFromData(FormSection section, JsonObject formData, List<BaseObjectModel> result)
			throws SvException {
		BaseObjectModel obj = factory.createObject(section.getTableName());
		JsonObject jsonObjRet = formData;

		for (String field : section.getFieldNames()) {
			boolean added = false;
			if (obj.getTableFields().containsKey(field)) {
				DbDataObject dboField = obj.getTableFields().get(field);
				JsonObject guiMetadata = null;
				if (dboField.getVal(CC.GUI_METADATA) != null) {
					guiMetadata = new Gson().fromJson(dboField.getVal(CC.GUI_METADATA).toString(), JsonObject.class);
				}

				if (guiMetadata != null && guiMetadata.has(CC.REACT)) {
					JsonObject jsonReactGui = guiMetadata.getAsJsonObject(CC.REACT);
					if (jsonReactGui != null && jsonReactGui.has(CC.GROUPPATH)) {
						jsonObjRet = formData.getAsJsonObject(jsonReactGui.get(CC.GROUPPATH).getAsString());
					}

					if (dboField.getVal(CC.REFERENTIAL_TABLE) != null && dboField.getVal(CC.REFERENTIAL_FIELD) != null
							&& dboField.getVal(CC.REFERENTIAL_FIELD).toString().equals(CC.OBJECT_ID)
							&& jsonReactGui != null && jsonReactGui.has(CC.DENORMALIZED_MNEMONIC)) {
						DbDataObject denormalizedField = Utils.findField(
								dboField.getVal(CC.REFERENTIAL_TABLE).toString(),
								jsonReactGui.get(CC.DENORMALIZED_MNEMONIC).getAsString());
						if (denormalizedField.getVal(CC.CODE_LIST_ID) != null) {
							if (jsonObjRet != null && jsonObjRet
									.has(field + "." + denormalizedField.getVal(CC.FIELD_NAME).toString())) {
								obj.setValue(field,
										jsonObjRet.get(field + "." + denormalizedField.getVal(CC.FIELD_NAME).toString())
												.getAsString());
								added = true;
							}
						}
					}
				}

				if (jsonObjRet != null && jsonObjRet.has(field) && !added) {
					obj.setValue(field, jsonObjRet.get(field).getAsString());
				}
			}
		}

		if (getInitialStatus().containsKey(obj.getTableName())) {
			obj.setStatus(getInitialStatus().get(obj.getTableName()));
		} else if (getInitialStatus().containsKey(CC.DEFAULT)) {
			obj.setStatus(getInitialStatus().get(CC.DEFAULT));
		}

		setMandatoryDefaults(obj, section, localeId);
		result.add(obj);
	}

	/**
	 * Sets default values for mandatory fields that are not included in the form.
	 * 
	 * @param obj      the object model being processed
	 * @param section  the form section
	 * @param localeId user locale identifier
	 */
	protected void setMandatoryDefaults(BaseObjectModel obj, FormSection section, String localeId) {
		for (String field : obj.getMandatoryFields()) {
			if (section.getFieldNames().contains(field) || obj.getValue(field) != null) {
				continue;
			}

			DbDataObject dboField = obj.getTableFields().get(field);
			Object defaultValue = getDefaultValueForField(obj.getTableName(), field, dboField);
			if (defaultValue != null) {
				obj.setValue(field, defaultValue);
			}
		}
	}

	/**
	 * Returns a default value for a mandatory field.
	 * 
	 * @param tableName the table name
	 * @param fieldName the field name
	 * @param dboField  the field metadata
	 * @return default value, or null if no default available
	 */
	protected Object getDefaultValueForField(String tableName, String fieldName, DbDataObject dboField) {
		String fieldType = dboField.getVal(CC.FIELD_TYPE).toString();
		String value = null;

		if (dboField.getVal(CC.GUI_METADATA) != null) {
			try {
				JsonObject guiMetadata = new Gson().fromJson(dboField.getVal(CC.GUI_METADATA).toString(),
						JsonObject.class);
				if (guiMetadata.has(CC.REACT)) {
					JsonObject reactJson = guiMetadata.getAsJsonObject(CC.REACT);
					if (reactJson.has("default")) {
						value = reactJson.get("default").getAsString();
					}
				}
			} catch (Exception e) {
				log4j.debug("Could not parse GUI_METADATA for field: " + fieldName, e);
			}
		}

		switch (fieldType) {
		case CC.NVARCHAR:
		case CC.TEXT:
			return value == null ? "" : value;
		case CC.NUMERIC:
			if (value != null) {
				return Long.valueOf(value);
			}
			return 0L;
		case CC.BOOLEAN:
			if (value != null) {
				return Boolean.valueOf(value);
			}
			return false;
		case CC.DATE:
			return Date.valueOf(LocalDate.now());
		case CC.TIMESTAMP:
		case CC.DATETIME:
			return new DateTime();
		default:
			return null;
		}
	}

	/**
	 * Defines the default save order in which the objects in the form will be saved
	 */
	public abstract List<SaveOrderEntry> getSaveOrder();

	/**
	 * Defines the initial status of the saved objects when saving the form. Can
	 * contain DEFAULT key that will be applied to all objects that are not in the
	 * map.
	 */
	public Map<String, String> getInitialStatus() {
		return Map.ofEntries(entry(CC.DEFAULT, CC.VALID));
	}

	/**
	 * Defines the default save order where it will save without parent dependencies
	 * 
	 * @return List of SaveOrderEntry defining table save order and parent tables
	 */
	protected List<SaveOrderEntry> getDefaultSaveOrder() {

		List<SaveOrderEntry> order = new ArrayList<>();
		if (getHeaderSection() != null) {
			order.add(new SaveOrderEntry(getHeaderSection().getTableName(), null));
		}
		for (FormSection section : getCentralSection()) {
			order.add(new SaveOrderEntry(section.getTableName(), null));
		}
		if (getFooterSection() != null) {
			order.add(new SaveOrderEntry(getFooterSection().getTableName(), null));
		}
		return order;
	}

	// Hook for actions before saving the form
	public abstract void beforeSave(Map<String, List<BaseObjectModel>> objectsToBeSaved, SvReader svr, SvWriter svw)
			throws SvException;

	// Hook for actions after form save
	public abstract void afterSave(Map<String, List<BaseObjectModel>> savedObjects, SvReader svr, SvWriter svw)
			throws SvException;

	public String getFormName() {
		return formName;
	}

	public void setFormName(String formName) {
		this.formName = formName;
	}

	public Map<String, String> getFormParams() {
		return formParams;
	}

	public void setFormParams(Map<String, String> formParams) {
		this.formParams = formParams;
	}

	public String getLocaleId() {
		return localeId;
	}

	public void setLocaleId(String localeId) {
		this.localeId = localeId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * Add the title and description to the schema
	 */
	private JsonObject setTitleAndDescription(JsonObject schema) {
		JsonObject output = schema == null ? new JsonObject() : schema;

		if (this.title != null) {
			output.addProperty(CC.TITLE_LC, I18n.getText(this.localeId, this.title));
		}
		if (this.description != null) {
			output.addProperty(CC.DESCRIPTION_LC, I18n.getText(this.localeId, this.description));
		}

		return output;
	}

	/**
	 * Inner class representing a save order entry with parent relationship.
	 */
	public static class SaveOrderEntry {
		private final String tableName;
		private final String parentTableName;

		public SaveOrderEntry(String tableName, String parentTableName) {
			this.tableName = tableName;
			this.parentTableName = parentTableName;
		}

		public String getTableName() {
			return tableName;
		}

		public String getParentTableName() {
			return parentTableName;
		}
	}
}
