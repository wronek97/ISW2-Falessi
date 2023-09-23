package logic;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.json.JSONException;

import logic.FileMetrics.CSV_Mode;

public class Analyzer {
	public static final String DISK = "E:\\";
	public static final String PROJECT_FOLDER = "GitProjects\\BookKeeper";
	
	public static final String PROJECT_NAME = "BookKeeper";
	public static final String PROJECT_PATH = DISK + PROJECT_FOLDER;
	public static final String SAVE_PATH = PROJECT_PATH;
	public static final String ML_PATH = "\\WalkForwardData";
	public static final String SOURCES_PATH = PROJECT_PATH + ML_PATH;
	public static final String JAVA_EXTENSION = ".java";
	public static final String MASTER = "master";
	public static final String CMD = "cmd.exe";
	public static final String ERROR = "Error analyzing project";
	
	public static final double DISCARD_RATE = 0.49;
	
	protected static List<Release> releases;
	protected static List<Ticket> tickets;
	protected static List<FileMetrics> fileMetrics;
	
	protected static List<String> releaseCommits;
	
	public static final Logger logger = Logger.getLogger(Analyzer.class.getName());
	
	public static void main(String[] args) {
		
		Integer totalVersions = 0;
		Integer versionsToAnalyze = 0;
		
		try {
			Analyzer.switchVersion(MASTER);
			
			releases = Release.getAllReleases(PROJECT_NAME);
			tickets = Ticket.getFixedBugTickets(PROJECT_NAME, releases);
			Ticket.setProportional(tickets); //after generating tickets list, use proportional method to extimate injected version if not known
			totalVersions = releases.size();
			versionsToAnalyze = (int)Math.floor(totalVersions*(1-DISCARD_RATE));
			releaseCommits = getReleaseCommits(PROJECT_NAME, PROJECT_PATH, versionsToAnalyze);
			
			fileMetrics = Analyzer.analyzeProject(JAVA_EXTENSION, versionsToAnalyze);
			
			Release.saveReleasesToCSV(SAVE_PATH, PROJECT_NAME, releases);
			Ticket.saveTicketsToCSV(SAVE_PATH, PROJECT_NAME, tickets);
			FileMetrics.saveFileMetricsToCSV(SAVE_PATH, PROJECT_NAME, PROJECT_PATH, fileMetrics, CSV_Mode.IT);
			FileMetrics.saveTrainingsForML(SAVE_PATH, fileMetrics, versionsToAnalyze);
			FileMetrics.saveTestsForML(SAVE_PATH, fileMetrics, versionsToAnalyze);
			
			WekaTools.generateAllArff(SOURCES_PATH);
			WekaTools.walkForwardToCSV(SOURCES_PATH, versionsToAnalyze, SAVE_PATH, PROJECT_NAME, CSV_Mode.IT);
			
		} catch (JSONException | IOException e) {
			logger.log(Level.SEVERE, ERROR, e);
		}
		
	}
	
	public static List<FileMetrics> analyzeProject(String fileExtension, int versionsToAnalyze) {
		
		List<List<String>> filesPerVersion = Analyzer.getFilesName(fileExtension, versionsToAnalyze);
		List<FileMetrics> result = FileMetrics.getFileMetricsFromName(filesPerVersion, versionsToAnalyze);
		
		//calculate all sizes
		Analyzer.setAllSize(result);
		Analyzer.switchVersion(MASTER);
		
		//calculate all other metrics
		for(FileMetrics fm : result) {
			List<List<Integer>> someMetrics = Analyzer.getMetrics(fm.getName(), fm.getVersion());
			
			Integer sum = 0;
			List<Integer> addedLines = someMetrics.get(0);
			for(Integer i : addedLines) {
				sum = sum + i;
			}
			fm.setLOCadded(sum);
			if(!addedLines.isEmpty()) {
				fm.setAVGLOCadded((double) sum/addedLines.size());
			}
			Integer aux = Analyzer.findMaxInteger(addedLines);
			fm.setMAXLOCadded(aux);
			
			Integer diff = 0;
			List<Integer> deletedLines = someMetrics.get(1);
			for(Integer i : deletedLines) {
				diff = diff + i;
			}
			fm.setLOCtouched(sum+diff);
			
			Integer churnSum = 0;
			List<Integer> churns = someMetrics.get(2);
			for(Integer i : churns) {
				churnSum = churnSum + i;
			}
			fm.setChurn(churnSum);
			if(!churns.isEmpty()) {
				fm.setAVGchurn((double) (churnSum)/churns.size());
			}
			aux = Analyzer.findMaxInteger(churns);
			fm.setMAXchurn(aux);
			
			fm.setNR(someMetrics.get(3).get(0));
			fm.setNF(someMetrics.get(4).get(0));
			
			fm.setBugged(Analyzer.isBugged(fm.getName(), fm.getVersion()));
		}
		Analyzer.switchVersion(MASTER);
		
		return result;
	}
	
	private static Integer findMaxInteger(List<Integer> integers) {
		Integer aux = 0;
		for(Integer j : integers) {
			if(j > aux) {
				aux = j;
			}
		}
		return aux;
	}
	
	private static void setAllSize(List<FileMetrics> fms) {
		//calculate all sizes of Filemetrics list fms
		for(FileMetrics fm : fms) {
			fm.setSize(Analyzer.getFileSize(fm.getName(), fm.getVersion())); //per ora devo aggiungerla a mano perchè ho solo il costruttore con tutte le metriche calcolate
		}
	}
	
	private static void switchVersion(String commitID) {
		String[] checkoutCommand = {CMD, "/c", "git", "checkout", commitID};
		Analyzer.callCMD(PROJECT_PATH, checkoutCommand);
	}
	
	private static List<List<String>> getFilesName(String fileExtension, int versionsToAnalyze, boolean discardTests){
		List<List<String>> result = new ArrayList<>();
		
		for(int k=0; k<versionsToAnalyze; k++) {
			//change release of the project folder with a checkout to the release date nearest commit
			Analyzer.switchVersion(releaseCommits.get(k));
			
			List<String> files = null;
			try (Stream<Path> walk = Files.walk(Paths.get(PROJECT_PATH))) {
				if(discardTests) {
					files = walk.map(Path::toString).filter(f -> f.endsWith(fileExtension)).filter(f -> ( !f.contains("src\\test") && !f.contains("\\tests\\") )).collect(Collectors.toList());
				}
				else {
					files = walk.map(Path::toString).filter(f -> f.endsWith(fileExtension)).collect(Collectors.toList());
				}
				result.add(k, files);
				
		    } catch (IOException e) {
				logger.log(Level.SEVERE, "Error reading folder", e);
		    }
		}
		//switch back to master version
		Analyzer.switchVersion(MASTER);
		
		return result;
	}
	
	private static List<List<String>> getFilesName(String fileExtension, int versionsToAnalyze){
		return Analyzer.getFilesName(fileExtension, versionsToAnalyze, true);
	}
	
	private static int getFileSize(String file, int version) {
		int size = 0;
		
		Analyzer.switchVersion(releaseCommits.get(version));
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			String line;
		    while ((line = br.readLine()) != null) {
		       if(!line.isEmpty()) {
					size++;
				}
		    }

		} catch (IOException e) {
			logger.log(Level.SEVERE, ERROR, e);
		}
		
		return size;
	}
	
	private static List<List<Integer>> getMetrics(String file, int version) { //return a list of lists, for addedLines, deletedLines, NR, NF
		List<List<Integer>> modifiedLines = new ArrayList<>();
		if(releases == null) {
			return modifiedLines;
		}
		
		final String toRepalce = "T00:00";
		List<Integer> insertions = new ArrayList<>();
		List<Integer> deletions = new ArrayList<>();
		List<Integer> churns = new ArrayList<>();
		List<Integer> nr = new ArrayList<>();
		List<Integer> nf = new ArrayList<>();
		Integer r = 0;
		Integer f = 0;

		String beforeDate = releases.get(version+1).getReleaseDate().toString().replace(toRepalce, "");
		String afterDate = releases.get(version).getReleaseDate().toString().replace(toRepalce, "");
		String[] cmd = {CMD, "/c", "git", "log", "--stat", "--before='" + beforeDate + "'", "--after='" + afterDate + "'", "--", file.replace(PROJECT_PATH + "\\", "")};
		
		String cmdOutput = Analyzer.callCMD(PROJECT_PATH, cmd);
		String[] parsedCMD = cmdOutput.split("\n");
		
		String commitTicketID = null;
		Integer ins;
		Integer del;
		for(String line : parsedCMD) {
			if(line.startsWith("commit ")) {
				commitTicketID = null;
				r++;
			}
			else if(commitTicketID == null && line.contains(PROJECT_NAME.toUpperCase() + "-")) {
					commitTicketID = PROJECT_NAME.toUpperCase() + "-" + Analyzer.extractNumber(line);
					if(Analyzer.isFixedTicket(commitTicketID)) {
						f++;
					}
			}
			else if(line.contains("1 file changed,")) {
				ins = Analyzer.getInsertions(line);
				del = Analyzer.getDeletions(line);
				
				insertions.add(ins);
				deletions.add(del);
				churns.add(ins-del);
			}
		}
		nr.add(r);
		nf.add(f);
		
		modifiedLines.add(0, insertions);
		modifiedLines.add(1, deletions);
		modifiedLines.add(2, churns);
		modifiedLines.add(3, nr);
		modifiedLines.add(4, nf);
		
		return modifiedLines;
	}
	
	private static Integer getInsertions(String stats) {
		String[] parsedLine = stats.split(",");
		for(String s : parsedLine) {
			if(s.contains("insertions(+)") || s.contains("insertion(+)")){
				return Analyzer.extractNumber(s);
			}
		}
		
		return 0;
	}
	
	private static Integer getDeletions(String stats) {
		String[] parsedLine = stats.split(",");
		for(String s : parsedLine) {
			if(s.contains("deletions(-)") || s.contains("deletion(-)")){
				return Analyzer.extractNumber(s);
			}
		}
		
		return 0;
	}
	
	private static boolean isBugged(String file, int version) {
		String firstReleaseDate = releases.get(0).getReleaseDate().toString().replace("T00:00", "");
		String[] cmd = {CMD, "/c", "git", "log", "--date=iso", "--after='" + firstReleaseDate + "'", "--", file.replace(PROJECT_PATH + "\\", "")};
		
		String cmdOutput = Analyzer.callCMD(PROJECT_PATH, cmd);
		String[] parsedCMD = cmdOutput.split("\n");
		
		String commitTicketID;
		for(String line : parsedCMD) {
			if(line.contains(PROJECT_NAME.toUpperCase() + "-")) {
				commitTicketID = PROJECT_NAME.toUpperCase() + "-" + Analyzer.extractNumber(line);
				Ticket aux = Analyzer.getTicketByName(commitTicketID); // gets ticket by name only if it is in the fixed bug tickets list !!
				
				//if IV <= version of file < FV, -1 because version in FileMetrics is saved from 0 to n-1 instead of in Tickets (from 1 to n)
				if(aux != null && (aux.getInjectedVersion()-1 <= version && version < aux.getFixedVersion()-1)) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	private static boolean isFixedTicket(String ticketID) {
		for(Ticket t : tickets) {
			if(t.getTicketID().equals(ticketID)) {
				return true;
			}
		}
		return false;
	}
	
	private static Ticket getTicketByName(String ticketID) {
		for(Ticket t : tickets) {
			if(t.getTicketID().equals(ticketID)) {
				return t;
			}
		}
		
		return null;
	}
	
	private static String callCMD(String path, String[] commands) {
		ProcessBuilder procBuilder = new ProcessBuilder(commands).directory(new File(path));
		procBuilder.redirectErrorStream(true);
		Process proc = null;
		StringBuilder result = new StringBuilder("");
		
		try {
			proc = procBuilder.start();
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
		
			String s = null;
			while ((s = stdInput.readLine()) != null) {
			    result.append(s+"\n");
			}
			
		} catch (IOException e) {
			logger.log(Level.SEVERE, ERROR, e);
		}
		
		return result.toString();
	}
	
	private static Integer extractNumber(String str) {
        if(str == null || str.isEmpty()){
            return 0;
        }
        
        StringBuilder sb = new StringBuilder("");
        boolean found = false;
        for(char c : str.toCharArray()){
            if(Character.isDigit(c)){
                sb.append(c);
                found = true;
            } else if(found){
                // If we already found a digit before and this char is not a digit, stop looping
                break;                
            }
        }
        
        if(!sb.toString().equals("")){
            return Integer.parseInt(sb.toString());
        }
        
        return 0;
    }
	
	private static List<String> getReleaseCommits(String projectName, String projectPath, int versionsToAnalyze){
		List<String> commits = new ArrayList<>();
		try {
			releases = Release.getAllReleases(projectName);
			
			for(int k=0; k<versionsToAnalyze; k++) {
				//find first commit of the release 'K'
				String[] commands = {CMD, "/c", "git", "log", "--date=iso", "--name-status", "--before='" + releases.get(k).getReleaseDate() + "'", "HEAD"};
				String[] aux = Analyzer.callCMD(projectPath, commands).split("\n", 2);
				commits.add(k, aux[0].replace("commit ", ""));
			}
		} catch (JSONException | IOException e) {
			logger.log(Level.SEVERE, ERROR, e);
		}

		return commits;
	}
	
}
