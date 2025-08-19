package com.prtech.menu.manager;

public enum BuildDirectionMode {
    UP, DOWN, BOTH;

    public static BuildDirectionMode fromString(String val) {
		if (val == null || val.trim().isEmpty())
			return DOWN;
        try {
            return BuildDirectionMode.valueOf(val.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DOWN;
        }
    }
}