/**
 * This class is responsible for generating single OUTGOING message QUEUE which is used by all server threads
 * to push messages generated and by client thread to pop messages to be sent.
 * All threads in turn are synchronized using this queue.
 * 
 * @author Bharat M Bhavsar, Sri Vidya Varanasi, Arvind Pandiyan
 * @version 1.0
 * @since 05/03/2016
 */


import java.util.LinkedList;
import java.util.Queue;


public class SendMesageQueue {
	public static Queue<Message> sendQueue = new LinkedList<Message>();
}
