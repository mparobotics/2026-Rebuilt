// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.


package frc.robot.Subsystems;


import com.ctre.phoenix6.configs.CANdleConfiguration;
import com.ctre.phoenix6.controls.ColorFlowAnimation;
import com.ctre.phoenix6.controls.EmptyAnimation;
import com.ctre.phoenix6.controls.SolidColor;
import com.ctre.phoenix6.hardware.CANdle;
import com.ctre.phoenix6.signals.RGBWColor;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants.CANdleConstants;


public class CandleSubsystem extends SubsystemBase {
 private final CANdle candle = new CANdle(CANdleConstants.CANDLE_ID);
 // LED indices: 0-7 control onboard LEDs, 8-399 control attached LED strip
 // We have 60 LEDs on our strip (indices 8-67)
 private static final int LED_START_INDEX = 0; //No need to do anything because I configure it all when seting anim
 private static final int LED_END_INDEX = 60;


 public CandleSubsystem() {
   CANdleConfiguration configAll = new CANdleConfiguration(); //Builds a configuration preset for the lights
   // Apply default configuration using Phoenix 6 API
   configAll.LED.BrightnessScalar = 0.25;
  
   candle.getConfigurator().apply(configAll); //Implements our awesome settings using Phoenix 6 API


   // Initialize LEDs to off
   LightConfig(AnimationType.Off, LED_START_INDEX, LED_END_INDEX, Colors.Black);
 }


 private RGBWColor LedColor;


 public enum Colors {
   None,
   Red,
   Orange,
   Yellow,
   Green,
   Blue,
   Purple,
   Black,
   Custom
 }


 public enum AnimationType {
   None,
   ColorFlow,
   Fire,
   Larson,
   Rainbow,
   RgbFade,
   SingleFade,
   Twinke,
   TwinkleOff,
   Solid, //Snake
   Off
 }


 public enum LedStates {
   None,
   OutOfRange,
   InRange,
   Aligned,
   ShooterUpToSpeed,
   HopperFull,
   ReadyToShoot,
   InRangeAligned,
   InRangeShooterSpeed,
   ShooterSpeedAligned
 }


 //Some Color Presets
 public void ChangeColor(Colors Color, int R, int G, int B, int W) {
   switch(Color) {
     default:
     case Red:
       LedColor = RGBWColor.fromHex("#ff0000ff").orElseThrow();
       break;
     case Orange:
       LedColor = RGBWColor.fromHex("#ff9d00ff").orElseThrow();
       break;
     case Yellow:
       LedColor = RGBWColor.fromHex("#fffb00ff").orElseThrow();
       break;
     case Green:
       LedColor = RGBWColor.fromHex("#26ff00ff").orElseThrow();
       break;
     case Blue:
       LedColor = RGBWColor.fromHex("#0000ffff").orElseThrow();
       break;
     case Purple:
       LedColor = RGBWColor.fromHex("#b700ffff").orElseThrow();
       break;
     case Black:
       LedColor = RGBWColor.fromHex("#000000ff").orElseThrow();
       break;
     case Custom:
       LedColor = new RGBWColor(R, G, B, W);
       break;
   }
 }


 public void ChangeState(LedStates State) {
   ClearAnimations();
   LightConfig(AnimationType.Off, LED_START_INDEX, LED_END_INDEX, Colors.Black);
   switch(State) {
     default:
     case None:
     break;
     case OutOfRange:
       LightConfig(AnimationType.Solid, LED_START_INDEX, LED_END_INDEX, Colors.Red);
       break;
     case InRange:
       LightConfig(AnimationType.Solid, 0, 20, Colors.Yellow);
       break;
     case Aligned:
       LightConfig(AnimationType.Solid, 21, 40, Colors.Orange);
       break;
     case ShooterUpToSpeed:
       LightConfig(AnimationType.ColorFlow, 41, 60, Colors.Yellow);
       break;
     case ReadyToShoot:
       LightConfig(AnimationType.Solid, LED_START_INDEX, LED_END_INDEX, Colors.Green);
       break;
     case HopperFull:
       LightConfig(AnimationType.ColorFlow, LED_START_INDEX, LED_END_INDEX, Colors.Blue);
       break;
     case InRangeAligned:
       LightConfig(AnimationType.Solid, 0, 20, Colors.Yellow);
       LightConfig(AnimationType.Solid, 21, 40, Colors.Orange);
       break;
     case InRangeShooterSpeed:
       LightConfig(AnimationType.Solid, 0, 20, Colors.Yellow);
       LightConfig(AnimationType.ColorFlow, 41, 60, Colors.Yellow);
       break;
     case ShooterSpeedAligned:
       LightConfig(AnimationType.Solid, 21, 40, Colors.Orange);
       LightConfig(AnimationType.ColorFlow, 41, 60, Colors.Yellow);
       break;
   }
 }
 public void ClearAnimations() {
   for (int i = 0; i < 8; ++i) {
     candle.setControl(new EmptyAnimation(i));
   }
 }




  public void LightConfig(AnimationType type, int kSlot1StartIdx, int kSlot1EndIdx, Colors NewColor) {
     ChangeColor(NewColor, 0,0,0,0);
    
     switch (type) {
       default:
       case ColorFlow:
         candle.setControl (
           new ColorFlowAnimation(kSlot1StartIdx + 7, kSlot1EndIdx + 7).withSlot(0).withColor(LedColor)
         );
         break;
       case Solid: //Snake
         candle.setControl(
           new SolidColor(kSlot1StartIdx + 7, kSlot1EndIdx + 7).withColor(LedColor)
         );
         break;
       case Off:
         ChangeColor(Colors.Black, 0,0,0,0);
         candle.setControl(
           new SolidColor(kSlot1StartIdx + 7, kSlot1EndIdx + 7).withColor(LedColor)
         );
         break;
     }
  } 
 }



