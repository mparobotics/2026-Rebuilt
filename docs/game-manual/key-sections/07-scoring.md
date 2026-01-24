# Section 6.5: Scoring

---


## Page 44

 
Section 6 Game Details V0 - Kickoff 44 of 162 
The expected result of the dispersal is a roughly equal split of FUEL on both sides of the CENTER LINE. The 
dispersal between the scoring table side and non-scoring table side of the field is expected to vary match to 
match. 
The placement of FUEL in the NEUTRAL ZONE is not intended to be in a perfect 
grid. Teams should expect variances in the placement of FUEL in the NEUTRAL 
ZONE at the start of the MATCH. 
6.4 MATCH Periods 
The first period of each MATCH is 20 seconds long and called the Autonomous Period (AUTO). During AUTO, 
ROBOTS operate without any DRIVE TEAM control or input. ROBOTS score SCORING ELEMENTS, may leave 
their ROBOT STARTING LINE and retrieve additional SCORING ELEMENTS, and may climb their TOWER. There 
is a 3-second delay between AUTO and TELEOP for scoring purposes as described in section 6.5 Scoring. 
The second period of each MATCH is the remaining 2 minutes and 20 seconds (2:20) and called the 
Teleoperated Period (TELEOP). During TELEOP, DRIVERS remotely operate ROBOTS to retrieve and score 
SCORING ELEMENTS and climb their TOWER. TELEOP is split into further segments: the TRANSITION SHIFT, 
the ALLIANCE SHIFTS, and END GAME. There are four ALLIANCE SHIFTS named SHIFT 1, SHIFT 2, SHIFT 3, 
and SHIFT 4. The duration of each timeframe is shown in Table 6-2. 
Table 6-2: MATCH SHIFTS 
MATCH Period MATCH Timeframe Duration Timer Values 
AUTO AUTO 20 Seconds 0:20 – 0:00 
TELEOP TRANSITION SHIFT 10 Seconds 2:20 – 2:10 
SHIFT 1 25 Seconds 2:10 – 1:45 
SHIFT 2 25 Seconds 1:45 – 1:20 
SHIFT 3 25 Seconds 1:20 – 0:55 
SHIFT 4 25 Seconds 0:55 – 0:30 
END GAME 30 Seconds 0:30 – 0:00 
6.4.1 HUB Status 
During the MATCH, the status of a HUB can be either active or inactive. FUEL scored in an active HUB is worth 
MATCH points but FUEL scored in an inactive HUB will not earn any points as shown in Table 6-3. Both 
ALLIANCE HUBS are active during AUTO, the TRANSITION SHIFT, and END GAME. During the ALLIANCE 
SHIFTS, only one ALLIANCE HUB will be active while the other ALLIANCE’S HUB becomes inactive.  
The status of both HUBS during the ALLIANCE SHIFTS is based on the results of AUTO. The ALLIANCE that 
scores the most FUEL during AUTO will have their HUB set to inactive for SHIFT 1 while their opponent’s HUB 
will be active, as shown in Table 6-3. HUB statuses will then alternate at the start of each following ALLIANCE 
SHIFT, until the start of END GAME where both HUBS return to active. If both ALLIANCES score the same 
number of FUEL during AUTO, the FMS will randomly select an ALLIANCE and use its HUB status order for the 
ALLIANCE SHIFTS during the MATCH.  

---


## Page 45

 
Section 6 Game Details V0 - Kickoff 45 of 162 
FMS relays the ALLIANCE who scored more FUEL during AUTO, or the ALLIANCE 
selected by FMS, to all OPERATOR CONSOLES simultaneously at the start of 
TELEOP.  
Specific details on the format of the data can be found on the 2026 FRC Control 
System website. 
Table 6-3: Hub Status during MATCH Timeframes 
AUTO Result: RED ALLIANCE scores more FUEL 
during AUTO or is selected by the FMS 
BLUE ALLIANCE scores more FUEL 
during AUTO or is selected by the FMS 
MATCH Timeframe 
(timer values) 
RED ALLIANCE 
HUB status 
BLUE ALLIANCE 
HUB status 
RED ALLIANCE 
HUB status 
BLUE ALLIANCE 
HUB status 
AUTO 
(0:20 – 0:00) 
Active Active Active Active 
TRANSITION SHIFT 
(2:20 – 2:10) 
Active Active Active Active 
SHIFT 1 
(2:10 – 1:45) 
Inactive Active Active Inactive 
SHIFT 2 
(1:45 – 1:20) 
Active Inactive Inactive Active 
SHIFT 3 
(1:20 – 0:55) 
Inactive Active Active Inactive 
SHIFT 4 
(0:55 – 0:30) 
Active Inactive Inactive Active 
END GAME 
(0:30 – 0:00) 
Active Active Active Active 
6.5 Scoring  
ALLIANCES are rewarded for accomplishing various actions throughout a MATCH, including scoring FUEL, 
climbing their TOWER, and winning or tying MATCHES. 
Rewards are granted either via MATCH points or Ranking Points (often abbreviated to RP, which increase the 
measure used to rank teams in the Qualification Tournament).  
All scores are assessed and updated throughout the MATCH, except as follows:  
A. assessment of FUEL scored in the HUB continues for up to 3 seconds after the ARENA timer displays 
0:00 following AUTO. 
B. assessment of FUEL scored in the HUB continues for up to 3 seconds after the ARENA timer displays 
0:00 following TELEOP. 
C. assessment of AUTO TOWER points is made after the ARENA timer displays 0:00 following AUTO. 
D. assessment of TELEOP TOWER points is made 3 seconds after the ARENA timer displays 0:00 
following TELEOP, or when all ROBOTS have come to rest following the conclusion of the MATCH, 
whichever happens first.  

---


## Page 46

 
Section 6 Game Details V0 - Kickoff 46 of 162 
Assessment of FUEL scored in the HUB continues for 3 seconds after the HUB deactivates to account for FUEL 
processing time. 
TOWER points are evaluated and scored by human volunteers. Teams are 
encouraged to make sure that it is obvious and unambiguous that the criteria are 
met. 
6.5.1 SCORING ELEMENT Scoring Criteria 
A FUEL is scored in the HUB once it passes through the top opening of the HUB and through the sensor array. 
6.5.2 ROBOT Scoring Criteria 
To qualify for TOWER points for a given LEVEL, a ROBOT must meet the following conditions:  
− For LEVEL 1 – a ROBOT must no longer touching the CARPET or the TOWER BASE, or 
− For LEVEL 2 – a ROBOT must be positioned such that its BUMPERS are completely above the LOW 
RUNG, or 
− For LEVEL 3 – a ROBOT must be positioned such that its BUMPERS are completely above the MID 
RUNG. 
Additionally, a ROBOT must be contacting the RUNGS or UPRIGHTS and may additionally only contact the 
following elements: 
A. the TOWER WALL, 
B. support structure, 
C. FUEL, and/or.  
D. another ROBOT.  
A ROBOT may only earn TOWER points for LEVEL 1 during AUTO. A ROBOT may only earn TOWER points for a 
single LEVEL during TELEOP.  
Figure 6-4: TOWER contact limitation for ROBOT Scoring Criteria 
 


---


## Page 47

 
Section 6 Game Details V0 - Kickoff 47 of 162 
6.5.3 Point Values 
Point values for tasks in REBUILT are detailed in Table 6-4. 
Table 6-4 REBUILT point values 
  MATCH points Ranking 
Points AUTO  TELEOP 
FUEL FUEL scored in an active HUB 1 1  
FUEL scored in an inactive HUB - - 
TOWER Each ROBOT at LEVEL 1 (2 ROBOTS max in 
AUTO) 
15 10 
Each ROBOT at LEVEL 2 - 20 
Each ROBOT at LEVEL 3 30 
*ENERGIZED RP – The amount of FUEL scored in the HUB is at or above threshold. 1 
*SUPERCHARGED RP – The amount of FUEL scored in the HUB is at or above threshold.  1 
*TRAVERSAL RP – The amount of TOWER points scored during the MATCH is at or 
above threshold. 
1 
Win completing a MATCH with more MATCH points than your 
opponent 
3 
Tie completing a MATCH with the same number of MATCH points as 
your opponent 
1 
*See Table 6-5 for threshold values. For District Championship and/or FIRST Championship events, 
the BONUS RP (ENERGIZED RP, SUPERCHARGED RP, and TRAVERSAL RP) requirement thresholds 
may increase. 
 
Table 6-5: REBUILT BONUS RP thresholds 
BONUS RP Type Regional/ 
District Events 
District 
Championships 
FIRST 
Championship 
ENERGIZED RP 100 TBA TBA 
SUPERCHARGED RP 360 TBA TBA 
TRAVERSAL RP 50 TBA TBA 
BONUS RP thresholds for District Championships and FIRST Championship will 
be announced in Team Updates.  
  

---


## Page 48

 
Section 6 Game Details V0 - Kickoff 48 of 162 
6.6 Violations 
Unless otherwise noted, all violations are assigned for each instance of a rule violation. A description of the 
penalties is listed in Table 6-6. All rules throughout the Game Rules section are called as perceived by a 
REFEREE.  
Table 6-6 Rule violations 
Penalty Description 
MINOR FOUL a credit of 5 points towards the opponent’s MATCH point total 
MAJOR FOUL a credit of 15 points towards the opponent’s MATCH point total 
YELLOW CARD issued by the Head REFEREE for egregious ROBOT or team member 
behavior or rule violations. A subsequent YELLOW CARD within the 
same tournament phase results in a RED CARD. 
RED CARD issued by the Head REFEREE for egregious ROBOT or team member 
behavior or rule violations which results in a team being 
DISQUALIFIED for the MATCH.  
DISABLED the state in which a ROBOT is commanded to deactivate all outputs, 
rendering the ROBOT inoperable for the remainder of the MATCH.  
DISQUALIFIED the state of a team in which they receive 0 MATCH points and 0 
Ranking Points in a Qualification MATCH or causes their ALLIANCE to 
receive 0 MATCH points in a Playoff MATCH 
VERBAL 
WARNING 
a warning issued by event staff or the Head REFEREE.  
ALLIANCE is 
ineligible for RP 
An ALLIANCE is ineligible for the specified RP for that MATCH. This 
overrides any RP awarded through normal MATCH play or other rule 
violations. 
6.6.1 YELLOW and RED CARDS 
In addition to rule violations explicitly listed throughout this document, YELLOW CARDS and RED CARDS are 
used in FIRST Robotics Competition to address team and ROBOT behavior that does not align with the mission, 
values, and culture of FIRST. 
The Head REFEREE may assign a YELLOW CARD as a warning, or a RED CARD for egregious behavior 
inappropriate at a FIRST Robotics Competition event. 
If an action resulting in the assignment of a YELLOW or RED CARD is determined to be the result of an ARENA 
FAULT, per section 10.2, the CARD will be rescinded. 
A YELLOW or RED CARD is indicated on the audience display MATCH results screen. During Qualification 
MATCHES, A YELLOW or RED CARD is indicated next to the team who received the CARD and the Game 
Announcer describes the violation. During Playoff MATCHES, the card is applied to the whole ALLIANCE and as 
such “RED CARD" or "YELLOW CARD" appears above the ALLIANCE number. 

---

