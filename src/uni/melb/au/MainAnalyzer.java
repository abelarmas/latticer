package uni.melb.au;

import java.io.File;
import java.io.FileWriter;
import java.util.Collection;
import java.util.LinkedList;

import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.in.XUniversalParser;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

public class MainAnalyzer {

	public static void main(String[] args) {
		MainAnalyzer main = new MainAnalyzer();
		main.analyze();
	}

	public void analyze() {
		File folder = new File("logs/");
		File[] listOfFiles = folder.listFiles();

		try {
			for (File file : listOfFiles) {
				if (file.isFile() && file.getName().contains(".xes")) {
					XLog log = openLog(file.getPath());
					Table<Integer, LinkedList<String>, Integer> counterList = HashBasedTable
							.<Integer, LinkedList<String>, Integer>create();
					Table<Integer, Multiset<String>, Integer> counterMS = HashBasedTable
							.<Integer, Multiset<String>, Integer>create();

					Table<Integer, LinkedList<String>, Integer> counterListFinal = HashBasedTable
							.<Integer, LinkedList<String>, Integer>create();
					Table<Integer, Multiset<String>, Integer> counterMSFinal = HashBasedTable
							.<Integer, Multiset<String>, Integer>create();

					for (XTrace trace : log) {
						int i = 1;
						LinkedList<String> traceStrings = new LinkedList<>();
						Multiset<String> traceMS = HashMultiset.<String>create();

						for (XEvent event : trace) {
							String name = getEventName(event);
							traceStrings.add(name);
							traceMS.add(name);

							if (counterList.contains(i, traceStrings))
								counterList.put(i, traceStrings, counterList.get(i, traceStrings) + 1);
							else
								counterList.put(i, new LinkedList<>(traceStrings), 1);

							if (counterMS.contains(i, traceMS))
								counterMS.put(i, traceMS, counterMS.get(i, traceMS) + 1);
							else
								counterMS.put(i, HashMultiset.<String>create(traceMS), 1);

							i++;
						}

						if (counterListFinal.contains(i, traceStrings))
							counterListFinal.put(i, traceStrings, counterListFinal.get(i, traceStrings) + 1);
						else {
							counterListFinal.put(i, new LinkedList<>(traceStrings), 1);

							if (counterMSFinal.contains(i, traceMS))
								counterMSFinal.put(i, traceMS, counterMSFinal.get(i, traceMS) + 1);
							else
								counterMSFinal.put(i, HashMultiset.<String>create(traceMS), 1);
						}
					}

					createFolder("outputSynthetic/" + file.getName());
					FileWriter myWriter = new FileWriter("outputSynthetic/" + file.getName() + "/tracePrefixList.txt");
					myWriter.write("Size,Prefix,Counter\n");
					for (Cell<Integer, LinkedList<String>, Integer> cell : counterList.cellSet()) {
						myWriter.write(cell.getRowKey() + "," + cell.getColumnKey() + "," + cell.getValue() + "\n");
						myWriter.flush();
					}
					myWriter.close();

					FileWriter myWriter2 = new FileWriter("outputSynthetic/" + file.getName() + "/tracePrefixMS.txt");
					myWriter2.write("Size,Prefix,Counter\n");
					for (Cell<Integer, Multiset<String>, Integer> cell : counterMS.cellSet()) {
						myWriter2.write(
								cell.getRowKey() + "," + cell.getColumnKey().toString() + "," + cell.getValue() + "\n");
						myWriter2.flush();
					}
					myWriter2.close();

					FileWriter myWriter3 = new FileWriter(
							"outputSynthetic/" + file.getName() + "/traceCompleteTraceList.txt");
					myWriter3.write("TraceSize,Counter\n");
					for (Cell<Integer, LinkedList<String>, Integer> cell : counterListFinal.cellSet()) {
						myWriter3.write(cell.getColumnKey().size() + "," + cell.getValue() + "\n");
						myWriter3.flush();
					}
					myWriter3.close();

					FileWriter myWriter4 = new FileWriter(
							"outputSynthetic/" + file.getName() + "/traceCompleteTraceMS.txt");
					myWriter4.write("Size,Repetition,Variants\n");
					for (Cell<Integer, Multiset<String>, Integer> cell : counterMSFinal.cellSet()) {
						myWriter4.write((cell.getRowKey() - 1) + ","
								+ (cell.getRowKey() - 1 - cell.getColumnKey().elementSet().size()) + ","
								+ cell.getValue() + "\n");
						myWriter4.flush();
					}
					myWriter4.close();

					int j = 0;
					for (Cell<Integer, Multiset<String>, Integer> cell : counterMSFinal.cellSet()) {
						if (cell.getValue() > 1) {
							FileWriter myWriter5 = new FileWriter(
									"outputSynthetic/" + file.getName() + "/traceCompleteTraceMS" + j + ".txt");
							myWriter5.write("Size,Variants\n");
							for (Cell<Integer, LinkedList<String>, Integer> cell2 : counterListFinal.cellSet()) {
								if (HashMultiset.<String>create(cell2.getColumnKey()).equals(cell.getColumnKey())) {
									myWriter5.write((cell2.getRowKey() - 1) + "," + cell2.getValue() + "\n");
									myWriter5.flush();
								}
							}
							myWriter5.close();
							j++;
						}
					}

					System.out.println("Successfully wrote to the file.");
				}
			}

		} catch (Exception e) {
			System.out.println("An error occurred.");
			e.printStackTrace();
		}
	}

	private String getEventName(XEvent e) {
		return e.getAttributes().get(XConceptExtension.KEY_NAME).toString();
	}

	private void createFolder(String pathname) {
		File folder = new File(pathname);

		if (!folder.exists())
			folder.mkdir();
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
