// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Subsystems;

import com.ctre.phoenix6.configs.CANdleConfiguration;
import com.ctre.phoenix6.controls.SolidColor;
import com.ctre.phoenix6.hardware.CANdle;
import com.ctre.phoenix6.signals.RGBWColor;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants.CANdleConstants;

public class CandleSubsystem extends SubsystemBase {
  private final CANdle candle = new CANdle(CANdleConstants.CANDLE_ID);
  // LED indices: 0-7 control onboard LEDs, 8-399 control attached LED strip
  // We have 60 LEDs on our strip (indices 8-67)
  private static final int LED_START_INDEX = 8; // First LED on attached strip
  private static final int LED_END_INDEX = 67; // 60 LEDs total (8 + 60 - 1 = 67)

  public CandleSubsystem() {
    CANdleConfiguration configAll = new CANdleConfiguration(); //Builds a configuration preset for the lights
    // Apply default configuration using Phoenix 6 API
    candle.getConfigurator().apply(configAll); //Implements our awesome settings using Phoenix 6 API

    // Initialize LEDs to off
    setLedOn(false);
  }

  public void setLedOn(boolean enabled) {
    if (enabled) {
      candle.setControl(new SolidColor(LED_START_INDEX, LED_END_INDEX)
          .withColor(new RGBWColor(255, 0, 0, 0)));
    } else {
      candle.setControl(new SolidColor(LED_START_INDEX, LED_END_INDEX)
          .withColor(new RGBWColor(0, 0, 0, 0)));
    }
  }
}
