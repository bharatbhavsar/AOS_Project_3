/**
 * This program is responsible to check if all nodes have generated the consistent states
 * with respect to each other. i.e. if we have GLOBAL CONSISTENT STATE available or not
 * after network has finished particular recovery sequence.
 * 
 * Basic Idea:
 * IF(VectorMax[] == VectorDiagonal[])
 * 		GLOBAL STATE is CONSISTENT
 * ELSE
 * 		GLOBAL STATE is IN-CONSISTENT
 * 
 * @author Bharat M Bhavsar, Sri Vidya Varanasi, Arvind Pandiyan
 * @version 1.0
 * @since 05/03/2016
 */

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
/**
 * Test class which accepts two command line arguments: number of nodes and number of failures
 * and checks whether Consistency is achieved. 
 *
 */
public class CheckpointTester {
	int noOfNodes, noOfFailures;
	BufferedReader[] files;
	int[][] vectors;
	/**
	 * Method to convert string to integer array
	 * @param string
	 * @return
	 */
	private static int[] fromString(String string) {
	    String[] strings = string.replace("[", "").replace("]", "").split(", ");
	    int result[] = new int[strings.length];
	    for (int i = 0; i < result.length; i++) {
	      result[i] = Integer.parseInt(strings[i]);
	    }
	    return result;
	  }
	
	/**
	 * Method to check if the two arrays (vectors) are equal or not using vector clock.
	 * This helps to conclude whether the processes captured a consistent checkpoint or not.
	 * @param v First vector to compare
	 * @param w Second vector to compare
	 * @return
	 */
	static boolean isEqual(int v[], int w[])
	{
		for (int i=0; i < v.length; i++) 
			if (v[i] != w[i])
				return false;
		return true;
	}
	/**
	 * Method to compute the maximum in a row of elements.
	 * @param index
	 * @return
	 */
	public int findMax(int index){
		int max=0;
		for(int i=0;i<noOfNodes;++i){
			if(vectors[i][index]>max)
				max = vectors[i][index];
		}
		return max;
	}
	
	public static void main(String args[]) throws IOException {
		
		CheckpointTester object = new CheckpointTester();
		if (args.length != 2) {
            System.exit(0);
            }
		object.noOfNodes = Integer.parseInt(args[0]);
		object.noOfFailures = Integer.parseInt(args[1]);
		object.files = new BufferedReader[object.noOfNodes];
		object.vectors = new int[object.noOfNodes][];
		for(int i =0;i<object.noOfNodes;++i){
			try {
				object.files[i] = new BufferedReader(new InputStreamReader(new FileInputStream("logs-"+i+".out")));
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		boolean consistent=true;
		
		for(int j = 0;j<object.noOfFailures&consistent;++j){
			for(int i =0;i<object.noOfNodes;++i){
				object.vectors[i] = fromString(object.files[i].readLine());
			}
			for(int i =0;i<object.noOfNodes&consistent;++i){
				int maxVector[] = new int[object.noOfNodes];
				int diagVector[] = new int[object.noOfNodes];
				diagVector[i]=	object.vectors[i][i];
				maxVector[i] = object.findMax(i);
				consistent = isEqual(maxVector,diagVector);
			}			
		}
		if(consistent)
			System.out.println("Consistency satisfied");
		else
			System.out.println("Consistency not satisfied");
		
	}
}
