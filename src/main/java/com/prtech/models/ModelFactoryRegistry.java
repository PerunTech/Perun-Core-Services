package com.prtech.models;

import org.osgi.util.tracker.ServiceTracker;

public class ModelFactoryRegistry {
	private static ServiceTracker<ModelFactory, ModelFactory> tracker;

	public static void setTracker(ServiceTracker<ModelFactory, ModelFactory> t) {
		tracker = t;
	}

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
