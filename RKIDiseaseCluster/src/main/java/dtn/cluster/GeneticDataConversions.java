package dtn.cluster;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GeneticDataConversions {
	
	
	public static void convertDistanceMatrixToGeneticDistancesFile(String inputFileName,String outputFileName, double treshold) {
		
		FileReader fr;
		FileWriter fw;
		try {
			fw = new FileWriter(outputFileName);
			fr = new FileReader(inputFileName);
			BufferedReader br = new BufferedReader(fr); 
			String[] patients = br.readLine().split("\t");
			String[] distances;
			String line;
			int count = 1;
			while((line = br.readLine())!=null)
			{ 
				distances = line.split("\t");

				
				for (int i = 1;i<distances.length;i++) {
					if(Double.valueOf(distances[i])<treshold&&!distances[0].equals(patients[i])&&count>i) {
							fw.append(distances[0]).append("\t").append(patients[i]).append("\t").append(distances[i]).append("\n");
					}
						
				}
				count++;
			}
			br.close();
			fr.close();
			fw.close();
		} catch ( IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static int convertVCFToMutationData(String inputPath, String extension, String outputFileName) {
		int count = 0;	
		FileReader fr;
		FileWriter fw;
		BufferedReader br;
		try (Stream<Path> walk = Files.walk(Paths.get(inputPath))) {
			List<String> patientVCFs = walk.map(x -> x.toString())
					.filter(f -> f.endsWith("."+extension)).collect(Collectors.toList());
			patientVCFs.forEach(System.out::println);
			fw = new FileWriter(outputFileName);
			String line;
			String[] mutationInfo;
			for (String patient : patientVCFs) {
				
				fr = new FileReader(patient);
				br = new BufferedReader(fr); 
				
				while((line = br.readLine())!=null)
				{ 
					mutationInfo = line.split("\t");
					File f = new File(patient);
					fw.append(f.getAbsolutePath().substring(f.getAbsolutePath().lastIndexOf("/")+1)).append("\t").append(mutationInfo[1]).append("\t").append(mutationInfo[3]).append("\t").append(mutationInfo[4]).append("\n");
				}
				count++;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return count;
	}
	
	public static void main(String[] args) {
		convertDistanceMatrixToGeneticDistancesFile("rkiall_sramdr_pairwise_rel_fract.txt","acaba.txt",30);
		convertVCFToMutationData("/media/giray/Windows/Nextcloud/transmission networks/christine data/vcfs","vcf","vcfInfo.txt");
	}

}
