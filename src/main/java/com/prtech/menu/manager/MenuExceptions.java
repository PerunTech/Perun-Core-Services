package com.prtech.menu.manager;

import java.util.List;

public class MenuExceptions {
	// Private constructor to prevent instantiation
	private MenuExceptions() {
	}

	public static class UserNotAuthorizedError extends Exception {
		public UserNotAuthorizedError(String message) {
			super(message);
		}
	}

	public static class MenuError extends Exception {
		public MenuError(String message) {
			super(message);
		}
	}

	public static class MenuSaveError extends MenuError {
		public MenuSaveError(String message) {
			super(message);
		}
	}

	public static class MenuNotFoundError extends MenuError {
		public MenuNotFoundError(String message) {
			super(message);
		}
	}

	public static class MenuInvalidConfigError extends MenuError {
		public MenuInvalidConfigError(String message) {
			super(message);
		}
	}

	public static class ImportedMenuNotFoundError extends MenuError {
		private final List<String> missingMenuCodes;

		public ImportedMenuNotFoundError(String message, List<String> missingMenuCodes) {
			super(message);
			this.missingMenuCodes = missingMenuCodes;
		}

		public List<String> getMissingMenuCodes() {
			return missingMenuCodes;
		}
	}

	public static class MenuDeleteConstraintError extends MenuError {
		private final List<String> referencingMenuCodes;

		public MenuDeleteConstraintError(String message, List<String> referencingMenuCodes) {
			super(message);
			this.referencingMenuCodes = referencingMenuCodes;
		}

		public List<String> getReferencingMenuCodes() {
			return referencingMenuCodes;
		}
	}
}
