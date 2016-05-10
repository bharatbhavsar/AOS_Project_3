/**
 * This class is used to generate Message object for all types of messages both for
 * APPLICATION and RECOVERY. Details for different types of messages given below.
 * 
 * @author Bharat M Bhavsar, Sri Vidya Varanasi, Arvind Pandiyan
 * @version 1.0
 * @since 05/03/2016
 */

import java.io.Serializable;


public class Message implements Serializable {

	private static final long serialVersionUID = 1L;
	static final int APPLICATION_MSG = 0; 
	static final int RECOVERY_MSG = 1; 
	static final int BROADCAST_MSG = 2;
	static final int CONVERGE_MSG = 3; 
	int messageType;
	int subMessageType;
	/**
	 * if messageType == APPLICATION_MSG
	 * 		No subMessageType
	 * 			dataField - message Number 
	 * if messageType == RECOVERY_MSG
	 * 		subMessageType = 0 means RecoveryStarted
	 * 			dataField - EMPTY
	 * 		subMessageType = 1 means RecoveryRounds
	 * 			dataField - sent Messages
	 * 			roundNumber - which round out of DIAMETER number of rounds (for All other messages its empty)
	 * 		subMessageType = 2 means FinalReceived After GlobalConsistentState
	 * 			dataField - received Messages
	 * if messageType == BROADCAST_MSG
	 * 		subMessageType = 0 means Continue
	 * 			dataField - EMPTY
	 * 		subMessageType = 1 means Stop
	 * 			dataField - EMPTY
	 * if messageType == CONVERGE_MSG
	 * 		subMessageType = 0 means RollBackTrue
	 * 			dataField - EMPTY
	 * 		subMessageType = 1 means RollBackFalse
	 * 			dataField - EMPTY
	 */
	int senderId;
	int starterId;
	int[] vectorClock;
	int dataField;
	int roundNumber;
	int receiverId;
	
	public Message(){

	}
	public Message(int messageType, int subMessageType){
		this.messageType = messageType;
		this.subMessageType = subMessageType;
		
	}
}