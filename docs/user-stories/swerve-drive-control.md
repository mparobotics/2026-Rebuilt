## User Story: Teleop Swerve Control

**As a** robot driver,  
**I want to** control the robot's movement using swerve drive with both field-oriented and robot-oriented driving modes,  
**so that** I can precisely navigate and maneuver the FRC field during teleop operation.

### Acceptance Criteria

**Given** the robot is in teleop mode and the swerve subsystem is initialized with four swerve modules,  
**When** I provide drive inputs (x-velocity, y-velocity, rotation) in field-oriented mode,  
**Then** the robot should move relative to the field coordinate system, maintaining orientation based on the Pigeon2 IMU yaw.

**Given** the robot is in teleop mode and the swerve subsystem is initialized,  
**When** I provide drive inputs in robot-oriented mode,  
**Then** the robot should move relative to its own coordinate system, where forward is always the front of the robot.

**Given** the robot is in teleop, auto, or test mode,  
**When** the swerve subsystem is running,  
**Then** it should publish both actual and desired swerve module states to NetworkTables for real-time monitoring and debugging.

**Given** the robot is on the red alliance and in any mode,  
**When** the gyro is zeroed,  
**Then** the Pigeon2 yaw should be set to 180 degrees to account for alliance-specific field orientation.

**Given** the robot is in teleop mode and driving with commanded chassis speeds,  
**When** the desired wheel speeds exceed physical limits,  
**Then** all wheel speeds should be proportionally desaturated while maintaining the intended motion direction.