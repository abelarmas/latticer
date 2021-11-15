package uni.melb.au.log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XTrace;

import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;

public class Trace{
	HashSet<XTrace> xtraces;
	LinkedList<String> listTrace;
	LinkedList<String> listTraceOriginal;
	LinkedList<Integer> listTraceId;
	HashMultimap<Integer, Integer> nonRep;
	
	HashBiMap<String, Integer> hashAct;
	HashMap<String, String> originalName;
	
	public Trace(HashSet<XTrace> xtraces) {
		this.nonRep = HashMultimap.<Integer, Integer>create();
		
		this.xtraces = new HashSet<>();
		this.xtraces.addAll(xtraces);
		
		getTraceList();
	}
	
	public HashSet<XTrace> getXTraces() {
		return xtraces;
	}

	public LinkedList<String> getListTrace() {
		return listTrace;
	}
	
	public LinkedList<String> getListTraceOriginal() {
		return listTraceOriginal;
	}

	public void setListTrace(LinkedList<String> listTrace) {
		this.listTrace = listTrace;
	}

	public LinkedList<Integer> getListTraceId() {
		return listTraceId;
	}

	private void getTraceList() {
		this.listTrace = new LinkedList<>();
		this.originalName = new HashMap<>();
		this.listTraceOriginal = new LinkedList<>();
		
		XTrace xtrace = this.xtraces.iterator().next();
		HashMap<String, Integer> counterLabel = new HashMap<>();
		
		Iterator<XEvent> it = xtrace.iterator();
		while(it.hasNext()){
			XEvent event = it.next(); 
			String name = getEventName(event);
			listTraceOriginal.add(name);
			
			String newName = name;

			if (!counterLabel.containsKey(name)) 
				counterLabel.put(name, 0);
			else {
				counterLabel.put(name, counterLabel.get(name) + 1);
				newName = name + "r-" +counterLabel.get(name);
				originalName.put(newName, name);
			}
			
			this.listTrace.add(newName);
		}
	}
	
	private String getEventName(XEvent e) {
		return e.getAttributes().get(XConceptExtension.KEY_NAME).toString();
	}

	public void setIds(HashBiMap<String, Integer> hashAct) {
		this.listTraceId = new LinkedList<>();
		
		for(String st : listTraceOriginal)
			listTraceId.add(hashAct.get(st));
		
	}
	
	public String toString() {
		return listTrace.toString();
	}

	public void addNewLabel(String newName) {
		if(this.listTrace == null)
			this.listTrace = new LinkedList<>();
		
		this.listTrace.add(newName);
	}

	public void setNonRep(int i, int j) {
		this.nonRep.put(i, j);
		this.nonRep.put(j, i);
	}
	
	public boolean isNonRep(Integer i, Integer j) {
		return this.nonRep.containsEntry(i,  j);
	}

	public HashSet<XEvent> getXEvents(int i) {
		HashSet<XEvent> events = new HashSet<>();
		
		for(XTrace trace : xtraces)		
			events.add(trace.get(i));
		
		return events;
	}

	public HashMap<String, String> getOriginalName() {
		return this.originalName;
	}
}
