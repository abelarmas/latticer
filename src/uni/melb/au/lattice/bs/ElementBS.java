package uni.melb.au.lattice.bs;

import java.text.ParseException;
import java.util.BitSet;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;

import org.deckfour.xes.model.XAttributeTimestamp;

import com.google.common.collect.HashMultimap;

public class ElementBS {
	public BitSet code;
	public LinkedList<ElementBS> pre;
	public LinkedList<ElementBS> post;
	private boolean synthetic = false;
	private boolean naturalJoin = false;
	
	// Utils elements for TarjanLCA 
	public ElementBS parent;
	public short rank;
	public ElementBS ancestor;
	public boolean flag = false;
	
	// Lattice container
	private LatticeBS lattice;
	
	// Events
//	private HashSet<XEvent> events;
	
	// Attributes
	private HashMultimap<String, Double> numericalAtt;
	private HashMultimap<String, String> stringAtt;
	private HashMultimap<String, Date> timeAtt;
	private LinkedList<String> labels;
	private HashSet<String> relabeled;
	private Integer pathNumber;
	
	public ElementBS(BitSet code, LatticeBS lattice) {
		this.code = code;
		this.lattice = lattice;
		
		this.pre = new LinkedList<>();
		this.post = new LinkedList<>();
	}
	
	public LinkedList<String> getLabels(){ 
		return labels;
	}
	
	public String toString() {
		return this.code.toString();
	}
	
	public void restartLCA() {
		this.parent = null;
		this.rank = 0;
		this.ancestor = null;
		this.flag = false;
	}

	public boolean isSynthetic() {
		return synthetic;
	}

	public void setSynthetic(boolean synthetic) {
		this.synthetic = synthetic;
	}

	public boolean isNaturalJoin() {
		return naturalJoin;
	}
	
	public void setNaturalJoin(boolean naturalJoin) {
		this.naturalJoin = naturalJoin;
	}

//	public void processEvt(XEvent xEvent) {
//		if(this.events == null)
//			this.events = new HashSet<>();
//		
//		this.events.add(xEvent);
//	}
//	
//	public void computeEvtAtt() {
//		try {
//			if(this.numericalAtt != null || this.synthetic)
//				return;
//			
//			this.numericalAtt = HashMultimap.<String, Double> create(); 
//			this.timeAtt = HashMultimap.<String, Date> create();
//			this.stringAtt = HashMultimap.<String, String> create();
//			
//			for(XEvent e : this.events) {
//				XAttributeMap attMap = e.getAttributes();
//				
//				for(Entry<String, XAttribute> entry : attMap.entrySet()) {
//					if(isInteger(entry.getValue().toString())) 
//						this.numericalAtt.put(entry.getKey(), new Double(Integer.valueOf(entry.getValue().toString())));
//					else if (isDouble(entry.getValue().toString()))
//						this.numericalAtt.put(entry.getKey(), Double.valueOf(entry.getValue().toString()));
//					else if(entry.getKey().contains("timestamp") || isDate(entry.getValue().toString())) 
//						this.timeAtt.put(entry.getKey(), XAttributeTimestamp.FORMATTER.parseObject(entry.getValue().toString()));
//					else 
//						this.stringAtt.put(entry.getKey(), entry.getValue().toString());
//				}
//			}
//		}catch(Exception e) { e.printStackTrace(); }
//	}

	private boolean isDate(String string) {
		try {
	        XAttributeTimestamp.FORMATTER.parseObject(string);
	        return true;
	    } catch (ParseException e) {
			return false;
		}
	}

	public boolean isInteger( String input ) {
	    try {
	        Integer.parseInt( input );
	        return true;
	    }
	    catch(NumberFormatException e ) {
	        return false;
	    }
	}
	
	public boolean isDouble( String input ) {
	    try {
	        Double.parseDouble( input );
	        return true;
	    }
	    catch(NumberFormatException e ) {
	        return false;
	    }
	}

	public HashMultimap<String, Double> getNumericalAtt() {
		return this.numericalAtt;
	}
	
	public HashMultimap<String, String> getStringAtt() {
		return this.stringAtt;
	}
	
	public HashMultimap<String, Date> getTimeAtt() {
		return this.timeAtt;
	}

	public void setTranslation(LinkedList<String> labels) {
		this.labels = labels;
	}
	
	public void setTranslationRelabeled(HashSet<String> relabeled) {
		this.relabeled = relabeled;
	}
	
	public HashSet<String> getSetRelabeled() {
		return this.relabeled;
	}

	public void setPathNumber(Integer paths) {
		this.pathNumber = paths;
	}
	
	public Integer getPathNumber() {
		return this.pathNumber;
	}

	public LinkedList<String> getEventLabels() {
		
		return this.lattice.getLabels(this);
	}
	
//	public HashSet<XEvent> getEvents() {
//		return events;
//	}
}
