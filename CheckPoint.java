/**
 * This is class to generate CHECKPOINT object when node takes checkpoint
 * 
 * @author Bharat M Bhavsar, Sri Vidya Varanasi, Arvind Pandiyan
 * @version 1.0
 * @since 05/03/2016
 */

import java.util.ArrayList;

public class CheckPoint {
	int[] sentVector,recvdVector; // vector to store sent and received messages count
	int noOfMessagesSent ; // total number of messages sent
	int[] vectorClock; // Vector clock
	ArrayList<Message> sentMessages; // Messages sent till now
	boolean active; //Node status
	int messagesSentInREB; // Messages sent in current REB instance
	int lastNeighborIndex;	// index of neighbor to whom last message sent in REB when checkpoint taken
}