package uni.melb.au;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;

import org.deckfour.xes.in.XUniversalParser;
import org.deckfour.xes.model.XLog;

import com.google.common.collect.Table;

import uni.melb.au.lattice.bs.ElementBS;
import uni.melb.au.lattice.bs.LatticeBS;
import uni.melb.au.lattice.bs.LatticeBlueprintDistMakerBS;
import uni.melb.au.lattice.bs.LatticeNonDistMakerBS;
import uni.melb.au.lattice.utils.CommandLine;
import uni.melb.au.lattice.utils.CommandLine.*;

@Command(name = "latticer", mixinStandardHelpOptions = true, version = "latticer 1.0", description = "Latticer is a library for computing lattices out of an event log. It is best used as a library.")
public class EntryPoint implements Callable<Integer> {

	@Parameters(index = "0", description = "Event log")
	File logFile = null;

	@Option(names = { "-t",
			"--threshold" }, description = "Value from 0 to 1, where 0 is the most permissive value and 1 the most restricitive value.",required=false)
	private float threshold = 0;

	@Option(names = { "-r",
			"--relabel" }, description = "A value of true relabels the event log according to concurrency relations.", required=false)
	private boolean relabel = false;
	
	@Option(names = { "-c",
	"--complete" }, description = "Generate a new log with all the traces represented in the lattices (due to the amount of potential traces, it is not guarantee to finish for big logs).", required=false)
	private boolean complete = false;

	public static void main(String... args) {
		int exitCode = new CommandLine(new EntryPoint()).execute(args);
		System.exit(exitCode);
	}

	@Override
	public Integer call() throws Exception {
		XLog log = openLog(logFile.getPath());

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

		return 0;
	}

	public List<LatticeBS> createLattices(XLog log, float threshold, boolean relabel) {
		LatticeNonDistMakerBS blueprint = new LatticeNonDistMakerBS(log);
		blueprint.create();

		LatticeBlueprintDistMakerBS latticeMaker = new LatticeBlueprintDistMakerBS(log, threshold, blueprint);
		latticeMaker.setOutputFolder(".");
		latticeMaker.create();
//		latticeMaker.printPES();

		try {
			if (relabel) {
				// Detection of equivalent events
				HashMap<ElementBS, Integer> equivalences = latticeMaker.getPrimeEqReplay();
				Table<ElementBS, ElementBS, ElementBS> classElements;

				classElements = latticeMaker.findEquivalencesLattice(equivalences);

				// latticeMaker.printPES(equivalences);
				latticeMaker.serializeLog(classElements, equivalences);
			}
		} catch (IOException e) { e.printStackTrace(); }

		return latticeMaker.getLatticeCollection();
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
