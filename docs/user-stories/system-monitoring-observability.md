## User Story: System Monitoring and Observability

**As a** robot programmer or engineer,  
**I want to** monitor real-time robot state and system performance through comprehensive data publishing,  
**so that** I can debug issues, tune performance, and understand robot behavior during operation.

### Acceptance Criteria

**Given** the robot is in teleop, auto, or test mode with swerve subsystem running,  
**When** the swerve modules are active,  
**Then** both actual and desired swerve module states should be published to NetworkTables for external monitoring tools.

**Given** the robot is operating in any mode with IMU initialized,  
**When** the Pigeon2 gyro is active,  
**Then** yaw angle should be published to SmartDashboard for real-time heading monitoring.

**Given** the robot is in any mode with swerve modules initialized,  
**When** the periodic loop runs,  
**Then** individual module data (CANCoder angles, integrated angles, velocities) should be published to SmartDashboard.

**Given** the robot is operating with odometry tracking enabled,  
**When** pose estimation is active,  
**Then** current robot pose and field visualization should be published to SmartDashboard.

**Given** the robot has vision systems configured,  
**When** limelight cameras are connected and detecting targets,  
**Then** vision data and pose corrections should be available through NetworkTables for monitoring.

**Given** the robot is in any operational mode,  
**When** subsystems are initialized,  
**Then** all published data should be accessible through AdvantageScope, Shuffleboard, or custom dashboards for analysis.

**Given** the robot is experiencing performance issues or unexpected behavior,  
**When** monitoring data is reviewed,  
**Then** the published values should accurately reflect actual sensor readings and commanded states for debugging.