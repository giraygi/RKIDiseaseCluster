
package dtn.cluster;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.types.Node; 
import org.neo4j.driver.v1.types.Relationship;


public class SubGraph implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	Set<Node> patients = new HashSet<Node>();
	Set<Relationship> transmissions = new HashSet<Relationship>();
	FileWriter fw;
	BufferedWriter bw;
	Session  session;
	Session innerSession;
	int senderNo;
	String type;
	
	public SubGraph() {
		session = DiseaseCluster.driver.session();
		innerSession = DiseaseCluster.driver.session();
	}
	
	public SubGraph(DiseaseCluster p,Long markedQuery){
		session = p.session;
		init(markedQuery);
	}
	
	public SubGraph init(Long markedQuery) {
		StatementResult result;
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			result = tx.run("(n:Patient) where ANY(x IN n.markedQuery WHERE x = '"+markedQuery+"') return n");
			while(result.hasNext()) {
				Record row = result.next();
				 patients.add(row.get(0).asNode());
			}
			result = tx.run("()-[s:TRANSMITS]-() where ANY(x IN s.markedQuery WHERE x = '"+markedQuery+"') return s");
			while(result.hasNext()) {
				Record row = result.next();
				 transmissions.add(row.get(0).asRelationship());
			}
			
			tx.success();
		} catch (Exception e){
			e.printStackTrace();
		}
		
		return null;}
	
	public SubGraph intersection(SubGraph s){
		s.patients.retainAll(this.patients);
		s.transmissions.retainAll(this.transmissions);
		return s;
	}
	
	public SubGraph union (SubGraph s){
		s.patients.addAll(this.patients);
		s.transmissions.addAll(this.transmissions);
		return s;
	}
	// Parametre olan alt �izgenin farkl� elemanlar�n� d�nd�r�r
	public /*synchronized*/ SubGraph difference (SubGraph s){
		for (Node node : this.patients) {
			s.patients.removeIf((Node n)->n.id() == node.id());
		}
//		
//		for (Relationship relationship : this.aligns) {
//			s.aligns.removeIf((Relationship r)->r.id() == relationship.id());
//			s.aligns.removeIf((Relationship r)->r.endNodeId() == relationship.endNodeId());
//			s.aligns.removeIf((Relationship r)->r.startNodeId() == relationship.startNodeId());
//		}
		
		for (Relationship relationship : this.transmissions) {
			s.transmissions.removeIf((Relationship r)->r.id() == relationship.id());
		}
		
		s.type = "Difference of "+s.type+" from "+this.type;
		return s;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();	
		builder.append("SubGraph info:");
		builder.append("\n");
		builder.append(" *** patients= ");
		 for (Node n : this.patients) {
		    builder.append(n.toString().replaceAll("\n", "")+" ");
		}
		builder.append("\n");
		builder.append(" *** similarities= ");
		for (Relationship r : this.transmissions) {
		    builder.append(r.toString().replaceAll("\n", "")+" ");
		}
		builder.append("\n");
		builder.append(" *** sender: "+this.senderNo);
		builder.append(" *** type: "+this.type);
		return builder.toString().replaceAll("\n", "");
	}
	
	//Neo4j Match ()-[r]-() Where ID(r)=1 ya da (n:Organism1) WHERE ID(s) = 65110 set bilmemne daha do�ru diyo
	
	public void markSubGraph(int queryNumber){
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
		   // set n.markedQuery = n.markedQuery + '"+queryNumber+"' 
			for (Node n : this.patients) {	
				tx.run( "start n=node("+n.id()+") where not ANY(x IN n.markedQuery WHERE x = '"+queryNumber+"') set n.markedQuery = n.markedQuery + '"+queryNumber+"' return (n)" );
			}
			for (Relationship r : this.transmissions) {
				tx.run( "start r=rel("+r.id()+") where not ANY(x IN r.markedQuery WHERE x = '"+queryNumber+"') set r.markedQuery = r.markedQuery + '"+queryNumber+"' return(r)" );
			}	
			
			tx.success();
		} catch (Exception e){
			e.printStackTrace();
		}
		
	}
	
	public void unmarkSubGraph(int queryNumber) {
		
	}
	
	public void writeTransmissionsToDisk(String fileName){		
		StatementResult result;
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction(); BufferedWriter bw = new BufferedWriter(new FileWriter(fileName)) )
		{
		
		for (Relationship r : this.transmissions) {
			result = tx.run("match (s:Patient)-[:TRANSMITS]->(e:Patient) where ID(s)="+r.startNodeId()+" and ID(e)="+r.endNodeId()+" return s.Isolate_ID,e.Isolate_ID");
				Record record = result.single();
				bw.write(record.get("s.Isolate_ID").asString()+" "+record.get("e.Isolate_ID").asString());
				bw.newLine();
		}
		bw.close();
		tx.success();
	} catch (Exception e){
		e.printStackTrace();
	}
		}
	
	public void writePatientsToDisk(String fileName){
		StatementResult result;
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction(); BufferedWriter bw = new BufferedWriter(new FileWriter(fileName)) )
		{
		for (Node n : this.patients) {
			result = tx.run("match (n:Patient) where ID(n)="+n.id()+" return n.Isolate_ID,n.Isolation_Date,n.Isolation_Country,n.resistance");
				Record record = result.single();
				bw.write(record.get("n.Isolate_ID").asString()+" "+record.get("n.Isolation_Date").asString()+" "+record.get("n.Isolation_Country").asString()+" "+record.get("n.resistance").asString());
				bw.newLine();
		}

		bw.close();
		tx.success();
	} catch (Exception e){
		e.printStackTrace();
	}	
		}
	
}
