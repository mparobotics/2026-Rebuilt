## User Story: Vision-Assisted Driving

**As an** autonomous programmer,  
**I want to** integrate limelight vision data with odometry for accurate pose estimation,  
**so that** the robot can precisely navigate the field and align with targets during autonomous routines.

### Acceptance Criteria

**Given** the robot is in auto or teleop mode and equipped with limelight cameras and AprilTags are visible on the field,  
**When** the limelight detects AprilTags using MegaTag2 pose estimation,  
**Then** the vision measurements should be fused with odometry to correct the robot's pose estimate.

**Given** the robot is in auto or teleop mode and moving with vision targets available,  
**When** the angular velocity exceeds 720 degrees per second,  
**Then** vision updates should be rejected to prevent inaccurate pose corrections during high-speed rotations.

**Given** the robot is in auto or teleop mode and has multiple limelights configured,  
**When** AprilTags are detected by any limelight,  
**Then** each limelight should independently provide pose corrections to improve overall localization accuracy.

**Given** the robot is in auto or teleop mode and vision measurements are available and conditions are valid,  
**When** applying vision corrections,  
**Then** the system should use appropriate standard deviations (0.7m for x/y, high value for rotation) to weight the vision measurements relative to odometry.

**Given** the robot is in auto or teleop mode and no AprilTags are visible to the limelight,  
**When** the system attempts to get vision measurements,  
**Then** no pose correction should be applied and the system should continue using odometry-only positioning.

**Given** the robot is in auto or teleop mode and the robot orientation is known from odometry,  
**When** requesting vision pose estimates,  
**Then** the limelight should be set with the current robot orientation to improve MegaTag2 accuracy.