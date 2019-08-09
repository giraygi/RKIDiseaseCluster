package dtn.cluster;

public class MetaData {
	
	double noofPercentileSteps = 10.0;
	
	double[] distance;
	
	double[] Power2;
	double[] Power3;
	double[] Power4;
	
	double[] Pagerank;
	double[] Betweenness ;
	double[] Closeness;
	double[] Harmonic;
	
	MetaData(){
		noofPercentileSteps = 10.0;
		distance = new double[10];
		Power2 = new double[10];
		Power3 = new double[10];
		Power4 = new double[10];
		
		Pagerank = new double[10];
		Betweenness = new double[10];
		Closeness = new double[10];
		Harmonic= new double[10];
	}
	
	MetaData(int n) {
		noofPercentileSteps = (double)n;
		distance = new double[n];
		Power2 = new double[n];
		Power3 = new double[n];
		Power4 = new double[n];
		
		Pagerank = new double[n];
		Betweenness = new double[n];
		Closeness = new double[n];
		Harmonic= new double[n];
	}

}
