package org.moundsparkacademy.frc.tuning;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;

public final class TuningManager implements AutoCloseable {

    private static final double VALUE_CHANGE_TOLERANCE = 1e-9;

    private final List<TunerBinding> bindings = new ArrayList<>();
    private boolean closed = false;

    public TuningManager(List<TunableProvider> providers) {
        NetworkTable rootTable = NetworkTableInstance.getDefault().getTable("Tuning");

        Set<String> seenSubsystems = new HashSet<>();

        for (TunableProvider provider : providers) {
            String subsystemName = TuningValidation.requireValidName(
                provider.getSubsystemName(), "Subsystem name");

            List<Tuner> tuners = provider.getTuners();

            if (!seenSubsystems.add(subsystemName)) {
                throw new IllegalArgumentException(
                    "Duplicate subsystem name: " + subsystemName);
            }

            if (tuners.isEmpty()) {
                throw new IllegalArgumentException(
                    "TunableProvider " + subsystemName + " returned an empty tuner list");
            }

            NetworkTable subsystemTable = rootTable.getSubTable(subsystemName);

            Set<String> seenTuners = new HashSet<>();

            for (Tuner tuner : tuners) {
                if (!seenTuners.add(tuner.name())) {
                    throw new IllegalArgumentException(
                        "Duplicate tuner name: " + tuner.name()
                        + " in subsystem " + subsystemName);
                }

                NetworkTable tunerTable = subsystemTable.getSubTable(tuner.name());

                Set<String> seenParams = new HashSet<>();

                for (TuningParameter param : tuner.parameters()) {
                    if (!seenParams.add(param.getName())) {
                        throw new IllegalArgumentException(
                            "Duplicate parameter name: " + param.getName()
                            + " in tuner " + subsystemName + "/" + tuner.name());
                    }

                    NetworkTableEntry entry = tunerTable.getEntry(param.getName());
                    double value = param.getValue();
                    entry.setDouble(value);
                    bindings.add(new TunerBinding(param, entry));
                    log("Registered: %s = %.4f", entry.getName(), value);
                }
            }
        }

        log("Initialized with %d tunable parameters", bindings.size());
    }

    public void periodic() {
        if (closed) {
            log("periodic() called after close() — ignoring");
            return;
        }

        for (TunerBinding binding : bindings) {
            double currentValue = binding.param.getValue();
            double dashboardValue = binding.entry.getDouble(currentValue);

            if (!MathUtil.isNear(currentValue, dashboardValue, VALUE_CHANGE_TOLERANCE)) {
                binding.param.setValue(dashboardValue);
                double appliedValue = binding.param.getValue();
                binding.entry.setDouble(appliedValue);
                log("%s changed: %.4f -> %.4f",
                    binding.entry.getName(), currentValue, appliedValue);
            }
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        for (TunerBinding binding : bindings) {
            binding.entry.unpublish();
        }
        bindings.clear();

        log("Closed — all tuning entries unpublished");
    }

    private void log(String fmt, Object... args) {
        System.out.printf("[TuningManager] " + fmt + "%n", args);
    }

    private record TunerBinding(TuningParameter param, NetworkTableEntry entry) {}
}
