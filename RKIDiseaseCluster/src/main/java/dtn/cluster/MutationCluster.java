package dtn.cluster;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

public class MutationCluster {
	
	static GraphDatabaseService graphDb;
	static String databaseAddress;
	FileReader fr;
	BufferedReader br;
	FileWriter fw;
	BufferedWriter bw;
	static Driver driver;
	Session session;
	
	
	public void init(String args){

		GraphDatabaseService graphDb = new GraphDatabaseFactory()
                .newEmbeddedDatabase( new File("~/"+args) );
		DiseaseCluster.graphDb = graphDb;
		driver = GraphDatabase.driver( "bolt://localhost:7688", AuthTokens.basic( "neo4j", "evet" ) );
		session = driver.session();
	}
	
	public void createGraph(){
		
	}

}
