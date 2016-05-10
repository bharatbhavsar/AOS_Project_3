/**
 * This program is responsible to generate BFS tree of the given GRAPH
 * It also generates parent and children information along with DIAMETER
 * of the given GRAPH. This all information is stored in file.
 * 
 * for DIAMETER:
 * 		Run BFS twice
 * 		once from root node and then from farthest node from root as root of second BFS.
 * 
 * @author Bharat M Bhavsar, Sri Vidya Varanasi, Arvind Pandiyan
 * @version 1.0
 * @since 05/03/2016
 */


import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

class qObj{
	int parentId;
	int nodeId;
	int level;
	public qObj(int i,int j,int k){
		nodeId = i;
		level = j;
		parentId = k;
	}
}

public class BFSTree {
	int[] parents;
	int[] level;
	Integer[][] children = null;
	int[][] neighbors = null;
	int[] visited;
	Queue<qObj> queue = new LinkedList<qObj>();
	int totalNodes;
	
	public void visit(int nodeId, int level){
		 
		 ArrayList<Integer> childrenList = new ArrayList<Integer>();
		 for(int i = 0;i< neighbors[nodeId].length;++i){
			 if(visited[neighbors[nodeId][i]]==0){
				 childrenList.add(neighbors[nodeId][i]);
				 visited[neighbors[nodeId][i]]=1;
				 queue.add(new qObj(neighbors[nodeId][i],level+1,nodeId));
				 
			 }
		 }
		 children[nodeId] = new Integer[childrenList.size()];
		 childrenList.toArray(children[nodeId]);
		 
	}
	public void parseConfigFile(String configFile2,int root) {
		// TODO Auto-generated method stub
		String line = null;
		totalNodes=0;
		int counterNLines = 0, counterNeighborLines = 0;		
		try {
			
			FileReader fr = new FileReader(configFile2);
			BufferedReader br = new BufferedReader(fr);
			while ((line = br.readLine()) != null) {
				if (line.startsWith("#"))
					continue;
				else if (line.isEmpty() || line.trim().equals("")
						|| line.trim().equals("\n")) {
					continue;
				} else 	if (line.split("\\s+").length == 5 && (totalNodes == 0)) {
					totalNodes = Integer.parseInt(line.split("\\s+")[0]);
					neighbors = new int[totalNodes][];
					visited = new int[totalNodes]; 
					level = new int[totalNodes];
					parents = new int[totalNodes];
					children = new Integer[totalNodes][];
				} else if (counterNLines != totalNodes) {
					String nLine = "";
					counterNLines++;
				} else if (counterNeighborLines != totalNodes){
					String nnLine = "";
					if (line.contains("#")) 
						nnLine = line.substring(0, line.indexOf("#"));
					else
						nnLine = line;
					String[] tokens = nnLine.split("\\s+");
					
					neighbors[counterNeighborLines] = new int[tokens.length];
					int i = 0;
					for (String s : tokens){
						neighbors[counterNeighborLines][i] = (Integer.parseInt(s));
						++i;
					}
					counterNeighborLines++;
				}
			}
			br.close();
		} catch (FileNotFoundException ex) {
			System.out.println("Unable to open file '" + configFile2 + "'");
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		parents[root] = -1;
		visited[root] = 1;
		visit(root,0);
		while(!queue.isEmpty()){
			qObj t = queue.poll();
			parents[t.nodeId] = t.parentId;
			level[t.nodeId]= t.level;
			visit(t.nodeId,t.level);
		}
		
		for(int i = 0;i<totalNodes;++i)
			System.out.println(parents[i]+"\t"+i+"\t"+Arrays.toString(children[i])+"\t"+level[i]);
			
		
	}
	
	public static void main(String[] args) throws IOException{
		BFSTree t = new BFSTree();
		FileWriter fw = new FileWriter("spanningTree.txt");
		if(args.length!=1){
			System.out.println("not enuf arguments, exiting...");
			System.exit(0);
		}
		String fileName=args[0];
		t.parseConfigFile(fileName,0);
		System.out.println();
		int max = 0, j = -1;
		for(int i = 0;i<t.totalNodes;++i){
			fw.write(""+t.parents[i]+"\t"+Arrays.toString(t.children[i])+"\n");
			if(t.level[i]>max){
				max = t.level[i];
				j=i;
			}
		}
		
		t.parseConfigFile(fileName,j);
		max = 0; j = -1;
		for(int i = 0;i<t.totalNodes;++i){
			if(t.level[i]>max){
				max = t.level[i];
				j=i;
			}
		}
		fw.write(""+max+"\n");
		fw.close();
	}
	

}
