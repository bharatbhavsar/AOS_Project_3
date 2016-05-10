/**
 * This class is responsible for sending all generated messages to respective neighbors
 * on persistent outgoing connection streams. This is Client thread.
 * 
 * @author Bharat M Bhavsar, Sri Vidya Varanasi, Arvind Pandiyan
 * @version 1.0
 * @since 05/03/2016
 */


import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.UnknownHostException;

public class TcpSender implements Runnable {

    /**
     * Constructor
     * 
     */
    public TcpSender() {
        
    }

    @Override
    public void run() {
    	while(true){
    		synchronized(SendMesageQueue.sendQueue){
    			if(!SendMesageQueue.sendQueue.isEmpty()){
	    			Message m = SendMesageQueue.sendQueue.poll();    
	    			ObjectOutputStream	currentOutPutStream = Node.writerStreamMap.get(m.receiverId);
	    			try {
	    	            synchronized (currentOutPutStream) {
			    			currentOutPutStream.writeObject(m);
			    			currentOutPutStream.flush();
	    	            }
	    			}
	    			catch (UnknownHostException e) {

	    			} catch (IOException e) {
			        
	    			}
    			}
    		}       
    	}
    }
}