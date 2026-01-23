package com.prtech.form;

import java.util.List;

public class FormSaveException extends Exception {
	private static final long serialVersionUID = 1L;
	private final List<String> errorsList;

	public FormSaveException(String message, List<String> errorsList) {
		super(message);
		this.errorsList = errorsList;
	}

	public List<String> getErrorsList() {
		return errorsList;
	}
}
