Source code file: Node.java
Test code file: CheckpointTester.java

Launcher script for Source code: launcher.sh
command: ./launcher.sh <configFileName.txt> <NetID>

Clean-up script to kill all java jobs: cleanup.sh
command: ./cleanup.sh <configFileName.txt> <NetID>

Testing script file: testCheckpoint.sh
command: ./testCheckpoint.sh <configFileName.txt>

Steps to execute:
1. Run Launcher script
2. Run cleanup script once execution of source code is finished
3. All nodes will generate logs-<nodeId>.out files for vector clocks plus FullLogs-<nodeId>.out for complete logs.
4. Run testing script to evaluate if Global Consistent State achieved for each recovery which uses all log files generated.

### Source code is self explainatory through all javadoc comments ### 
 