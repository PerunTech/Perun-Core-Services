package com.prtech.models;

import com.google.gson.JsonObject;

public interface JsonSchemaService {
	JsonObject buildSchema(String tableName, String sessionId) throws Exception;
}
