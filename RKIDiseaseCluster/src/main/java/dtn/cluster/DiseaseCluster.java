package dtn.cluster;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.types.Entity;
import org.neo4j.driver.v1.types.Node;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

public class DiseaseCluster {
	
	/**
	 * @param args
	 */
	
	static GraphDatabaseService graphDb;
	static String databaseAddress;
	FileReader fr;
	BufferedReader br;
	FileWriter fw;
	BufferedWriter bw;
	static Driver driver;
	Session session;
	double maxDistance= 0.0;
	double averageDistance = 0.0;
	double minDistance = 0.0;
	int numberOfSimilarityLinks = 0;
	int numberOfCloserLinks = 0;
	String weightProperty;
	String distanceProperty;
	MetaData md;
	
	
	
	
	public DiseaseCluster(int noofAligners, String args, int toleranceLimitForUnimprovedAligners,int toleranceCycleForUnimprovedAligners, String weightProperty, String distanceProperty){
		this.init(args);
		md  = new MetaData(20);
		this.weightProperty = weightProperty;
		this.distanceProperty = distanceProperty;
	}
	
	public void init(String args){

//		@SuppressWarnings("unused")
		GraphDatabaseService graphDb = new GraphDatabaseFactory()
                .newEmbeddedDatabase( new File("~/"+args) );
		DiseaseCluster.graphDb = graphDb;
		driver = GraphDatabase.driver( "bolt://localhost:7688", AuthTokens.basic( "neo4j", "evet" ) );
		session = driver.session();
	}
	
	public void createGraph(String isolates, String distances, String resistances){
		
		try ( org.neo4j.driver.v1.Transaction setConstraints = session.beginTransaction() ){
			setConstraints.run("CREATE CONSTRAINT ON (n:Patient) ASSERT n.Isolate_ID IS UNIQUE");
			setConstraints.success();
		} catch (Exception e){
			e.printStackTrace();
		}
		
		String line;
		String[] isolateInfo;
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			 
			fr = new FileReader(isolates); 
			br = new BufferedReader(fr); 
			line = null;
			isolateInfo = null;
			while((line = br.readLine())!=null)
			{ 
				isolateInfo = line.split(";");
				System.out.println(isolateInfo[0]);
				tx.run("CREATE (a:Patient {Isolate_ID: '"+isolateInfo[0]+"', Previous_Cluster_ID: "+isolateInfo[1]+", BioProject: '"+isolateInfo[3]+"', BioSample: '"+isolateInfo[4]+"', Sequencing_Institution: '"+isolateInfo[5]+"', Sample_Name: '"+isolateInfo[6]+"', Isolation_Date: '"+isolateInfo[7]+"', Isolation_Country: '"+isolateInfo[8]+"', Host_Age: '"+isolateInfo[9]+"', host_anti_retroviral_status: '"+isolateInfo[10]+"', host_hiv_status: '"+isolateInfo[11]+"', host_hiv_status_diagnosis_postmortem: '"+isolateInfo[12]+"', host_location_sampled: '"+isolateInfo[13]+"', patient_gender: '"+isolateInfo[14]+"', host_subject: '"+isolateInfo[15]+"', isolate_name: '"+isolateInfo[16]+"', strain: '"+isolateInfo[17]+"', power2: 0, power3: 0, power4: 0, pos:[],ref:[],alt:[],drug_resistance:[],mutation_identifier:[],full_mutation_list:[],multidrug_resistance:'NA'})");
			}
			fr = new FileReader(distances);
			br =  new BufferedReader(fr); 
			line = null;
			String[] distanceInfo = null;
			System.out.println("Patients are created.");
			double weight = 0.0;
			String from;
			String to;
			while((line = br.readLine())!=null)
			{ 
				distanceInfo = line.split("\t");
				weight = Double.parseDouble(distanceInfo[2]) != 0.0 ? 1/Double.parseDouble(distanceInfo[2]) : 1;
				
				
				if (distanceInfo[0].charAt(0) == 'X') {		
					from = distanceInfo[0].substring(1, distanceInfo[0].split("_")[0].length()).replace('.', '-');
					System.out.println(from);
				} else if (Character.isDigit(distanceInfo[0].charAt(0)))
					from = distanceInfo[0].split("_")[0];
				else
					from = distanceInfo[0];
				
				
				if (distanceInfo[1].charAt(0) == 'X') {		
					to = distanceInfo[1].substring(1, distanceInfo[1].split("_")[0].length()).replace('.', '-');
					System.out.println(to);
				} else if (Character.isDigit(distanceInfo[1].charAt(0)))
					to = distanceInfo[1].split("_")[0];
				else
					to = distanceInfo[1];
					
				
				tx.run("match (n:Patient {Isolate_ID: '"+from+"'}), (m:Patient {Isolate_ID: '"+to+"'}) create (n)-[:TRANSMITS {distance: "+Double.parseDouble(distanceInfo[2])+",weight: "+weight+"}]->(m) ");

			}
			System.out.println("Distances are created.");	
			
			fr = new FileReader(resistances);
			br =  new BufferedReader(fr); 
			line = null;
			String[] resistanceInfo;
			
			br.readLine();
			while((line = br.readLine())!=null)
			{ 
				resistanceInfo = line.split(",");
				System.out.println(resistanceInfo[0]+" - "+resistanceInfo[5]);
				tx.run("MATCH (n:Patient {Isolate_ID: '"+resistanceInfo[0].split("_")[0]+"'}) SET n.pos = n.pos +  "+resistanceInfo[1]+",n.ref = n.ref + '"+resistanceInfo[2]+"',n.alt = n.alt + '"+resistanceInfo[3]+"', n.drug_resistance = n.drug_resistance + '"+resistanceInfo[4]+"',n.mutation_identifier = n.mutation_identifier + '"+resistanceInfo[1]+resistanceInfo[3]+"',n.multidrug_resistance = '"+resistanceInfo[5]+"' return (n)");
			}
			
			System.out.println("Resistances are created.");	
			
			tx.run( "match (n)-[t:TRANSMITS]->(m) SET t.mutation_difference = LENGTH(n.pos)+LENGTH(m.pos)-LENGTH(FILTER(x in n.mutation_identifier WHERE x in m.mutation_identifier))");
			
			System.out.println("Mutation differences are created.");	
			
			tx.success(); tx.close();
		} catch (Exception e) {
			System.out.println("Could not create isolates, distances, resistances and mutation differences");
			e.printStackTrace();
		} 
		System.out.println("Creating the Graph is completed.");
	}
	
	public void computeDifferenceOfMutations() {
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			tx.run( "match (n)-[t:TRANSMITS]->(m) SET t.mutation_difference = LENGTH(n.pos)+LENGTH(m.pos)-LENGTH(FILTER(x in n.mutation_identifier WHERE x in m.mutation_identifier))");
			System.out.println("Mutation differences are created.");	
			tx.success(); tx.close();
		} catch (Exception e) {
			System.err.println("Could not create mutation differences::: "+e.getMessage());
		}
		
	}
	
	public void addInteractionsForLowDifferences(int differenceTreshold) {
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			tx.run( "match (n),(m) where not (n)-[:TRANSMITS]->(m) and LENGTH(n.pos)+LENGTH(m.pos)-LENGTH(FILTER(x in n.mutation_identifier WHERE x in m.mutation_identifier)) <= "+differenceTreshold+" and ID(n)>ID(m) with LENGTH(n.pos)+LENGTH(m.pos)-LENGTH(FILTER(x in n.mutation_identifier WHERE x in m.mutation_identifier)) as difference create (m)-[:DIFFERS {diff: difference}]->(n)");
			tx.success(); tx.close();
		} catch (Exception e) {
			System.err.println("Could not create mutation differences::: "+e.getMessage());
		}
		
	}
	
	public void computeFullMutations(String fullMutations) {
		try {
		fr = new FileReader(fullMutations);
		br =  new BufferedReader(fr); 
		String line = null;
		String[] mutationInfo;
		br.readLine();
		while((line = br.readLine())!=null)
		{ 
			try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() ){
				mutationInfo = line.split("\t");
				tx.run("MATCH (n:Patient {Isolate_ID: '"+mutationInfo[0].split("_")[0]+"'}) SET n.full_mutation_list = n.full_mutation_list +  '"+mutationInfo[1]+mutationInfo[2]+mutationInfo[3]+"' return (n)");
				tx.success(); tx.close();
			} catch(Exception e) {
				System.err.println("computeFullMutations inner transaction Exception");
			}
		}	
		System.out.println("Full mutations are created.");	
		}
		catch(Exception e) {
			System.err.println("computeFullMutations I/O Exception");
		}

	}
		
		public void computeFullMutationDifferences() {
			try {
			ArrayList<Node> patients = sortCentralPatients("pagerank",true);
			for (Node nodeN: patients) {
				for (Node nodeM : patients) {
					try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() ){
						if (nodeN.id()>nodeM.id()) {
							tx.run( "match (n:Patient)-[t:TRANSMITS]-(m:Patient) where ID(n) = "+nodeN.id()+" and ID(m) = "+nodeM.id()+" and ID(n)>ID(m) SET t.full_mutation_difference = LENGTH(n.full_mutation_list)+LENGTH(m.full_mutation_list)-LENGTH(FILTER(x in n.full_mutation_list WHERE x in m.full_mutation_list)) return t.full_mutation_difference");
							System.err.println(nodeN.id()+" DIST: "+nodeM.id());
							tx.success(); tx.close();
						}
					}
					catch(Exception e) {
						System.err.println("computeFullMutationDifferences inner transaction Exception");
					}
				}
			}
			System.out.println("Full Mutation differences and their differences are created.");	
			} catch(Exception e) {
				System.err.println("computeFullMutationDifferences I/O Exception");
			}
		}
		
		public void computeFullMutationDifferences2() {
			try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() ){
					tx.run( "match (n:Patient)-[t:TRANSMITS]->(m:Patient) SET t.full_mutation_difference = LENGTH(n.full_mutation_list)+LENGTH(m.full_mutation_list)-LENGTH(FILTER(x in n.full_mutation_list WHERE x in m.full_mutation_list)) return t.full_mutation_difference");	
					tx.success(); tx.close();
			}
			catch(Exception e) {
				System.err.println("computeFullMutationDifferences2 transaction Exception");
			} 
		}
		// Distance ve Weight ilişkisi incelenecek
		public void computeWeightForMutationDifferences() {
			try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() ){
				tx.run( "match (n)-[t:TRANSMITS]->(m) SET t.weightmd1 = case t.mutation_difference when 0 then 1.0 else toFloat(1)/toFloat(t.mutation_difference) end");
				tx.run( "match (n)-[t:TRANSMITS]->(m) SET t.weightmd2 = case t.mutation_difference when 0 then 1.0 else toFloat((LENGTH(n.pos)+LENGTH(m.pos)-t.mutation_difference))/t.mutation_difference end");	
				tx.success(); tx.close();
		}
		catch(Exception e) {
			System.err.println("computeWeightForMutationDifferences transaction Exception");
		} 
		}
		
		public void computeWeightForFullMutationDifferences() {
			try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() ){
				tx.run( "match (n)-[t:TRANSMITS]->(m) SET t.weightfullmd1 = case t.full_mutation_difference when 0 then 1.0 else toFloat(1)/toFloat(t.full_mutation_difference) end");
				tx.run( "match (n)-[t:TRANSMITS]->(m) SET t.weightfullmd2 = case t.full_mutation_difference when 0 then 1.0 else toFloat((LENGTH(n.full_mutation_list)+LENGTH(m.full_mutation_list)-t.full_mutation_difference))/t.full_mutation_difference end");
				tx.run( "match (n) SET n.no_of_full_mutations = LENGTH(n.full_mutation_list)");
				tx.success(); tx.close();
		}
		catch(Exception e) {
			System.err.println("computeWeightForFullMutationDifferences transaction Exception");
		}
		}
	
	public void computeMetaData() {
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			StatementResult result;
			result = tx.run( "match (p:Patient)-[t:TRANSMITS]->(n:Patient) return max(t.distance)");
			maxDistance = Double.parseDouble(result.single().get("max(t.distance)").toString());
			System.out.println("Maximum Distance: "+maxDistance);
			
			result = tx.run( "match (p:Patient)-[t:TRANSMITS]->(n:Patient) return avg(t.distance)");
			averageDistance = Double.parseDouble(result.single().get("avg(t.distance)").toString());
			System.out.println("Average Distance: "+averageDistance);
			
			result = tx.run( "match (p:Patient)-[t:TRANSMITS]->(n:Patient) return min(t.distance)");
			minDistance = Double.parseDouble(result.single().get("min(t.distance)").toString());
			System.out.println("Minimum Distance: "+minDistance);
			
			result = tx.run( "match (p:Patient)-[t:TRANSMITS]->(n:Patient) return count(t)");
			numberOfSimilarityLinks = Integer.parseInt(result.single().get("count(t.similarity)").toString());
			System.out.println("Number of Similarity Links: "+numberOfSimilarityLinks);
			
			tx.success(); tx.close();
		} catch (Exception e) {
			System.err.println("Could not create metadata::: "+e.getMessage());
		}
		this.numberOfCloserLinks = this.countEdgesBelowDistance(averageDistance);
		System.out.println("Number of Closer Links: "+this.numberOfCloserLinks);

	}
	
	public void computeDistanceMetaData() {
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			StatementResult result;	
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {	
				result = tx.run( "match (p:Patient)-[t:TRANSMITS]->(n:Patient) return percentileCont(t.distance,"+(i+1)/md.noofPercentileSteps+")");
				md.distance[i] = Double.parseDouble(result.single().get("percentileCont(t.distance,"+(i+1)/md.noofPercentileSteps+")").toString());
//				System.out.println(i+". Similarity "+md.distance[i] );
			}
			
			tx.success(); tx.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
				
	}
	
	public void computePowerMetaData() {
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			StatementResult result;
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (p:Patient) return percentileCont(p.power2,"+(i+1)/md.noofPercentileSteps+")");
				md.Power2[i] = Double.parseDouble(result.single().get("percentileCont(p.power2,"+(i+1)/md.noofPercentileSteps+")").toString());
//				System.out.println(i+". Power2: "+md.Power2[i] );
			}
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (p:Patient) return percentileCont(p.power3,"+(i+1)/md.noofPercentileSteps+")");
				md.Power3[i] = Double.parseDouble(result.single().get("percentileCont(p.power3,"+(i+1)/md.noofPercentileSteps+")").toString());
//				System.out.println(i+". Power3: "+md.Power3[i] );
			}
			
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (p:Patient) return percentileCont(p.power4,"+(i+1)/md.noofPercentileSteps+")");
				md.Power4[i] = Double.parseDouble(result.single().get("percentileCont(p.power4,"+(i+1)/md.noofPercentileSteps+")").toString());
//				System.out.println(i+". Power4: "+md.Power4[i] );
			}
			
			tx.success(); tx.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void computeCentralityMetaData() {
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			StatementResult result;
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (p:Patient) return percentileCont(p.pagerank20d085,"+(i+1)/md.noofPercentileSteps+")");
				md.Pagerank[i] = Double.parseDouble(result.single().get("percentileCont(p.pagerank20d085,"+(i+1)/md.noofPercentileSteps+")").toString());
//				System.out.println(i+". Pagerank: "+md.Pagerank[i] );
			}
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (p:Patient) return percentileCont(p.betweenness,"+(i+1)/md.noofPercentileSteps+")");
				md.Betweenness[i] = Double.parseDouble(result.single().get("percentileCont(p.betweenness,"+(i+1)/md.noofPercentileSteps+")").toString());
//				System.out.println(i+". Betweenness: "+md.Betweenness[i] );
			}
			
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (p:Patient) return percentileCont(p.closeness,"+(i+1)/md.noofPercentileSteps+")");
				md.Closeness[i] = Double.parseDouble(result.single().get("percentileCont(p.closeness,"+(i+1)/md.noofPercentileSteps+")").toString());
//				System.out.println(i+". Closeness: "+md.Closeness[i] );
			}
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (p:Patient) return percentileCont(p.harmonic,"+(i+1)/md.noofPercentileSteps+")");
				md.Harmonic[i] = Double.parseDouble(result.single().get("percentileCont(p.harmonic,"+(i+1)/md.noofPercentileSteps+")").toString());
//				System.out.println(i+". Harmonic: "+md.Harmonic[i] );
			}
			
			tx.success(); tx.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	public void computeDrugResistanceMetaData() {
		
	}
	
	
	public void computePowers(){
		boolean error = false;
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			tx.run("match (n:Patient)-[i:TRANSMITS]-(m:Patient) with (n),count(n) as connections set n.power2 = connections");
			tx.run("match (n:Patient)-[i:TRANSMITS]-(m:Patient)-[i2:TRANSMITS]-(o:Patient)-[i3:TRANSMITS]-(n) with (n),count(n) as connections set n.power3 = connections");
			System.out.println("Powers 2 and 3 are computed for all Nodes");      
			tx.success(); tx.close();
		} catch (Exception e){
			error = true;
			e.printStackTrace();
		} finally {
			if(!error)
				System.out.println("Powers 2 and 3 are computed for all Nodes"); 
		}
		
		error = false;
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{

			tx.run("match (a:Patient)-[r1:TRANSMITS]-(b:Patient)-[r2:TRANSMITS]-(c:Patient)-[r3:TRANSMITS]-(a:Patient),(c:Patient)-[r4:TRANSMITS]-(d:Patient)-[r5:TRANSMITS]-(a:Patient),(d:Patient)-[r6:TRANSMITS]-(b:Patient) with (a),count(a) as connections set a.power4 = connections");
			System.out.println("Powers 4 are computed for all Nodes");      
			tx.success(); tx.close();
		} catch (Exception e){
			error = true;
			e.printStackTrace();
		} finally {
			if(!error)
				System.out.println("Powers 4 are computed for all Nodes"); 
		}
	}
	
	public void computePageRank(int iterations, double dampingFactor, String weightProperty) {
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			tx.run("CALL algo.pageRank('Patient', 'TRANSMITS',\n" + 
					"  {direction:'BOTH', iterations:"+iterations+", dampingFactor:"+dampingFactor+", write: true,writeProperty:'pagerank"+iterations+"d"+Double.toString(dampingFactor).replace(".","")+"', weightProperty: '"+weightProperty+"',defaultValue:0.0})\n" + 
					"YIELD nodes, iterations, loadMillis, computeMillis, writeMillis, dampingFactor, write, writeProperty");
		     System.out.println("Page Rank Values are computed for all Nodes"); 
			tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
	public void computeEigenVector(int iterations, double dampingFactor, String weightProperty){
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			tx.run("CALL algo.eigenvector('Patient', 'TRANSMITS',\n" + 
					"  {direction:'BOTH', iterations:"+iterations+", dampingFactor:"+dampingFactor+", write: true,writeProperty:'eigenvector"+iterations+"d"+Double.toString(dampingFactor).replace(".","")+"', weightProperty: '"+weightProperty+"',defaultValue:0.0})\n" + 
					"YIELD nodes, iterations, loadMillis, computeMillis, writeMillis, dampingFactor, write, writeProperty");
		     System.out.println("Eigen Vector Values are computed for all Nodes"); 
			tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
	public void computeArticleRank(int iterations, double dampingFactor, String weightProperty){
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			tx.run("CALL algo.articleRank('Patient', 'TRANSMITS',\n" + 
					"  {direction:'BOTH', iterations:"+iterations+", dampingFactor:"+dampingFactor+", write: true,writeProperty:'articlerank"+iterations+"d"+Double.toString(dampingFactor).replace(".","")+"', weightProperty: '"+weightProperty+"',defaultValue:0.0})\n" + 
					"YIELD nodes, iterations, loadMillis, computeMillis, writeMillis, dampingFactor, write, writeProperty");
		     System.out.println("Article Rank Values are computed for all Nodes"); 
			tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
	public void computeDegreeCentrality(int iterations, double dampingFactor, String weightProperty){
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			tx.run("CALL algo.degree('Patient', 'TRANSMITS',\n" + 
					"  {direction:'BOTH', iterations:"+iterations+", dampingFactor:"+dampingFactor+", write: true,writeProperty:'degree"+iterations+"d"+Double.toString(dampingFactor).replace(".","")+"', weightProperty: '"+weightProperty+"',defaultValue:0.0})\n" + 
					"YIELD nodes, loadMillis, computeMillis, writeMillis, write, writeProperty");
		     System.out.println("Degree Centrality Values are computed for all Nodes"); 
			tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
	// Proteinler arası İlişkilerin yönleri bir şey ifade etmediği için direction özelliği "both" olarak seçilmiştir. Yön bilgisi anlamlı olsaydı "in" ya da "out" olarak da seçilebilirdi. 
	public void computeBetweennessCentrality(String weightProperty) {
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			
			tx.run("CALL algo.betweenness('Patient','TRANSMITS', {direction:'both',write:true, writeProperty:'betweenness',weightProperty:'"+weightProperty+"',defaultValue:0.0})\n" + 
					"YIELD nodes, minCentrality, maxCentrality, sumCentrality, loadMillis, computeMillis, writeMillis;");
			System.out.println("Betweenness Centrality Values are computed for all Nodes");  
			tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
	// Sadece bu sürümünde weight olabiliyormuş?
	public void computeClosenessCentrality(String weightProperty) {
			
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			tx.run("CALL algo.closeness(\n" + 
					"  'MATCH (p:Patient) RETURN id(p) as id',\n" + 
					"  'MATCH (p1:Patient)-[:TRANSMITS]-(p2:Patient) RETURN id(p1) as source, id(p2) as target',\n" + 
					"  {graph:'cypher', write: true, writeProperty:'closeness',weightProperty: '"+weightProperty+"', defaultValue:0.0}\n" + 
					");");
			System.out.println("Closeness Centrality Values are computed for all Nodes");   
			tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
	// Bu en doğru
	public void computeCloseness2Centrality(String weightProperty) {
		
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{
		tx.run("CALL algo.closeness('Patient', 'TRANSMITS', {direction:'both',write:true,writeProperty:'closeness2',weightProperty:'"+weightProperty+"',defaultValue:0.0})\n" + 
				"	YIELD nodes,loadMillis, computeMillis, writeMillis;");
		System.out.println("Closeness3 Centrality Values are computed");  
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
}

	public void computeHarmonicCentrality(String weightProperty) {
		
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{
		tx.run("CALL algo.closeness.harmonic('Patient', 'TRANSMITS', {write:true,writeProperty:'harmonic',weightProperty:'"+weightProperty+"',defaultValue:0.0})\n" + 
				"	YIELD nodes,loadMillis, computeMillis, writeMillis;");
		System.out.println("Harmonic Centrality Values are computed");  
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
}
	// not working
	public void computeLouvainCommunities(String weightProperty) {
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			tx.run("CALL algo.louvain(\n" + 
					"			  'Patient',\n" + 
					"			  'TRANSMITS',\n" + 
					"			  {weightProperty:'"+weightProperty+"', defaultValue:0.0, graph:'huge',write:true,writeProperty:'louvain'})");
			System.out.println("Louvain Communities are computed");  
			tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
public void computeLouvainCommunities2(String weightProperty) {
	
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{
		tx.run("CALL algo.louvain(\n" + 
				"			  'MATCH (p:Patient) RETURN id(p) as id',\n" + 
				"			  'MATCH (p1:Patient)-[f:TRANSMITS]-(p2:Patient)\n" + 
				"			   RETURN id(p1) as source, id(p2) as target, f."+weightProperty+" as weight',\n" + 
				"			  {weightProperty:'weight', defaultValue:0.0, graph:'cypher',write:true,writeProperty:'louvain2'})");
		System.out.println("Louvain Communities 2 are computed");  
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
}
// calismiyor
public void computeLabelPropagationCommunities(String propertyName,int iterations, String weightProperty) {
	
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{	
		tx.run("CALL algo.labelPropagation('Patient', 'TRANSMITS','BOTH',\n" + 
				"			  {weightProperty:'"+weightProperty+"', defaultValue:0.0, iterations:"+iterations+",partitionProperty:'"+propertyName+iterations+"', write:true})\n" + 
				"			YIELD nodes, iterations, loadMillis, computeMillis, writeMillis, write, partitionProperty;");
		System.out.println("Label Propagation Communities are computed");  
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
	
}

public void computeLabelPropagationCommunities2(String propertyName,int iterations, String weightProperty) {
	
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{	
		tx.run("CALL algo.labelPropagation('MATCH (p:Patient) RETURN id(p) as id', 'MATCH (p1:Patient)-[f:TRANSMITS]-(p2:Patient) RETURN id(p1) as source, id(p2) as target','BOTH',\n" + 
				"			  {weightProperty:'"+weightProperty+"', defaultValue:0.0, iterations:"+iterations+",partitionProperty:'"+propertyName+iterations+"', graph:'cypher', write:true})\n" + 
				"			YIELD nodes, iterations, loadMillis, computeMillis, writeMillis, write, partitionProperty;");
		System.out.println("Label Propagation Communities 2 are computed");  
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
	
}

public int computeUnionFind(String unionName, String weightProperty) {
	Session clccs = driver.session();
	StatementResult result;
	int count = 0;
	try ( org.neo4j.driver.v1.Transaction computeUF = clccs.beginTransaction() )
    {
		computeUF.run("CALL algo.unionFind('Patient','TRANSMITS', {weightProperty:'"+weightProperty+"', defaultValue:0.0, write: true, writeProperty:'"+unionName+"'});");
		result = computeUF.run("match (n:Patient)  where n."+unionName+" is not null with distinct(n."+unionName+") as clusterid, count(n) as clustersize return clustersize order by clustersize desc limit 1");
		count = Integer.parseInt(result.single().get("clustersize").toString());	
		computeUF.success();
		computeUF.close();
    } catch (Exception e){
    	  e.printStackTrace();
      } finally {clccs.close();}
	return count;
}

public int computeUnionFind2(String unionName, String weightProperty) {
	Session clccs = driver.session();
	StatementResult result;
	int count = 0;
	try ( org.neo4j.driver.v1.Transaction computeUF = clccs.beginTransaction() )
    {
		computeUF.run("CALL algo.unionFind('MATCH (p:Patient) RETURN id(p) as id','MATCH (p1:Patient)-[f:TRANSMITS]-(p2:Patient) RETURN id(p1) as source, id(p2) as target', {weightProperty:'"+weightProperty+"', defaultValue:0.0, graph:'cypher', write: true, writeProperty:'"+unionName+"'});");
		result = computeUF.run("match (n:Patient)  where n."+unionName+" is not null with distinct(n."+unionName+") as clusterid, count(n) as clustersize return clustersize order by clustersize desc limit 1");
		count = Integer.parseInt(result.single().get("clustersize").toString());	
		computeUF.success();
		computeUF.close();
    } catch (Exception e){
    	  e.printStackTrace();
      } finally {clccs.close();}
	return count;
}
// not working
public int computeSCC(String unionName, String weightProperty) {
	Session clccs = driver.session();
	StatementResult result;
	int count = 0;
	try ( org.neo4j.driver.v1.Transaction computeSCC = clccs.beginTransaction() )
    {
		computeSCC.run("CALL algo.scc('Patient','TRANSMITS', {weightProperty:'"+weightProperty+"', defaultValue:0.0, graph:'huge', write: true, writeProperty:'"+unionName+"'});");
		System.out.println("CALL algo.scc('Patient','TRANSMITS', {weightProperty:'"+weightProperty+"', defaultValue:0.0, graph:'huge', write: true, writeProperty:'"+unionName+"'});");
		result = computeSCC.run("match (n:Patient)  where n."+unionName+" is not null with distinct(n."+unionName+") as clusterid, count(n) as clustersize return clustersize order by clustersize desc limit 1");
		count = Integer.parseInt(result.single().get("clustersize").toString());	
		computeSCC.success();
		computeSCC.close();
    } catch (Exception e){
    	  e.printStackTrace();
      } finally {clccs.close();}
	return count;
}
// the same with union cluster
public int computeSCC2(String unionName, String weightProperty) {
	Session clccs = driver.session();
	StatementResult result;
	int count = 0;
	try ( org.neo4j.driver.v1.Transaction computeSCC = clccs.beginTransaction() )
    {
		computeSCC.run("CALL algo.scc('MATCH (p:Patient) RETURN id(p) as id','MATCH (p1:Patient)-[f:TRANSMITS]-(p2:Patient) RETURN id(p1) as source, id(p2) as target', {weightProperty:'"+weightProperty+"', defaultValue:0.0, graph:'cypher', write: true, writeProperty:'"+unionName+"'});");
		result = computeSCC.run("match (n:Patient)  where n."+unionName+" is not null with distinct(n."+unionName+") as clusterid, count(n) as clustersize return clustersize order by clustersize desc limit 1");
		count = Integer.parseInt(result.single().get("clustersize").toString());	
		computeSCC.success();
		computeSCC.close();
    } catch (Exception e){
    	  e.printStackTrace();
      } finally {clccs.close();}
	return count;
}

public ArrayList<ArrayList<Entity>> transmissionBetweenCountries(double levenshteinSimilarity) {
				
	StatementResult result;		
	ArrayList<ArrayList<Entity>> records = new ArrayList<ArrayList<Entity>>();
	ArrayList<Entity> record = new ArrayList<Entity>();
			
			try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
			{	
				result = tx.run("match (n:Patient)-[t:TRANSMITS]->(m:Patient) where apoc.text.levenshteinSimilarity(n.Isolation_Country,m.Isolation_Country) < "+levenshteinSimilarity+" return n,m,t order by t.distance desc");

				while(result.hasNext()){
					Record row = result.next();
					record.clear();
					for ( Entry<String,Object> column : row.asMap().entrySet() ){
						if(column.getValue()!=null)
							switch (column.getKey()) {
							case "n":
								record.add(0,row.get( column.getKey() ).asNode());
								break;
							case "m":
								record.add(1,row.get( column.getKey() ).asNode());
								break;
							case "t":
								record.add(2,row.get( column.getKey() ).asRelationship());
								break;
							default:
								System.out.println("Unexpected column"+column.getKey());
								break;
							}
						}
					records.add(new ArrayList<Entity>(record));
					}
				
				System.out.println("Transmission Between Countries are computed");  
				tx.success(); tx.close();
			} catch (Exception e){
				e.printStackTrace();
			}
		return records;	
			// ALmanya içeren 3 lüler
			// match (o:Patient)-[t2:TRANSMITS]-(n:Patient)-[t1:TRANSMITS]-(m:Patient) where (n.Isolation_Country = 'Germany' or m.Isolation_Country = 'Germany' or o.Isolation_Country = 'Germany') and apoc.text.levenshteinSimilarity(n.Isolation_Country,m.Isolation_Country) < 0.4 and apoc.text.levenshteinSimilarity(n.Isolation_Country,o.Isolation_Country) < 0.4 return o.Isolation_Country,t2.distance,n.Isolation_Country,t1.distance,m.Isolation_Country order by t1.distance+t2.distance
			// match (o:Patient)-[t2:TRANSMITS]-(n:Patient)-[t1:TRANSMITS]-(m:Patient) where (n.Isolation_Country CONTAINS 'Germany' or m.Isolation_Country CONTAINS 'Germany' or o.Isolation_Country CONTAINS 'Germany') and n.Isolation_Country <> m.Isolation_Country and n.Isolation_Country <> o.Isolation_Country return o.Isolation_Country,t2.distance,n.Isolation_Country,t1.distance,m.Isolation_Country order by t1.distance+t2.distance
			// match (o:Patient)-[q:TRANSMITS]-(n:Patient)-[p:TRANSMITS]-(m:Patient) where (n.Isolation_Country CONTAINS 'Germany' or m.Isolation_Country CONTAINS 'Germany' or o.Isolation_Country CONTAINS 'Germany') and n.Isolation_Country <> m.Isolation_Country and n.Isolation_Country <> o.Isolation_Country return o,q,n,p,m
			// match (o:Patient)-[q:TRANSMITS]-(n:Patient) where (n.Isolation_Country CONTAINS 'Germany' or o.Isolation_Country CONTAINS 'Germany') and n.Isolation_Country <> o.Isolation_Country return o,q,n
}

public ArrayList<ArrayList<Entity>> cliquesIncludingACountry(String country, double levenshteinSimilarity) {
	
	StatementResult result;	
	ArrayList<ArrayList<Entity>> records = new ArrayList<ArrayList<Entity>>();
	ArrayList<Entity> record = new ArrayList<Entity>();
	
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{	
		result = tx.run("match (o:Patient)-[t1:TRANSMITS]-(p:Patient)-[t2:TRANSMITS]-(q:Patient)-[t3:TRANSMITS]-(o) where apoc.text.levenshteinSimilarity(o.Isolation_Country,"+country+") > "+levenshteinSimilarity+" or apoc.text.levenshteinSimilarity(p.Isolation_Country,"+country+") > "+levenshteinSimilarity+" or apoc.text.levenshteinSimilarity(q.Isolation_Country,"+country+") > "+levenshteinSimilarity+" return o,t1,p,t2,q,t3 order by t1.distance+t2.distance+t3.distance");

		while(result.hasNext()){
			Record row = result.next();
			record.clear();
			for ( Entry<String,Object> column : row.asMap().entrySet() ){
				if(column.getValue()!=null)
					switch (column.getKey()) {
					case "o":
						record.add(0,row.get( column.getKey() ).asNode());
						break;
					case "t1":
						record.add(1,row.get( column.getKey() ).asRelationship());
					break;
					case "p":
						record.add(2,row.get( column.getKey() ).asNode());
						break;
					case "t2":
						record.add(3,row.get( column.getKey() ).asRelationship());
					break;
					case "q":
						record.add(4,row.get( column.getKey() ).asNode());
						break;
					case "t3":
						record.add(5,row.get( column.getKey() ).asRelationship());
					break;
				default:
						System.out.println("Unexpected column"+column.getKey());
						break;
					}
				}
			records.add(new ArrayList<Entity>(record));
			}
		
		System.out.println("Transmission Between Countries are computed");  
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
	return records;
	
}

public HashMap<String,ArrayList<Node>> computesharedMutationsWithinACluster(String communityType,long communityID) {
	
	StatementResult result;	
	HashMap<String,ArrayList<Node>> records = new HashMap<String,ArrayList<Node>>();

	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{	
		result = tx.run("match (o:Patient) where o."+communityType+" = "+communityID+" return o.pos,o.ref,o.alt,o");
		while(result.hasNext()){
			Record row = result.next();
			List<Object> plist = row.get(0).asList();
			List<Object> rlist = row.get(1).asList();
			List<Object> alist = row.get(2).asList();
			for (int p = 0;p<plist.size();p++) {
				String key = plist.get(p)+(String) rlist.get(p)+(String) alist.get(p);
	
					if(!records.containsKey(key))
						records.put(key, new ArrayList<Node>(Arrays.asList(row.get(3).asNode())));
					else
						{
							ArrayList<Node> al = records.get(key);
							al.add(row.get(3).asNode());
							records.put(key,al);
						}		
			}
			}
		System.out.println("Mutations in "+communityType+" and communityID "+communityID);
		for (Map.Entry<String,ArrayList<Node>> entry : records.entrySet())  {
			 System.out.println("Key = " + entry.getKey() + 
                     ", with "+ entry.getValue().size() + " occurences in "+removeDuplicates(entry.getValue()).size()+" nodes"); 
		}
		System.out.println();
           
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
	return records;	
}
// tek metoda indirgemek için long[] de yapilabilir
public HashMap<String,ArrayList<Node>> computesharedMutationsWithinAClusterArray(String communityType,long[] communityIDs){
		
	StatementResult result;	
	HashMap<String,ArrayList<Node>> records = new HashMap<String,ArrayList<Node>>();
	StringBuilder sb = new StringBuilder();
	sb.append("where ");
	for(int i= 0;i<communityIDs.length;i++) {
		sb.append("o.").append(communityType).append(" = ").append(communityIDs[i]);
		if(i<communityIDs.length-1)
			sb.append(" OR ");
	}

	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{		
		System.out.println("match (o:Patient) "+sb+" return o.pos,o.ref,o.alt,o");
		result = tx.run("match (o:Patient) "+sb+" return o.pos,o.ref,o.alt,o");
		int count = 0;
		while(result.hasNext()){
			count++;
			Record row = result.next();
			List<Object> plist = row.get(0).asList();
			List<Object> rlist = row.get(1).asList();
			List<Object> alist = row.get(2).asList();
			for (int p = 0;p<plist.size();p++) {
				String key = plist.get(p)+(String) rlist.get(p)+(String) alist.get(p);
	
					if(!records.containsKey(key))
						records.put(key, new ArrayList<Node>(Arrays.asList(row.get(3).asNode())));
					else
						{
							ArrayList<Node> al = records.get(key);
							al.add(row.get(3).asNode());
							records.put(key,al);
						}		
			}
			}
		System.err.println("count: "+count);
//		sb.setLength(0);
//		for(int i= 0;i<communityIDs.length;i++)
//			sb.append(communityIDs[i]).append("-");
//		
//		System.out.println("Mutations in "+communityType+" and communityIDs "+sb);
//		for (Map.Entry<String,ArrayList<Node>> entry : records.entrySet())  {
//			 System.out.println("Key = " + entry.getKey() + 
//                     ", with "+ entry.getValue().size() + " occurences in "+removeDuplicates(entry.getValue()).size()+" nodes"); 
//		}
//		System.out.println("");
           
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
	return records;	
}

public HashMap<String,ArrayList<Node>> computeSharedDrugResistanceWithinACluster(String communityType,long communityID) {
	StatementResult result;	
	HashMap<String,ArrayList<Node>> records = new HashMap<String,ArrayList<Node>>();

	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{	
		result = tx.run("match (o:Patient) where o."+communityType+" = "+communityID+" return o.drug_resistance,o");
		while(result.hasNext()){
			Record row = result.next();
			for (Object key : row.get(0).asList()) {
				String[] keys = ((String) key).split("_");
				for (int i = 0;i<keys.length;i++) {
					if(!records.containsKey(keys[i]))
						records.put(keys[i], new ArrayList<Node>(Arrays.asList(row.get(1).asNode())));
					else
						{
							ArrayList<Node> al = records.get(keys[i]);
							al.add(row.get(1).asNode());
							records.put(keys[i],al);
						}
				}
					
			}
			}
//		ArrayList<String> totalDrugResistances = new ArrayList<String>();
//		StringBuilder sb = new StringBuilder();
//		
//		System.out.println("Drug Resistances in "+communityType+" and communityID "+communityID);
//		for (Map.Entry<String,ArrayList<Node>> entry : records.entrySet())  {
//			 System.out.println("Key = " + entry.getKey() + 
//                     ", with "+ entry.getValue().size() + " occurences in "+removeDuplicates(entry.getValue()).size()+" nodes"); 
//			 
//			if (!totalDrugResistances.contains(entry.getKey())) {
//				totalDrugResistances.add(entry.getKey());
//				sb.append(entry.getKey());
//				sb.append(", ");
//			}
//			 
//		}
//		if(totalDrugResistances.size()>0)
//			sb.delete(sb.length()-2, sb.length());
//		System.out.println("TOTAL DRUG RESISTANCES");
//		System.err.println(sb);
//		System.out.println();
           
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
	
	return records;
}

public HashMap<String,ArrayList<Node>> computeSharedDrugResistanceWithinAClusterArray(String communityType,long[] communityIDs) {
	StatementResult result;	
	HashMap<String,ArrayList<Node>> records = new HashMap<String,ArrayList<Node>>();
	
	StringBuilder sb = new StringBuilder();
	sb.append("where ");
	for(int i= 0;i<communityIDs.length;i++) {
		sb.append("o.").append(communityType).append(" = ").append(communityIDs[i]);
		if(i<communityIDs.length-1)
			sb.append(" OR ");
	}

	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{	
		result = tx.run("match (o:Patient) "+sb+" return o.drug_resistance,o");
		while(result.hasNext()){
			Record row = result.next();
			for (Object key : row.get(0).asList()) {
				String[] keys = ((String) key).split("_");
				for (int i = 0;i<keys.length;i++) {
					if(!records.containsKey(keys[i]))
						records.put(keys[i], new ArrayList<Node>(Arrays.asList(row.get(1).asNode())));
					else
						{
							ArrayList<Node> al = records.get(keys[i]);
							al.add(row.get(1).asNode());
							records.put(keys[i],al);
						}
				}		
			}
			}
           
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
	
	return records;
}

public static <T> ArrayList<T> removeDuplicates(ArrayList<T> list) 
{ 

    // Create a new ArrayList 
    ArrayList<T> newList = new ArrayList<T>(); 

    // Traverse through the first list 
    for (T element : list) { 

        // If this element is not present in newList 
        // then add it 
        if (!newList.contains(element)) { 

            newList.add(element); 
        } 
    } 

    // return the new list 
    return newList; 
} 

public static ArrayList<String> detectAllKeyEntitiesWithinAHashMap(HashMap<String,ArrayList<Node>> clusterName){
	
	ArrayList<String> totalKeyEntities = new ArrayList<String>();
	for (Map.Entry<String,ArrayList<Node>> entry : clusterName.entrySet())  {
		 
		if (!totalKeyEntities.contains(entry.getKey())) {
			totalKeyEntities.add(entry.getKey());
		}	
		 
	}
	
	return totalKeyEntities;
	
}


public static ArrayList<Node> detectAllPatientsWithinACluster(HashMap<String,ArrayList<Node>> clusterName){
	
	ArrayList<Node> patients = new ArrayList<Node>();
	for (Map.Entry<String,ArrayList<Node>> entry : clusterName.entrySet())  {
		for (Node patient : entry.getValue())
			if (!patients.contains(patient)) {
			patients.add(patient);
		}	
	}	
	return patients;
}

public boolean[][] convertDrugResistancesToMatrix(HashMap<String,ArrayList<Node>> clusterName,String clusterType, long[] cluster_IDs,String sorter,boolean desc){
	
	final Object[] totalDrugResistances = detectAllKeyEntitiesWithinAHashMap(clusterName).toArray();
	// The line below does not retrieve patients unmapped to a key.
	//	final Object[] patients = detectAllPatientsWithinACluster(clusterName).toArray();	
	final Object[] patients  = sortCentralPatientsWithinACommunityArray(sorter,clusterType,cluster_IDs,desc).toArray();
	
	StringBuilder sbl = new StringBuilder();
	for (int i = 0;i<cluster_IDs.length;i++) {
		sbl.append(cluster_IDs[i]);
		if(i<cluster_IDs.length-1)
			sbl.append("_");	
	}
	
	boolean[][] drm = null;
	try {
		FileWriter fw = new FileWriter("drugresistances_"+clusterType+"_"+sbl+".txt");
		FileWriter fwl = new FileWriter("drugresistances_"+clusterType+"_"+sbl+"_labels.txt");
		ArrayList<String> clids = new ArrayList<String>();
		StringBuilder sb = new StringBuilder();
		
		for (int k = 0;k<totalDrugResistances.length;k++) {
			sb.append(totalDrugResistances[k]);
			
			if (k==totalDrugResistances.length-1)
				sb.append("\n");
			else
				sb.append(" ");		
		}
		
		drm = new boolean[patients.length][totalDrugResistances.length];
		
		for (int i = 0; i<patients.length;i++)  {	
			
			if(!clids.contains(String.valueOf(( (Node) patients[i]).get(clusterType).asInt())))
				clids.add(String.valueOf(( (Node) patients[i]).get(clusterType).asInt()));
			
			for (int j = 0;j<totalDrugResistances.length;j++) {
				
				List<Object> l =( (Node) patients[i]).get("drug_resistance").asList();
				List<Object> lsplitted = new ArrayList<Object>();
				for (Object o : l) {
				
					String[] sarray = ((String) o).split("_");
					for (int m = 0;m<sarray.length;m++)
						lsplitted.add(sarray[m]);
				}
				
				if(lsplitted.contains(totalDrugResistances[j]))
					drm[i][j] = true;
						
				if(drm[i][j])
					sb.append("1");
				else 
					sb.append("0");
				if(j==totalDrugResistances.length-1&&i==patients.length-1)
					;
				else if (j==totalDrugResistances.length-1)
					sb.append("\n");
				else if (j < totalDrugResistances.length-1)
					sb.append(" ");	
				else
					System.out.println("There is a vibe in the force");
			} 
			fwl.append(String.valueOf(clids.indexOf(String.valueOf(( (Node) patients[i]).get(clusterType).asInt()))+1));
			fwl/*.append(",").append(String.valueOf(( (Node) patients[i]).get(clusterType).asInt()))*/.append("\n");		
		}
		
		fw.write(sb.toString(),0,sb.toString().length());		
		fw.close();
		fwl.close();
		
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}

	return drm;
}

public boolean[][] convertMutationsToMatrix(HashMap<String,ArrayList<Node>> clusterName,String clusterType, long[] cluster_IDs, String sorter, boolean desc){
	
	final Object[] totalMutations = detectAllKeyEntitiesWithinAHashMap(clusterName).toArray();
	// The line below does not retrieve patients unmapped to a key.
	//	final Object[] patients = detectAllPatientsWithinACluster(clusterName).toArray();	
	final Object[] patients  = sortCentralPatientsWithinACommunityArray(sorter,clusterType,cluster_IDs,desc).toArray();
	
	boolean[][] drm = null;
	System.err.println(patients.length);
	System.err.println(totalMutations.length);
	
	StringBuilder sbl = new StringBuilder();
	for (int i = 0;i<cluster_IDs.length;i++) {
		sbl.append(cluster_IDs[i]);
		if(i<cluster_IDs.length-1)
			sbl.append("_");	
	}
	
	try {
		System.err.println("mutations_"+clusterType+"_"+sbl+".txt");
		FileWriter fw = new FileWriter("mutations_"+clusterType+"_"+sbl+".txt");
		FileWriter fwl = new FileWriter("mutations_"+clusterType+"_"+sbl+"_labels.txt");
		ArrayList<String> clids = new ArrayList<String>();
		StringBuilder sb = new StringBuilder();
		
		for (int k = 0;k<totalMutations.length;k++) {
			sb.append(totalMutations[k]);
			
			if (k==totalMutations.length-1)
				sb.append("\n");
			else
				sb.append(" ");		
		}
		
		drm = new boolean[patients.length][totalMutations.length];
		
		for (int i = 0; i<patients.length;i++)  {	
			if(!clids.contains(String.valueOf(( (Node) patients[i]).get(clusterType).asInt())))
				clids.add(String.valueOf(( (Node) patients[i]).get(clusterType).asInt()));
			for (int j = 0;j<totalMutations.length;j++) {
//				System.err.println(totalMutations[j]);		
				if(( concatanateItemsWithSameIndex(( (Node) patients[i]).get("pos").asList(),( (Node) patients[i]).get("ref").asList(),( (Node) patients[i]).get("alt").asList()).contains(totalMutations[j])))
					drm[i][j] = true;
				if(drm[i][j])
					sb.append("1");
				else 
					sb.append("0");
				if(j==totalMutations.length-1&&i==patients.length-1)
					;
				else if (j==totalMutations.length-1)
					sb.append("\n");
				else if (j < totalMutations.length-1)
					sb.append(" ");	
				else
					System.out.println("There is a vibe in the force");			
			} 
			fwl.append(String.valueOf(clids.indexOf(String.valueOf(( (Node) patients[i]).get(clusterType).asInt()))+1));
			fwl/*.append(",").append(String.valueOf(( (Node) patients[i]).get(clusterType).asInt()))*/.append("\n");				
		}
		fw.write(sb.toString(),0,sb.toString().length());		
		fw.close();
		fwl.close();
		
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}

	return drm;
}

public static List<String> concatanateItemsWithSameIndex(List<Object> pos, List<Object> ref, List<Object> alt){
	
	List<String> listOfMergedItems = new ArrayList<String>();
	
	for (int i = 0;i<pos.size();i++) {
		listOfMergedItems.add(String.valueOf(pos.get(i))+(String)ref.get(i)+(String)alt.get(i));
	}	
	return listOfMergedItems;	
}

public ArrayList<Node> sortCentralPatients(String centralityType, boolean desc) {
	StatementResult result;	
	ArrayList<Node> records = new ArrayList<Node>();
	
	String orderby = "";
	if(desc)
		orderby = " desc";
	
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{	
		result = tx.run("match (o:Patient) return o order by o."+centralityType+orderby);

		while(result.hasNext()){
			Record row = result.next();
			records.add(row.get(0).asNode());
			}
		
		System.out.println("Patients are sorted with respect to "+centralityType);  
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
	return records;
}

public ArrayList<Node> sortCentralPatientsWithinACommunity(String centralityType,String communityType,String communityID,boolean desc) {
	StatementResult result;	
	ArrayList<Node> records = new ArrayList<Node>();
	
	String orderby = "";
	if(desc)
		orderby = " desc";
	
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{	
		result = tx.run("match (o:Patient) where o."+communityType+" = "+communityID+" return o order by o."+centralityType+orderby);

		while(result.hasNext()){
			Record row = result.next();
			records.add(row.get(0).asNode());
			}
		
		System.out.println("Patients are sorted with respect to "+centralityType+" in "+communityType+" type communities with "+communityID+" ");  
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
	return records;
}

public ArrayList<Node> sortCentralPatientsWithinACommunityArray(String centralityType,String communityType,long[] communityIDs, boolean desc) {
	StatementResult result;	
	ArrayList<Node> records = new ArrayList<Node>();
	
	StringBuilder sb = new StringBuilder();
	StringBuilder sb2 = new StringBuilder();
	sb.append("where ");
	for(int i= 0;i<communityIDs.length;i++) {
		sb.append("o.").append(communityType).append(" = ").append(communityIDs[i]);
		sb2.append(communityIDs[i]);
		if(i<communityIDs.length-1) {
			sb.append(" OR ");
			sb2.append(" - ");
		}	
	}
	String orderby = "";
	if(desc)
		orderby = " desc";
	
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{	
		result = tx.run("match (o:Patient) "+sb+" return o order by o."+centralityType+orderby);

		while(result.hasNext()){
			Record row = result.next();
			records.add(row.get(0).asNode());
			}
		
		System.out.println("Patients are sorted with respect to "+centralityType+" in "+communityType+" type communities with "+sb2+" ");  
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
	return records;
}

public static BasicStatistics basicCentralityStatisticsWithinACommunity(String centralityType, ArrayList<Node> community) {
	
	double sum = 0;
	double max = Double.MIN_VALUE;
	double min = Double.MAX_VALUE;
	double stdev = 0;
	
	for (Node node : community) {
		sum+=node.get(centralityType).asDouble();
		if(node.get(centralityType).asDouble()>max)
			max = node.get(centralityType).asDouble();
		if(node.get(centralityType).asDouble()<min)
			min = node.get(centralityType).asDouble();
	}
	double average = (sum/community.size());
	for (Node node : community) {
		stdev+=Math.pow((node.get(centralityType).asDouble()-average), 2);
	}
	return new BasicStatistics(max,min,average,stdev/community.size());
}

public static StringBuilder buildNodeInformationMatrix(ArrayList<Node> patients, String[] nodeProperties) {
	StringBuilder sb = new StringBuilder();
	int count_drs = 0;
	
	sb.append("Isolate_ID").append(",").append("drug_resistances").append(",").append("noof_resistances").append(",").append("mutations").append(",").append("noof_mutations").append(",");
	
	for (int i = 0;i<nodeProperties.length;i++) {
		sb.append(nodeProperties[i]).append(",");
	}
		sb.delete(sb.length()-1, sb.length());
		sb.append("\n");
	
	for  (Node node : patients) {
		
		sb.append(node.get("Isolate_ID")).append(",");
		
		List<Object> patient_drs = node.get("drug_resistance").asList();
		List<Object> drssplitted = new ArrayList<Object>();
		count_drs = 0;
		for (Object drs: patient_drs) {	
			String[] sarray = ((String) drs).split("_");
			for (int m = 0;m<sarray.length;m++)
				if(!drssplitted.contains(sarray[m]))
						if(drssplitted.add(sarray[m])) {
							sb.append(sarray[m]).append("-");
							count_drs++;
						}
							
		}
		if(count_drs>0)
			sb.delete(sb.length()-1, sb.length());
		else
			sb.append("none");
		sb.append(",").append(count_drs).append(",");
		
		List<String> patient_mutations = concatanateItemsWithSameIndex(node.get("pos").asList(), node.get("ref").asList(),node.get("alt").asList());
		
		for (String mutation : patient_mutations) {
			sb.append(mutation).append("-");
		}
		if(patient_mutations.size()>0)
			sb.delete(sb.length()-1, sb.length());
		else
			sb.append("none");
		
		sb.append(",").append(patient_mutations.size()).append(",");
		
		for (int i = 0;i<nodeProperties.length;i++) {
			sb.append(node.get(nodeProperties[i])).append(",");
		}
		sb.delete(sb.length()-1, sb.length());
		sb.append("\n");
	}
	sb.delete(sb.length()-1, sb.length());
	
	return sb;
}
		
	public void deleteAllNodesRelationships(){
		System.out.println("DELETING ALL");
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
		      tx.run( "MATCH (n) DETACH DELETE n" );
		      System.out.println("Previous data cleared");
			tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
	public void removeAllTransmissions(){
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction())
	    {
			tx.run("match (n:Patient)-[r:TRANSMITS]->(m:Patient) delete r");
			System.out.println("Previous transmissions are deleted.");
			tx.success(); tx.close();
	    } catch (Exception e){
	    	  e.printStackTrace();
	      }

	}
	
	public void removeGreaterTransmissions(double treshold){
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction())
	    {
			tx.run("match (n:Patient)-[r:TRANSMITS]->(m:Patient) where r.distance >= "+treshold+" delete r");
			
			tx.success(); tx.close();
	    } catch (Exception e){
	    	  e.printStackTrace();
	      }
		System.out.println(treshold+" Greater transmissions are deleted.");
	}
	
public void removeIdenticalTransmissions(boolean removeOlder){
	System.out.println("removeIdenticalTransmissions");
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction())
    {
		if(removeOlder)
		tx.run("match (n:Patient)-[r:TRANSMITS]->(m:Patient)<-[k:TRANSMITS]-(n:Patient) where r.Isolate_ID = k.Isolate_ID and r.isolate_name = k.isolate_name and id(r) < id(k) delete r");
		else
		tx.run("match (n:Patient)-[r:TRANSMITS]->(m:Patient)<-[k:TRANSMITS]-(n:Patient) where r.Isolate_ID = k.Isolate_ID and r.isolate_name = k.isolate_name and id(r) >= id(k) delete r");
		tx.success(); tx.close();
    } catch (Exception e){
    	  e.printStackTrace();
      }
}

public void removeProperties(String[] properties) {
	System.out.println("removeProperties");
	
	for (int i = 0;i<properties.length;i++)
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction())
    {
		tx.run("match (n)-[t:TRANSMITS]->(m) remove t."+properties[i]+" return t");
		tx.success(); tx.close();
    } catch (Exception e){
    	  e.printStackTrace();
      }
}


public void assignPropertyNameToAnother(String finalPropertyName, String assignedPropertyName) {
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() ){
		tx.run( "match (n)-[t:TRANSMITS]->(m) SET t."+finalPropertyName+" = t."+assignedPropertyName);	
		tx.run( "match (n) SET n."+finalPropertyName+" = n."+assignedPropertyName);	
		tx.success(); tx.close();
}
catch(Exception e) {
	e.printStackTrace();
	System.err.println("assignPropertyToAnother Exception");
}
}

public void changeLabelOfUnconnectedNodes() {
	System.out.println("changeLabelOfUnconnectedNodes");
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction())
    {
		tx.run("match (n) where not (n)-[]-() remove n:Patient set n:SinglePatient");
		tx.success(); tx.close();
    } catch (Exception e){
    	  e.printStackTrace();
      }
}


/**
 * Bir ağda düğümler arası uzaklığın belirli bir sayıdan küçük olduğu tüm kenarların sayısı
 **/

public int countEdgesBelowDistance(double distance){
	int count = 0;
	StatementResult result;
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction()  )
	{
		result = tx.run( "match (n:Patient)-[r:TRANSMITS]->(m:Patient) where r.distance <="+distance+" return count(r)");
		count = Integer.parseInt(result.single().get("count(r)").toString());
		//result.close();
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
	
	return count;
}

public int countEdgesInACluster(long clusterid, String clusterType, double distance) {
	int count = 0;
	StatementResult result;
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction()  )
	{
		result = tx.run( "match (n:Patient)-[r:TRANSMITS]->(m:Patient) where r.distance <="+distance+" and n."+clusterType+" = "+clusterid+" and m."+clusterType+" = "+clusterid+" return count(r)");
		count = Integer.parseInt(result.single().get("count(r)").toString());
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
	
	return count;
}

//Sıralı olması gerekiyordu !!!
public ArrayList<PowerNode> findCentralNodes2( int limit){
	StatementResult result;
	ArrayList<PowerNode> nodes = new ArrayList<PowerNode>();
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction())
 {
		result = tx.run("match (n:Patient)-[t:TRANSMITS]-(m:Patient) return (n),count(m) as connections order by connections desc limit "+limit+"");
		Record record;
		while(result.hasNext()){
			record = result.next();
			nodes.add(new PowerNode(record.get(0).asNode(),record.get(1).asInt()));
			tx.success(); tx.close();
		}
 } catch (Exception e){
 	  e.printStackTrace();
   }
	return nodes;
	
}
//match (n:Organism1)-[i:INTERACTS_1]-(m:Organism1)-[i2:INTERACTS_1]-(o:Organism1)-[i3:INTERACTS_1]-(n) with count(n) as connections set n.power = connections
public ArrayList<PowerNode> findCentralNodes3(int limit){
	StatementResult result;
	ArrayList<PowerNode> nodes = new ArrayList<PowerNode>();
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction())
 {
		result = tx.run("match (n:Patient)-[i:TRANSMITS]-(m:Patient)-[i2:TRANSMITS]-(o:Patient)-[i3:TRANSMITS]-(n) return (n),count(i2) as connections order by connections desc limit "+limit+"");
		Record record;
		while(result.hasNext()){
			record = result.next();
			nodes.add(new PowerNode(record.get(0).asNode(),record.get(1).asInt()));
		}
		tx.success(); tx.close();
 } catch (Exception e){
 	  e.printStackTrace();
   }
	return nodes;
	
}

public ArrayList<PowerNode> findCentralNodes4( int limit){
	StatementResult result;
	ArrayList<PowerNode> nodes = new ArrayList<PowerNode>();
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction())
 {
		result = tx.run("match (a:Patient)-[r1:TRANSMITS]-(b:Patient)-[r2:TRANSMITS]-(c:Patient)-[r3:TRANSMITS]-(a:Patient),(c:Patient)-[r4:TRANSMITS]-(d:Patient)-[r5:TRANSMITS]-(a:Patient),(d:Patient)-[r6:TRANSMITS]-(b:Patient) return a,count(a) as connections order by connections desc limit "+limit+"");
		Record record;
		while(result.hasNext()){
			record = result.next();
			nodes.add(new PowerNode(record.get(0).asNode(),record.get(1).asInt()));
		}
		tx.success(); tx.close();
 } catch (Exception e){
 	  e.printStackTrace();
   }
	return nodes;
	
}
// If treshold is less than or equal to 1 then all the cluster ids including the ones with single elements are listed.
// When 0 is given as limit there is no limitation in the number of records.
public ArrayList<Long> detectCommunityIDs(String communityType,long treshold, int limit) {
	long activeTreshold = 1L;
	if(treshold>1)
		activeTreshold = treshold;
	String limitSuffix = "";
	if(limit>0)
		limitSuffix = " limit "+limit;
	
	StatementResult result;	
	ArrayList<Long> records = new ArrayList<Long>();
	
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{	
		result = tx.run("match (o:Patient) return o."+communityType+", count(o) order by count(o) desc"+limitSuffix);

		while(result.hasNext()){
			Record row = result.next();
			if(row.get(1).asLong()>activeTreshold) {
				records.add(row.get(0).asLong());
//				System.out.println("Community ID: "+row.get(0).asLong());
			}
			
			}
		
		System.out.println("Communities are detected with respect to "+communityType);  
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
	
	return records;
}
	
/**
 * Herhangi bir sorgu çalıştırmak için
 **/
public void queryGraph(String queryString){
	System.out.println("QUERYING");
	
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
		StatementResult result = tx.run( queryString );   
		while ( result.hasNext() )
		    {
		      System.out.println(result.next().toString());
		    }
		    tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
}

//7.4.4. The All Pairs Shortest Path algorithmten yararlanılabilir
//Aslında bir ülkeden diğerine en kısa yol olabilir
public ArrayList<Node> shortestPathNodesLeadingToACountry(String country,double levenshteinSimilarity, String distanceProperty) {
	StatementResult result;
	ArrayList<Node> al = new ArrayList<Node>();

	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() ){
		
		result = tx.run("CALL algo.allShortestPaths.stream('"+distanceProperty+"',{nodeQuery:'Patient',defaultValue:1.0,graph:'huge'})\n" + 
				"YIELD sourceNodeId, targetNodeId, distance where distance >= 0.0\n" + 
				"with sourceNodeId, targetNodeId, distance match (n:Patient) where ID(n) = sourceNodeId " +
				"with n,targetNodeId,distance match (m:Patient) where ID(m) = targetNodeId and apoc.text.levenshteinSimilarity(n.Isolation_Country,m.Isolation_Country) < "+levenshteinSimilarity+" and (apoc.text.levenshteinSimilarity(n.Isolation_Country,'"+country+"') > "+levenshteinSimilarity+" or apoc.text.levenshteinSimilarity('"+country+"',m.Isolation_Country) > "+levenshteinSimilarity+") " +
				"return n,m,distance order by distance asc");
	int count = 0;	
		while(result.hasNext()){
			Record row = result.next();
			for ( Entry<String,Object> column : row.asMap().entrySet() ){
				if(column.getValue()!=null)
					switch (column.getKey()) {
					case "n":
						if(!row.get( column.getKey() ).asNode().get("Isolation_Country").asString().toLowerCase().contains(country.toLowerCase()))
							al.add(row.get( column.getKey() ).asNode());
						break;
					case "m":
						if(!row.get( column.getKey() ).asNode().get("Isolation_Country").asString().toLowerCase().contains(country.toLowerCase()))
							al.add(row.get( column.getKey() ).asNode());
						break;
					case "distance":
						;
						break;
					default:
						System.out.println("Unexpected column"+column.getKey());
						break;	
					}
				if(row.get( column.getKey() ).asObject() instanceof Node)
					System.out.print(count+" - "+row.get( column.getKey() ).asNode().get("Isolation_Country").asString());
				System.out.print("   ");
				}
			System.out.println();
			count++;
			}
		
	tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
	return al;
}

public ArrayList<ShortestPathNodePair> shortestPathNodePairsLeadingToACountry(String country,double levenshteinSimilarity, String distanceProperty) {
	StatementResult result;
	ArrayList<ShortestPathNodePair> al = new ArrayList<ShortestPathNodePair>();

	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() ){

		result = tx.run("CALL algo.allShortestPaths.stream('"+distanceProperty+"',{nodeQuery:'Patient',defaultValue:1.0,graph:'huge'})\n" + 
				"YIELD sourceNodeId, targetNodeId, distance where distance >= 0.0\n" + 
				"with sourceNodeId, targetNodeId, distance match (n:Patient) where ID(n) = sourceNodeId " +
				"with n,targetNodeId,distance match (m:Patient) where ID(m) = targetNodeId and apoc.text.levenshteinSimilarity(n.Isolation_Country,m.Isolation_Country) < "+levenshteinSimilarity+" and (apoc.text.levenshteinSimilarity(n.Isolation_Country,'"+country+"') > "+levenshteinSimilarity+" or apoc.text.levenshteinSimilarity('"+country+"',m.Isolation_Country) > "+levenshteinSimilarity+") " +
				"return n,m,distance order by distance asc");
		int count = 0;
		while(result.hasNext()){
			Record row = result.next();
			ShortestPathNodePair spnp = new ShortestPathNodePair();
			for ( Entry<String,Object> column : row.asMap().entrySet() ){
				if(column.getValue()!=null)
					switch (column.getKey()) {
					case "n":
						spnp.setNode1(row.get( column.getKey() ).asNode());
						break;
					case "m":
						spnp.setNode2(row.get( column.getKey() ).asNode());
						break;
					case "distance":
						spnp.setDistance(row.get( column.getKey() ).asDouble());;
						break;
					default:
						System.out.println("Unexpected column"+column.getKey());
						break;	
					}
				}
			al.add(spnp);
			count++;
			System.out.println();
			}
		System.err.println(" shortest path count: "+count);
	tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
	
	try {
		FileWriter fw = new FileWriter("ShortestPathsTo"+country+".txt");
		
		for (int i = 0;i<al.size();i++) {
			fw.append(al.get(i).node1.get("Isolation_Country").asString()).append("!").append(Double.toString(al.get(i).distance)).append("!").append(al.get(i).node2.get("Isolation_Country").asString());	
			if(i<al.size()-1)
				fw.append("\n");
		}
		fw.close();
		
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	
	return al;
}

// diğer ülkelerdeki hastalardan hedef ülkeye ulaşabilecek en merkezi olanı
// 7.4.4. The All Pairs Shortest Path algorithmten yararlanılabilir
//Aslında bir ülkeden diğerine en kısa yol olabilir

public Node mostCentralPatientThatCanAccessTargetCountry(String targetCountry, String centrality, int limit) {
	return null;
}

public SubGraph minimumSpanningTreeOfANode(Long nodeID,boolean removeEdge, String distanceProperty) {
	StatementResult result;
	SubGraph sg = new SubGraph();
	
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{
	tx.run( "CALL algo.spanningTree.minimum('Patient','TRANSMITS','"+distanceProperty+"',"+nodeID+",{write:true,writeProperty:'minspan"+nodeID+"'}) \n" + 
			"YIELD loadMillis, computeMillis, writeMillis, effectiveNodeCount" );   
	result = tx.run("match (p)-[r:minspan"+nodeID+"]-(q)-[t:TRANSMITS]-(p) return p,q,t");
//	System.out.println(result.list().size()+" records");
	while(result.hasNext()){
		Record row = result.next();
		for ( Entry<String,Object> column : row.asMap().entrySet() ){
			if(column.getValue()!=null)
				switch (column.getKey()) {
				case "p":
					sg.patients.add(row.get( column.getKey() ).asNode());
					break;
				case "q":
					sg.patients.add(row.get( column.getKey() ).asNode());
					break;
				case "t":
					sg.transmissions.add(row.get( column.getKey() ).asRelationship());
					break;
				default:
					System.out.println("Unexpected column"+column.getKey());
					break;
				}
			}
		}
	if(removeEdge)
		tx.run( "match ()-[r:minspan"+nodeID+"]->() delete r");
	tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
	
	return sg;
}

	public static void main(String[] args) {
		databaseAddress = args[2];	
		final DiseaseCluster as = new DiseaseCluster(1,args[2],100,20,"weight","distance");	
//		as.deleteAllNodesRelationships();
//		as.createGraph(args[0], args[1],args[2]);
//		as.computeFullMutations(args[3]);
		
		
		
		as.removeGreaterTransmissions(13);
		
		
		as.changeLabelOfUnconnectedNodes();
//		as.computeFullMutationDifferences();
//		as.computePowers();
		
		
//		String[] properties = {"pagerank","eigenvector","articlerank","degree"};
//		as.removeProperties(properties);
//		as.computeWeightForMutationDifferences();
//		as.computeWeightForFullMutationDifferences();
		
//		as.assignPropertyNameToAnother("distance", "mutation_difference");
//		as.assignPropertyNameToAnother("weight", "weightmd2");
		
		

		as.computePageRank(20, 0.85,as.weightProperty);
		as.computeArticleRank(20, 0.85,as.weightProperty);
		as.computeEigenVector(20, 0.85,as.weightProperty);
		as.computeDegreeCentrality(20, 0.85,as.weightProperty);
		as.computePageRank(20, 0.75,as.weightProperty);
		as.computeArticleRank(20, 0.75,as.weightProperty);
		as.computeEigenVector(20, 0.75,as.weightProperty);
		as.computeDegreeCentrality(20, 0.75,as.weightProperty);
		as.computePageRank(20, 0.95,as.weightProperty);
		as.computeArticleRank(20, 0.95,as.weightProperty);
		as.computeEigenVector(20, 0.95,as.weightProperty);
		as.computeDegreeCentrality(20, 0.95,as.weightProperty);
		as.computeBetweennessCentrality(as.weightProperty);
		as.computeClosenessCentrality(as.weightProperty);
		as.computeCloseness2Centrality(as.weightProperty);
		as.computeHarmonicCentrality(as.weightProperty);
		as.computeLouvainCommunities(as.weightProperty);
		as.computeLouvainCommunities2(as.weightProperty);
		as.computeLabelPropagationCommunities("lp1", 1,as.weightProperty);
		as.computeLabelPropagationCommunities2("lp2", 1,as.weightProperty);
		as.computeLabelPropagationCommunities("lp1", 2,as.weightProperty);
		as.computeLabelPropagationCommunities2("lp2", 2,as.weightProperty);
		as.computeLabelPropagationCommunities("lp1", 3,as.weightProperty);
		as.computeLabelPropagationCommunities2("lp2", 3,as.weightProperty);
		as.computeLabelPropagationCommunities("lp1", 4,as.weightProperty);
		as.computeLabelPropagationCommunities2("lp2", 4,as.weightProperty);
		as.computeLabelPropagationCommunities("lp1", 5,as.weightProperty);
		as.computeLabelPropagationCommunities2("lp2", 5,as.weightProperty);
		as.computeLabelPropagationCommunities("lp1", 6,as.weightProperty);
		as.computeLabelPropagationCommunities2("lp2", 6,as.weightProperty);
		as.computeUnionFind("union_cluster",as.weightProperty);
		as.computeUnionFind("union2_cluster",as.weightProperty);
//		as.computeSCC("scc_cluster",weightProperty);
		as.computeSCC2("scc2_cluster",as.weightProperty);
		as.computeDistanceMetaData();
		as.computeCentralityMetaData();
		System.err.println("kamil");
//		as.computeWeightForMutationDifferences();
//		as.computeWeightForFullMutationDifferences();
		
		ArrayList<Long> al = as.detectCommunityIDs("union_cluster", 0,5);
//		long[] single1= {al.get(0)};
//		long[] single2= {al.get(1)};
//		long[] merged= {al.get(0),al.get(1)};
//		as.convertMutationsToMatrix(as.computesharedMutationsWithinAClusterArray("union_cluster",single1),"union_cluster",single1,"pagerank20d085",true);
//		as.convertMutationsToMatrix(as.computesharedMutationsWithinAClusterArray("union_cluster",single2),"union_cluster",single2,"pagerank20d085",true);
//		as.convertMutationsToMatrix(as.computesharedMutationsWithinAClusterArray("union_cluster",merged),"union_cluster",merged,"pagerank20d085",true);
		
		for (int i =0;i<al.size();i++)
			for (int j =0;j<al.size();j++) if(i>j){	
				long[] temp = {al.get(i),al.get(j)};
			HashMap<String,ArrayList<Node>> hm = as.computeSharedDrugResistanceWithinAClusterArray("union_cluster", temp);
			ArrayList<Node> patients = detectAllPatientsWithinACluster(hm);
			BasicStatistics bs = basicCentralityStatisticsWithinACommunity("pagerank20d085",patients);
			System.out.println(bs);
			
			as.convertDrugResistancesToMatrix(hm,"union_cluster",temp,"pagerank20d085",true);		
			as.convertMutationsToMatrix(as.computesharedMutationsWithinAClusterArray("union_cluster", temp),"union_cluster",temp,"pagerank20d085",true);	
		}
	
		ArrayList<Long> al2 = as.detectCommunityIDs("louvain", 0,5);
		
		for (int i =0;i<al2.size();i++) 
			for (int j =0;j<al2.size();j++) if(i>j){	
				long[] temp = {al2.get(i),al2.get(j)};
			HashMap<String,ArrayList<Node>> hm2 = as.computeSharedDrugResistanceWithinAClusterArray("louvain", temp);
			ArrayList<Node> patients = detectAllPatientsWithinACluster(hm2);
			BasicStatistics bs2 = basicCentralityStatisticsWithinACommunity("pagerank20d085",patients);
			System.out.println(bs2);
			
			as.convertDrugResistancesToMatrix(hm2,"louvain",temp,"pagerank20d085",true);		
			as.convertMutationsToMatrix(as.computesharedMutationsWithinAClusterArray("louvain", temp),"louvain",temp,"pagerank20d085",true);	
		}
		
//		
		ArrayList<Long> al3 = as.detectCommunityIDs("lp26", 0,5);
		
		for (int i =0;i<al3.size();i++) 
			for (int j =0;j<al3.size();j++) if(i>j){	
				long[] temp = {al3.get(i),al3.get(j)};
			HashMap<String,ArrayList<Node>> hm3 = as.computeSharedDrugResistanceWithinAClusterArray("lp26", temp);
			ArrayList<Node> patients = detectAllPatientsWithinACluster(hm3);
			BasicStatistics bs3 = basicCentralityStatisticsWithinACommunity("pagerank20d085",patients);
			System.out.println(bs3);
			
			as.convertDrugResistancesToMatrix(hm3,"lp26",temp,"pagerank20d085",true);		
			as.convertMutationsToMatrix(as.computesharedMutationsWithinAClusterArray("lp26", temp),"lp26",temp,"pagerank20d085",true);	
		}
		
		ArrayList<Long> al4 = as.detectCommunityIDs("lp16", 0,5);
		
		for (int i =0;i<al4.size();i++) 
			for (int j =0;j<al4.size();j++) if(i>j){	
				long[] temp = {al4.get(i),al4.get(j)};
			HashMap<String,ArrayList<Node>> hm4 = as.computeSharedDrugResistanceWithinAClusterArray("lp16", temp);
			ArrayList<Node> patients = detectAllPatientsWithinACluster(hm4);
			BasicStatistics bs4 = basicCentralityStatisticsWithinACommunity("pagerank20d085",patients);
			System.out.println(bs4);
			
			as.convertDrugResistancesToMatrix(hm4,"lp16",temp,"pagerank20d085",true);		
			as.convertMutationsToMatrix(as.computesharedMutationsWithinAClusterArray("lp16", temp),"lp16",temp,"pagerank20d085",true);	
		}

		try {
			FileWriter fw = new FileWriter("centralities.txt");
			String[] centralities = {"no_of_full_mutations","pagerank20d075","articlerank20d075","pagerank20d085","articlerank20d085","pagerank20d095","articlerank20d095","eigenvector20d085","degree20d085","betweenness","closeness","closeness2","harmonic","power2","power3","power4","louvain2","lp21","lp22","lp23","lp24","lp25","lp26","union_cluster","union2_cluster","scc_cluster","scc2_cluster"};
			StringBuilder sb = buildNodeInformationMatrix(as.sortCentralPatients("pagerank20d085",true), centralities);
			
			fw.write(sb.toString(),0,sb.toString().length());		
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//		
//		try {
//			FileWriter fw = new FileWriter("nusret2.txt");
//			String[] centralities = {"pagerank20d075","articlerank20d075","pagerank20d085","articlerank20d085","pagerank20d095","articlerank20d095","eigenvector20d085","degree20d085","betweenness","closeness","closeness2","harmonic","power2","power3","power4"};
//			StringBuilder sb = buildNodeInformationMatrix(as.sortCentralPatientsWithinACommunity("pagerank20d085","lp24",String.valueOf(as.detectCommunityIDs("lp24", 5).get(0)),true), centralities);
//			
//			fw.write(sb.toString(),0,sb.toString().length());		
//			fw.close();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		System.out.println("deneme");
		
		
		try {
			FileWriter fw = new FileWriter("communities.txt");
			String[] communities = {"louvain","louvain2","lp11","lp21","lp12","lp22","lp13","lp23","lp14","lp24","lp15","lp25","lp16","lp26","union_cluster","union2_cluster","scc_cluster","scc2_cluster"};
			StringBuilder sb = buildNodeInformationMatrix(as.sortCentralPatients("pagerank20d085",true), communities);
			
			fw.write(sb.toString(),0,sb.toString().length());		
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			FileWriter fw = new FileWriter("ForeignNodesReachingToGermany.txt");
			String[] centralities = {"no_of_full_mutations","Isolation_Country","pagerank20d075","articlerank20d075","pagerank20d085","articlerank20d085","pagerank20d095","articlerank20d095","eigenvector20d085","degree20d085","betweenness","closeness","closeness2","harmonic","power2","power3","power4"};
			StringBuilder sb = buildNodeInformationMatrix(as.shortestPathNodesLeadingToACountry("Germany",0.4,as.distanceProperty), centralities);
//			System.out.println(sb);
			
			fw.write(sb.toString(),0,sb.toString().length());		
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		ArrayList<Node> alpagerank = as.sortCentralPatients("pagerank20d085",true);
		FileWriter fw;
		try {
			fw = new FileWriter("MinimumSpanningTrees.txt");
			for (int t =0;t<alpagerank.size();t++) {
				
				long l = alpagerank.get(t).id();
				fw.append("Country of Patient: "+alpagerank.get(t).get("Isolation_Country").asString()+" - "+alpagerank.get(t).get("Isolate_ID").asString()+" - "+l); fw.append("\n");
				System.out.println("Country of Patient: "+alpagerank.get(t).get("Isolation_Country").asString()+" - "+alpagerank.get(t).get("Isolate_ID").asString()+" - "+l);
				SubGraph sg = as.minimumSpanningTreeOfANode(l,true,as.distanceProperty);
//				fw.append("Size of MST: "+sg.patients.size()); fw.append("\n");
//				System.out.println("Size of MST: "+sg.patients.size());
				Set<String> countries = new HashSet<String>();
				List<String> countriesOccurences = new ArrayList<String>();
				for (Node node: sg.patients) {
					countries.add(node.get("Isolation_Country").asString());	
					countriesOccurences.add(node.get("Isolation_Country").asString());
				}
				StringBuilder cntrs = new StringBuilder();
				cntrs.append("Countries of Neighbours: ");
				for (String country : countries) {
					cntrs.append(country).append(": ").append(Collections.frequency(countriesOccurences, country)).append(", ");
				}
				cntrs.delete(cntrs.length()-2, cntrs.length());
				System.out.println(cntrs);
				fw.append(cntrs);	fw.append("\n");
				fw.append("no of transmissions: "+sg.transmissions.size()+" - "+"no of patients: "+sg.patients.size());  fw.append("\n");
				System.out.println("no of transmissions: "+sg.transmissions.size()+" - "+"no of patients: "+sg.patients.size());
				fw.append("\n");
			}
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		as.shortestPathNodePairsLeadingToACountry("Germany",0.4,as.distanceProperty);
		System.exit(0);

	}
}

class DrugResistancePrevalance {
	String drug;
	String clusterType;
	String clusterID;
	ArrayList<Node> patients;
	
	public DrugResistancePrevalance(String drug, String clusterType, String clusterID, ArrayList<Node> patients) {
		super();
		this.drug = drug;
		this.clusterType = clusterType;
		this.clusterID = clusterID;
		this.patients = patients;
	}

	@Override
	public String toString() {
		return "DrugResistancePrevalance [drug=" + drug + ", clusterType=" + clusterType + ", clusterID=" + clusterID
				+ ", patients=" + patients + "]";
	}

}

class BasicStatistics {
	double max;
	double min;
	double average;
	double stdev;
	
	public BasicStatistics(double max,double min, double average, double stdev) {
		// TODO Auto-generated constructor stub
		this.max = max;
		this.min = min;
		this.average = average;
		this.stdev = stdev;
	}

	@Override
	public String toString() {
		return "BasicStatistics [max=" + max + ", min=" + min + ", average=" + average + ", stdev=" + stdev + "]";
	}
	
}
