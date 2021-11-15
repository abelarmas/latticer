package uni.melb.au;

import java.io.File;
import java.util.HashMap;
import java.util.Scanner;
import java.util.StringTokenizer;

import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.factory.XFactoryRegistry;
import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.xeslite.lite.factory.XFactoryLiteImpl;

import uni.melb.au.log.Relabeler;

public class MainFromTr2XES {

	public static void main(String[] args) {
		File folder = new File("logs/traces-bpm16/withnoise/traces");
		File[] listOfFiles = folder.listFiles();

		try {
			MainFromTr2XES main = new MainFromTr2XES();
			main.createFolder("logs/xes");

			for (File file : listOfFiles) {
				if (file.isFile() && file.getName().contains(".tr")) {
					File myObj = new File(file.getPath());
					Scanner myReader = new Scanner(myObj);

					XFactory factory = new XFactoryLiteImpl();
					XLog log = factory.createLog();

					while (myReader.hasNextLine()) {
						String data = myReader.nextLine();
						StringTokenizer token = new StringTokenizer(data);
						XTrace xtrace = factory.createTrace();

						while (token.hasMoreTokens()) {
							String name = token.nextToken();

							XEvent evt1 = factory.createEvent();
							XAttribute a = XFactoryRegistry.instance().currentDefault()
									.createAttributeLiteral(XConceptExtension.KEY_NAME, name, null);
							evt1.getAttributes().put(XConceptExtension.KEY_NAME, a);
							xtrace.add(evt1);
						}

						log.add(xtrace);
					}

					Relabeler relabeler = new Relabeler(new HashMap<>());
					relabeler.serializeLog(log, "logs/xes",
							file.getName().substring(0, file.getName().indexOf(".tr")));
					
					myReader.close();
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
}
