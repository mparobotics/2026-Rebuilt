package org.moundsparkacademy.frc.tuning;

import java.util.List;

public interface TunableProvider {
    String getSubsystemName();
    List<Tuner> getTuners();
}
