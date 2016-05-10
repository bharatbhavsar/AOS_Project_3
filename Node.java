/**
 * This is simulation of Juang Venkatesan Checkpointing and recovery Protocol.
 * This is not actual application and failure detection. We just input the failure
 * sequence and according to that sequence nodes fail based on failure condition for number of check points.
 * For more details on messaging application and how we generate failure sequence, please review the project.pdf.
 * Here we have implemented EARLY STOPPING protocol where nodes complete DIAMETER number
 * of rounds and then CONVERGECAST to root node regarding ROLLBACK condition. Based on information gathered
 * root node decides if to GO for next DIAMETER rounds or STOP. If node receives STOP then it stores its state as
 * CONSISTENT STATE else continue for next DIAMETER rounds. Each node locally takes checkpoint on receiving/ sending message.
 * A vector clock is maintained for sending and receiving messages.
 * Nodes will stop generating REB application messages once the failure sequence completed.
 * All logs will be stored in string and will be written to file once failure sequence completes.
 * 
 * @author Bharat M Bhavsar, Sri Vidya Varanasi, Arvind Pandiyan
 * @version 1.0
 * @since 05/03/2016
 */


import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Stack;


public class Node {
	//variables For Client Server Channels
	static Node object = new Node();
	static HashMap<Integer, Socket> socketMap = new HashMap<>();
	static HashMap<Integer, ObjectInputStream> readerStreamMap = new HashMap<>();
	static HashMap<Integer, ObjectOutputStream> writerStreamMap = new HashMap<>();
	
	//variables for initial argument values and other assisting values
	boolean active;
	int nodeId;
	int noOfNodes;
	String[] nodeNames;
	int[] nodePorts;
	String[] neighbors;
	int numberOfFailureEvents;
	int maxNum;
	int maxPerActive;
	int interMessageDelay;
	Queue<Integer> failingProcess = new LinkedList<Integer>(); 
	Queue<Integer> failingCheckpointNumbers = new LinkedList<Integer>();
	int parent;
	int[] children;
	int diameter;
	String fullLog="";
	String vectorLog="";
	FileOutputStream outFullLog=null;
	FileOutputStream outVectorLog=null;
	ArrayList<Thread> receiverThreadPool;
	Thread Sender;
	REB reb;
	
	//Variables For Juang-Venky Protocol
	int[] currentVectorClock;
	int[] sentMessagesVector,receivedMessagesVector;
	Stack<CheckPoint> CPStack = new Stack<CheckPoint>();
	int noOfMsgsSent = 0;
	ArrayList<Message> sentMessages=new ArrayList<Message>();
	boolean recovery;
	int recoveryStartCounter=0;
	int[] recoveryRoundCounter;
	int recoveryRoundIndex =0;
	int childrenConvergecastCounter=0;
	int recoveryRounds=0;
	int childrenRollbackResult=1;
	int myRollbackResult=1;
	int lastNeigborIndex;
	static boolean isJVFinished=false;
	int retransmitCheck=0;
	
	
	/**
	 * This method will be called by REB to add messages generated to outgoing queue
	 * which will be accessed by client thread. This method is responsible for taking checkpoint for
	 * sent messages.
	 * 
	 * @param sendingMessage
	 * @param active
	 * @param msgsSent
	 * @param lastNeighborIndex
	 */
	void sendApplicationMessage(Message sendingMessage,boolean active,int msgsSent,int lastNeighborIndex){
		synchronized(SendMesageQueue.sendQueue){
			if(noOfMsgsSent<=maxNum && !recovery){
				currentVectorClock[nodeId]++;
				sendingMessage.vectorClock = Arrays.copyOf(currentVectorClock, currentVectorClock.length);
				SendMesageQueue.sendQueue.add(sendingMessage);
				sentMessages.add(sendingMessage);
				sentMessagesVector[sendingMessage.receiverId]++;
				noOfMsgsSent++;
				fullLog += "Sent from " + nodeId + " to "+ sendingMessage.receiverId + " Vector: " + Arrays.toString(sendingMessage.vectorClock) + "\n";
				takeCheckpoint(active, msgsSent, lastNeighborIndex);
			}
		}
	}
	
	/**
	 * This part is responsible for accepting incoming connection and storing
	 * incoming stream and generating outgoing stream. This is called by setupChannel()
	 * as a thread and runs in parallel with setupChannel() to complete the socketmap.
	 * Terminated by setupChannel() once the socketMap is completed for all input and output streams.
	 * 
	 */
	class TcpListener implements Runnable {

	    public volatile boolean isRunning = true;
	    private final int id;
	    private final ServerSocket listenerSocket;
	    
	    
	    public TcpListener(final int id, final ServerSocket listenerSocket) {

	        this.id = id;
	        this.listenerSocket = listenerSocket;
	    }

	    @Override
	    public void run() {

	    	Socket connectionSocket = null;

	        try {

	            while (socketMap.size() < neighbors.length) {

	                try {
	                    connectionSocket = listenerSocket.accept();

	                    ObjectInputStream ois = new ObjectInputStream(connectionSocket.getInputStream());
	                    byte[] buff = new byte[4];
	                    ois.read(buff, 0, 4);
	                    ByteBuffer bytebuff = ByteBuffer.wrap(buff);
	                    int neighborNodeId = bytebuff.getInt();
	                    
	                    if(neighborNodeId == id) {
	                        readerStreamMap.put(neighborNodeId, ois);
	                        continue;
	                    }

	                    synchronized (socketMap) {
	                            socketMap.put(neighborNodeId, connectionSocket);
	                    }

	                    readerStreamMap.put(neighborNodeId, ois);
	                    writerStreamMap.put(neighborNodeId, new ObjectOutputStream(connectionSocket.getOutputStream()));

	                } catch (IOException e) {
	                    e.printStackTrace();
	                }
	            }
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	    }

	}
	
	/**
	 * This method is responsible to make sure that node has created persistent connection with all
	 * neighbors and stored their input and output streams which will be used by server and client threads
	 * for all incoming and outgoing messages respectively. This method generates TcpListener thread and
	 * runs in parallel for storing all incoming and outgoing streams until socketMap is finished for all neighbors.
	 *  
	 * @throws InterruptedException
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	void setupChannels() throws InterruptedException, UnknownHostException, IOException{
		TcpListener tcpListener = new TcpListener(nodeId,new ServerSocket(nodePorts[nodeId]));
        Thread listenerThread = new Thread(tcpListener);
        listenerThread.start();

        /**
         *  Wait for sometime so that all the nodes are initialized
         *  and their listener threads are up
         */
        Thread.sleep(3000);

        for (int i = 0; i<neighbors.length;++i) {

            int neighborNodeId = Integer.parseInt(neighbors[i]);
            if(neighborNodeId < nodeId) {
                continue;
            }
            if(socketMap.containsKey(neighborNodeId) && neighborNodeId != nodeId) {
                // Prune
                continue;
            }

            boolean connected = false;
            Socket sock = null;
            while(!connected) {
                try {
		            sock = new Socket(nodeNames[neighborNodeId]+".utdallas.edu", nodePorts[neighborNodeId]);
		            connected = true;
		            } 
                catch (ConnectException ce) {

                }
            }

            synchronized (socketMap) {
            	socketMap.put(neighborNodeId, sock);
            }
            ByteBuffer dbuf = ByteBuffer.allocate(4);
            dbuf.putInt(nodeId);
            ObjectOutputStream oos = new ObjectOutputStream(sock.getOutputStream());
            byte[] bytes = dbuf.array();
            oos.write(bytes);
            oos.flush();
            oos.reset();
            writerStreamMap.put(neighborNodeId, oos);
            if(neighborNodeId == nodeId) {
                continue;
            }
            readerStreamMap.put(neighborNodeId, new ObjectInputStream(sock.getInputStream()));
        }

        while (socketMap.size() < neighbors.length) {
            Thread.sleep(3000);
        }
        listenerThread.interrupt();
	}
	
	
	/**
	 * This method makes all inputStreams mapped as server threads which were stored in setupChannel() method
	 * These all are parallel threads running for incoming messages and are responsible for persistent connection
	 * with all the neighbors. 
	 * 
	 */
	public void setupServers(){
		receiverThreadPool = new ArrayList<Thread>();
		for(int i=0; i < neighbors.length; i++ ){
            TcpReceiver tcpReceiver = new TcpReceiver(readerStreamMap.get(Integer.parseInt(neighbors[i])));
            Thread receiverThread = new Thread(tcpReceiver);
            receiverThreadPool.add(receiverThread);
            receiverThread.start();
        }
	}
	
	/**
	 * This is actual algorithm responsible for processing all messages. It process incoming message
	 * based on MessageType. We have total 4 MessageTypes and corresponding subMessageTypes:
	 * 1. APPLICATION_MSG
	 * 		No subMessageType
	 * 2. RECOVERY_MSG
	 * 		subMessageType = 0 means RecoveryStarted
	 * 		subMessageType = 1 means RecoveryRounds
	 * 		subMessageType = 2 means FinalReceived After GlobalConsistentState
	 * 3. BROADCAST_MSG
	 * 		subMessageType = 0 means Continue
	 * 		subMessageType = 1 means Stop
	 * 4. CONVERGE_MSG
	 * 		subMessageType = 0 means RollBackTrue
	 * 		subMessageType = 1 means RollBackFalse
	 * 
	 * Each messageType is responsible for some specific action from the node.
	 * These are explained with every messegeType switch case block
	 * 
	 * @param receivedMessage
	 * @throws InterruptedException
	 * @throws IOException
	 */
	synchronized void receiveMessage(Message receivedMessage) throws InterruptedException, IOException{
		synchronized(SendMesageQueue.sendQueue){
			switch(receivedMessage.messageType){
			case Message.APPLICATION_MSG:
				/**
				 * This block is responsible for processing application message.
				 * Vector clock is updated.
				 * Received Vector is updated.
				 * Checkpoint is taken.
				 * and new REB instance is generated.
				 */
				if(!recovery){
					//Update Vector clock
					for (int i=0; i < receivedMessage.vectorClock.length; i++){ 
						if (receivedMessage.vectorClock[i] >= currentVectorClock[i])
							currentVectorClock[i] = receivedMessage.vectorClock[i];
					}
					
					currentVectorClock[nodeId]++;
					fullLog+=("Received from "+receivedMessage.senderId+" to "+nodeId+" Vector "+Arrays.toString(currentVectorClock)+" recvd vector "+Arrays.toString(receivedMessage.vectorClock)+"\n" );
					
					receivedMessagesVector[receivedMessage.senderId]++;
					
					reb = new REB(true,0,0);
					takeCheckpoint(true,0,0); //Take checkPoint
					reb.runREBRound();
				}
				break;
			case Message.RECOVERY_MSG:
				/**
				 * This block is responsible for processing 3 subTypes of recovery messages
				 * 1. Recovery start: A node is failed in network, stop REB
				 * 2. Recovery Rounds: Send sentMessagesVector Vector value to each of your neighbor for DIAMETER number of rounds
				 * 3. Retransmit after Recovery: Send lost messages once recovery is finished
				 * 
				 * At the end (after finish of failure sequence) it outputs all consistent vector clocks to file.
				 */
				if(receivedMessage.subMessageType == 0){
					recoveryStartCounter++;
					//If not in recovery, go into recovery state and send StartRecovery message to all neighbors
					if(!recovery){
						fullLog+=("Recovery type 0 from "+receivedMessage.senderId+" to "+nodeId + " starter ID " + receivedMessage.starterId+"\n");
						
						recovery=true;
						//send recovery message to all neighbors with subMessageType = 0
						for(int i=0; i < neighbors.length; i++ ){
							Message recoveryStartMessage = new Message(Message.RECOVERY_MSG, 0);
							recoveryStartMessage.senderId=nodeId;
							recoveryStartMessage.receiverId = Integer.parseInt(neighbors[i]);
							recoveryStartMessage.starterId = receivedMessage.starterId;
							SendMesageQueue.sendQueue.add(recoveryStartMessage);
						}
					}
					if(recoveryStartCounter == neighbors.length){
						recoveryRounds++;
						//start 1st recovery rounds of DIAMETER rounds
						for(int i=0; i < neighbors.length; i++ ){
							Message recoveryRoundMessage = new Message(Message.RECOVERY_MSG, 1);
							recoveryRoundMessage.senderId=nodeId;
							recoveryRoundMessage.receiverId = Integer.parseInt(neighbors[i]);
							recoveryRoundMessage.dataField = sentMessagesVector[recoveryRoundMessage.receiverId];
							recoveryRoundMessage.starterId = receivedMessage.starterId;
							recoveryRoundMessage.roundNumber = recoveryRounds;
							SendMesageQueue.sendQueue.add(recoveryRoundMessage);
						}
					}
				}
					
				if(receivedMessage.subMessageType == 1){
					recoveryRoundCounter[receivedMessage.roundNumber-1]++;
					
					fullLog+=("Recovery type 1 from "+receivedMessage.senderId+" to "+nodeId + " starter ID " + receivedMessage.starterId+" RoundNo "+receivedMessage.roundNumber+"\n");
					
					//check if rollback is required
					if(receivedMessage.dataField < receivedMessagesVector[receivedMessage.senderId]){
						
						myRollbackResult = 0;
						CheckPoint temp = CPStack.pop();
						while(receivedMessage.dataField < temp.recvdVector[receivedMessage.senderId]){
							temp = CPStack.pop();
						}
						receivedMessagesVector = temp.recvdVector;
						sentMessagesVector = temp.sentVector;
						currentVectorClock = temp.vectorClock;
						sentMessages = temp.sentMessages;
						noOfMsgsSent = temp.noOfMessagesSent;
						reb = new REB(temp.active,temp.messagesSentInREB,temp.lastNeighborIndex);
						
					}
					//DIAMETER number of rounds
					if(recoveryRounds!=0 && recoveryRoundCounter[recoveryRounds-1] == neighbors.length && recoveryRounds < diameter){
						recoveryRounds++;
						//broadcast sentMessagesVector vector values as next recovery rounds 
						for(int i=0; i < neighbors.length; i++ ){
							Message recoveryRoundMessage = new Message(Message.RECOVERY_MSG, 1);
							recoveryRoundMessage.senderId=nodeId;
							recoveryRoundMessage.receiverId = Integer.parseInt(neighbors[i]);
							recoveryRoundMessage.dataField = sentMessagesVector[recoveryRoundMessage.receiverId];
							recoveryRoundMessage.starterId = receivedMessage.starterId;
							recoveryRoundMessage.roundNumber = recoveryRounds;
							SendMesageQueue.sendQueue.add(recoveryRoundMessage);
						}
					}
					
					//Check for CONVERGECAST condition for leaf nodes
					if(checkArray(recoveryRoundCounter) && (children.length == 0) && (recoveryRounds == diameter)){
						//send myConvergecast to parent.
						Message recoveryConvergecastMessage = null;
						if(myRollbackResult == 0){
							recoveryConvergecastMessage = new Message(Message.CONVERGE_MSG, 0);
						}
						else if(myRollbackResult == 1){
							recoveryConvergecastMessage = new Message(Message.CONVERGE_MSG, 1);
						}
						recoveryConvergecastMessage.senderId=nodeId;
						recoveryConvergecastMessage.receiverId = parent;
						recoveryConvergecastMessage.starterId = receivedMessage.starterId;
						
						SendMesageQueue.sendQueue.add(recoveryConvergecastMessage);
						
						recoveryRoundCounter=new int[diameter];
						childrenConvergecastCounter=0;
						recoveryRounds=0;
						childrenRollbackResult=1;
						myRollbackResult=1;
						recoveryStartCounter=0;
					}
					
					/**
					 * Check CONVERGECAST condition for non-Leaf nodes
					 * and BROADCAST condition for parent node 
					 */
					if(children.length>0){
						if(checkArray(recoveryRoundCounter) && recoveryRounds == diameter && childrenConvergecastCounter == children.length){
							int myConvergecast = (childrenRollbackResult & myRollbackResult);
							if(parent == -1){
								//send Broadcast message to children with myConvergecast
								for(int i=0; i < children.length; i++ ){
									Message recoveryBroadcastMessage = new Message(Message.BROADCAST_MSG, myConvergecast);
									recoveryBroadcastMessage.senderId=nodeId;
									recoveryBroadcastMessage.receiverId = children[i];
									recoveryBroadcastMessage.starterId = receivedMessage.starterId;
									SendMesageQueue.sendQueue.add(recoveryBroadcastMessage);
								}
								recoveryRoundCounter=new int[diameter];
								childrenConvergecastCounter=0;
								recoveryRounds=0;
								childrenRollbackResult=1;
								myRollbackResult=1;
								recoveryStartCounter=0;
								if(myConvergecast == 1){
									//Saving consistent global checkpoint
									CheckPoint temp = CPStack.pop();
									CPStack.clear();
									CPStack.push(temp);
									//delete head entry of failure sequence
									failingProcess.poll();
									failingCheckpointNumbers.poll();
									
									
									
									//send receivedMessagesVector vector to neighbors to receive lost messages 
									recovery = false;
									
									for(int i=0; i < neighbors.length; i++ ){
										Message recoveryRoundMessage = new Message(Message.RECOVERY_MSG, 2);
										recoveryRoundMessage.senderId=nodeId;
										recoveryRoundMessage.receiverId = Integer.parseInt(neighbors[i]);
										recoveryRoundMessage.dataField = receivedMessagesVector[recoveryRoundMessage.receiverId];
										recoveryRoundMessage.starterId = receivedMessage.starterId;
										SendMesageQueue.sendQueue.add(recoveryRoundMessage);
									}
									
									//log vectorClock in file as consistent state
									fullLog+=(Arrays.toString(currentVectorClock)+"\t"+active+"\n");
									writeVectorsToFile(Arrays.toString(currentVectorClock));
									
									//Check if Failure Sequence finished to ensure no Application messages get generated after that
									if(failingProcess.size()==0 &&   failingCheckpointNumbers.size()==0 ){
										isJVFinished = true;
										writeFullLogsToFile(fullLog);
									}
								}
								if(myConvergecast == 0){
									
									//start 1st recovery rounds since consistent global state is not achieved after DIAMETER rounds
									recoveryRounds = 1;
									myRollbackResult=1;
									
									for(int i=0; i < neighbors.length; i++ ){
										Message recoveryRoundMessage = new Message(Message.RECOVERY_MSG, 1);
										recoveryRoundMessage.senderId=nodeId;
										recoveryRoundMessage.receiverId = Integer.parseInt(neighbors[i]);
										recoveryRoundMessage.dataField = sentMessagesVector[recoveryRoundMessage.receiverId];
										recoveryRoundMessage.starterId = receivedMessage.starterId;
										SendMesageQueue.sendQueue.add(recoveryRoundMessage);
									}
								}
							}
							else{
							//send myConvergecast to parent. This is combined result of both children + current node
								Message recoveryConvergecastMessage = new Message(Message.CONVERGE_MSG, myConvergecast);
								recoveryConvergecastMessage.senderId=nodeId;
								recoveryConvergecastMessage.receiverId = parent;
								recoveryConvergecastMessage.starterId = receivedMessage.starterId;
								SendMesageQueue.sendQueue.add(recoveryConvergecastMessage);
								
								recoveryRoundCounter=new int[diameter];
								childrenConvergecastCounter=0;
								childrenRollbackResult=1;
								myRollbackResult=1;
								recoveryStartCounter=0;
								recoveryRounds=0;
							}
						}
					}
				}
				
				/**
				 * This block is responsible for sending lost messages to neighbors based on received vector from
				 * neighbors comparing with own send vector.
				 */
				if(receivedMessage.subMessageType == 2){
					if(!recovery){
						fullLog+=("Recovery type 2 from "+receivedMessage.senderId+" to "+nodeId + " starter ID " + receivedMessage.starterId +"\n");
						
						retransmitCheck++;
						if(receivedMessage.dataField < sentMessagesVector[receivedMessage.senderId]){
							//retransmit lost message 
							for(int i = receivedMessage.dataField + 1;i <= sentMessagesVector[receivedMessage.senderId] ; i++ ){
								for(Message m : sentMessages){
									if(m.receiverId == receivedMessage.senderId && m.dataField == i){
										fullLog+=("Retransmit message from "+nodeId+" to "+m.receiverId+" vector "+Arrays.toString(m.vectorClock)+"\n");
										SendMesageQueue.sendQueue.add(m);
									}
								}							
							}						
						}
						if(retransmitCheck == neighbors.length){
							
							retransmitCheck=0;
							
							if(reb.active && failingCheckpointNumbers.size() != 0 && failingProcess.size()!=0){
								reb.runREBRound();
							}

							// Check after the final failure event from the file for termination condition of all nodes.
							if(failingCheckpointNumbers.size() == 0 && failingProcess.size()==0){
								System.out.println("Simulation Finished of node "+ nodeId);
								
							}
						}
					}
				}
				break;
				
			case Message.CONVERGE_MSG:
				
				/**
				 * This block is responsible for generating CONVERGECAST of non-leaf nodes and BROADCAST from parent node
				 * It is based on result from all children and status of current node.
				 */
				
				fullLog+=("Convergecast from "+receivedMessage.senderId+" to "+nodeId + " starter ID " + receivedMessage.starterId+"\n");
				
				childrenConvergecastCounter++;
				childrenRollbackResult = (childrenRollbackResult & receivedMessage.subMessageType);
				if(checkArray(recoveryRoundCounter) && recoveryRounds == diameter && childrenConvergecastCounter == children.length){
					int myConvergecast = (childrenRollbackResult & myRollbackResult);
					if(parent == -1){
						//send Broadcast message to children with myConvergecast
						for(int i=0; i < children.length; i++ ){
							Message recoveryBroadcastMessage = new Message(Message.BROADCAST_MSG, myConvergecast);
							recoveryBroadcastMessage.senderId=nodeId;
							recoveryBroadcastMessage.receiverId = children[i];
							recoveryBroadcastMessage.starterId = receivedMessage.starterId;
							SendMesageQueue.sendQueue.add(recoveryBroadcastMessage);
						}
						
						recoveryStartCounter=0;
						recoveryRoundCounter=new int[diameter];
						childrenConvergecastCounter=0;
						recoveryRounds=0;
						childrenRollbackResult=1;
						myRollbackResult=1;
						
						if(myConvergecast == 1){
							//Saving consistent global checkpoint
							CheckPoint temp = CPStack.pop();
							CPStack.clear();
							CPStack.push(temp);
							//delete head entry of failure sequence
							failingProcess.poll();
							failingCheckpointNumbers.poll();
							
							//send receivedMessagesVector vector to neighbor
							
							recovery = false;
							
							for(int i=0; i < neighbors.length; i++ ){
								Message recoveryRoundMessage = new Message(Message.RECOVERY_MSG, 2);
								recoveryRoundMessage.senderId=nodeId;
								recoveryRoundMessage.receiverId = Integer.parseInt(neighbors[i]);
								recoveryRoundMessage.dataField = receivedMessagesVector[recoveryRoundMessage.receiverId];
								recoveryRoundMessage.starterId = receivedMessage.starterId;
								SendMesageQueue.sendQueue.add(recoveryRoundMessage);
							}
							//log vectorClock in file
							fullLog+=(Arrays.toString(currentVectorClock)+"\t"+active +"\n");
							writeVectorsToFile(Arrays.toString(currentVectorClock));
							
							if(failingProcess.size()==0 &&   failingCheckpointNumbers.size()==0 ){
								isJVFinished = true;
								writeFullLogsToFile(fullLog);
							}
						}
						if(myConvergecast == 0){
							
							//start 1st recovery rounds
							
							recoveryRounds = 1;
							myRollbackResult=1;
							
							for(int i=0; i < neighbors.length; i++ ){
								Message recoveryRoundMessage = new Message(Message.RECOVERY_MSG, 1);
								recoveryRoundMessage.senderId=nodeId;
								recoveryRoundMessage.receiverId = Integer.parseInt(neighbors[i]);
								recoveryRoundMessage.dataField = sentMessagesVector[recoveryRoundMessage.receiverId];
								recoveryRoundMessage.starterId = receivedMessage.starterId;
								SendMesageQueue.sendQueue.add(recoveryRoundMessage);
								
								
							}
						}
					}
					else{
					//send myConvergecast to parent. This is combined result of both children + current node
						Message recoveryConvergecastMessage = new Message(Message.CONVERGE_MSG, myConvergecast);
						recoveryConvergecastMessage.senderId=nodeId;
						recoveryConvergecastMessage.receiverId = parent;
						recoveryConvergecastMessage.starterId = receivedMessage.starterId;
						SendMesageQueue.sendQueue.add(recoveryConvergecastMessage);
						
						recoveryRoundCounter=new int[diameter];
						childrenConvergecastCounter=0;
						childrenRollbackResult=1;
						myRollbackResult=1;
						recoveryStartCounter=0;
						recoveryRounds=0;
					}
				}
				break;
				
			case Message.BROADCAST_MSG:
				
				/**
				 * This block is responsible for handling broadcast message from parent and
				 * decides to go to next DIAMETER rounds or stop based on subMessageType received
				 */
				
				if(receivedMessage.subMessageType == 1){
					//Steps to do if receive STOP:
					fullLog+=("Broadcast type 1 from "+receivedMessage.senderId+" to "+nodeId + " starter ID " + receivedMessage.starterId+"\n");
					
					//Saving consistent global checkpoint
					CheckPoint temp = CPStack.pop();
					CPStack.clear();
					CPStack.push(temp);
					//delete head entry of failure sequence
					failingProcess.poll();
					failingCheckpointNumbers.poll();
					
					
					recovery = false;
					// send broadcast value to children
					for(int i=0; i < children.length; i++ ){
						Message recoveryRoundMessage = new Message(Message.BROADCAST_MSG, 1);
						recoveryRoundMessage.senderId=nodeId;
						recoveryRoundMessage.receiverId = children[i];
						recoveryRoundMessage.starterId = receivedMessage.starterId;
						SendMesageQueue.sendQueue.add(recoveryRoundMessage);
					}
					//send receivedMessagesVector vector to neighbor
					for(int i=0; i < neighbors.length; i++ ){
						Message recoveryRoundMessage = new Message(Message.RECOVERY_MSG, 2);
						recoveryRoundMessage.senderId=nodeId;
						recoveryRoundMessage.receiverId = Integer.parseInt(neighbors[i]);
						recoveryRoundMessage.dataField = receivedMessagesVector[recoveryRoundMessage.receiverId];
						recoveryRoundMessage.starterId = receivedMessage.starterId;
						SendMesageQueue.sendQueue.add(recoveryRoundMessage);
					}
					//log vectorClock in file
					fullLog+=(Arrays.toString(currentVectorClock)+"\t"+active+"\n");
					writeVectorsToFile(Arrays.toString(currentVectorClock));
					
					if(failingProcess.size()==0 &&   failingCheckpointNumbers.size()==0 ){
						isJVFinished = true;
						writeFullLogsToFile(fullLog);
					}
				}
				if(receivedMessage.subMessageType == 0){
					//Steps to do if received GO
					fullLog+=("Broadcast type 0 from "+receivedMessage.senderId+" to "+nodeId + " starter ID " + receivedMessage.starterId+"\n");
					
					recoveryRounds = 1;
					myRollbackResult = 1;
					//send broadcast value to children
					for(int i=0; i < children.length; i++ ){
						Message recoveryRoundMessage = new Message(Message.BROADCAST_MSG, 0);
						recoveryRoundMessage.senderId=nodeId;
						recoveryRoundMessage.receiverId = children[i];
						recoveryRoundMessage.starterId = receivedMessage.starterId;
						SendMesageQueue.sendQueue.add(recoveryRoundMessage);
					}
					
					//start 1st recovery rounds
					for(int i=0; i < neighbors.length; i++ ){
						Message recoveryRoundMessage = new Message(Message.RECOVERY_MSG, 1);
						recoveryRoundMessage.senderId=nodeId;
						recoveryRoundMessage.receiverId = Integer.parseInt(neighbors[i]);
						recoveryRoundMessage.dataField = sentMessagesVector[recoveryRoundMessage.receiverId];
						recoveryRoundMessage.starterId = receivedMessage.starterId;
						SendMesageQueue.sendQueue.add(recoveryRoundMessage);
					}
				}
				break;
			}
		}
	}
	
	/**
	 * Check if received all messages from all neighbors for all DIAMETER rounds
	 * 
	 * @param roundCounter
	 * @return
	 */
	boolean checkArray(int[] roundCounter){
		boolean returning=true;
		for(int i : roundCounter)
			returning &= (i==neighbors.length)? true:false;
		return returning;
	}
	
	/**
	 * This method is responsible for taking and storing CHECKPOINT
	 * This is called when node sends/ receives message
	 * This block is also responsible for checking if node is responsible
	 * for next failure event based on sequence provided. If failing, this block generated first
	 * recovery message to start recovery rounds.
	 * 
	 * @param active
	 * @param msgsSent
	 * @param lastNeighborIndex
	 */
	void takeCheckpoint(boolean active, int msgsSent, int lastNeighborIndex){
		CheckPoint obj = new CheckPoint();
		obj.vectorClock = currentVectorClock;
		obj.sentVector = sentMessagesVector;
		obj.recvdVector = receivedMessagesVector;
		obj.noOfMessagesSent = noOfMsgsSent;
		obj.sentMessages = sentMessages;
		obj.active = active;
		obj.lastNeighborIndex = lastNeighborIndex;
		obj.messagesSentInREB = msgsSent;
		CPStack.push(obj);
		
		//Check if node has to fail according to the sequence.
		if(failingProcess.size()!=0 && failingCheckpointNumbers.size()!=0){
			if(nodeId == failingProcess.peek() && CPStack.size() == failingCheckpointNumbers.peek()+1){
				recovery = true;
				fullLog+=(nodeId+" Failed at "+CPStack.size()+"\n");
				int randomNum = 1 + (int)(Math.random() * failingCheckpointNumbers.peek());
				int clearCPDifference = failingCheckpointNumbers.peek() - randomNum;
				while(clearCPDifference > 0){
					CPStack.pop();
					clearCPDifference--;
				}
				CheckPoint top=CPStack.peek();
				receivedMessagesVector = top.recvdVector;
				sentMessagesVector = top.sentVector;
				currentVectorClock = top.vectorClock;
				sentMessages = top.sentMessages;
				noOfMsgsSent = top.noOfMessagesSent;
				reb = new REB(top.active,top.messagesSentInREB,top.lastNeighborIndex);
				for(int i=0; i < neighbors.length; i++ ){
					Message recoveryStartMessage = new Message(Message.RECOVERY_MSG, 0);
					recoveryStartMessage.senderId=nodeId;
					recoveryStartMessage.receiverId = Integer.parseInt(neighbors[i]);
					recoveryStartMessage.starterId = nodeId;
					fullLog+=("Send Rec 0 from "+nodeId+" to "+recoveryStartMessage.receiverId+"\n");
					SendMesageQueue.sendQueue.add(recoveryStartMessage);
				}
			}
		}
		
	}
	
	/**
	 * This method is called once the failure sequence is successfully finished to write
	 * all logs generated and store all consistent states.
	 * 
	 * @param fullLogs
	 * @param vectorClock
	 */
	public void writeFullLogsToFile(String fullLogs){
		try 
		{
			outFullLog.write((fullLogs+"\n").getBytes());
			outFullLog.close();
			outVectorLog.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void writeVectorsToFile(String vectorClock){
		try 
		{
			outVectorLog.write((vectorClock+"\n").getBytes());
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String args[]) throws InterruptedException, IOException, ClassNotFoundException  {
		
		if (args.length != 11) {
            System.exit(0);
            }
		object.nodeId = Integer.parseInt(args[0]); //Store Current node ID
		/**
		 *  store all node details and neighbor details
		 */
		object.noOfNodes = args[1].split("#").length;
		object.nodeNames = new String[object.noOfNodes];
		object.nodePorts = new int[object.noOfNodes];
		for (String s : args[1].split("#") ) {
			String[] parts = s.split(" ");
			int nodeIndex = Integer.parseInt(parts[0]);
			object.nodeNames[nodeIndex] = parts[1];
			object.nodePorts[nodeIndex] = Integer.parseInt(parts[2]);
		}		
		object.neighbors = args[2].split(" ");
		object.numberOfFailureEvents = Integer.parseInt(args[3]); //Total number of Failure events
		object.maxNum = Integer.parseInt(args[4]); //Maximum number of messages that one node can send
		object.maxPerActive = Integer.parseInt(args[5].trim()); //Maximum number of messages that node can send during one REB instance
		object.active = Math.random() < 0.5; //Toss to decide if node is active or passive
		object.interMessageDelay = Integer.parseInt(args[6].trim()); //Delay between 2 REB messages
		
		/**
		 * Store failure events
		 */
		String[] failureEvents = args[7].trim().split("#");
		for (int k =0; k<object.numberOfFailureEvents; ++k ) {
			String[] parts = failureEvents[k].split(" ");
			object.failingProcess.add(Integer.parseInt(parts[0]));
			object.failingCheckpointNumbers.add(Integer.parseInt(parts[1]));
		}
		/**
		 * Store Parent, Children and DIAMETER of the graph details
		 */
		object.parent = Integer.parseInt(args[8].trim());
		String[] childrenString = args[9].replace("[", "").replace("]", "").split(", ");
		if(!(childrenString.length ==1 && childrenString[0].equalsIgnoreCase(""))){
			object.children = new int[childrenString.length];
			for (int i = 0; i < childrenString.length; i++) {
				object.children[i] = Integer.parseInt(childrenString[i].trim());
			}
		}
		else{
			object.children = new int[0];
		}
		object.diameter = Integer.parseInt(args[10].trim());
		object.currentVectorClock = new int[object.noOfNodes];
		object.sentMessagesVector = new int[object.noOfNodes];
		object.receivedMessagesVector = new int[object.noOfNodes];
		object.recoveryRoundCounter = new int[object.diameter];
		try {
			object.outFullLog = new FileOutputStream("FullLogs-"+object.nodeId+".out");
			object.outVectorLog = new FileOutputStream("logs-"+object.nodeId+".out");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		/**
		 * Setup persistent connections with neighbors
		 * and make server up and running with client
		 */
		object.setupChannels();
		System.out.println("Channel Setup Complete " + object.nodeId);
		object.reb = new REB(object.active, 0,0);
		object.setupServers();
		object.takeCheckpoint(object.active, 0,0); // Take initial CHECKPOINT
		object.fullLog+=(" "+object.active+"\n");
		TcpSender s = new TcpSender();
		object.Sender = new Thread(s);
		object.Sender.start();		
		/**
		 * if ACTIVE start REB
		 */
		if(object.active){
			object.reb.runREBRound();
		}
	}
}