package com.calendar.university;

import java.util.List;

public class UniversityRegistry {

    private static final List<University> SUPPORTED = List.of(
            new TuribaUniversity()
            // Add more universities here in the future
    );

    public static List<University> getAll() {
        return SUPPORTED;
    }

    public static University getByCode(String code) {
        return SUPPORTED.stream()
                .filter(u -> u.getCode().equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
    }
}
