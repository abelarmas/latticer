package uni.melb.au;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.HashMap;

import org.deckfour.xes.in.XUniversalParser;
import org.deckfour.xes.model.XLog;
import org.processmining.framework.util.Pair;
import org.processmining.models.connections.GraphLayoutConnection;
import org.processmining.models.connections.transitionsystem.TransitionSystemConnection;
import org.processmining.models.graphbased.directed.DirectedGraphElementWeights;
import org.processmining.models.graphbased.directed.transitionsystem.AcceptStateSet;
import org.processmining.models.graphbased.directed.transitionsystem.StartStateSet;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystem;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystemFactory;
import org.processmining.plugins.tsml.Tsml;

import com.google.common.collect.Table;

import uni.melb.au.lattice.bs.ElementBS;
import uni.melb.au.lattice.bs.LatticeBS;
import uni.melb.au.lattice.bs.LatticeBlueprintDistMakerBS;
import uni.melb.au.lattice.bs.LatticeNonDistMakerBS;

public class EntryPointMain {
	private static float threshold = 0;
	private static boolean relabel = false;
	private static boolean complete = false;
	
	public static void main(String[] args) {
		String logFile = "input/checkInAirAcyclic.xes"; 
		EntryPointMain main = new EntryPointMain();
		
		XLog log;
		try {
			log = main.openLog(logFile);
		
		LatticeNonDistMakerBS blueprint = new LatticeNonDistMakerBS(log);
		blueprint.create();

		LatticeBlueprintDistMakerBS latticeMaker = new LatticeBlueprintDistMakerBS(log, threshold, blueprint);
		latticeMaker.setOutputFolder(".");
		latticeMaker.create();
//		latticeMaker.printPES();

		double[] minMaxAvgVar = latticeMaker.getMinMaxAvgVar();
		double[] minMaxAvgCompleteness = latticeMaker.getMinMaxAvgCompleteness();

		{
			latticeMaker.combineLattices();
			main.writeTSML(latticeMaker);
		}
		
		if(complete)
			latticeMaker.serializeLog();

		if (relabel) {
			// Detection of equivalent events
			HashMap<ElementBS, Integer> equivalences = latticeMaker.getPrimeEqReplay();
			Table<ElementBS, ElementBS, ElementBS> classElements = latticeMaker.findEquivalencesLattice(equivalences);
			// latticeMaker.printPES(equivalences);
			latticeMaker.serializeLog(classElements, equivalences);
		}
		
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	private void writeTSML(LatticeBlueprintDistMakerBS latticeMaker) {
		int i = 1;
		for(LatticeBS lattice : latticeMaker.getLatticeCollection()){
			TransitionSystem ts = parse(lattice,i);
			
			StartStateSet starts = new StartStateSet();
			starts.add(lattice.getBottom());
			
			AcceptStateSet accepts = new AcceptStateSet();
			accepts.add(lattice.getTop());
			
			DirectedGraphElementWeights w = new DirectedGraphElementWeights();
			for(ElementBS e : lattice.getElements())
				w.put(e, 3);
			
			GraphLayoutConnection layout = new GraphLayoutConnection(ts);
			layout.expandAll();
			
			Tsml tsml = new Tsml().marshall(ts, starts, accepts, w, layout);
			String text = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n" + tsml.exportElement(tsml);

			try {
				BufferedWriter bw;
				bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File("ts"+i+".tsml"))));
				bw.write(text);
				bw.close();
			} catch (Exception e) { e.printStackTrace();}
			
			i++;
		}
	}

	private TransitionSystem parse(LatticeBS lattice, int  i) {
		TransitionSystem ts = TransitionSystemFactory.newTransitionSystem("t" + i);
		HashMap<ElementBS, State> mapStates = new HashMap<>();
		
		for(ElementBS element : lattice.getElements()) {
			ts.addState(element);
			mapStates.put(element, ts.getNode(element));
		}
		
		
		for(ElementBS e1 : lattice.getElements()) 
			for(ElementBS e2 : e1.post) 
				ts.addTransition(e1, e2, lattice.getLabelBetween(e1, e2));
		
		return ts;
	}

	private XLog openLog(String fileName) {
		try {
			XLog log = null;
			XUniversalParser parser = new org.deckfour.xes.in.XUniversalParser();
			Collection<XLog> logs;

			System.out.println("file name = " + fileName);

			logs = (Collection<XLog>) parser.parse(new File(fileName));
			log = logs.iterator().next();
			return log;
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

}
