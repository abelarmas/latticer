package uni.melb.au.log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;

import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.factory.XFactoryRegistry;
import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.deckfour.xes.out.XesXmlGZIPSerializer;
import org.processmining.xeslite.lite.factory.XFactoryLiteImpl;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;

import uni.melb.au.lattice.bs.ElementBS;
import uni.melb.au.lattice.bs.LatticeBS;

public class Relabeler {

	private HashMap<String, String> originalName;
	private LinkedList<HashSet<String>> fakeCounter;
	private HashMultimap<String, String> dependeceAllRel;
	private HashSet<String> repetitive = new HashSet<>();
	
	public HashMultimap<String, String> getDependeceAllRel() {
		return dependeceAllRel;
	}

	public Relabeler(HashMap<String, String> originalName) {
		this.originalName = originalName;
		this.fakeCounter = new LinkedList<>();
	}

	public XLog createLog(List<LatticeBS> latticeCollection, String folder, String suffix, Boolean complement) {
		XFactory factory = new XFactoryLiteImpl();// XFactoryRegistry.instance().currentDefault();
		XLog log = factory.createLog();

		for (LatticeBS lattice : latticeCollection) 
//			HashSet<XTrace> traces = getXTraces(lattice, factory, complement);
			for(Trace t : lattice.getTraces())
				log.addAll(t.getXTraces());
		
		if (complement)
			serializeLogComplement(log, folder, suffix);
		else
			serializeLog(log, folder, suffix);

		return log;
	}
	
	public XLog createSubLog(Table<ElementBS, ElementBS, ElementBS> classElements, HashMap<ElementBS, Integer> equivalences, List<LatticeBS> latticeCollection, String folder, String suffix, Boolean complement) {
		XFactory factory = new XFactoryLiteImpl();
		XLog log = factory.createLog();

		for (LatticeBS lattice : latticeCollection) {
			HashSet<XTrace> traces = getXTraces(classElements, equivalences, lattice, factory);
			log.addAll(traces);
		}
		
		serializeLog(log, folder, suffix +"-sub");

		return log;
	}

//	public void createLog(LatticeBS lattice) {
//		XFactory factory = XFactoryRegistry.instance().currentDefault();
//		XLog log = factory.createLog();
//
//		Queue<Pair<LinkedList<String>, ElementBS>> queue = new LinkedList<>();
//		queue.add(new Pair<>(new LinkedList<String>(), lattice.getBottom()));
//
//		while (!queue.isEmpty()) {
//			Pair<LinkedList<String>, ElementBS> element = queue.remove();
//			LinkedList<String> past = element.getElement1();
//			ElementBS current = element.getElement2();
//
//			if (current.post.isEmpty()) {
//				XTrace trace = factory.createTrace();
//
//				for (int i = 0; i < past.size(); i++) {
//					XEvent evt1 = factory.createEvent();
//					XAttribute a = XFactoryRegistry.instance().currentDefault()
//							.createAttributeLiteral(XConceptExtension.KEY_NAME, originalName.get(past.get(i)), null);
//					evt1.getAttributes().put(XConceptExtension.KEY_NAME, a);
//					trace.add(evt1);
//				}
//
//				log.add(trace);
//			} else {
//				for (ElementBS next : current.post) {
//					LinkedList<String> copy = new LinkedList<>(element.getElement1());
//					BitSet labelIndex = (BitSet) current.code.clone();
//					labelIndex.and(next.code);
//
//					int index = labelIndex.nextSetBit(0);
//					String label = lattice.getLabel(index);
//					copy.add(label);
//
//					queue.add(new Pair<>(copy, next));
//				}
//
//			}
//		}
//
//		serializeLog(log);
//	}

	public void serializeLog(XLog log, String folder, String name) {
		try {
			XesXmlGZIPSerializer serializer = new XesXmlGZIPSerializer();
			File folderFile = new File(folder + "/" + name +".xes.gz");
			OutputStream output = new FileOutputStream(folderFile);

			serializer.serialize(log, output);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void serializeLogComplement(XLog log, String folder, String suffix) {
		try {
			XesXmlGZIPSerializer serializer = new XesXmlGZIPSerializer();
			File folderFile = new File(folder + "/" + suffix +".xes.gz");
			OutputStream output = new FileOutputStream(folderFile);

			serializer.serialize(log, output);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private HashSet<XTrace> getXTraces(LatticeBS lattice, XFactory factory, boolean complement) {
		Set<LinkedList<ElementBS>> traces = lattice.getComplement();
		HashSet<XTrace> xTraces = new HashSet<>();

		for (LinkedList<ElementBS> trace : traces) {
			boolean flag = false;
			XTrace xtrace = factory.createTrace();

			for (int i = 1; i < trace.size(); i++) {
				if (trace.get(i).isSynthetic())
					flag = true;

				XEvent evt1 = factory.createEvent();

				String name = "";
				
				ElementBS one = trace.get(i);
				ElementBS two = trace.get(i - 1);

				BitSet labelIndex = (BitSet) one.code.clone();
				labelIndex.andNot(two.code);

				int index = labelIndex.nextSetBit(0);
				name = lattice.getLabel(index);

				if (lattice.isNewPath(one, two))
					flag = true;

				if (originalName.containsKey(name))
					name = originalName.get(name);

				XAttribute a = XFactoryRegistry.instance().currentDefault()
						.createAttributeLiteral(XConceptExtension.KEY_NAME, name, null);
				evt1.getAttributes().put(XConceptExtension.KEY_NAME, a);
				xtrace.add(evt1);
				
//				if(lattice.isJoinOfPrimes(one) && one.pre.size() > 1 && one.post.size() > 1) {
//					HashSet<String> nextlabels = new HashSet<>();
//					
//					for(ElementBS next : one.post)
//						nextlabels.add(lattice.getLabelBetween(one, next));
//					
//					if(!this.fakeCounter.contains(nextlabels))
//						this.fakeCounter.add(nextlabels);
//					
//					XEvent evtFake = factory.createEvent();
//					XAttribute b = XFactoryRegistry.instance().currentDefault()
//							.createAttributeLiteral(XConceptExtension.KEY_NAME, "fake" + this.fakeCounter.indexOf(nextlabels) , null);
//					evtFake.getAttributes().put(XConceptExtension.KEY_NAME, b);
//					xtrace.add(evtFake);
//				}
				
			}

			if (flag || !complement)
				xTraces.add(xtrace);
		}

		return xTraces;
	}

	private HashSet<XTrace> getXTraces(LatticeBS lattice, XFactory factory) {
		Set<LinkedList<ElementBS>> traces = lattice.getComplement();
		HashSet<XTrace> xTraces = new HashSet<>();

		for (LinkedList<ElementBS> trace : traces) {
			boolean flag = false;
			XTrace xtrace = factory.createTrace();

			for (int i = 1; i < trace.size(); i++) {
				if (trace.get(i).isSynthetic())
					flag = true;

				XEvent evt1 = factory.createEvent();

				String name = "";
				ElementBS one = trace.get(i);
				ElementBS two = trace.get(i - 1);

				BitSet labelIndex = (BitSet) one.code.clone();
				labelIndex.andNot(two.code);

				int index = labelIndex.nextSetBit(0);
				name = lattice.getLabel(index);

				if (lattice.isNewPath(one, two))
					flag = true;

				if (originalName.containsKey(name))
					name = originalName.get(name);

				XAttribute a = XFactoryRegistry.instance().currentDefault()
						.createAttributeLiteral(XConceptExtension.KEY_NAME, name, null);
				evt1.getAttributes().put(XConceptExtension.KEY_NAME, a);
				xtrace.add(evt1);
				
//				if(lattice.isJoinOfPrimes(one) && one.pre.size() > 1 && one.post.size() > 1) {
//					HashSet<String> nextlabels = new HashSet<>();
//					
//					for(ElementBS next : one.post)
//						nextlabels.add(lattice.getLabelBetween(one, next));
//					
//					if(!this.fakeCounter.contains(nextlabels))
//						this.fakeCounter.add(nextlabels);
//					
//					XEvent evtFake = factory.createEvent();
//					XAttribute b = XFactoryRegistry.instance().currentDefault()
//							.createAttributeLiteral(XConceptExtension.KEY_NAME, "fake" + this.fakeCounter.indexOf(nextlabels) , null);
//					evtFake.getAttributes().put(XConceptExtension.KEY_NAME, b);
//					xtrace.add(evtFake);
//				}
			}

			if (flag)
				xTraces.add(xtrace);
		}

		return xTraces;
	}
	
	private HashSet<XTrace> getXTraces(Table<ElementBS, ElementBS, ElementBS> classElements, HashMap<ElementBS, Integer> equivalences, LatticeBS lattice,
			XFactory factory) {
		Set<LinkedList<ElementBS>> traces = lattice.getComplement();
		HashSet<XTrace> xTraces = new HashSet<>();
		HashMap<ElementBS, String> hashedPrimes = new HashMap<>();
		
		for (LinkedList<ElementBS> trace : traces) {
			XTrace xtrace = factory.createTrace();

			for (int i = 1; i < trace.size(); i++) {
				XEvent evt1 = factory.createEvent();

				String name = "";
				ElementBS one = trace.get(i);
				ElementBS two = trace.get(i - 1);

				if(classElements.contains(two, one)) {
					one = classElements.get(two, one);
					two = one.pre.getFirst();
				}
				
				BitSet labelIndex = (BitSet) one.code.clone();
				labelIndex.andNot(two.code);

				int index = labelIndex.nextSetBit(0);
				name = lattice.getLabel(index);

				if (originalName.containsKey(name))
					name = originalName.get(name);
				name += "_c" + equivalences.get(one);
				
				// POD Independence relation
				hashedPrimes.put(one,name);

				XAttribute a = XFactoryRegistry.instance().currentDefault()
						.createAttributeLiteral(XConceptExtension.KEY_NAME, name, null);
				evt1.getAttributes().put(XConceptExtension.KEY_NAME, a);
				xtrace.add(evt1);
				
//				if(lattice.isJoinOfPrimes(trace.get(i)) && trace.get(i).pre.size() > 1 && trace.get(i).post.size() > 1) {
//					HashSet<String> nextlabels = new HashSet<>();
//					
//					for(ElementBS next : trace.get(i).post)
//						nextlabels.add(lattice.getLabelBetween(trace.get(i), next));
//					
//					if(!this.fakeCounter.contains(nextlabels))
//						this.fakeCounter.add(nextlabels);
//					
//					XEvent evtFake = factory.createEvent();
//					XAttribute b = XFactoryRegistry.instance().currentDefault()
//							.createAttributeLiteral(XConceptExtension.KEY_NAME, "fake" + this.fakeCounter.indexOf(nextlabels) , null);
//					evtFake.getAttributes().put(XConceptExtension.KEY_NAME, b);
//					xtrace.add(evtFake);
//				}
			}

			xTraces.add(xtrace);
		}

		if(dependeceAllRel == null)
		this.dependeceAllRel = HashMultimap.<String, String> create();
		HashMultimap<ElementBS, ElementBS> dependenceRels = lattice.getDependenceRelations();
		
		for(ElementBS e1 : dependenceRels.keySet())
			for(ElementBS e2 : dependenceRels.get(e1))
				if(!this.dependeceAllRel.containsEntry(hashedPrimes.get(e1), hashedPrimes.get(e2)))
					this.dependeceAllRel.put(hashedPrimes.get(e1), hashedPrimes.get(e2));
		
		return xTraces;
	}
	
	public HashMap<String, Integer> relabel(Table<ElementBS, ElementBS, ElementBS> classElements, HashMap<ElementBS, Integer> equivalences,
			XLog log, List<LatticeBS> latticeCollection) {
		HashMap<XEvent, Integer> eqMapEvents = new HashMap<>();
		
		for(LatticeBS lattice : latticeCollection) {
			for(ElementBS e1 : lattice.getElements())
				for(ElementBS e2 : e1.pre) {
					HashSet<XEvent> events = lattice.getEvents(e2, e1);
					if(events != null) {
						ElementBS prime = e1;
						
						if(classElements.contains(e2, e1)) 
							prime = classElements.get(e2, e1);
						
						for(XEvent event : events)
							if(equivalences.get(prime)!= null)
								eqMapEvents.put(event, equivalences.get(prime));
					}
				}
		}
		
		HashMap<String, Integer> counter = relabel(eqMapEvents);
		
		return counter;
	}

	public HashMap<String, Integer> relabel(HashMap<XEvent, Integer> eqMapEvents) {
		HashMap<String, Integer> duplicateCounter = new HashMap<>();
		Multimap<String, String> labelMapper= HashMultimap.<String, String> create();
		
		for (Entry<XEvent, Integer> entry : eqMapEvents.entrySet()) {
			XEvent event = entry.getKey();
			String name = getEventName(event);
			String suffix = "-c" + entry.getValue();
			String newLabel1 = name + suffix;
			
			if(!name.endsWith(suffix)) {
				XAttribute a = XFactoryRegistry.instance().currentDefault().createAttributeLiteral(XConceptExtension.KEY_NAME, newLabel1, null);
				event.getAttributes().put(XConceptExtension.KEY_NAME, a);
			}
			
			if(!duplicateCounter.containsKey(name))
				duplicateCounter.put(name, 0);
			
			if(!labelMapper.containsEntry(newLabel1, name)) {
				labelMapper.put(newLabel1, name);
				duplicateCounter.put(name, duplicateCounter.get(name) + 1);
			}
		}
		
		return duplicateCounter;
	}

	private String getEventName(XEvent e) {
		String name = e.getAttributes().get(XConceptExtension.KEY_NAME).toString();

		if (name.contains("_end"))
			name = name.substring(0, name.length() - 4);

		return name;
	}

	// Create log from event equivalences and only those contained in the log
	public XLog createLog(HashSet<LatticeBS> latticeCollection, String suffix, boolean b) {
		XFactory factory = new XFactoryLiteImpl();// XFactoryRegistry.instance().currentDefault();
		XLog log = factory.createLog();

		for (LatticeBS lattice : latticeCollection) {
			HashSet<XTrace> traces = getXTraces(lattice, factory);
			log.addAll(traces);
		}
		
		serializeLog(log, "output", suffix);

		return log;
	}

	// Create log from prime equivalences and the whole lattice
	public XLog createLog(Table<ElementBS, ElementBS, ElementBS> classElements, HashMap<ElementBS, Integer> equivalences, List<LatticeBS> latticeCollection, String outputFolder, boolean b, String suffix) {
		XFactory factory = new XFactoryLiteImpl();
		XLog log = factory.createLog();

		for (LatticeBS lattice : latticeCollection) {
			HashSet<XTrace> traces = getXTraces(classElements, equivalences, lattice, factory);
			log.addAll(traces);
		}
		
		serializeLog(log, outputFolder, suffix);
		
		return log;
	}

	public void createSublog(LinkedList<Trace> sublogTraces, String outputFolder) {
		XFactory factory = new XFactoryLiteImpl();// XFactoryRegistry.instance().currentDefault();
		XLog log = factory.createLog();
		
		for(Trace trace : sublogTraces)
			log.addAll(trace.getXTraces());
		
		serializeLog(log, outputFolder, "topGroups" + sublogTraces.size());
	}
}
