/* More or less a framework. Refrence it when needed apon other classes */
// If you want, phoenix6 has smartdash support, so you can add it if you want.
// Heres an example if you do want to add it: https://github.com/CrossTheRoadElec/Phoenix6-Examples/blob/main/java/CANdle/src/main/java/frc/robot/Robot.java
package frc.robot.Subsystems;

import com.ctre.phoenix6.CANBus;
import com.ctre.phoenix6.configs.CANdleConfiguration;
import com.ctre.phoenix6.controls.*;
import com.ctre.phoenix6.hardware.CANdle;
import com.ctre.phoenix6.signals.AnimationDirectionValue;
import com.ctre.phoenix6.signals.RGBWColor;
import com.ctre.phoenix6.signals.StatusLedWhenActiveValue;
import com.ctre.phoenix6.signals.StripTypeValue;
import edu.wpi.first.wpilibj.TimedRobot;




//Add color presets here:
class Robot extends TimedRobot {
  private static final RGBWColor kred = RGBWColor.fromHex("#ff0000ff").orElseThrow();
  private static final RGBWColor kgreen = RGBWColor.fromHex("#00c25bff").orElseThrow();
  private static final RGBWColor kblue = RGBWColor.fromHex("#0000ffff").orElseThrow();

  //Sets up CANdle
  private final CANdle candle = new CANdle(18, CANBus.roboRIO());

  //Add presets here
  private enum AnimationType {
    None,
    ColorFlow,
    Fire,
    Larson,
    Rainbow,
    RgbFade,
    SingleFade,
    Twinke,
    TwinkleOff,
  }


  //Candle Config
  public void CandleSubsystem() {
    var config = new CANdleConfiguration();
    config.LED.StripType = StripTypeValue.GRB;
    config.LED.BrightnessScalar = 0.25;
    config.CANdleFeatures.StatusLedWhenActive = StatusLedWhenActiveValue.Disabled;
  
    /* Clearing All previous anims */
    for (int i = 0; i < 8; ++i) {
      candle.setControl(new EmptyAnimation(i));
    }
  
    //The leds on the candle (NOT the leds on the strip) controll. They are addressable.
    candle.setControl(new SolidColor(0, 7).withColor(kblue));
  }
    /*Its called k1slotIndex because I had a variable for the first half of LEDs before.
     I removed it and placed them here instead because its easier */
    public void Anim1State(AnimationType type, int kSlot1StartIdx, int kSlot1EndIdx) {
      switch (type) {

        default:
        case ColorFlow:
          candle.setControl (
            new ColorFlowAnimation(kSlot1StartIdx, kSlot1EndIdx).withSlot(0).withColor(kred)
          );
          break;
        case Fire:
          candle.setControl(
            new FireAnimation(kSlot1StartIdx, kSlot1EndIdx).withSlot(0).withDirection(AnimationDirectionValue.Forward).withCooling(0.4).withSparking(0.5) //Idk what they were thinking when making fire animation be very different from every other animation
          );
          break;
        case Larson:
          candle.setControl(
            new LarsonAnimation(kSlot1StartIdx, kSlot1EndIdx).withSlot(0) //I honestly have no idea what this does, but it was in the docs
          );
          break;
        case Rainbow:
          candle.setControl(
            new RainbowAnimation(kSlot1StartIdx, kSlot1EndIdx).withSlot(0) //Rainbow by default does a rainbow pattern so we dont have to color it
          );
          break;
        case RgbFade:
          candle.setControl(
            new RgbFadeAnimation(kSlot1StartIdx, kSlot1EndIdx).withSlot(0) //Dont need color for this one
          );
          break;
        case SingleFade:
          candle.setControl(
            new SingleFadeAnimation(kSlot1StartIdx, kSlot1EndIdx).withSlot(0) //Dont need color for this one
          );
          break;
        case Twinke:
          candle.setControl(
            new TwinkleAnimation(kSlot1StartIdx, kSlot1EndIdx).withSlot(0).withColor(kred)
          );
          break;
        case TwinkleOff:
          candle.setControl (
            new TwinkleOffAnimation(kSlot1StartIdx, kSlot1EndIdx).withSlot(0).withColor(kred)
          );

      }
    
  }
}