package com.prtech.menu.manager;

public class MenuExceptions {
	// Private constructor to prevent instantiation
	private MenuExceptions() {
	}

	public static class UserNotAuthorizedError extends Exception {
		public UserNotAuthorizedError(String message) {
			super(message);
		}
	}

	public static class MenuSaveError extends Exception {
		public MenuSaveError(String message) {
			super(message);
		}
	}
}
