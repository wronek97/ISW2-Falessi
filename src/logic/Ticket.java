package logic;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Ticket {
	public static final Logger logger = Logger.getLogger(Ticket.class.getName());
	
	private String ticketID;
	private LocalDateTime openDate;
	private LocalDateTime resolutionDate;
	private int injectedVersion;
	private int openVersion;
	private int fixedVersion;
	
	public Ticket(String ticketID, LocalDateTime openDate, LocalDateTime resolutionDate, int injectedVersion, int openVersion, int fixedVersion) {
		this.ticketID = ticketID;
		this.openDate = openDate;
		this.resolutionDate = resolutionDate;
		this.injectedVersion = injectedVersion;
		this.openVersion = openVersion;
		this.fixedVersion = fixedVersion;
	}

	public String getTicketID() {
		return ticketID;
	}

	public void setTicketID(String ticketID) {
		this.ticketID = ticketID;
	}

	public LocalDateTime getOpenDate() {
		return openDate;
	}

	public void setOpenDate(LocalDateTime openDate) {
		this.openDate = openDate;
	}

	public LocalDateTime getResolutionDate() {
		return resolutionDate;
	}

	public void setResolutionDate(LocalDateTime resolutionDate) {
		this.resolutionDate = resolutionDate;
	}

	public int getInjectedVersion() {
		return injectedVersion;
	}

	public void setInjectedVersion(int injectedVersion) {
		this.injectedVersion = injectedVersion;
	}

	public int getOpenVersion() {
		return openVersion;
	}

	public void setOpenVersion(int openVersion) {
		this.openVersion = openVersion;
	}

	public int getFixedVersion() {
		return fixedVersion;
	}

	public void setFixedVersion(int fixedVersion) {
		this.fixedVersion = fixedVersion;
	}
	
	public static List<Ticket> getFixedBugTickets(String projName, List<Release> releases) throws JSONException, IOException {
		List<Ticket> tickets = new ArrayList<>();
		Integer i = 0;
		Integer j = 0;
		Integer total = 1;
		//Get JSON API for closed bugs w/ AV in the project
		
		while (i < total) { //Only gets a max of 1000 at a time, so must do this multiple times if bugs >1000
			j = i + 1000;
			String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22"
			+ projName + "%22AND%22issueType%22=%22Bug%22AND(%22status%22=%22closed%22OR"
			+ "%22status%22=%22resolved%22)AND%22resolution%22=%22fixed%22&fields=key,resolutiondate,versions,created&startAt="
			+ i.toString() + "&maxResults=" + j.toString();
			
			JSONObject json = readJsonFromUrl(url);
			JSONArray issues = json.getJSONArray("issues");
			total = json.getInt("total");
			for (; i < total && i < j; i++) { //Iterate through each ticket found
				JSONObject jsonIssues = issues.getJSONObject(i%1000);
				JSONObject jsonFields = jsonIssues.getJSONObject("fields");
				JSONArray jsonAffVers = jsonFields.getJSONArray("versions");
				int injectedVersion = 0;				
				
				String key = jsonIssues.get("key").toString();
				String createdAt = jsonFields.get("created").toString().replace(".000+0000", "");
				LocalDateTime openDate = LocalDateTime.parse(createdAt);
				String resolutionDate = jsonFields.get("resolutiondate").toString().replace(".000+0000", "");
				LocalDateTime fixedDate = LocalDateTime.parse(resolutionDate);
				if(!jsonAffVers.isEmpty()) {
					injectedVersion = Ticket.getVersionIndex(jsonAffVers.getJSONObject(0).get("id").toString(), releases);
				}
				int openVersion = findOpenVersion(createdAt, releases);
				int fixedVersion = findFixedVersion(resolutionDate, releases);
				
				injectedVersion = min(injectedVersion, openVersion);
				
				//Discard tickets with open date before the first release date and with fixed version not defined
				if(!openDate.isBefore(releases.get(0).getReleaseDate()) && fixedVersion > 0 && openVersion <= fixedVersion) {
					tickets.add(new Ticket(key, openDate, fixedDate, injectedVersion, openVersion, fixedVersion));
				}
			}
		}
		
		// order tickets by open date
		Comparator<Ticket> ticketComparator = (t1, t2) -> t1.getOpenDate().compareTo(t2.getOpenDate());
		Collections.sort(tickets, ticketComparator);
		
		return tickets;
	}
	
	public static void saveTicketsToCSV(String savePath, String projectName, List<Ticket> tickets) throws JSONException, IOException {
		String outname = savePath + "\\" + projectName + "TicketInfo.csv";
		
		try (FileWriter fileWriter = new FileWriter(outname)) {
			StringBuilder outputBuilder = new StringBuilder("Ticket Name;Open Date;Resolution Date;Injected Version;Open Version;Fixed Version\n");
			int injVer;
			int fixVer;
			
			for (Ticket t : tickets) {
				injVer = t.getInjectedVersion();
				fixVer = t.getFixedVersion();
				if(injVer == 0) {
					outputBuilder.append(t.getTicketID() + ";" + t.getOpenDate() + ";" + t.getResolutionDate() + ";" + ";" + t.getOpenVersion() + ";" + t.getFixedVersion() + "\n");
				}
				else if(injVer < fixVer){
					outputBuilder.append(t.getTicketID() + ";" + t.getOpenDate() + ";" + t.getResolutionDate() + ";" + injVer + ";" + t.getOpenVersion() + ";" + fixVer + "\n");
				}
			}
			fileWriter.append(outputBuilder.toString());
			
		} catch (Exception e) {
			Logger logger = Logger.getLogger(Ticket.class.getName());
			logger.log(Level.SEVERE, "Error in csv writer", e);
		}
	}
	
	public static void setProportional(List<Ticket> tickets) {
		int injectedVersion;
		int openVersion;
		int fixedVersion;
		int p = getProportional(tickets);
		
		for(Ticket t : tickets) {
			injectedVersion = t.getInjectedVersion();
			openVersion = t.getOpenVersion();
			fixedVersion = t.getFixedVersion();
			if(injectedVersion == 0) {
				injectedVersion = fixedVersion-p*(fixedVersion-openVersion+1);
				t.setInjectedVersion(min(injectedVersion, openVersion));
			}
		}
	}
	
	private static int min(int a, int b) {
		if(a < b) {
			return a;
		}
		return b;
	}

	private static int getProportional(List<Ticket> tickets) {
		double sum = 0;
		int k = 0;
		
		int iv;
		int ov;
		int fv;
		
		for(Ticket t : tickets) {
			iv = t.getInjectedVersion();
			if(iv > 0) { //if IV is known
				ov = t.getOpenVersion();
				fv = t.getFixedVersion();
				sum = sum + (double)(fv-iv)/(fv-ov+1);
				k++;
			}
		}
		if(k!=0) {
			return (int) Math.round(sum/k);
		}
		
		return 0;
	}
	
	private static int getVersionIndex(String versionID, List<Release> releases) {
		for (Release rel : releases) {
			if (versionID.equals(rel.getVersionID())) {
				return releases.indexOf(rel)+1;
			}
		}
		return 0;
	}
	
	private static int findOpenVersion(String createdAt, List<Release> orderedReleases) {
		LocalDateTime openDate = LocalDateTime.parse(createdAt);
		LocalDateTime releaseDate = orderedReleases.get(0).getReleaseDate();
		if(openDate.isBefore(releaseDate)) {
			return 1;
		}
		else {
			for(int k = 1; k < orderedReleases.size(); k++) {
				releaseDate = orderedReleases.get(k).getReleaseDate();
				if(openDate.isBefore(releaseDate)) {
					return k+1;
				}
			}
		}
		
		return 0;
	}
	
	private static int findFixedVersion(String resolutionDate, List<Release> orderedReleases) {
		LocalDateTime resDate = LocalDateTime.parse(resolutionDate);
		LocalDateTime releaseDate = orderedReleases.get(0).getReleaseDate();
		if(resDate.isBefore(releaseDate)) {
			return 1;
		}
		else {
			for(Integer k = 1; k < orderedReleases.size(); k++) {
				releaseDate = orderedReleases.get(k).getReleaseDate();
				if(resDate.isBefore(releaseDate)) {
					return k+1;
				}
			}
		}
		
		return 0;
	}
	
	private static String readAll(Reader rd) throws IOException {
		StringBuilder sb = new StringBuilder();
		int cp;
		while ((cp = rd.read()) != -1) {
			sb.append((char) cp);
		}
		return sb.toString();
	}

	private static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
		try (InputStream is = new URL(url).openStream()) {
			BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
			String jsonText = readAll(rd);
			
			return new JSONObject(jsonText);
		}
	}
	
}
