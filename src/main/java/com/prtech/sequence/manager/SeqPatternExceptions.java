package com.prtech.sequence.manager;

public class SeqPatternExceptions {

	public static class SeqPatternError extends Exception {
		public SeqPatternError(String message) {
			super(message);
		}
	}

	public static class SeqPatternValidationError extends SeqPatternError {
		public SeqPatternValidationError(String message) {
			super(message);
		}
	}

	public static class SeqPatternDuplicateError extends SeqPatternError {
		public SeqPatternDuplicateError(String message) {
			super(message);
		}
	}
}
