// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Subsystems;



import edu.wpi.first.wpilibj.AddressableLED;
import edu.wpi.first.wpilibj.AddressableLEDBuffer;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.SubsystemBase;;

public class lightSubsystem extends SubsystemBase {
  public final int ledCount = 1; // Placeholder LED count

  private final Timer timer = new Timer();
   private AddressableLEDBuffer m_buffer = new AddressableLEDBuffer(ledCount);
   public AddressableLED m_led = new AddressableLED(0); // Placeholder PWM port

      
  /** Creates a new lightSubsystem. */
  public lightSubsystem() {}
    public void setAll(int r, int g, int b){
            for(var i = 0; i < ledCount; i++){
                m_buffer.setRGB(i,r,g,b);
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
