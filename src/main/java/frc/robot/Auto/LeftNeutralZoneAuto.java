// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Auto;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import frc.robot.Constants.ShooterConstants;
import frc.robot.Constants.SwerveConstants;
import frc.robot.Subsystems.SwerveSubsystem;
import frc.robot.Subsystems.IntakeSubsystem;
import frc.robot.Subsystems.ShooterSubsystem;

import java.util.concurrent.atomic.AtomicReference;

public class LeftNeutralZoneAuto extends SequentialCommandGroup {
  private static final double DRIVE_SPEED_MPS = 1.0;
  private static final double TURN_P = 4.0;
  private static final double TURN_TOLERANCE_DEG = 3.0;
  private static final double TURN_TIMEOUT_SEC = 2.5;

  private static final double BACKWARD_METERS_1 = 3.4;
  private static final double FORWARD_METERS_1 = 3.0;
  private static final double FORWARD_METERS_2 = 1.0;
  private static final double FORWARD_METERS_3 = 3.1;
  private static final double FORWARD_METERS_4 = 1.0;

  private static final double INTAKE_POWER = -1.0;

  public LeftNeutralZoneAuto(SwerveSubsystem drive, IntakeSubsystem intake, ShooterSubsystem shooter) {
    addRequirements(drive, intake, shooter);

    addCommands(
      Commands.runOnce(intake::lowerIntake, intake),

      // Drive backwards 3.4m.
      driveDistanceMeters(drive, -BACKWARD_METERS_1, DRIVE_SPEED_MPS),

      // Turn 90 degrees left.
      turnRelativeDegrees(drive, 90.0),

      // Drive forward 3m while starting intake (intake stays on for the rest of auto).
      Commands.runOnce(() -> intake.setIntakePower(INTAKE_POWER), intake),
      driveDistanceMeters(drive, FORWARD_METERS_1, DRIVE_SPEED_MPS),

      // Turn 90 degrees right (intake still on).
      turnRelativeDegrees(drive, -90.0),

      // Drive forward 1m (intake still on).
      driveDistanceMeters(drive, FORWARD_METERS_2, DRIVE_SPEED_MPS),

      // Turn 90 degrees right (intake still on).
      turnRelativeDegrees(drive, -90.0),

      // Drive forward 3m (intake still on).
      driveDistanceMeters(drive, FORWARD_METERS_3, DRIVE_SPEED_MPS),

      // Stop intake at the end.
      Commands.runOnce(() -> intake.setIntakePower(0.0), intake),
      Commands.runOnce(() -> drive.drive(0, 0, 0, false), drive),

      // Turn 90 degrees left
      turnRelativeDegrees(drive, 90.0),

      // Drive forward (back to the trench)
      driveDistanceMeters(drive, FORWARD_METERS_4, DRIVE_SPEED_MPS),

      // Turn 10 degrees left
      turnRelativeDegrees(drive, 10.0),

      // Bring hood up to HIGH angle.
      Commands.runOnce(() -> shooter.setHoodAngle(ShooterSubsystem.HoodAngle.HIGH), shooter),

      // Start kicker first, then start indexer 1 second later (kicker keeps running).
      Commands.sequence(
        Commands.run(() -> {
          shooter.setKickerSpeed(ShooterConstants.KICKER_SPEED);
          shooter.setIndexerSpeed(0.0);
        }, shooter).withTimeout(1.0),
        Commands.run(() -> {
          shooter.setKickerSpeed(ShooterConstants.KICKER_SPEED);
          shooter.setIndexerSpeed(ShooterConstants.INDEXER_SPEED);
        }, shooter)
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

    AtomicReference<Pose2d> startPose = new AtomicReference<>(new Pose2d());

    return Commands.sequence(
      Commands.runOnce(() -> startPose.set(drive.getPose()), drive),
      Commands.runEnd(
        () -> drive.drive(commandedSpeedMps, 0, 0, false),
        () -> drive.drive(0, 0, 0, false),
        drive)
        .until(
          () ->
            drive.getPose().getTranslation().getDistance(startPose.get().getTranslation())
              >= distanceAbsMeters)
        .withTimeout(distanceAbsMeters / Math.max(0.1, Math.abs(commandedSpeedMps)) + 1.0)
    );
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
