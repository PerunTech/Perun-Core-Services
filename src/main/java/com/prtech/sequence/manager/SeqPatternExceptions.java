package com.prtech.sequence.manager;

public class SeqPatternExceptions {

	private SeqPatternExceptions() {
	}

	public static class SeqPatternError extends Exception {
		private static final long serialVersionUID = 1L;

		public SeqPatternError(String message) {
			super(message);
		}

		public SeqPatternError(String message, Throwable cause) {
			super(message, cause);
		}
	}

	public static class SeqPatternValidationError extends SeqPatternError {
		private static final long serialVersionUID = 1L;

		public SeqPatternValidationError(String message) {
			super(message);
		}

		public SeqPatternValidationError(String message, Throwable cause) {
			super(message, cause);
		}
	}

	public static class SeqPatternDuplicateError extends SeqPatternError {
		private static final long serialVersionUID = 1L;

		public SeqPatternDuplicateError(String message) {
			super(message);
		}

		public SeqPatternDuplicateError(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
