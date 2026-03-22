package org.moundsparkacademy.frc.tuning;

import java.util.Objects;

public final class TuningValidation {

    private TuningValidation() {}

    public static String requireValidName(String name, String label) {
        Objects.requireNonNull(name, label + " must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        if (name.contains("/")) {
            throw new IllegalArgumentException(
                label + " must not contain '/' (causes malformed NetworkTables paths): " + name);
        }
        return name;
    }
}
