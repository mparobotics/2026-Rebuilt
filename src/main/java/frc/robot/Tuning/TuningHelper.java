package frc.robot.Tuning;

import java.time.Period;

import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

public class TuningHelper {

    /* PID Tuning Variables */
    public double tuningkP;
    public double tuningkI;
    public double tuningkD;

    /* Feedforward Tuning Variables */
    public double tuningkS;
    public double tuningkG;
    public double tuningkV;
    public double tuningkA;

    /* Motor Speed Tuning */
    public double tuningSpeed;

    /* Setpoint Tuning */
    public double tuningSetpoint;

    /* tuningDeadband */
    public double tuningDeadband;

    public TuningHelper() {
        SmartDashboard.putNumber("Tuning/kP", tuningkP);
        SmartDashboard.putNumber("Tuning/kI", tuningkI);
        SmartDashboard.putNumber("Tuning/kD", tuningkD);

        SmartDashboard.putNumber("Tuning/kS", tuningkS);
        SmartDashboard.putNumber("Tuning/kG", tuningkG);
        SmartDashboard.putNumber("Tuning/kV", tuningkV);
        SmartDashboard.putNumber("Tuning/kA", tuningkA);

        SmartDashboard.putNumber("Tuning/Speed", tuningSpeed);
        SmartDashboard.putNumber("Tuning/Setpoint", tuningSetpoint);
        SmartDashboard.putNumber("Tuning/Deadband", tuningDeadband);
    }

    public void TuningPeriodic() {
        tuningkP = SmartDashboard.getNumber("Tuning/kP", tuningkP);
        tuningkI = SmartDashboard.getNumber("Tuning/kI", tuningkI);
        tuningkD = SmartDashboard.getNumber("Tuning/kD", tuningkD);

        tuningkS = SmartDashboard.getNumber("Tuning/kS", tuningkS);
        tuningkG = SmartDashboard.getNumber("Tuning/kG", tuningkG);
        tuningkV = SmartDashboard.getNumber("Tuning/kV", tuningkV);
        tuningkA = SmartDashboard.getNumber("Tuning/kA", tuningkA);

        tuningSpeed = SmartDashboard.getNumber("Tuning/Speed", tuningSpeed);
        tuningSetpoint = SmartDashboard.getNumber("Tuning/Setpoint", tuningSetpoint);
        tuningDeadband = SmartDashboard.getNumber("Tuning/Deadband", tuningDeadband);
    }
    
}
