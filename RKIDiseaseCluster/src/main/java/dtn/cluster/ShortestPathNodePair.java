
package dtn.cluster;

import org.neo4j.driver.v1.types.Node;

public class ShortestPathNodePair {
	Node node1;
	double distance;
	Node node2;
	
	public ShortestPathNodePair() {
		super();
	}
	
	public ShortestPathNodePair(Node node1, Node node2,  int distance) {
		super();
		this.node1 = node1;
		this.node2 = node2;
		this.distance = distance;
	}

	public Node getNode1() {
		return node1;
	}

	public void setNode1(Node node1) {
		this.node1 = node1;
	}

	public double getDistance() {
		return distance;
	}

	public void setDistance(double distance) {
		this.distance = distance;
	}

	public Node getNode2() {
		return node2;
	}

	public void setNode2(Node node2) {
		this.node2 = node2;
	}
	
	
}
