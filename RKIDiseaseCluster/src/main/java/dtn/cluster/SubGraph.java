
package dtn.cluster;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.summary.ResultSummary;
import org.neo4j.driver.v1.types.Node; 
import org.neo4j.driver.v1.types.Relationship;
import org.neo4j.helpers.TransactionTemplate;


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
		SubGraph sg = new SubGraph();
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			result = tx.run("(n:Patient) where ANY(x IN n.markedQuery WHERE x = '"+markedQuery+"') return n");
			while(result.hasNext()) {
				Record row = result.next();
				sg.patients.add(row.get(0).asNode());
			}
			result = tx.run("()-[s:TRANSMITS]-() where ANY(x IN s.markedQuery WHERE x = '"+markedQuery+"') return s");
			while(result.hasNext()) {
				Record row = result.next();
				 sg.transmissions.add(row.get(0).asRelationship());
			}
			
			tx.success();
		} catch (Exception e){
			e.printStackTrace();
		}
		
		return sg;}
	
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
	
	public void markCluster(long clusterid, String clusterType, long markedQuery) {
		
		TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
		tm.failure(new Throwable("Herkesin tuttuğu kendine"));
		TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
		boolean success = template.with(DiseaseCluster.graphDb).execute( transaction -> {
			boolean uncaught = true;
			ResultSummary rs = null;
			Session markKGOTermsSession = DiseaseCluster.driver.session();
			try{
			rs =	markKGOTermsSession.run("match (n:Patient) where n."+clusterType+"="+clusterid+" "
						+ "set n.markedQuery = case when not ANY(x IN n.markedQuery WHERE x = '"+markedQuery+"') then n.markedQuery+'"+markedQuery+"' else n.markedQuery end ").consume();
			} catch(Exception e){
				System.err.println("Mark K GO Terms::: "+e.getMessage());
				uncaught = false;
			} finally {markKGOTermsSession.close();}
			System.out.println(rs.counters().propertiesSet()+" properties were set on nodes and relationships for GO Terms");
			return uncaught;
		} );
		if(success) 
		{
			
		}
	else {
		
	}
		
	}

	public void markUnionOfQueries(String queryNumber1,String queryNumber2, String unionNumber){
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
		tx.run("match (n) where not ANY(x IN n.markedQuery WHERE x = '"+unionNumber+"') and (ANY(x IN n.markedQuery WHERE x = '"+queryNumber1+"') or ANY(x IN n.markedQuery WHERE x = '"+queryNumber2+"')) set n.markedQuery = n.markedQuery + '"+unionNumber+"' return (n)");
		tx.run("match ()-[n]-() where not ANY(x IN n.markedQuery WHERE x = '"+unionNumber+"') and (ANY(x IN n.markedQuery WHERE x = '"+queryNumber1+"') or ANY(x IN n.markedQuery WHERE x = '"+queryNumber2+"')) set n.markedQuery = n.markedQuery + '"+unionNumber+"' return (n)");
		tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	public void markIntersectionOfQueries(String queryNumber1,String queryNumber2, String intersectionNumber){
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
		tx.run("match (n) where not ANY(x IN n.markedQuery WHERE x = '"+intersectionNumber+"') and (ANY(x IN n.markedQuery WHERE x = '"+queryNumber1+"') and ANY(x IN n.markedQuery WHERE x = '"+queryNumber2+"')) set n.markedQuery = n.markedQuery + '"+intersectionNumber+"' return (n)");
		tx.run("match ()-[n]-() where not ANY(x IN n.markedQuery WHERE x = '"+intersectionNumber+"') and (ANY(x IN n.markedQuery WHERE x = '"+queryNumber1+"') and ANY(x IN n.markedQuery WHERE x = '"+queryNumber2+"')) set n.markedQuery = n.markedQuery + '"+intersectionNumber+"' return (n)");
		tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	} 

	public void markDifferenceOfQueries(String queryNumber1,String queryNumber2, String differenceNumber){
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
		tx.run("match (n) where not ANY(x IN n.markedQuery WHERE x = '"+differenceNumber+"') and (ANY(x IN n.markedQuery WHERE x = '"+queryNumber1+"') and not ANY(x IN n.markedQuery WHERE x = '"+queryNumber2+"')) set n.markedQuery = n.markedQuery + '"+differenceNumber+"' return (n)");
		tx.run("match ()-[n]-() where not ANY(x IN n.markedQuery WHERE x = '"+differenceNumber+"') and (ANY(x IN n.markedQuery WHERE x = '"+queryNumber1+"') and not ANY(x IN n.markedQuery WHERE x = '"+queryNumber2+"')) set n.markedQuery = n.markedQuery + '"+differenceNumber+"' return (n)");
		tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	public void removeQuery(String queryNumber){
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
		tx.run("MATCH (n) WHERE EXISTS(n.markedQuery) SET n.markedQuery = FILTER(x IN n.markedQuery WHERE x <> '"+queryNumber+"')");	
		tx.run("MATCH ()-[n]-() WHERE EXISTS(n.markedQuery) SET n.markedQuery = FILTER(x IN n.markedQuery WHERE x <> '"+queryNumber+"')");	
		tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	//AkkaSystemde bulunması daha doğru olabilir.
	public void unmarkConservedStructureQuery(int markedQuery) {
		
		TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
		tm.failure(new Throwable("Herkesin tuttuğu kendine"));
		TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
		boolean success = template.with(DiseaseCluster.graphDb).execute( transaction -> {
			Session unMarkSession = DiseaseCluster.driver.session();
			try{
				unMarkSession.run("MATCH (n) WHERE EXISTS(n.markedQuery) SET n.markedQuery = FILTER(x IN n.markedQuery WHERE x <> '"+markedQuery+"')");
				unMarkSession.run("MATCH ()-[r:TRANSMITS]->() WHERE EXISTS(r.markedQuery) SET r.markedQuery = FILTER(x IN r.markedQuery WHERE x <> '"+markedQuery+"')");
			} catch(Exception e){
				System.err.println("Unmark all nodes::: "+e.getMessage());
				unmarkConservedStructureQuery(markedQuery) ;
			} finally {unMarkSession.close();}
			return true;
		} );
		if(success)
			System.out.println("Unmark Conserved Structure Query was successful.");
		else
			System.err.println("Unmark Conserved Structure Query was interrupted!");
		
	}

	public void removeAllQueries(){
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
		tx.run("MATCH (n) WHERE EXISTS(n.markedQuery) SET n.markedQuery = []");	
		tx.run("MATCH ()-[n]-() WHERE EXISTS(n.markedQuery) SET n.markedQuery = []");	
		tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	public void removeAllMarks(){
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
		tx.run("MATCH (n) WHERE EXISTS(n.markedQuery) SET n.marked = []");	
		tx.run("MATCH ()-[n]-() WHERE EXISTS(n.markedQuery) SET n.marked = []");	
		tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	public void unmarkAllConservedStructureQueries() {
		
		TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
		tm.failure(new Throwable("Herkesin tuttuğu kendine"));
		TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
		boolean success = template.with(DiseaseCluster.graphDb).execute( transaction -> {
			Session unMarkSession = DiseaseCluster.driver.session();
			try{
				unMarkSession.run("MATCH (n) WHERE EXISTS(n.markedQuery) SET n.markedQuery = []");
				unMarkSession.run("MATCH ()-[r:TRANSMITS]->() WHERE EXISTS(r.markedQuery) SET r.markedQuery = []");
			} catch(Exception e){
				System.err.println("Unmark all nodes::: "+e.getMessage());
				unmarkAllConservedStructureQueries() ;
			} finally {unMarkSession.close();}
			return true;
		} );
		
		if(success)
			System.out.println("Unmark All Conserved Structure Queries was successful.");
		else
			System.err.println("Unmark All Conserved Structure Queries was interrupted!");
		
	}

	//Test Edilmedi. Yanlış gibi. Yarıda bırakılmış uygulamanın işaretli sorgularını dosya adına göre dosyadan veritabanına yükler.
	public void loadOldMarkedQueriesFromFileToDB(String fileName) {
		FileReader fr;
		BufferedReader br;
		String line = null;
		String[] markedQueries;
		Session saveOldQuerySession = DiseaseCluster.driver.session();
		try {
			fr = new FileReader(fileName);
			br =  new BufferedReader(fr); 
			
			while((line = br.readLine())!=null)
			{ 
				markedQueries = line.split(" ");
				if(markedQueries.length >=2) {
					// burada bir stringbuilderla ilgili alignment birden çok değerle set işlemi yapılacak.
					StringBuilder sb = new StringBuilder(" set a.markedQuery = a.markedQuery + [");
					for (int i =1;i<markedQueries.length;i++) {
						sb.append("'"+markedQueries[i]+"'");
						if(i<=markedQueries.length-2)
							sb.append(", ");
						else
							sb.append("]");
					}
					saveOldQuerySession.run("match (n)-[a:TRANSMITS]->(m) where n.Isolate_ID+'***'+m.Isolate_ID = '"+markedQueries[0]+"'");	
				}
			}
			
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			
		}

	} 
	//Uygulamada mevcut durumda veritabanında bulunan İşaretli Sorgular daha sonra tekrar veritbanına yüklenebilecek biçimde dosyaya kaydedilir.
	public void saveOldMarkedQueriesToFile(String fileName) {
		StatementResult result;
		Record record;
			
			Session womqtd = DiseaseCluster.driver.session();
			try(BufferedWriter bw = new BufferedWriter(new FileWriter(fileName))){

					result = womqtd.run("match (n)-[a:TRANSMITS]->(m) return distinct a.markedQuery,n.Isolate_ID,m.Isolate_ID");
					while(result.hasNext()){
						record = result.next();
						bw.write("TransmissionIndex:"+record.get(1).asString()+"***"+record.get(2).asString()+" ");
						if(!record.get(0).isNull())
						for (Object o : record.get(0).asList()) {
							bw.write((String) o+" ");
							System.out.println((String) o);
						}
						bw.newLine();
					}
						
			} catch(Exception e){
				e.printStackTrace();
				System.err.println("Write Old Marked Queries::: "+e.getMessage());
				unmarkAllConservedStructureQueries() ;
			} finally {womqtd.close();System.out.println("Old Marked Queries has been written to file: "+fileName);}
	}


	// Daha olmadi
	public SubGraph convertMarkToSubGraph(String markNumber){
		SubGraph sg = new SubGraph();
		StatementResult result;
		Record record;
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
		result = tx.run("MATCH (n:Patient) WHERE ANY(x IN n.markedQuery WHERE x = '"+markNumber+"') return distinct (n)");
		while(result.hasNext()){
			record = result.next();
			sg.patients.add(record.get(0).asNode());
		}
		
		record = null; result = null;
		result = tx.run("MATCH ()-[n:TRANSMITS]-() WHERE ANY(x IN n.markedQuery WHERE x = '"+markNumber+"') return distinct (n)");
		while(result.hasNext()){
			record = result.next();
			sg.transmissions.add(record.get(0).asRelationship());
		}
		
		tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
		
		return sg;
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
