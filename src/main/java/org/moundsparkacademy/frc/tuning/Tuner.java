package org.moundsparkacademy.frc.tuning;

import java.util.List;
import java.util.Objects;

public record Tuner(String name, List<TuningParameter> parameters) {
    public Tuner {
        TuningValidation.requireValidName(name, "Tuner name");
        Objects.requireNonNull(parameters, "parameters must not be null");
        if (parameters.isEmpty()) {
            throw new IllegalArgumentException("parameters must not be empty");
        }
        parameters = List.copyOf(parameters);
    }
}
