package com.prtech.models;

import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.prtech.svarog.SvReader;

public class JsonSchemaServiceImpl implements JsonSchemaService {
	private final Function<String, BaseObjectModel> modelFactory;

	public JsonSchemaServiceImpl() {
		this(ModelFactoryRegistry::createModel);
	}

	public JsonSchemaServiceImpl(Function<String, BaseObjectModel> modelFactory) {
		this.modelFactory = modelFactory;
	}

	@Override
	public JsonObject buildSchema(String tableName, String sessionId) throws Exception {
		JsonObject jsonSchema = new JsonObject();
		try (SvReader svr = new SvReader(sessionId)) {
			String localeId = svr.getUserLocaleId(svr.getInstanceUser());
			BaseObjectModel obj = modelFactory.apply(tableName);
			if (obj != null) {
				DependencyBuilder builder = new DependencyBuilder(obj, localeId, svr);
				jsonSchema = obj.getTableJsonSchema(localeId, svr);
				JsonElement dependencies = builder.build(jsonSchema);
				if (dependencies != null) {
					JsonArray dependenciesArr = dependencies.getAsJsonArray();
					if (!dependenciesArr.isEmpty()) {
						jsonSchema.add("allOf", dependenciesArr);
					}
				}
			}
		}
		return jsonSchema;
	}
}
