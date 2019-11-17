package dtn.cluster;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.summary.ResultSummary;
import org.neo4j.driver.v1.types.Entity;
import org.neo4j.driver.v1.types.Node;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.helpers.TransactionTemplate;

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
	int numberOfNodePairsWithBothSimilarityAndCommonAnnotations = 0;
	int noofAligners;
	MetaData md;
	
	
	
	public DiseaseCluster(int noofAligners, String args, int toleranceLimitForUnimprovedAligners,int toleranceCycleForUnimprovedAligners){
		this.noofAligners = noofAligners;
		this.init(args);
		md  = new MetaData(20);
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
				tx.run("CREATE (a:Patient {Isolate_ID: '"+isolateInfo[0]+"', Previous_Cluster_ID: "+isolateInfo[1]+", BioProject: '"+isolateInfo[3]+"', BioSample: '"+isolateInfo[4]+"', Sequencing_Institution: '"+isolateInfo[5]+"', Sample_Name: '"+isolateInfo[6]+"', Isolation_Date: '"+isolateInfo[7]+"', Isolation_Country: '"+isolateInfo[8]+"', Host_Age: '"+isolateInfo[9]+"', host_anti_retroviral_status: '"+isolateInfo[10]+"', host_hiv_status: '"+isolateInfo[11]+"', host_hiv_status_diagnosis_postmortem: '"+isolateInfo[12]+"', host_location_sampled: '"+isolateInfo[13]+"', patient_gender: '"+isolateInfo[14]+"', host_subject: '"+isolateInfo[15]+"', isolate_name: '"+isolateInfo[16]+"', strain: '"+isolateInfo[17]+"', power2: 0, power3: 0, power4: 0, pos:[],ref:[],alt:[],drug_resistance:[],multidrug_resistance:'NA'})");
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
				} else
					from = distanceInfo[0];
				
				
				if (distanceInfo[1].charAt(0) == 'X') {		
					to = distanceInfo[1].substring(1, distanceInfo[1].split("_")[0].length()).replace('.', '-');
					System.out.println(to);
				} else
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
				tx.run("MATCH (n:Patient {Isolate_ID: '"+resistanceInfo[0]+"'}) SET n.pos = n.pos +  "+resistanceInfo[1]+",n.ref = n.ref + '"+resistanceInfo[2]+"',n.alt = n.alt + '"+resistanceInfo[3]+"', n.drug_resistance = n.drug_resistance + '"+resistanceInfo[4]+"',n.multidrug_resistance = '"+resistanceInfo[5]+"' return (n)");
			}
			
			System.out.println("Resistances are created.");	
			
			tx.success(); tx.close();
		} catch (Exception e) {
			System.out.println("Could not create isolates, distances and resistances");
			e.printStackTrace();
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
				System.out.println(i+". Similarity "+md.distance[i] );
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
				System.out.println(i+". Power2: "+md.Power2[i] );
			}
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (p:Patient) return percentileCont(p.power3,"+(i+1)/md.noofPercentileSteps+")");
				md.Power3[i] = Double.parseDouble(result.single().get("percentileCont(p.power3,"+(i+1)/md.noofPercentileSteps+")").toString());
				System.out.println(i+". Power3: "+md.Power3[i] );
			}
			
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (p:Patient) return percentileCont(p.power4,"+(i+1)/md.noofPercentileSteps+")");
				md.Power4[i] = Double.parseDouble(result.single().get("percentileCont(p.power4,"+(i+1)/md.noofPercentileSteps+")").toString());
				System.out.println(i+". Power4: "+md.Power4[i] );
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
				result = tx.run( "match (p:Patient) return percentileCont(p.pagerank,"+(i+1)/md.noofPercentileSteps+")");
				md.Pagerank[i] = Double.parseDouble(result.single().get("percentileCont(p.pagerank,"+(i+1)/md.noofPercentileSteps+")").toString());
				System.out.println(i+". Pagerank: "+md.Pagerank[i] );
			}
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (p:Patient) return percentileCont(p.betweenness,"+(i+1)/md.noofPercentileSteps+")");
				md.Betweenness[i] = Double.parseDouble(result.single().get("percentileCont(p.betweenness,"+(i+1)/md.noofPercentileSteps+")").toString());
				System.out.println(i+". Betweenness: "+md.Betweenness[i] );
			}
			
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (p:Patient) return percentileCont(p.closeness,"+(i+1)/md.noofPercentileSteps+")");
				md.Closeness[i] = Double.parseDouble(result.single().get("percentileCont(p.closeness,"+(i+1)/md.noofPercentileSteps+")").toString());
				System.out.println(i+". Closeness: "+md.Closeness[i] );
			}
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (p:Patient) return percentileCont(p.harmonic,"+(i+1)/md.noofPercentileSteps+")");
				md.Harmonic[i] = Double.parseDouble(result.single().get("percentileCont(p.harmonic,"+(i+1)/md.noofPercentileSteps+")").toString());
				System.out.println(i+". Harmonic: "+md.Harmonic[i] );
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
			tx.run("match (a:Patient)-[r1:TRANSMITS]-(b:Patient)-[r2:TRANSMITS]-(c:Patient)-[r3:TRANSMITS]-(a:Patient),(c:Patient)-[r4:TRANSMITS]-(d:Patient)-[r5:TRANSMITS]-(a:Patient),(d:Patient)-[r6:TRANSMITS]-(b:Patient) with (a),count(a) as connections set a.power4 = connections");
			System.out.println("Powers are computed for all Nodes");      
			tx.success(); tx.close();
		} catch (Exception e){
			error = true;
			e.printStackTrace();
		} finally {
			if(!error)
				System.out.println("Powers are computed for all Nodes"); 
		}
	}
	
	public void computePageRank(int iterations, double dampingFactor) {
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			tx.run("CALL algo.pageRank('Patient', 'TRANSMITS',\n" + 
					"  {direction:'BOTH', iterations:"+iterations+", dampingFactor:"+dampingFactor+", write: true,writeProperty:'pagerank', weightProperty: 'weight',defaultValue:0.0})\n" + 
					"YIELD nodes, iterations, loadMillis, computeMillis, writeMillis, dampingFactor, write, writeProperty");
		     System.out.println("Page Rank Values are computed for all Nodes"); 
			tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
	public void computeEigenVector(int iterations, double dampingFactor){
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			tx.run("CALL algo.eigenvector('Patient', 'TRANSMITS',\n" + 
					"  {direction:'BOTH', iterations:"+iterations+", dampingFactor:"+dampingFactor+", write: true,writeProperty:'eigenvector', weightProperty: 'weight',defaultValue:0.0})\n" + 
					"YIELD nodes, iterations, loadMillis, computeMillis, writeMillis, dampingFactor, write, writeProperty");
		     System.out.println("Eigen Vector Values are computed for all Nodes"); 
			tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
	public void computeArticleRank(int iterations, double dampingFactor){
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			tx.run("CALL algo.articleRank('Patient', 'TRANSMITS',\n" + 
					"  {direction:'BOTH', iterations:"+iterations+", dampingFactor:"+dampingFactor+", write: true,writeProperty:'articlerank', weightProperty: 'weight',defaultValue:0.0})\n" + 
					"YIELD nodes, iterations, loadMillis, computeMillis, writeMillis, dampingFactor, write, writeProperty");
		     System.out.println("Article Rank Values are computed for all Nodes"); 
			tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
	public void computeDegreeCentrality(int iterations, double dampingFactor){
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			tx.run("CALL algo.degree('Patient', 'TRANSMITS',\n" + 
					"  {direction:'BOTH', iterations:"+iterations+", dampingFactor:"+dampingFactor+", write: true,writeProperty:'degree', weightProperty: 'weight',defaultValue:0.0})\n" + 
					"YIELD nodes, loadMillis, computeMillis, writeMillis, write, writeProperty");
		     System.out.println("Degree Centrality Values are computed for all Nodes"); 
			tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
	// Proteinler arası İlişkilerin yönleri bir şey ifade etmediği için direction özelliği "both" olarak seçilmiştir. Yön bilgisi anlamlı olsaydı "in" ya da "out" olarak da seçilebilirdi. 
	public void computeBetweennessCentrality() {
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			
			tx.run("CALL algo.betweenness('Patient','TRANSMITS', {direction:'both',write:true, writeProperty:'betweenness',weightProperty:'weight',defaultValue:0.0})\n" + 
					"YIELD nodes, minCentrality, maxCentrality, sumCentrality, loadMillis, computeMillis, writeMillis;");
			System.out.println("Betweenness Centrality Values are computed for all Nodes");  
			tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
	// Sadece bu sürümünde weight olabiliyormuş?
	public void computeClosenessCentrality() {
			
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			tx.run("CALL algo.closeness(\n" + 
					"  'MATCH (p:Patient) RETURN id(p) as id',\n" + 
					"  'MATCH (p1:Patient)-[:TRANSMITS]-(p2:Patient) RETURN id(p1) as source, id(p2) as target',\n" + 
					"  {graph:'cypher', write: true, writeProperty:'closeness',weightProperty: 'weight', defaultValue:0.0}\n" + 
					");");
			System.out.println("Closeness Centrality Values are computed for all Nodes");   
			tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
	// Bu en doğru
	public void computeCloseness2Centrality() {
		
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{
		tx.run("CALL algo.closeness('Patient', 'TRANSMITS', {direction:'both',write:true,writeProperty:'closeness2',weightProperty:'weight',defaultValue:0.0})\n" + 
				"	YIELD nodes,loadMillis, computeMillis, writeMillis;");
		System.out.println("Closeness3 Centrality Values are computed");  
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
}

	public void computeHarmonicCentrality() {
		
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{
		tx.run("CALL algo.closeness.harmonic('Patient', 'TRANSMITS', {write:true,writeProperty:'harmonic',weightProperty:'weight',defaultValue:0.0})\n" + 
				"	YIELD nodes,loadMillis, computeMillis, writeMillis;");
		System.out.println("Harmonic Centrality Values are computed");  
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
}
	
	public void computeLouvainCommunities() {
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			tx.run("CALL algo.louvain(\n" + 
					"			  'Patient',\n" + 
					"			  'TRANSMITS',\n" + 
					"			  {weightProperty:'weight', defaultValue:0.0, graph:'huge',write:true,writeProperty:'louvain'})");
			System.out.println("Louvain Communities are computed");  
			tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
public void computeLouvainCommunities2() {
	
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{
		tx.run("CALL algo.louvain(\n" + 
				"			  'MATCH (p:Patient) RETURN id(p) as id',\n" + 
				"			  'MATCH (p1:Patient)-[f:TRANSMITS]-(p2:Patient)\n" + 
				"			   RETURN id(p1) as source, id(p2) as target, f.weight as weight',\n" + 
				"			  {weightProperty:'weight', defaultValue:0.0, graph:'cypher',write:true,writeProperty:'louvain2'})");
		System.out.println("Louvain Communities 2 are computed");  
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
}
// calismiyor
public void computeLabelPropagationCommunities(String propertyName,int iterations) {
	
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{	
		tx.run("CALL algo.labelPropagation('Patient', 'TRANSMITS','BOTH',\n" + 
				"			  {weightProperty:'weight', defaultValue:0.0, iterations:"+iterations+",partitionProperty:'"+propertyName+iterations+"'', write:true})\n" + 
				"			YIELD nodes, iterations, loadMillis, computeMillis, writeMillis, write, partitionProperty;");
		System.out.println("Label Propagation Communities are computed");  
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
	
}

public void computeLabelPropagationCommunities2(String propertyName,int iterations) {
	
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{	
		tx.run("CALL algo.labelPropagation('MATCH (p:Patient) RETURN id(p) as id', 'MATCH (p1:Patient)-[f:TRANSMITS]-(p2:Patient) RETURN id(p1) as source, id(p2) as target','BOTH',\n" + 
				"			  {weightProperty:'weight', defaultValue:0.0, iterations:"+iterations+",partitionProperty:'"+propertyName+iterations+"', graph:'cypher', write:true})\n" + 
				"			YIELD nodes, iterations, loadMillis, computeMillis, writeMillis, write, partitionProperty;");
		System.out.println("Label Propagation Communities 2 are computed");  
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
	
}

public int computeUnionFind(String unionName) {
	Session clccs = driver.session();
	StatementResult result;
	int count = 0;
	try ( org.neo4j.driver.v1.Transaction computeUF = clccs.beginTransaction() )
    {
		computeUF.run("CALL algo.unionFind('Patient','TRANSMITS', {weightProperty:'weight', defaultValue:0.0, write: true, writeProperty:'"+unionName+"'});");
		result = computeUF.run("match (n:Patient)  where n."+unionName+" is not null with distinct(n."+unionName+") as clusterid, count(n) as clustersize return clustersize order by clustersize desc limit 1");
		count = Integer.parseInt(result.single().get("clustersize").toString());	
		computeUF.success();
		computeUF.close();
    } catch (Exception e){
    	  e.printStackTrace();
      } finally {clccs.close();}
	return count;
}

public int computeUnionFind2(String unionName) {
	Session clccs = driver.session();
	StatementResult result;
	int count = 0;
	try ( org.neo4j.driver.v1.Transaction computeUF = clccs.beginTransaction() )
    {
		computeUF.run("CALL algo.unionFind('MATCH (p:Patient) RETURN id(p) as id','MATCH (p1:Patient)-[f:TRANSMITS]-(p2:Patient) RETURN id(p1) as source, id(p2) as target', {weightProperty:'weight', defaultValue:0.0, graph:'cypher', write: true, writeProperty:'"+unionName+"'});");
		result = computeUF.run("match (n:Patient)  where n."+unionName+" is not null with distinct(n."+unionName+") as clusterid, count(n) as clustersize return clustersize order by clustersize desc limit 1");
		count = Integer.parseInt(result.single().get("clustersize").toString());	
		computeUF.success();
		computeUF.close();
    } catch (Exception e){
    	  e.printStackTrace();
      } finally {clccs.close();}
	return count;
}
// calismiyor
public int computeSCC(String unionName) {
	Session clccs = driver.session();
	StatementResult result;
	int count = 0;
	try ( org.neo4j.driver.v1.Transaction computeSCC = clccs.beginTransaction() )
    {
		computeSCC.run("CALL algo.scc('Patient','TRANSMITS', {weightProperty:'weight', defaultValue:0.0, graph:'huge', write: true, writeProperty:'"+unionName+"'});");
		result = computeSCC.run("match (n:Patient)  where n."+unionName+" is not null with distinct(n."+unionName+") as clusterid, count(n) as clustersize return clustersize order by clustersize desc limit 1");
		count = Integer.parseInt(result.single().get("clustersize").toString());	
		computeSCC.success();
		computeSCC.close();
    } catch (Exception e){
    	  e.printStackTrace();
      } finally {clccs.close();}
	return count;
}
// union cluster ile aynı
public int computeSCC2(String unionName) {
	Session clccs = driver.session();
	StatementResult result;
	int count = 0;
	try ( org.neo4j.driver.v1.Transaction computeSCC = clccs.beginTransaction() )
    {
		computeSCC.run("CALL algo.scc('MATCH (p:Patient) RETURN id(p) as id','MATCH (p1:Patient)-[f:TRANSMITS]-(p2:Patient) RETURN id(p1) as source, id(p2) as target', {weightProperty:'weight', defaultValue:0.0, graph:'cypher', write: true, writeProperty:'"+unionName+"'});");
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
		
		ArrayList<String> totalDrugResistances = new ArrayList<String>();
		StringBuilder sb = new StringBuilder();
		
		System.out.println("Drug Resistances in "+communityType+" and communityID "+communityID);
		for (Map.Entry<String,ArrayList<Node>> entry : records.entrySet())  {
			 System.out.println("Key = " + entry.getKey() + 
                     ", with "+ entry.getValue().size() + " occurences in "+removeDuplicates(entry.getValue()).size()+" nodes"); 
			 
			if (!totalDrugResistances.contains(entry.getKey())) {
				totalDrugResistances.add(entry.getKey());
				sb.append(entry.getKey());
				sb.append(", ");
			}
			 
		}
		if(totalDrugResistances.size()>0)
			sb.delete(sb.length()-2, sb.length());
		System.out.println("TOTAL DRUG RESISTANCES");
		System.err.println(sb);
		System.out.println();
           
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
		
		for (Node patient : removeDuplicates(entry.getValue()))
			if (!patients.contains(patient)) {
			patients.add(patient);
		}	
		 
	}	
	return patients;
}

public static boolean[][] convertDrugResistancesToMatrix(HashMap<String,ArrayList<Node>> clusterName,String clusterType, Long cluster_ID){
	
	final Object[] totalDrugResistances = detectAllKeyEntitiesWithinAHashMap(clusterName).toArray();
	final Object[] patients = detectAllPatientsWithinACluster(clusterName).toArray();
	boolean[][] drm = null;
	try {
		FileWriter fw = new FileWriter("drugresistances_"+clusterType+"_"+cluster_ID+".txt");
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
		}
		
		fw.write(sb.toString(),0,sb.toString().length());		
		fw.close();
		
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}

	return drm;
}

public static boolean[][] convertMutationsToMatrix(HashMap<String,ArrayList<Node>> clusterName,String clusterType, Long cluster_ID){
	
	final Object[] totalMutations = detectAllKeyEntitiesWithinAHashMap(clusterName).toArray();
	final Object[] patients = detectAllPatientsWithinACluster(clusterName).toArray();
	boolean[][] drm = null;
	try {
		FileWriter fw = new FileWriter("mutations_"+clusterType+"_"+cluster_ID+".txt");
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
			for (int j = 0;j<totalMutations.length;j++) {
				
				System.err.println(totalMutations[j]);
				
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
		}
		
		fw.write(sb.toString(),0,sb.toString().length());		
		fw.close();
		
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
//		System.out.println("adding::: "+(String)pos.get(i)+(String)ref.get(i)+(String)alt.get(i));
	}	
	return listOfMergedItems;	
}

public ArrayList<Node> sortCentralPatients(String centralityType) {
	StatementResult result;	
	ArrayList<Node> records = new ArrayList<Node>();
	
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{	
		result = tx.run("match (o:Patient) return o order by o."+centralityType+" desc");

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

public ArrayList<Node> sortCentralPatientsWithinACommunity(String centralityType,String communityType,String communityID) {
	StatementResult result;	
	ArrayList<Node> records = new ArrayList<Node>();
	
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{	
		result = tx.run("match (o:Patient) where o."+communityType+" = "+communityID+" return o order by o."+centralityType+" desc");

		while(result.hasNext()){
			Record row = result.next();
			records.add(row.get(0).asNode());
			}
		
		System.out.println("Paients are sorted with respect to "+centralityType+" in "+communityType+" type communities with "+communityID+" ");  
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
			unMarkSession.run("MATCH ()-[r:ALIGNS]->() WHERE EXISTS(r.markedQuery) SET r.markedQuery = FILTER(x IN r.markedQuery WHERE x <> '"+markedQuery+"')");
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

//AkkaSystemde bulunması daha doğru olabilir.
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
	
	try {
		fr = new FileReader(fileName);
	} catch (FileNotFoundException e1) {
		// TODO Auto-generated catch block
		e1.printStackTrace();
	}
	br =  new BufferedReader(fr); 
	String line = null;
	String[] markedQueries;
	Session saveOldQuerySession = DiseaseCluster.driver.session();
	
	try {
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
				saveOldQuerySession.run("match ()-[a:ALIGNS]->() where a.alignmentIndex = '"+markedQueries[0]+"'");	
			}
		}
	} catch (Exception e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}	
} 
// Hizalayıcı numaraları elle veriliyor. Ön tanımlı: 1-10
//Uygulamada mevcut durumda veritabanında bulunan İşaretli Sorgular daha sonra tekrar veritbanına yüklenebilecek biçimde dosyaya kaydedilir.
public void saveOldMarkedQueriesToFile(String fileName) {
	StatementResult result;
	Record record;
		
		Session womqtd = DiseaseCluster.driver.session();
		try(BufferedWriter bw = new BufferedWriter(new FileWriter(fileName))){
			
			for (int i = 1;i<11;i++) {
				result = womqtd.run("match ()-[a:TRANSMITS]-() where a.alignmentNumber = '"+i+"' return distinct a.markedQuery,a.alignmentIndex");
				while(result.hasNext()){
					record = result.next();
					bw.write("AlignmentIndex:"+record.get(1).asString()+" ");
					if(!record.get(0).isNull())
					for (Object o : record.get(0).asList()) {
						bw.write((String) o+" ");
						System.out.println((String) o);
					}
					bw.newLine();
				}
				
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

public ArrayList<Long> detectCommunityIDs(String communityType,long treshold) {
	long activeTreshold = 1L;
	if(treshold>1)
		activeTreshold = treshold;
	
	StatementResult result;	
	ArrayList<Long> records = new ArrayList<Long>();
	
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{	
		result = tx.run("match (o:Patient) return o."+communityType+", count(o) order by count(o) desc");

		while(result.hasNext()){
			Record row = result.next();
			if(row.get(1).asLong()>activeTreshold) {
				records.add(row.get(0).asLong());
				System.out.println("Community ID: "+row.get(0).asLong());
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
public ArrayList<Node> shortestPathNodesLeadingToACountry(String country,double levenshteinSimilarity) {
	StatementResult result;
	ArrayList<Node> al = new ArrayList<Node>();
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() ){
		
		result = tx.run("CALL algo.allShortestPaths.stream('distance',{nodeQuery:'Loc',defaultValue:1.0,graph:'huge'})\n" + 
				"YIELD sourceNodeId, targetNodeId, distance where distance > 0.0\n" + 
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

// diğer ülkelerdeki hastalardan hedef ülkeye ulaşabilecek en merkezi olanı
// 7.4.4. The All Pairs Shortest Path algorithmten yararlanılabilir
//Aslında bir ülkeden diğerine en kısa yol olabilir

public Node mostCentralPatientThatCanAccessTargetCountry(String targetCountry, String centrality, int limit) {
	return null;
}

public SubGraph minimumSpanningTreeOfANode(Long nodeID,boolean removeEdge) {
	StatementResult result;
	SubGraph sg = new SubGraph();
	
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{
	tx.run( "CALL algo.spanningTree.minimum('Patient','TRANSMITS','distance',"+nodeID+",{write:true,writeProperty:'minspan"+nodeID+"'})\n" + 
			"YIELD loadMillis, computeMillis, writeMillis, effectiveNodeCount" );   
	result = tx.run("match (p)-[r:minspan"+nodeID+"]-(q)-[t:TRANSMITS]-(p) return p,q,t");
	
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
		final DiseaseCluster as = new DiseaseCluster(1,args[2],100,20);	
//		as.deleteAllNodesRelationships();
//		as.createGraph(args[0], args[1],args[2]);
//		as.computePowers();
//		as.computePageRank(20, 0.85);
//		as.computeEigenVector(20, 0.85);
//		as.computeArticleRank(20, 0.85);
//		as.computeDegreeCentrality(20, 0.85);
//		as.computeBetweennessCentrality();
//		as.computeClosenessCentrality();
//		as.computeCloseness2Centrality();
//		as.computeHarmonicCentrality();
//		as.computeLouvainCommunities();
//		as.computeLouvainCommunities2();
//		as.computeLabelPropagationCommunities("lp1", 1);
//		as.computeLabelPropagationCommunities2("lp2", 1);
//		as.computeLabelPropagationCommunities("lp1", 2);
//		as.computeLabelPropagationCommunities2("lp2", 2);
//		as.computeLabelPropagationCommunities("lp1", 3);
//		as.computeLabelPropagationCommunities2("lp2", 3);
//		as.computeLabelPropagationCommunities("lp1", 4);
//		as.computeLabelPropagationCommunities2("lp2", 4);
//		as.computeUnionFind("union_cluster");
//		as.computeUnionFind("union2_cluster");
//		as.computeSCC("scc_cluster");
//		as.computeSCC2("scc2_cluster");
		as.computeDistanceMetaData();
		as.computeCentralityMetaData();
		
		ArrayList<Long> al = as.detectCommunityIDs("union_cluster", 5);
		
		for (int i =0;i<al.size();i++) {		
			HashMap<String,ArrayList<Node>> hm = as.computeSharedDrugResistanceWithinACluster("union_cluster", al.get(i));
			ArrayList<Node> patients = detectAllPatientsWithinACluster(hm);
			BasicStatistics bs = basicCentralityStatisticsWithinACommunity("pagerank",patients);
			System.out.println("average pagerank: "+bs.average);
			System.out.println("max pagerank: "+bs.max);
			System.out.println("min pagerank: "+bs.min);
			System.out.println("stdev pagerank: "+bs.stdev);
			
			convertDrugResistancesToMatrix(hm,"union_cluster",al.get(i));		
			convertMutationsToMatrix(as.computesharedMutationsWithinACluster("union_cluster", al.get(i)),"union_cluster",al.get(i));	
		}
		
		
		ArrayList<Long> al2 = as.detectCommunityIDs("louvain", 5);
		
		for (int i =0;i<al2.size();i++) {		
			HashMap<String,ArrayList<Node>> hm2 = as.computeSharedDrugResistanceWithinACluster("louvain", al2.get(i));
			ArrayList<Node> patients = detectAllPatientsWithinACluster(hm2);
			BasicStatistics bs2 = basicCentralityStatisticsWithinACommunity("pagerank",patients);
			System.out.println("average pagerank: "+bs2.average);
			System.out.println("max pagerank: "+bs2.max);
			System.out.println("min pagerank: "+bs2.min);
			System.out.println("stdev pagerank: "+bs2.stdev);
			
			convertDrugResistancesToMatrix(hm2,"louvain",al2.get(i));		
			convertMutationsToMatrix(as.computesharedMutationsWithinACluster("louvain", al2.get(i)),"louvain",al2.get(i));	
		}
		
		
		ArrayList<Long> al3 = as.detectCommunityIDs("lp23", 5);
		
		for (int i =0;i<al3.size();i++) {		
			HashMap<String,ArrayList<Node>> hm3 = as.computeSharedDrugResistanceWithinACluster("lp23", al3.get(i));
			ArrayList<Node> patients = detectAllPatientsWithinACluster(hm3);
			BasicStatistics bs3 = basicCentralityStatisticsWithinACommunity("pagerank",patients);
			System.out.println("average pagerank: "+bs3.average);
			System.out.println("max pagerank: "+bs3.max);
			System.out.println("min pagerank: "+bs3.min);
			System.out.println("stdev pagerank: "+bs3.stdev);
			
			convertDrugResistancesToMatrix(hm3,"lp23",al3.get(i));		
			convertMutationsToMatrix(as.computesharedMutationsWithinACluster("lp23", al3.get(i)),"lp23",al3.get(i));	
		}
		
		try {
			FileWriter fw = new FileWriter("nazmi.txt");
			String[] centralities = {"pagerank","eigenvector","articlerank","degree","betweenness","closeness","closeness2","harmonic","power2","power3","power4"};
			StringBuilder sb = buildNodeInformationMatrix(as.sortCentralPatients("pagerank"), centralities);
			
			fw.write(sb.toString(),0,sb.toString().length());		
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			FileWriter fw = new FileWriter("faruk.txt");
			String[] centralities = {"pagerank","eigenvector","articlerank","degree","betweenness","closeness","closeness2","harmonic","power2","power3","power4"};
			StringBuilder sb = buildNodeInformationMatrix(as.shortestPathNodesLeadingToACountry("Germany",0.4), centralities);
			
			fw.write(sb.toString(),0,sb.toString().length());		
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		ArrayList<Node> alpagerank = as.sortCentralPatients("closeness");
		for (int t =0;t<alpagerank.size();t++) {
			long l = alpagerank.get(t).id();
			SubGraph sg = as.minimumSpanningTreeOfANode(l,true);
			System.out.println("no of transmissions"+sg.transmissions.size()+" - "+"no of patients"+sg.patients.size());
		}	
	}
}

class DrugResistancePrevalance {
	String drug;
	String clusterType;
	String clusterID;
	ArrayList<Node> patients;;
	int noOfResistantPatients;
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
}
