package frc.robot.Tuning;

import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

public class TuningHelper {

    /* PID Tuning Variables */
    public static double tuningkP;
    public static double tuningkI;
    public static double tuningkD;

    /* Feedforward Tuning Variables */
    public static double tuningkS;
    public static double tuningkG;
    public static double tuningkV;
    public static double tuningkA;

    /* Motor Speed Tuning */
    public static double tuningSpeed;

    /* Setpoint Tuning */
    public static double tuningSetpoint;

    /* tuningDeadband */
    public static double tuningDeadband;

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
