package com.prtech.models;

import java.util.List;
import java.util.Map.Entry;

import javax.ws.rs.core.MultivaluedMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.osgi.util.tracker.ServiceTracker;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * This registry acts as a central access point for all registered ModelFactory
 * services in the OSGi environment. ModelFactory services are registered in the
 * OSGi service registry through the Activator class for each bundle.
 */
public class ModelFactoryRegistry {
	static final Logger log4j = LogManager.getLogger(ModelFactoryRegistry.class.getName());

	/*
	 * ServiceTracker instance that tracks all registered ModelFactory services in
	 * the OSGi service registry.
	 */
	private static ServiceTracker<ModelFactory, ModelFactory> tracker;

	public static void setTracker(ServiceTracker<ModelFactory, ModelFactory> t) {
		tracker = t;
	}

	/*
	 * Creates a BaseObjectModel instance for the specified table name. This method
	 * iterates through all registered ModelFactory services and attempts to create
	 * a model object for the given table name. The first factory that successfully
	 * creates a non-null object will have its result returned.
	 */
	public static BaseObjectModel createModel(String tableName) {
		BaseObjectModel obj = null;
		ModelFactory[] factories = tracker.getServices(new ModelFactory[0]);
		for (ModelFactory factory : factories) {
			obj = factory.createObject(tableName);
			if (obj != null) {
				return obj;
			}
		}
		return obj;
	}

	public static BaseObjectModel createObject(String tableName, MultivaluedMap<String, String> formVals) {
		BaseObjectModel obj = ModelFactoryRegistry.createModel(tableName);
		if (obj != null) {
			obj = fromMap(formVals, obj);
		}
		return obj;
	}

	public static BaseObjectModel createObject(String tableName, JsonObject objData) {
		BaseObjectModel obj = ModelFactoryRegistry.createModel(tableName);
		if (obj != null) {
			objData = unnest(objData, null, null);
			obj = fromJsonObject(objData, obj);
		}
		return obj;
	}

	public static <T extends BaseObjectModel> T fromMap(MultivaluedMap<String, String> map, T baseModelObj) {
		try {
			for (Entry<String, List<String>> entry : map.entrySet()) {
				if (entry.getKey() != null && !entry.getKey().isEmpty()) {
					baseModelObj.setValue(entry.getKey(), entry.getValue().get(0));
				}
			}
			return baseModelObj;
		} catch (Exception e) {
			log4j.error(e.getMessage(), e);
			return null;
		}
	}

	public static <T extends BaseObjectModel> T fromJsonObject(JsonObject obj, T baseModelObj) {
		try {
			for (Entry<String, JsonElement> entry : obj.entrySet()) {
				if (entry.getKey() != null && !entry.getKey().isEmpty()) {
					baseModelObj.setValue(entry.getKey(), entry.getValue().getAsString());
				}
			}
			return baseModelObj;
		} catch (Exception e) {
			log4j.error(e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Return a flattened version of the input JSON object
	 * 
	 * @param obj JSON object to unnest
	 * @return
	 */
	public static JsonObject unnest(JsonElement obj, String key, JsonObject target) {
		if (target == null) {
			target = new JsonObject();
		}
		if (obj.isJsonObject()) {
			for (Entry<String, JsonElement> entry : obj.getAsJsonObject().entrySet()) {
				if (entry.getKey() != null && !entry.getKey().isEmpty()) {
					unnest(entry.getValue(), entry.getKey(), target);
				}
			}
		} else if (obj.isJsonPrimitive()) {
			target.add(key, obj);
		} else if (obj.isJsonNull()) {
			target.add(key, obj);
		}
		return target;
	}
}
