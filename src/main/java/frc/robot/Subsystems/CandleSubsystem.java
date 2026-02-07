// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.


package frc.robot.Subsystems;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants.CANdleConstants;
import com.ctre.phoenix.led.CANdle;
import com.ctre.phoenix.led.CANdleConfiguration;
import com.ctre.phoenix.led.CANdle.LEDStripType;
import com.ctre.phoenix.led.CANdle.VBatOutputMode;



//I imported stuff. Enjoy looking at the 4 unused imports.




public class CandleSubsystem extends SubsystemBase {
 private final int ledAmount = 0; //Placeholder (LED AMOUNT)
 private final CANdle candle = new CANdle(CANdleConstants.CANDLE_ID, "rio");
 private int candleChannel = 0; //Placeholder (PORT)
 private boolean clearAllAnims = false; //Sets up anim toggle
 private boolean setAnim = false; //Allows for simple transition between animations


 public enum AnimTypes {
   Setall,
   Empty
 }
 private AnimTypes currentAnim; //Sets to null by default


 public CandleSubsystem() {
     changeAnimation(AnimTypes.Empty);
     CANdleConfiguration configAll = new CANdleConfiguration(); //Builds a configuration preset for the lights
     configAll.statusLedOffWhenActive = true; //Candle hub has an LED, we hate when it lights up to "tell us usefull information"
     configAll.disableWhenLOS = false; //Makes LEDs NOT turn off when a loss of signal occurs (Needed so on mini radio issue the leds dont start spazing out)
     configAll.stripType = LEDStripType.GRB; //GRB because Dle, do you get the humor. Okay I'll leave...
     configAll.brightnessScalar = 0.25; //So we dont burn out the bulbs
     configAll.vBatOutputMode = VBatOutputMode.Modulated; //Sets up the CANdle for the inputs we want to insert.
     candle.configAllSettings(configAll); //Imlements our awesome settings
 }

 public void setLedOn(boolean enabled) {
   changeAnimation(enabled ? AnimTypes.Setall : AnimTypes.Empty);
 }

 public void incrementAnimations() {
   switch(currentAnim) {
     case Empty:
       changeAnimation(AnimTypes.Setall);
       break;
     case Setall:
       changeAnimation(AnimTypes.Empty);
       break;
     default:
       changeAnimation(AnimTypes.Empty);
       break;
   }
 }


 public void changeAnimation(AnimTypes toChange) {
   currentAnim = toChange; //Changes animations to a type


   switch(toChange)
   {
     case Setall:
       break;
     case Empty:
       break;
   }
 }




 public void clearAllAnimations() {clearAllAnims = true;}


 @Override
 public void periodic() {
   if (currentAnim == AnimTypes.Empty) {
     candle.setLEDs(0, 0, 0);
   } else if (currentAnim == AnimTypes.Setall) {
     candle.setLEDs(255, 0, 0); //Red, fight me on it.
   }
   if(clearAllAnims) {


       clearAllAnims = false;
       /*Hey guys, welcome back to another video, today I will show you how to toggle something in java.
        Make sure to like and subscribe, and without further ado, lets get straight into the video! 
        Welcome into my vs code, so the first thing im going to do is create a new file, and name it whatever I want.
        In this case im going to name it "Toggle trigger". Then lets click create. 
        Now what im going to do is make a function. Just.. like that! 
        And now what im going to do is make a boolean outside of the function. 
        Lets call it "Toggled". And lets set it to false. Now what im going to do is add it into my function,
        and make an if then statement saying "If Toggle is equal to false, set toggle to true." 
        lets add in a print statement printing out the value of toggle at the end of the function. 
        Now look at that! When I run my code, it toggles! But thats not all, we can also make it toggle both ways! 
        First thing im going to do is replace the if then statement, and just replace it with a simple not statement, 
        the toggle now goes both ways! If you found this tutorial helpfull, make sure to leave a like and subscribe, 
        and I'll see you all in the next one, bye! (Outro plays)
      */

       for(int i = 0; i < 10; i++) {
        
         candle.clearAnimation(i); //Clears all animations on seperate leds. Humor is in the past.
       }
     }
 }
}

