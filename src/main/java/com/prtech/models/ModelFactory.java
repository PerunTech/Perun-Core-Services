package com.prtech.models;

import java.util.List;
import java.util.Map.Entry;

import javax.ws.rs.core.MultivaluedMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ModelFactory {
	static final Logger log4j = LogManager.getLogger(ModelFactory.class.getName());

	public static <T extends BaseModel> T fromMap(MultivaluedMap<String, String> map, Class<T> clazz) {
		try {
			T instance = clazz.getDeclaredConstructor().newInstance();
			for (Entry<String, List<String>> entry : map.entrySet()) {
				if (entry.getKey() != null && !entry.getKey().isEmpty()) {
					instance.setValue(entry.getKey(), entry.getValue().get(0));
				}
			}
			return instance;
		} catch (Exception e) {
			log4j.error(e.getMessage(), e);
			return null;
		}
	}
}
