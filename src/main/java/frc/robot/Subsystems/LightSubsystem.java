// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Subsystems;



import edu.wpi.first.wpilibj.AddressableLED;
import edu.wpi.first.wpilibj.AddressableLEDBuffer;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.SubsystemBase;;

public class LightSubsystem extends SubsystemBase {
  public final int ledCount = 60; // Placeholder LED count
  public final double brightness = 0.25; // Placeholder LED power level

  private final Timer timer = new Timer();
   private AddressableLEDBuffer m_buffer = new AddressableLEDBuffer(ledCount);
   public AddressableLED m_led = new AddressableLED(18); // Placeholder PWM port

      
  /** Creates a new lightSubsystem. */
  public LightSubsystem() {}
    public void setAll(int r, int g, int b){
            for(var i = 0; i < ledCount; i++){
                int sr = (int)Math.round(r * brightness);
                int sg = (int)Math.round(g * brightness);
                int sb = (int)Math.round(b * brightness);
                m_buffer.setRGB(i,sr,sg,sb);
            }
        }

    public void blink(int r, int g, int b, double interval){
        if((timer.get() % (interval * 2)) < interval){
            setAll(r,g,b);
        } else {
          setAll(0,0,0);
        }
      } 

      public void off(){
        setAll(0,0,0);
      }
  @Override
  public void periodic() {
    // This method will be called once per scheduler run
    
  }
}
