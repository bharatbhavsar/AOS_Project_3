/**
 * This class is used to generate REB messages based on given conditions as per protocol
 * described in project description.
 * 
 * @author Bharat M Bhavsar, Sri Vidya Varanasi, Arvind Pandiyan
 * @version 1.0
 * @since 05/03/2016
 */

public class REB {
	boolean active;
	int msgsSent;
	int lastNeighborIndex;
	
	/**
	 * Toss to determine if message will be sent to the selected node.
	 * 
	 * @return
	 */
	public boolean randomToss(){
		return Math.random() < 0.5;
	}
	
	/**
	 * Constructor
	 * 
	 * @param active
	 * @param msgsSent
	 * @param lastNeigbhorIndex
	 */
	public REB(boolean active,int msgsSent,int lastNeigbhorIndex){
		this.active = active;
		this.msgsSent = msgsSent;
		this.lastNeighborIndex = lastNeigbhorIndex;
		
	}
	
	/**
	 * Method to send REB messages to random subset of neighbors with some predefined restrictions.
	 * For restriction please read project description.
	 * 
	 * @throws InterruptedException
	 */
	public void runREBRound() throws InterruptedException{
		if(!Node.isJVFinished){
			int messageSent=0;
			while(lastNeighborIndex < Node.object.neighbors.length && !Node.object.recovery){
				if(randomToss()&& msgsSent < Node.object.maxPerActive){
					Message rebMessage = new Message();
					rebMessage.messageType=Message.APPLICATION_MSG;
					rebMessage.receiverId=Integer.parseInt(Node.object.neighbors[lastNeighborIndex]);
					rebMessage.dataField = Node.object.sentMessagesVector[rebMessage.receiverId]++;
					rebMessage.senderId = Node.object.nodeId;
					msgsSent++;
					messageSent++;
					lastNeighborIndex++;
					if(lastNeighborIndex ==Node.object.neighbors.length){
						this.active=false;
					}
					Node.object.sendApplicationMessage(rebMessage,active,msgsSent,lastNeighborIndex);
					Thread.sleep(Node.object.interMessageDelay);
					
				}
				if(randomToss()){
					break;
				}
			}
			//This block makes sure at least one message is sent.
			if(messageSent == 0){
				int randomNum = (int)(Math.random() * (Node.object.neighbors.length-1));
				Message rebMessage = new Message();
				rebMessage.messageType=Message.APPLICATION_MSG;
				rebMessage.receiverId=Integer.parseInt(Node.object.neighbors[randomNum]);
				rebMessage.dataField = Node.object.sentMessagesVector[rebMessage.receiverId]++;
				rebMessage.senderId = Node.object.nodeId;
				msgsSent++;
				lastNeighborIndex = randomNum +1;
				Node.object.sendApplicationMessage(rebMessage,active,msgsSent,lastNeighborIndex);
				
			}
		}
	}
}