package org.moundsparkacademy.frc.tuning;

import java.util.Objects;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import edu.wpi.first.math.MathUtil;

public final class TuningParameter {

    private final String name;
    private final double min;
    private final double max;
    private final DoubleSupplier getter;
    private final DoubleConsumer setter;

    public TuningParameter(String name, DoubleSupplier getter, DoubleConsumer setter) {
        this(name, getter, setter, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    public TuningParameter(String name, DoubleSupplier getter, DoubleConsumer setter,
                           double min, double max) {
        this.name = TuningValidation.requireValidName(name, "TuningParameter name");
        this.getter = Objects.requireNonNull(getter, "getter must not be null");
        this.setter = Objects.requireNonNull(setter, "setter must not be null");
        if (min > max) {
            throw new IllegalArgumentException(
                "min (" + min + ") must not be greater than max (" + max + ")");
        }
        this.min = min;
        this.max = max;
    }

    public String getName() {
        return name;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getValue() {
        return getter.getAsDouble();
    }

    public void setValue(double value) {
        setter.accept(MathUtil.clamp(value, min, max));
    }
}
