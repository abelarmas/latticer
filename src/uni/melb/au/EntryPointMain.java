package uni.melb.au;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;

import org.deckfour.xes.in.XUniversalParser;
import org.deckfour.xes.model.XLog;

import com.google.common.collect.Table;

import uni.melb.au.lattice.bs.ElementBS;
import uni.melb.au.lattice.bs.LatticeBlueprintDistMakerBS;
import uni.melb.au.lattice.bs.LatticeNonDistMakerBS;

public class EntryPointMain {
	private static float threshold = 0;
	private static boolean relabel = false;
	private static boolean complete = false;
	
	public static void main(String[] args) {
		String logFile = "input/landDev.xes"; 
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
