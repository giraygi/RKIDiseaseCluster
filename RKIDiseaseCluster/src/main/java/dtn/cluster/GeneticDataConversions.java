package dtn.cluster;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GeneticDataConversions {
	
	
	public static void convertDistanceMatrixToGeneticDistancesFile(String inputFileName,String outputFileName, ArrayList<String> excludeList, double treshold) {
		
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
						if(!excludeList.contains(distances[0])&&!excludeList.contains(patients[i]))
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
//					String[] path = patient.split("/");		
					fw.append(f.getAbsolutePath().substring(f.getAbsolutePath().lastIndexOf("/")+1)).append("\t").append(mutationInfo[1]).append("\t").append(mutationInfo[3]).append("\t").append(mutationInfo[4]).append("\n");
//					fw.append(path[path.length-1]).append("\t").append(mutationInfo[1]).append("\t").append(mutationInfo[3]).append("\t").append(mutationInfo[4]).append("\n");
				}
				count++;
				
			}
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return count;
	}
	
	public static void printSimilarFiles(String inputPath, String extension) {
		
		try (Stream<Path> walk = Files.walk(Paths.get(inputPath))) {
			List<String> patientVCFs = walk.map(x -> x.toString())
					.filter(f -> f.endsWith("."+extension)).collect(Collectors.toList());
			
			for (String string1 : patientVCFs) {
				for (String string2 : patientVCFs) {
					if (string1.split("_")[0].equals(string2.split("_")[0])&&!string1.equals(string2))
						System.out.println(string1.split("_")[0]);
				}
			}
		
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	public static void printExtraFilesFromVCFDirectory(String isolatesFile, String VCFDirectory) {
		
		FileReader fr;
		BufferedReader br;
		ArrayList<String> patients = new ArrayList<String>();
		
		try {
			fr = new FileReader(isolatesFile);
			br = new BufferedReader(fr); 
			String line;
			
			
			while((line = br.readLine())!=null)
			{ 
				patients.add(line.split(";")[0]);
//				System.err.println(line.split(";")[0]);
			}
			
			br.close();
			fr.close();
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try (Stream<Path> walk = Files.walk(Paths.get(VCFDirectory))) {
			List<String> patientVCFs = walk.map(x -> x.toString())
					.filter(f -> f.endsWith(".vcf")).collect(Collectors.toList());
			System.out.println("kamil");
			for (String string : patientVCFs) {
//				System.out.println(string.split("/")[string.split("/").length-1].split("_")[0]);
				if (!patients.contains(string.split("/")[string.split("/").length-1].split("_")[0]))
					System.err.println(string.split("/")[string.split("/").length-1].split("_")[0]);

			}	
		}  catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		ArrayList<String> excludedVCFs = new ArrayList<String>();
		try (Stream<Path> walk = Files.walk(Paths.get("/media/giray/Windows/excludedvcfs"))) {
			List<String> patientVCFs = walk.map(x -> x.toString())
					.filter(f -> f.endsWith(".vcf")).collect(Collectors.toList());
			for (String string : patientVCFs) {
				String fileName = string.split("/")[string.split("/").length-1];
				String [] parsedFileNameBits = fileName.split("_");
				StringBuilder parsedFileName = new StringBuilder("");
				for (int i = 0;i<parsedFileNameBits.length;i++) {
					
						parsedFileName.append(parsedFileNameBits[i]).append("_");
						if(parsedFileNameBits[i].endsWith("bp")) {
							parsedFileName.deleteCharAt(parsedFileName.length()-1);
							break;
						}
							
					}
				excludedVCFs.add(parsedFileName.toString());
				System.out.println(parsedFileName.toString());
				}
			
			}  catch (IOException e) {
				e.printStackTrace();
			}
		convertDistanceMatrixToGeneticDistancesFile("rkiall_sramdr_pairwise_rel_fract.txt","distances40reduced.txt",excludedVCFs,40);
		convertVCFToMutationData("/media/giray/Windows/vcfs","vcf","vcfInforeduced.txt");
//		printSimilarFiles("/media/giray/Windows/Nextcloud/transmission networks/christine data/vcfs2", "vcf");
//		printExtraFilesFromVCFDirectory("isolates.txt", "/media/giray/Windows/vcfs");
	

		}
}
