package com.prtech.models;

import org.osgi.util.tracker.ServiceTracker;

/**
 * This registry acts as a central access point for all registered ModelFactory
 * services in the OSGi environment. ModelFactory services are registered in the
 * OSGi service registry through the Activator class for each bundle.
 */
public class ModelFactoryRegistry {
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
}
