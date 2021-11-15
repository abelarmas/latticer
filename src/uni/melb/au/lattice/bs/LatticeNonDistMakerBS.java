package uni.melb.au.lattice.bs;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import com.google.common.collect.HashBiMap;
import uni.melb.au.log.Trace;

public class LatticeNonDistMakerBS {
	private XLog log;
	HashBiMap<String, Integer> hashAct;
	HashMap<Integer, HashSet<XTrace>> hashXTraces;
	HashBiMap<LinkedList<String>, Integer> hashTracesList;
	HashMap<Trace, Integer> traceEquiv;
	public int distributive = 0;
	public int nonDistributive = 0;
	public LatticeBS lattice;
	
	HashSet<Trace> traces;
	
	public HashMap<String, String> originalName;
	HashSet<String> labels;
	
	public HashSet<String> repetitiveLabLattice = null;

	public LatticeNonDistMakerBS(XLog log) {
		this.log = log;
	}

	public void create() {
		hashTraceActivities();
		createLattices();
	}

	private void createLattices() {
		this.lattice = new LatticeBS(traces, null);
	}

	private void hashTraceActivities() {
		this.hashAct = HashBiMap.<String, Integer>create();
		this.hashXTraces = new HashMap<>();
		this.hashTracesList = HashBiMap.<LinkedList<String>, Integer>create();
		this.originalName = new HashMap<String, String>();
		this.labels = new HashSet<>();
		this.repetitiveLabLattice = new HashSet<>();
		this.traces = new HashSet<>();
		
		for (XTrace xtrace : log) {
			LinkedList<String> listTrace = new LinkedList<>();
			
			for (XEvent event : xtrace) {
				String name = getEventName(event);
				labels.add(name);
				listTrace.add(name);
			}
			
			if(!hashTracesList.containsKey(listTrace)) {
				hashTracesList.put(listTrace, hashTracesList.size());
				hashXTraces.put(hashTracesList.get(listTrace), new HashSet<>());
			}
			
			hashXTraces.get(hashTracesList.get(listTrace)).add(xtrace);
		}
		
		for(int i = 0; i < hashTracesList.size(); i++) {
			Trace trace = new Trace(hashXTraces.get(i));
			
			for(String name : trace.getListTrace()) 
				if(!hashAct.containsKey(name))
					hashAct.put(name, hashAct.size());
			
			trace.setIds(hashAct);
			traces.add(trace);
			this.originalName.putAll(trace.getOriginalName());
			
			identifyRepetitive(trace);
		}
	}
	
	private String getEventName(XEvent e) {
		return e.getAttributes().get(XConceptExtension.KEY_NAME).toString();
	}

	private void identifyRepetitive(Trace trace) {
		HashSet<String> repetitiveIntra = new HashSet<>();
		for (int i = 0; i < trace.getListTraceOriginal().size(); i++) {
			String activity = trace.getListTraceOriginal().get(i);

			if(repetitiveIntra.contains(activity))
				this.repetitiveLabLattice.add(activity);
			else
				repetitiveIntra.add(activity);
		}
	}

	public HashSet<Trace> getTraces() {
		return this.traces;
	}

	public String toPetrify() {
		lattice.toDotNonDist();
		
		LinkedList<ElementBS> elements = lattice.getElements();
		
		String header = "# State graph generated ... \n" +
		"# from <pn_syn.g> on    ... \n" +
		".model pn_synthesis \n";
		
		String inputs = ".inputs ";
		HashSet<String> visited = new HashSet<>();
		for(ElementBS next : lattice.bottom.post) {
			inputs += "t" + lattice.labels.indexOf(lattice.getLabelBetween(lattice.bottom, next)) + " ";
			visited.add(lattice.getLabelBetween(lattice.bottom, next));
		}
		inputs += "\n";
		
		String outputs = ".outputs ";
		for(String s : lattice.labels)
			if(!visited.contains(s))
				outputs += "t" + lattice.labels.indexOf(s) + " ";
		outputs += "\n";
		
		String stateNumber = ".state graph #" + elements.size() + "states \n";
		String transitions = "";
		
		for(ElementBS elem : elements)
			for(ElementBS e2 : elem.post) 
				transitions += ("s"+elements.indexOf(elem)) + " " + ("t" +lattice.labels.indexOf(lattice.getLabelBetween(elem, e2))) + " " + ("s" + elements.indexOf(e2)) + "\n";
		
		String closing = ".marking {"+("s"+elements.indexOf(lattice.bottom))+"} \n";
		
		return header + inputs + outputs + stateNumber +transitions + closing + ".end";
	}
}
