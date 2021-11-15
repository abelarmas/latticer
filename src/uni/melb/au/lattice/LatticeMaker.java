package uni.melb.au.lattice;

import java.io.FileWriter;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;

import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;

import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import uni.melb.au.log.Trace;

public class LatticeMaker {
	private XLog log;
	HashBiMap<String, Integer> hashAct;
	HashBiMap<Trace, Integer> hashTraces;
	HashMap<Trace, Integer> traceEquiv;
	HashMap<Multiset<Integer>, Integer> multiSetEquivCounter;
	public int distributive = 0;
	public int nonDistributive = 0;

	public LatticeMaker(XLog log) {
		this.log = log;
	}

	public void create() {
		hashTraceActivities();
		initializeByLabel();
		createLattices();
	}

	private void createLattices() {
		int i = 0;
		for (Multiset<Integer> key : multiSetEquivCounter.keySet()) {
			if (multiSetEquivCounter.get(key) > 1) {
				HashSet<Trace> traceEquivalence = getTraces(key);
				Lattice lattice = new Lattice(traceEquivalence);
				if(!lattice.isDistributive()) {
					this.nonDistributive++;
					writeToFile(lattice.toDot(), i + "-NonDistLM");
					lattice.complete();
					writeToFile(lattice.toDot(), i + "-DistLM(Fixed)");
					lattice.computePaths();
				}else {
					this.distributive++;
					writeToFile(lattice.toDot(), i + "-DistLM");
					lattice.computePaths();
				}
			}
			
			i++;
		}
	}

	private void writeToFile(String content, String name) {
		try {
			FileWriter myWriter = new FileWriter("output/lattice-" + name + ".dot");
			myWriter.write(content);
			myWriter.close();
			System.out.println("Successfully wrote to the file.");
		} catch (Exception e) {
			System.out.println("An error occurred.");
			e.printStackTrace();
		}
	}

	private HashSet<Trace> getTraces(Multiset<Integer> key) {
		HashSet<Trace> equivalence = new HashSet<>();

		for (Trace trace : hashTraces.keySet()) {
			Multiset<Integer> multisetTrace = HashMultiset.<Integer>create(trace.getListTraceId());
			if (key.equals(multisetTrace))
				equivalence.add(trace);
		}

		return equivalence;
	}

	private void initializeByLabel() {
		LinkedHashSet<Multiset<Integer>> multisetIds = new LinkedHashSet<>();
		HashBiMap<Multiset<Integer>, Integer> multiSetEquiv = HashBiMap.<Multiset<Integer>, Integer>create();
		this.multiSetEquivCounter = new HashMap<Multiset<Integer>, Integer>();
		this.traceEquiv = new HashMap<Trace, Integer>();

		for (Trace trace : hashTraces.keySet()) {
			trace.setIds(hashAct);
			Multiset<Integer> multisetTrace = HashMultiset.<Integer>create(trace.getListTraceId());

			if (!multisetIds.contains(multisetTrace)) {
				multiSetEquiv.put(multisetTrace, multisetIds.size());
				multisetIds.add(multisetTrace);
				multiSetEquivCounter.put(multisetTrace, 0);
			}

			Integer value = multiSetEquivCounter.get(multisetTrace);
			multiSetEquivCounter.put(multisetTrace, value + 1);

			traceEquiv.put(trace, multiSetEquiv.get(multisetTrace));
		}

		LinkedList<Multiset<Integer>> orderedList = new LinkedList<>(multiSetEquivCounter.keySet());
		Collections.sort(orderedList, new Comparator<Multiset<Integer>>() {

			@Override
			public int compare(Multiset<Integer> o1, Multiset<Integer> o2) {
				return o1.size() - o2.size();
			}
		});

//		for (Multiset<Integer> key : orderedList)
//			if (multiSetEquivCounter.get(key) > 1)
//				System.out.println(key + " - " + multiSetEquivCounter.get(key));
	}

	private void hashTraceActivities() {
//		this.hashAct = HashBiMap.<String, Integer>create();
//		this.hashTraces = HashBiMap.<Trace, Integer>create();
//
//		for (XTrace xtrace : log) {
//			Trace trace = new Trace(xtrace);
//			hashTraces.put(trace, hashTraces.size());
//
//			for (String name : trace.getListTraceOriginal())
//				if (!hashAct.containsKey(name))
//					hashAct.put(name, hashAct.size());
//		}
	}
}
