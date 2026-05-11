// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Auto;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import frc.robot.Constants.IntakeConstants;
import frc.robot.Constants.ShooterConstants;
import frc.robot.Constants.SwerveConstants;
import frc.robot.Subsystems.SwerveSubsystem;
import frc.robot.Subsystems.IntakeSubsystem;
import frc.robot.Subsystems.ShooterSubsystem;

import java.util.concurrent.atomic.AtomicReference;


public class LeftNeutralZoneAuto2 extends SequentialCommandGroup {
  private static final double DRIVE_SPEED_MPS = 3.5;
  private static final double DRIVE_HEADING_P = 3.0;
  private static final double DRIVE_HEADING_MAX_OMEGA_RAD_PER_SEC = 2.0;
  private static final double TURN_P = 4.0;
  private static final double TURN_TOLERANCE_DEG = 3.0;
  private static final double TURN_TIMEOUT_SEC = 2.5;

  private static final double BACKWARD_METERS_1 = 3.6;
  private static final double BACKWARD_METERS_2 = 4.3;
  private static final double FORWARD_METERS_1 = 3.0;
  //private static final double FORWARD_METERS_2 = 1.0;
  //private static final double FORWARD_METERS_3 = 3.2;

  private static final double INTAKE_POWER = -0.75;
  private static final double FEED_DURATION_SEC = 3.0;

  public LeftNeutralZoneAuto2(SwerveSubsystem drive, IntakeSubsystem intake, ShooterSubsystem shooter) {
    addRequirements(drive, intake, shooter);

    addCommands(
      Commands.runOnce(intake::lowerIntake, intake),

      // Drive backwards 3.6m.
      driveDistanceMeters(drive, -BACKWARD_METERS_1, DRIVE_SPEED_MPS),

      // Turn 90 degrees left.
      turnRelativeDegrees(drive, 90.0),

      // Drive forward 3m while starting intake (intake stays on for the rest of auto).
      Commands.runOnce(() -> intake.setIntakePower(INTAKE_POWER), intake),
      driveDistanceMeters(drive, FORWARD_METERS_1, DRIVE_SPEED_MPS),

      // Drive backwards 3m (intake still on).
      driveDistanceMeters(drive, -FORWARD_METERS_1, DRIVE_SPEED_MPS),

      // Stop intake at the end.
      Commands.runOnce(() -> intake.setIntakePower(0.0), intake),
      Commands.runOnce(() -> drive.drive(0, 0, 0, false), drive),

      // Turn 90 degrees left
      turnRelativeDegrees(drive, 90.0),

      // Drive backward (back to the trench)
      driveDistanceMeters(drive, -BACKWARD_METERS_2, DRIVE_SPEED_MPS),

      // Turn 20 degrees left
      turnRelativeDegrees(drive, 20.0),

      // Bring hood up to HIGH angle.
      Commands.runOnce(() -> shooter.setHoodAngle(ShooterSubsystem.HoodAngle.HIGH), shooter),

      // Shooter
      Commands.runOnce(() -> {
        shooter.runIndexer(false);
        shooter.runKicker(false);
      }, shooter),
      Commands.run(() -> shooter.setShooterSpeed(ShooterConstants.SHOOTER_SPEED), shooter)
        .until(() -> shooter.getShooterVelocityRpm() >= ShooterConstants.SHOOTER_READY_RPM)
        .withTimeout(1.0),


      /*Run the intake, kicker, indexer, hopper, and intake arm together for a fixed time, 
      then stop the feeding mechanisms so another driving path can be added.*/
      // Keep intake running while the intake arm cycles up/down during shooting.
      Commands.runOnce(() -> intake.setIntakePower(INTAKE_POWER), intake),

      Commands.parallel(
        // Start kicker first, then start indexer 1 second later (kicker keeps running).
        Commands.sequence(
          Commands.run(() -> {
            shooter.setKickerSpeed(ShooterConstants.KICKER_SPEED);
            shooter.setIndexerSpeed(0.0);
          }, shooter).withTimeout(1.0),
          Commands.run(() -> {
            shooter.setKickerSpeed(ShooterConstants.KICKER_SPEED);
            shooter.setIndexerSpeed(ShooterConstants.INDEXER_SPEED);
            shooter.setHopperSpeed(ShooterConstants.HOPPER_SPEED);
          }, shooter)
          )
        ),

      Commands.waitSeconds(1),

      // While feeding, continuously move the intake arm up/down for the fixed feed time.
      Commands.sequence(
          Commands.runOnce(intake::lowerIntake, intake),
          Commands.waitUntil(() ->
            Math.abs(intake.getArmPositionDeg() - IntakeConstants.INTAKE_ARM_LOWERED_POSITION)
              <= IntakeConstants.INTAKE_ARM_TOLERANCE_DEG),
          Commands.runOnce(intake::raiseIntake, intake),
          Commands.waitUntil(() ->
            Math.abs(intake.getArmPositionDeg() - IntakeConstants.INTAKE_ARM_RAISED_POSITION)
              <= IntakeConstants.INTAKE_ARM_TOLERANCE_DEG)
        )
        .repeatedly()
        .withTimeout(FEED_DURATION_SEC),



        
        //Go to the neutral zone a second time.
        // Turn 20 degrees right
        turnRelativeDegrees(drive, -20.0),

        // Drive FORWARD (back to the trench)
        driveDistanceMeters(drive, BACKWARD_METERS_2, DRIVE_SPEED_MPS),

        // Turn 90 degrees right
        turnRelativeDegrees(drive, -90.0),

        // Drive forwards 3m (intake still on).
        driveDistanceMeters(drive, FORWARD_METERS_1, DRIVE_SPEED_MPS),
        Commands.runOnce(() -> intake.setIntakePower(INTAKE_POWER), intake),

        // Drive backwards 3m (intake still on).
        driveDistanceMeters(drive, -FORWARD_METERS_1, DRIVE_SPEED_MPS),

        // Stop intake at the end.
       Commands.runOnce(() -> intake.setIntakePower(0.0), intake),
        Commands.runOnce(() -> drive.drive(0, 0, 0, false), drive),

        // Turn 90 degrees left
        turnRelativeDegrees(drive, 90.0),

        // Drive backward (back to the trench)
        driveDistanceMeters(drive, -BACKWARD_METERS_2, DRIVE_SPEED_MPS),

        // Turn 20 degrees left
        turnRelativeDegrees(drive, 20.0),

        // Bring hood up to HIGH angle.
        Commands.runOnce(() -> shooter.setHoodAngle(ShooterSubsystem.HoodAngle.HIGH), shooter),

        Commands.parallel(
        // Start kicker first, then start indexer 1 second later (kicker keeps running).
        Commands.sequence(
          Commands.run(() -> {
            shooter.setKickerSpeed(ShooterConstants.KICKER_SPEED);
            shooter.setIndexerSpeed(0.0);
          }, shooter).withTimeout(1.0),
          Commands.run(() -> {
            shooter.setKickerSpeed(ShooterConstants.KICKER_SPEED);
            shooter.setIndexerSpeed(ShooterConstants.INDEXER_SPEED);
            shooter.setHopperSpeed(ShooterConstants.HOPPER_SPEED);
          }, shooter)
        ),

        Commands.waitSeconds(1),

        // While shooting/indexing, continuously move the intake arm up/down.
        Commands.sequence(
            Commands.runOnce(intake::lowerIntake, intake),
            Commands.waitUntil(() ->
              Math.abs(intake.getArmPositionDeg() - IntakeConstants.INTAKE_ARM_LOWERED_POSITION)
                <= IntakeConstants.INTAKE_ARM_TOLERANCE_DEG),
            Commands.runOnce(intake::raiseIntake, intake),
            Commands.waitUntil(() ->
              Math.abs(intake.getArmPositionDeg() - IntakeConstants.INTAKE_ARM_RAISED_POSITION)
                <= IntakeConstants.INTAKE_ARM_TOLERANCE_DEG)
          )
          .repeatedly()

      )
    );
  }

  private static edu.wpi.first.wpilibj2.command.Command driveDistanceMeters(
    SwerveSubsystem drive,
    double distanceMeters,
    double speedMps) {
    double clampedSpeedMps = MathUtil.clamp(Math.abs(speedMps), 0.0, SwerveConstants.maxSpeed);
    double commandedSpeedMps = Math.copySign(clampedSpeedMps, distanceMeters);
    double distanceAbsMeters = Math.abs(distanceMeters);

    AtomicReference<SwerveModulePosition[]> startPositions = new AtomicReference<>();
    final double[] startYawRad = new double[1];

    return Commands.sequence(
      Commands.runOnce(() -> {
        startPositions.set(drive.getPositions());
        startYawRad[0] = drive.getYaw().getRadians();
      }, drive),
      Commands.runEnd(
        () -> {
          double errorRad = MathUtil.angleModulus(startYawRad[0] - drive.getYaw().getRadians());
          double maxOmegaRadPerSec =
            Math.min(DRIVE_HEADING_MAX_OMEGA_RAD_PER_SEC, SwerveConstants.maxAngularVelocity);
          double omegaRadPerSec =
            MathUtil.clamp(errorRad * DRIVE_HEADING_P, -maxOmegaRadPerSec, maxOmegaRadPerSec);
          drive.drive(commandedSpeedMps, 0, omegaRadPerSec, false);
        },
        () -> drive.drive(0, 0, 0, false),
        drive)
        .until(
          () -> getAverageWheelDeltaMeters(startPositions.get(), drive.getPositions()) >= distanceAbsMeters)
        .withTimeout(distanceAbsMeters / Math.max(0.1, Math.abs(commandedSpeedMps)) + 1.0)
    );
  }

  private static double getAverageWheelDeltaMeters(
    SwerveModulePosition[] startPositions,
    SwerveModulePosition[] currentPositions) {
    if (startPositions == null || currentPositions == null) {
      return 0.0;
    }

    int count = Math.min(startPositions.length, currentPositions.length);
    if (count <= 0) {
      return 0.0;
    }

    double sum = 0.0;
    for (int i = 0; i < count; i++) {
      sum += Math.abs(currentPositions[i].distanceMeters - startPositions[i].distanceMeters);
    }
    return sum / count;
  }

  private static edu.wpi.first.wpilibj2.command.Command turnRelativeDegrees(
    SwerveSubsystem drive,
    double deltaDegrees) {
    final double[] startYawRad = new double[1];

    return Commands.sequence(
      Commands.runOnce(() -> startYawRad[0] = drive.getYaw().getRadians(), drive),
      Commands.runEnd(
        () -> {
          double targetYawRad = startYawRad[0] + Math.toRadians(deltaDegrees);
          double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
          double omegaRadPerSec =
            MathUtil.clamp(
              errorRad * TURN_P,
              -SwerveConstants.maxAngularVelocity,
              SwerveConstants.maxAngularVelocity);
          drive.drive(0, 0, omegaRadPerSec, false);
        },
        () -> drive.drive(0, 0, 0, false),
        drive)
        .until(() -> {
          double targetYawRad = startYawRad[0] + Math.toRadians(deltaDegrees);
          double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
          return Math.abs(errorRad) < Math.toRadians(TURN_TOLERANCE_DEG);
        })
        .withTimeout(TURN_TIMEOUT_SEC)

    );
  }
}
