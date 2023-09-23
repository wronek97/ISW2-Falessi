package logic;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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

public class Release {
	private String id;
	private String name;
	private LocalDateTime releaseDate;
	
	public Release(String versionID, String versionName, LocalDateTime releaseDate) {
		this.id = versionID;
		this.name = versionName;
		this.releaseDate = releaseDate;
	}

	public String getVersionID() {
		return id;
	}

	public void setVersionID(String versionID) {
		this.id = versionID;
	}

	public String getVersionName() {
		return name;
	}

	public void setVersionName(String versionName) {
		this.name = versionName;
	}

	public LocalDateTime getReleaseDate() {
		return releaseDate;
	}

	public void setReleaseDate(LocalDateTime releaseDate) {
		this.releaseDate = releaseDate;
	}
	
	public static List<Release> getAllReleases(String projectName) throws IOException, JSONException {
		//Fills the arrayList with releases dates and orders them
		//Ignores releases with missing dates
		List<Release> releases = new ArrayList<>();
		String url = "https://issues.apache.org/jira/rest/api/2/project/" + projectName.toUpperCase();
		JSONObject json = readJsonFromUrl(url);
		JSONArray versions = json.getJSONArray("versions");
		
		Integer i;
		for (i = 0; i < versions.length(); i++) {
			JSONObject version = versions.getJSONObject(i);
			String id = "";
			String name = "";
			String releaseDate;
			if(version.has("releaseDate") && version.has("id") && version.has("name")) {
				releaseDate = version.get("releaseDate").toString();
				id = version.get("id").toString();
				name = version.get("name").toString();
				addRelease(releases, id, name, releaseDate);
			}
		}
		
		// order releases by date
		Comparator<Release> releaseComparator = (r1, r2) -> r1.getReleaseDate().compareTo(r2.getReleaseDate());
		Collections.sort(releases, releaseComparator);
		
		return releases;
	}
	
	public static void saveReleasesToCSV(String savePath, String projectName, List<Release> releases) throws JSONException, IOException {
		String outname = savePath + "\\" + projectName + "VersionsInfo.csv";
		
		try (FileWriter fileWriter = new FileWriter(outname)) {
			int i = 1;
			StringBuilder outputBuilder = new StringBuilder("Index;Version ID;Version Name;Release Date\n");
			
			for (Release r : releases) {
				outputBuilder.append(String.valueOf(i) + ";" + r.getVersionID() + ";" + r.getVersionName() + ";" + r.getReleaseDate() + "\n");
				i++;
			}
			fileWriter.append(outputBuilder.toString());
			
		} catch (Exception e) {
			Logger logger = Logger.getLogger(Release.class.getName());
			logger.log(Level.SEVERE, "Error in csv writer", e);
		}
	}
	
	private static void addRelease(List<Release> releases, String id, String name, String strDate) {
		LocalDate date = LocalDate.parse(strDate);
		LocalDateTime dateTime = date.atStartOfDay();
		if (!Release.isAlreadyAdded(dateTime, releases)) {
			releases.add(new Release(id, name, dateTime));
		}
	}
	
	private static boolean isAlreadyAdded(LocalDateTime releaseDate, List<Release> releases) {
		for (Release rel : releases) {
			if (releaseDate == rel.getReleaseDate()) {
				return true;
			}
		}
		return false;
	}
	
	private static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
		InputStream is = new URL(url).openStream();
		try (BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
			String jsonText = readAll(rd);
			return new JSONObject(jsonText);
		} finally {
			is.close();
		}
	}
	
	private static String readAll(Reader rd) throws IOException {
		StringBuilder sb = new StringBuilder();
		int cp;
		while ((cp = rd.read()) != -1) {
			sb.append((char) cp);
			}
		return sb.toString();
	}
	
}
