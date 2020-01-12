package dtn.cluster;

import static org.junit.Assert.*;

import org.junit.Test;

public class UnitTest2 {

	@Test
	public void testShortestPathNodePairsLeadingToACountry() {
		
		DiseaseCluster.databaseAddress = "resistance_data_SRA_RKI.csv";	
		final DiseaseCluster as = new DiseaseCluster("weight","distance",RelTypes.TRANSMITS.name(),20);	
		
		as.computeMetaData();
		as.computeCentralityMetaData();
		
		int noofRecords = as.shortestPathNodePairsLeadingToACountry("Germany",0.4,"distance").size();
		
		assertEquals(782,noofRecords);
//		fail("Not yet implemented");
	}

}
