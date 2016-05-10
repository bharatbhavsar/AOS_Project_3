/**
 * Receiver thread for the node which receives messages sent to current node on a given input stream.
 * It processes the received message through receivedMessage() method from Node class.
 * There will be neighbor.length() number of threads of this class to maintain persistent connection
 * with all neighbor clients. Synchronized on Node object. 
 * 
 * @author Bharat M Bhavsar, Sri Vidya Varanasi, Arvind Pandiyan
 * @version 1.0
 * @since 05/03/2016
 */


import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;

public class TcpReceiver implements Runnable {

    
    private final ObjectInputStream inputStream;
    
    public TcpReceiver(
            final ObjectInputStream inputStream) {

        this.inputStream = inputStream;
        
    }

    @Override
    public void run() {

        while(true) {
            Message receivedMessage = null;
            try {
            	synchronized (inputStream) {
                    receivedMessage = (Message) inputStream.readObject();
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }catch(EOFException e){
            
            }
            catch (IOException e) {

            }
            try {
            	if(receivedMessage!=null){
            		Node.object.receiveMessage(receivedMessage);
            	}
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (IOException e) {
			
			}
        }
    }
}