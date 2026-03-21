package frc.robot.Tuning;

import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import frc.robot.Subsystems.IntakeSubsystem;

/**
 * TuningHelper is a class that helps with tuning the robot's PID and
 * feedforward gains.
 *
 * This class is currently designed for the intake arm subsystem, but
 * could be extended to other subsystems.  Alternatively, it could be
 * made more generic to take "motors" as input instead of subsystems and
 * tune the PID and feedforward gains for the supplied motor(s).
 */
public class TuningHelper {

    private static final boolean VERBOSE = true;

    private final IntakeSubsystem intake;

    private double lastKP, lastKI, lastKD;
    private double lastKS, lastKG, lastKV, lastKA;

    public TuningHelper(IntakeSubsystem intake) {
        this.intake = intake;

        // initialize the last values for the PID and feedforward gains
        // to the current values from the intake subsystem
        lastKP = intake.getArmKP();
        lastKI = intake.getArmKI();
        lastKD = intake.getArmKD();

        lastKS = intake.getArmKS();
        lastKG = intake.getArmKG();
        lastKV = intake.getArmKV();
        lastKA = intake.getArmKA();

        // initialize the dashboard values for the PID and feedforward gains
        SmartDashboard.putNumber("Tuning/kP", lastKP);
        SmartDashboard.putNumber("Tuning/kI", lastKI);
        SmartDashboard.putNumber("Tuning/kD", lastKD);

        SmartDashboard.putNumber("Tuning/kS", lastKS);
        SmartDashboard.putNumber("Tuning/kG", lastKG);
        SmartDashboard.putNumber("Tuning/kV", lastKV);
        SmartDashboard.putNumber("Tuning/kA", lastKA);

        log("[TuningHelper] Initialized with current controller values:");
        log("  PID:  kP=%.4f  kI=%.4f  kD=%.4f", lastKP, lastKI, lastKD);
        log("  FF:   kS=%.4f  kG=%.4f  kV=%.4f  kA=%.4f", lastKS, lastKG, lastKV, lastKA);
    }

    public void periodic() {

        // get the PID gains from the dashboard
        double kP = SmartDashboard.getNumber("Tuning/kP", lastKP);
        double kI = SmartDashboard.getNumber("Tuning/kI", lastKI);
        double kD = SmartDashboard.getNumber("Tuning/kD", lastKD);

        // update PID gains if user has changed them
        if (kP != lastKP || kI != lastKI || kD != lastKD) {
            if (VERBOSE) {
                logPIDChange(lastKP, lastKI, lastKD, kP, kI, kD);
            }
            // update the PID gains for the intake arm subsystem
            intake.setArmPIDGains(kP, kI, kD);
            lastKP = kP;
            lastKI = kI;
            lastKD = kD;
        }

        // get the feedforward gains from the dashboard
        double kS = SmartDashboard.getNumber("Tuning/kS", lastKS);
        double kG = SmartDashboard.getNumber("Tuning/kG", lastKG);
        double kV = SmartDashboard.getNumber("Tuning/kV", lastKV);
        double kA = SmartDashboard.getNumber("Tuning/kA", lastKA);

        // update feedforward gains if user has changed them
        if (kS != lastKS || kG != lastKG || kV != lastKV || kA != lastKA) {
            if (VERBOSE) {
                logFFChange(lastKS, lastKG, lastKV, lastKA, kS, kG, kV, kA);
            }
            // update the feedforward gains for the intake arm subsystem
            intake.setArmFeedforwardGains(kS, kG, kV, kA);
            lastKS = kS;
            lastKG = kG;
            lastKV = kV;
            lastKA = kA;
        }
    }

    /* =========================================================================
     * Logging and Debugging
     *
     * The methods below are used to log changes to PID and feedforward gains.
     * They are called by the periodic() method when verbose mode is enabled.
     * ======================================================================= */

    private void log(String fmt, Object... args) {
        System.out.printf(fmt + "%n", args);
    }

    private void logPIDChange(double oldP, double oldI, double oldD,
                              double newP, double newI, double newD) {
        log("[TuningHelper] PID changed:");
        log("  old:  kP=%.4f  kI=%.4f  kD=%.4f", oldP, oldI, oldD);
        log("  new:  kP=%.4f  kI=%.4f  kD=%.4f", newP, newI, newD);
    }

    private void logFFChange(double oldS, double oldG, double oldV, double oldA,
                             double newS, double newG, double newV, double newA) {
        log("[TuningHelper] FF changed:");
        log("  old:  kS=%.4f  kG=%.4f  kV=%.4f  kA=%.4f", oldS, oldG, oldV, oldA);
        log("  new:  kS=%.4f  kG=%.4f  kV=%.4f  kA=%.4f", newS, newG, newV, newA);
    }
}
