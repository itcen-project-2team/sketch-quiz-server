package com.itcen.whiteboardserver.quiz;

public enum QuizCategory {
    TECH, ITCEN, COMMON;

    public static QuizCategory from(String value) {
        return switch (value.toLowerCase()) {
            case "tech" -> TECH;
            case "itcen" -> ITCEN;
            case "common" -> COMMON;
            default -> throw new IllegalArgumentException("Unknown category: " + value);
        };
    }
}
