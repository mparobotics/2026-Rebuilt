// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Auto;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import frc.robot.Constants.SwerveConstants;
import frc.robot.Subsystems.SwerveSubsystem;
import frc.robot.Subsystems.IntakeSubsystem;


public class LeftNeutralZoneAuto extends SequentialCommandGroup {
  // Tune these values to change how far/fast the auto drives.
  private static final double INITIAL_BACKUP_DISTANCE_METERS = 3.6;
  // Positive value; the auto will negate it to drive backwards.
  private static final double INITIAL_BACKUP_SPEED_MPS = 2.0;
  private static final double FORWARD_DISTANCE_METERS = 4.0;
  private static final double FORWARD_SPEED_MPS = 2.0;

  public LeftNeutralZoneAuto(SwerveSubsystem drive, IntakeSubsystem intake) {
    this(
      drive,
      intake,
      INITIAL_BACKUP_DISTANCE_METERS,
      INITIAL_BACKUP_SPEED_MPS,
      FORWARD_DISTANCE_METERS,
      FORWARD_SPEED_MPS);
  }

  public LeftNeutralZoneAuto(
    SwerveSubsystem drive,
    IntakeSubsystem intake,
    double driveDistanceMeters,
    double driveSpeedMps) {
    this(drive, intake, driveDistanceMeters, driveSpeedMps, FORWARD_DISTANCE_METERS, FORWARD_SPEED_MPS);
  }

  public LeftNeutralZoneAuto(
    SwerveSubsystem drive,
    IntakeSubsystem intake,
    double backupDistanceMeters,
    double backupSpeedMps,
    double forwardDistanceMeters,
    double forwardSpeedMps) {
    final double[] startYawRad = new double[1];
    final Pose2d[] startPose = new Pose2d[1];
    final Pose2d[] startPoseAfterTurn = new Pose2d[1];
    final double backupSpeedMpsClamped =
      -MathUtil.clamp(Math.abs(backupSpeedMps), 0.0, SwerveConstants.maxSpeed);
    final double forwardSpeedMpsClamped =
      MathUtil.clamp(Math.abs(forwardSpeedMps), 0.0, SwerveConstants.maxSpeed);

    addCommands(
      new InstantCommand(intake::lowerIntake, intake),

      // Drive backwards
      Commands.runOnce(() -> startPose[0] = drive.getPose(), drive),
      Commands.runEnd(
        () -> drive.drive(backupSpeedMpsClamped, 0, 0, false),
        () -> drive.drive(0, 0, 0, false),
        drive)
        .until(() -> drive.getPose().getTranslation().getDistance(startPose[0].getTranslation()) >= backupDistanceMeters)
        .withTimeout(Math.abs(backupDistanceMeters / Math.max(0.1, Math.abs(backupSpeedMpsClamped))) + 1.0),

      // Turn 90 degrees left (CCW) relative to current heading.
      Commands.runOnce(() -> startYawRad[0] = drive.getYaw().getRadians(), drive),
      Commands.run(() -> {
        double targetYawRad = startYawRad[0] + Math.toRadians(90.0);
        double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
        double omegaRadiansPerSecond =
          MathUtil.clamp(errorRad * 4.0, -SwerveConstants.maxAngularVelocity, SwerveConstants.maxAngularVelocity);
        drive.drive(0, 0, omegaRadiansPerSecond, false);
      }, drive).until(() -> {
        double targetYawRad = startYawRad[0] + Math.toRadians(90.0);
        double errorRad = MathUtil.angleModulus(targetYawRad - drive.getYaw().getRadians());
        return Math.abs(errorRad) < Math.toRadians(3.0);
      }),
      Commands.runOnce(() -> drive.drive(0, 0, 0, false), drive),

      // Drive forward after the turn.
      Commands.runOnce(() -> startPoseAfterTurn[0] = drive.getPose(), drive),
      Commands.runEnd(
        () -> drive.drive(forwardSpeedMpsClamped, 0, 0, false),
        () -> drive.drive(0, 0, 0, false),
        drive)
        .until(() -> drive.getPose()
          .getTranslation()
          .getDistance(startPoseAfterTurn[0].getTranslation()) >= forwardDistanceMeters)
        .withTimeout(Math.abs(forwardDistanceMeters / Math.max(0.1, Math.abs(forwardSpeedMpsClamped))) + 1.0)
    );
  }
}
