package dtn.cluster;

import java.io.FileWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.neo4j.driver.v1.types.Node;

public class CentralityProduction {
	public static void main(String[] args) {
		int activeRelType = Integer.valueOf(args[1]);
		final DiseaseCluster as = new DiseaseCluster("weight","distance", RelTypes.values()[activeRelType].name(),20);
		
		for (int i = 0; i < RelTypes.values().length;i++)
			if (i != activeRelType)
				as.removeAllInteractionsWithLabel(RelTypes.values()[i].name());

//		as.removeGreaterInteractionsByTresholdValue(13, RelTypes.TRANSMITS.name());
		as.computeAggregateInteractionMetaData();
		
		double threshold = Double.valueOf(args[0]).doubleValue();
		System.out.println("Threshold value is: "+threshold);
		
//		System.out.println("Number of Similarity Links: "+as.numberOfSimilarityLinks);
		
		as.removeGreaterInteractionsByPercentile(threshold, RelTypes.values()[activeRelType].name());
		
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
		as.computeHarmonicCentrality(as.weightProperty);
		
		as.computePowers();
		
		
		try {
			FileWriter fw = new FileWriter("centralities"+as.globalLabel+args[0].substring(2)+".csv");
			String[] centralities = {"pagerank20d075","articlerank20d075","pagerank20d085","articlerank20d085","pagerank20d095","articlerank20d095","eigenvector20d085","degree20d085","betweenness","closeness","harmonic","power2","power3,","power4","no_of_full_mutations"};
			StringBuilder sb = buildNodeInformationMatrix(as.sortCentralPatientsWithLabel("pagerank20d085",true,""), centralities);
			
			fw.write(sb.toString(),0,sb.toString().length());		
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.exit(0);
		
	}

//	public static StringBuilder buildNodeInformationMatrix(ArrayList<Node> patients, String[] nodeProperties) {
//		StringBuilder sb = new StringBuilder();
//		
//		for (int i = 0;i<nodeProperties.length;i++) {
//			sb.append(nodeProperties[i]).append(",");
//		}
//			sb.delete(sb.length()-1, sb.length());
//			sb.append("\n");
//		
//		for  (Node node : patients) {
//			
//			for (int i = 0;i<nodeProperties.length;i++) {
//				sb.append(node.get(nodeProperties[i])).append(",");
//			}
//			sb.delete(sb.length()-1, sb.length());
//			sb.append("\n");
//		}
//		sb.delete(sb.length()-1, sb.length());
//		
//		return sb;
//	}
//	
	
	public static StringBuilder buildNodeInformationMatrix(ArrayList<Node> patients, String[] nodeProperties) {
		StringBuilder sb = new StringBuilder();
		int count_drs = 0;
			
		for (int i = 0;i<nodeProperties.length;i++) {
			sb.append(nodeProperties[i]).append(",");
			
			if (i == nodeProperties.length -2) {
				sb.append("noof_resistances").append(",").append("noof_mutations").append(",");
			}
			
		}
			sb.delete(sb.length()-1, sb.length());
			sb.append("\n");
		
		for  (Node node : patients) {
			
			//sb.append(node.get("Isolate_ID")).append(",");
			
			
			
			for (int i = 0;i<nodeProperties.length;i++) {
				sb.append(node.get(nodeProperties[i])).append(",");
				
				
				if (i ==nodeProperties.length-2) {
					
					List<Object> patient_drs = node.get("drug_resistance").asList();
					List<Object> drssplitted = new ArrayList<Object>();
					count_drs = 0;
					for (Object drs: patient_drs) {	
						String[] sarray = ((String) drs).split("_");
						for (int m = 0;m<sarray.length;m++)
							if(!drssplitted.contains(sarray[m]))
									if(drssplitted.add(sarray[m])) {
										//sb.append(sarray[m]).append("-");
										count_drs++;
									}
										
					}
					/*if(count_drs>0)
						sb.delete(sb.length()-1, sb.length());
					else
						sb.append("none");
					sb.append(",")*/sb.append(count_drs).append(",");
					
					List<String> patient_mutations = concatanateItemsWithSameIndex(node.get("pos").asList(), node.get("ref").asList(),node.get("alt").asList());
					
					/*for (String mutation : patient_mutations) {
						sb.append(mutation).append("-");
					}
					if(patient_mutations.size()>0)
						sb.delete(sb.length()-1, sb.length());
					else
						sb.append("none");
					
					sb.append(",")*/sb.append(patient_mutations.size()).append(",");
									
				}
			}
			sb.delete(sb.length()-1, sb.length());
			sb.append("\n");
		}
		sb.delete(sb.length()-1, sb.length());
		
		return sb;
	}
	
	
	
	public static List<String> concatanateItemsWithSameIndex(List<Object> pos, List<Object> ref, List<Object> alt){
		
		List<String> listOfMergedItems = new ArrayList<String>();
		
		for (int i = 0;i<pos.size();i++) {
			listOfMergedItems.add(String.valueOf(pos.get(i))+(String)ref.get(i)+(String)alt.get(i));
		}	
		return listOfMergedItems;	
	}
}
