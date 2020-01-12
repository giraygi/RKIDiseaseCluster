package dtn.cluster;

import java.util.ArrayList;

import org.junit.Test;

public class UnitTest1 {

	@Test
	public void testComputesharedMutationsWithinAClusterArray() {
		
		DiseaseCluster.databaseAddress = "resistance_data_SRA_RKI.csv";	
		final DiseaseCluster as = new DiseaseCluster("weight","distance",RelTypes.TRANSMITS.name(),20);	
		
		as.computeMetaData();
		ArrayList<Long> al = as.detectCommunityIDs("union_cluster", 5,0);
		long[] x= {al.get(0),al.get(1)};
		as.convertMutationsToMatrix(as.computesharedMutationsWithinAClusterArray("union_cluster",x),"union_cluster",x,"pagerank",true);
//		fail("Not yet implemented");
	}

}
