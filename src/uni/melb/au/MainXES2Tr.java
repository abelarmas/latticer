package uni.melb.au;

import java.io.File;
import java.io.FileWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.in.XUniversalParser;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;

public class MainXES2Tr {

	public static void main(String[] args) {
		MainXES2Tr main = new MainXES2Tr();
		main.transform();
	}

	public void transform() {
		File folder = new File("logs/");
		File[] listOfFiles = folder.listFiles();

		try {
			createFolder("logs/tr");

			for (File file : listOfFiles) {				
				HashSet<LinkedList<String>> visited = new HashSet<>();
				
				
				if (file.isFile() && file.getName().contains(".xes")) {
					HashMap<String, String> relabeling = new HashMap<>();
					
					FileWriter myWriter = new FileWriter("logs/tr/" + file.getName().substring(0,file.getName().indexOf(".xes")) + ".tr");
					XLog log = openLog(file.getPath());
					
					char x='a';
					for(XTrace trace : log) {
						LinkedList<String> traceSet = new LinkedList<>();
						
						for(XEvent event : trace) {
							String eventName = getEventName(event);
							
							if(!relabeling.containsKey(eventName)) 
								relabeling.put(eventName, "" + x++);
							
							traceSet.add(relabeling.get(eventName));
						}
						
						if(!visited.contains(traceSet)) 
							visited.add(traceSet);
					}
					
					for(LinkedList<String> trace : visited) {
						for(int i =0; i < trace.size(); i++) {
							myWriter.write(trace.get(i));
							
							if(i < trace.size()-1)
								myWriter.write(" ");
						}
						myWriter.write("\n");
						myWriter.flush();
					}
					
					myWriter.close();
				}
			}

		} catch (Exception e) {
			System.out.println("An error occurred.");
			e.printStackTrace();
		}
	}

	private void createFolder(String pathname) {
		File folder = new File(pathname);

		if (!folder.exists())
			folder.mkdir();
	}

	private String getEventName(XEvent e) {
		return e.getAttributes().get(XConceptExtension.KEY_NAME).toString();
	}

	
	private XLog openLog(String fileName) {
		try {
			XLog log = null;
//			XesLiteXmlParser parser = new XesLiteXmlParser(new XFactoryExternalStore.MapDBDiskSequentialAccessImpl(), false);   
			XUniversalParser parser = new org.deckfour.xes.in.XUniversalParser();
			Collection<XLog> logs;

			logs = (Collection<XLog>) parser.parse(new File(fileName));
			log = logs.iterator().next();
			return log;
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

}
