package uni.melb.au.lattice.bs;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;

import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.processmining.framework.util.Pair;
import org.processmining.models.connections.GraphLayoutConnection;
import org.processmining.models.graphbased.directed.DirectedGraphElementWeights;
import org.processmining.models.graphbased.directed.transitionsystem.AcceptStateSet;
import org.processmining.models.graphbased.directed.transitionsystem.StartStateSet;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystem;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystemFactory;
import org.processmining.plugins.tsml.Tsml;

import com.apporiented.algorithm.clustering.AverageLinkageStrategy;
import com.apporiented.algorithm.clustering.Cluster;
import com.apporiented.algorithm.clustering.ClusteringAlgorithm;
import com.apporiented.algorithm.clustering.DefaultClusteringAlgorithm;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

import at.unisalzburg.dbresearch.apted.costmodel.CostModel;
import at.unisalzburg.dbresearch.apted.node.Node;
import uni.melb.au.lattice.Lattice;
import uni.melb.au.log.Relabeler;
import uni.melb.au.log.Trace;

public class LatticeBlueprintDistMakerBS {
	private XLog log;
	HashMap<Trace, Integer> traceEquiv;
	HashMap<Multiset<Integer>, Integer> multiSetEquivCounter;
	HashBiMap<Multiset<Integer>, Integer> multiSetEquiv;
	public int distributive = 0;
	public int nonDistributive = 0;
	List<LatticeBS> latticeCollection;

	// Count duplications
	HashMap<Multiset<Integer>, Integer> multiSetEquivCounterXTraces;

	double threshold = -1.0d;
	private String outputFolder = "output";
	private LatticeNonDistMakerBS blueprint;

	HashBiMap<String, Integer> hashAct;
	HashSet<String> labels;
	HashMap<String, String> originalName;
//	HashBiMap<Trace, Integer> hashTraces;

	boolean dynamic = false;
	private double average;

	// Test dynamic treshold
	public LatticeBlueprintDistMakerBS(XLog log, LatticeNonDistMakerBS blueprint) {
		this.log = log;
		this.blueprint = blueprint;

		// Use global blueprint
//		this.hashTraces = blueprint.hashTraces;
		this.hashAct = blueprint.hashAct;
		this.labels = blueprint.labels;
		this.originalName = blueprint.originalName;
	}

	public LatticeBlueprintDistMakerBS(XLog log, float threshold, LatticeNonDistMakerBS blueprint) {
		this.log = log;
		this.threshold = threshold;
		this.blueprint = blueprint;

		// Use global blueprint
//		this.hashTraces = blueprint.hashTraces;
		this.hashAct = blueprint.hashAct;
		this.labels = blueprint.labels;
		this.originalName = blueprint.originalName;
	}

	public void setDynamic() {
		this.dynamic = true;
	}

	public void create() {
		this.latticeCollection = new LinkedList<LatticeBS>();
		initializeByLabelMultiset();
		createLattices();
	}

	// MultisetEquivalence
	private void initializeByLabelMultiset() {
		LinkedHashSet<Multiset<Integer>> multisetIds = new LinkedHashSet<>();
		this.multiSetEquiv = HashBiMap.<Multiset<Integer>, Integer>create();
		this.multiSetEquivCounter = new HashMap<Multiset<Integer>, Integer>();
		this.multiSetEquivCounterXTraces = new HashMap<Multiset<Integer>, Integer>();
		this.traceEquiv = new HashMap<Trace, Integer>();

		for (Trace trace : blueprint.getTraces()) {
			trace.setIds(hashAct);
			Multiset<Integer> multisetTrace = HashMultiset.<Integer>create(trace.getListTraceId());

			if (!multisetIds.contains(multisetTrace)) {
				multiSetEquiv.put(multisetTrace, multisetIds.size());
				multisetIds.add(multisetTrace);
				multiSetEquivCounter.put(multisetTrace, 0);
				multiSetEquivCounterXTraces.put(multisetTrace, 0);
			}

			Integer value = multiSetEquivCounter.get(multisetTrace);
			multiSetEquivCounter.put(multisetTrace, value + 1);
			multiSetEquivCounterXTraces.put(multisetTrace,
					multiSetEquivCounterXTraces.get(multisetTrace) + trace.getXTraces().size());

			traceEquiv.put(trace, multiSetEquiv.get(multisetTrace));
		}

		LinkedList<Multiset<Integer>> orderedList = new LinkedList<>(multiSetEquivCounter.keySet());
		Collections.sort(orderedList, new Comparator<Multiset<Integer>>() {

			@Override
			public int compare(Multiset<Integer> o1, Multiset<Integer> o2) {
				return o1.size() - o2.size();
			}
		});

		this.average = computeDecayParam();
	}

	private void createLattices() {
		int i = 0;
		int undef = 0;

		try {
			for (Multiset<Integer> key : multiSetEquivCounter.keySet()) {
				// if (multiSetEquivCounter.get(key) > 1)
				{
					System.out.println("*" + i);
					// Queue to refine equivalences
					Queue<HashSet<Trace>> queue = new LinkedList<>();
					HashSet<Trace> traceEq = getTraces(key);
					queue.add(traceEq);

					if (dynamic) {
						this.threshold = computeThreshold(traceEq);

						System.out.println("Completeness threshold: " + this.threshold);
					}

					boolean firstTrial = true;

					while (!queue.isEmpty()) {
						System.out.println("-");
						HashSet<Trace> traceEquivalence = queue.poll();
						LatticeBS latticeBS = new LatticeBS(traceEquivalence, blueprint.lattice.labels);

						if (firstTrial) {
//							latticeBS.completeBlueprint(blueprint);	
							latticeBS.complete();
//							latticeBS.expand();
						} else
							latticeBS.complete();

						double numberOriginal = traceEquivalence.size();

						double pathsDP = latticeBS.countPathsDP();
						double completenessfactor = (numberOriginal / pathsDP);
						latticeBS.setCompletenessFactor(completenessfactor);

						if (completenessfactor < threshold) {
							// Strategy to split trace equivalence based on clustering
							System.out.println("Threshold: " + threshold);
							LinkedList<Trace> tracesList = new LinkedList<>(traceEquivalence);
							Cluster cluster = clusterTraces(tracesList);

							if (firstTrial) {
								firstTrial = false;
								queue.add(getTraces(key));
							} else
								queue.addAll(destroyEquivalence(cluster, tracesList));

							System.out.println(".");
						} else {
							if (getVariantNumber(traceEquivalence) != traceEquivalence.size()) {
								System.out.println("Error .....");
								System.exit(0);
							}

							int variantNumber = getVariantNumber(traceEquivalence);
							int traceLenght = traceEquivalence.iterator().next().getListTrace().size();

							this.distributive++;
							this.latticeCollection.add(latticeBS);

							latticeBS.countPathsDPInverse();
							writeToFile(latticeBS.toDot(), "traces" + (undef++));

							latticeBS.computePES();
							latticeBS.setCompletenessFactor(completenessfactor);
						}
					}
				}
				i++;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

//		Collections.sort(latticeCollection, new Comparator<LatticeBS>() {
//
//			@Override
//			public int compare(LatticeBS o1, LatticeBS o2) {
//				if (o1.getCompletenessFactor() < o2.getCompletenessFactor())
//					return -1;
//				if (o1.getCompletenessFactor() > o2.getCompletenessFactor())
//					return 1;
//				return 0;
//			}
//		});

	}

	private int getXTraceCount(HashSet<Trace> traceEq) {
		int xTraceCounter = 0;

		for (Trace t : traceEq)
			xTraceCounter += t.getXTraces().size();

		return xTraceCounter;
	}

	public double computeDecayParam() {
		double sum = this.log.size();
		return sum / multiSetEquivCounter.size();
	}

	private double computeThreshold(HashSet<Trace> traceEq) {
		// The completeness factor is based on the proportion of the trace equivalence
		// of the log
//		double sum = 0.0d;
//		for (Trace t : traceEq)
//			sum += t.getXTraces().size();
//		
//		double total = this.log.size();
//		
//		return (sum/total);

		// Compute completeness threshold based on the probability of an exponential
		// distribution.
//		double k = 1.0f;
//		
//		double sum = 0.0d;
//		for (Trace t : traceEq)
//			sum += t.getXTraces().size();
//
//		System.out.println(sum + " out of " + this.log.size());
//
//		if((1 - (1/Math.log10(sum + k))) < 0)
//			System.out.println("Error! value less than 0");
//		
//		return 1 - (1/Math.log(sum + k));

		// Compute completeness threshold based on the probability of an exponential
		// distribution.
		System.out.println("Average = " + this.average);
		System.out.println("decay param = " + (1.0f / this.average));

		double decay = 1.0d / this.average;

		double sum = 0.0d;
		for (Trace t : traceEq)
			sum += t.getXTraces().size();

		System.out.println(sum + " out of " + this.log.size());

		return 1 - Math.pow(Math.E, -1.0d * decay * sum);
//		

//		int max = 0;
//		int min = Integer.MAX_VALUE;
//		
//		int sum = 0;
//		for(Trace t : traceEq)
//			sum += t.getXTraces().size();
//		
//		LinkedList<Integer> counters = new LinkedList<>(new HashSet<>(multiSetEquivCounterXTraces.values()));
//		Collections.sort(counters);
//		
//		int number = counters.size();
//		
//		int j = 0;
//		for(Integer i : counters) {
//			if(i > sum)
//				break;
//			j++;
//		}
//		
//		return (float) (j / ((float) number/10.0f)) / 10.0f;

//		for(Entry<Multiset<Integer>, Integer> entry : multiSetEquivCounterXTraces.entrySet()) {
//			if(max < entry.getValue())
//				max = entry.getValue();
//			
//			if(min > entry.getValue())
//				min = entry.getValue();
//		}
//		
//		int sum = 0;
//		for(Trace t : traceEq)
//			sum += t.getXTraces().size();
//					
//		return (float) sum/max;
	}

	private int getVariantNumber(HashSet<Trace> traceEquivalence) {
		HashSet<LinkedList<String>> counterListFinal = new HashSet<>();

		for (Trace trace : traceEquivalence)
			if (!counterListFinal.contains(trace.getListTraceOriginal()))
				counterListFinal.add(trace.getListTraceOriginal());

		return counterListFinal.size();
	}

	private LinkedList<HashSet<Trace>> destroyEquivalence(Cluster cluster, LinkedList<Trace> tracesList) {
		LinkedList<HashSet<Trace>> groups = new LinkedList<>();
		System.out.println("*");
		if (cluster.getChildren().isEmpty()) {
			int i = tracesList.size() / 2;
			HashSet<Trace> group1 = new HashSet<>();
			for (int j = 0; j < i; j++)
				group1.add(tracesList.get(j));

			HashSet<Trace> group2 = new HashSet<>();
			for (int j = i; j < tracesList.size(); j++)
				group2.add(tracesList.get(j));
			groups.add(group1);
			groups.add(group2);
		} else {
			for (Cluster child : cluster.getChildren()) {
				HashSet<Trace> group = new HashSet<>();

				for (String leaf : child.getLeafNames())
					group.add(tracesList.get(Integer.parseInt(leaf)));

				groups.add(group);
			}
		}
		System.out.println("*");
		return groups;
	}

	private Cluster clusterTraces(LinkedList<Trace> traces) {
		System.out.println("Enter -- " + traces.size());

		double[][] matrix = new double[traces.size()][traces.size()];
		String[] names = new String[traces.size()];

		for (int i = 0; i < traces.size(); i++) {
			names[i] = "" + i;
			matrix[i][i] = 0.0d;
			for (int j = i + 1; j < traces.size(); j++) {
				System.out.println("i = " + i + " - j = " + j);

				// Distance by completion
//					HashSet<Trace> tracesSet = new HashSet<>();
//					tracesSet.add(traces.get(i));
//					tracesSet.add(traces.get(j));
//					LatticeBS lat = new LatticeBS(tracesSet, blueprint.lattice.labels);
//					double numberOriginal = lat.countPaths(lat.bottom, lat.top, 0);
//
//					lat.complete();
//					double pathsDP = lat.countPathsDP();
//					double completenessfactor = (numberOriginal / pathsDP);

				double pathsDP = getDistance(traces.get(i), traces.get(j));
				double completenessfactor = (pathsDP / traces.get(i).getListTraceOriginal().size());

				matrix[i][j] = 1.0d - completenessfactor;
				matrix[j][i] = matrix[i][j];
			}
		}

		for (int i = 0; i < matrix.length; i++) {
			for (int j = 0; j < matrix[i].length; j++) {
				System.out.print(matrix[i][j] + " ");
			}
			System.out.println();
		}

		System.out.println("Clustering start");
		ClusteringAlgorithm alg = new DefaultClusteringAlgorithm();
		System.out.println("Clustering end");
		return alg.performClustering(matrix, names, new AverageLinkageStrategy());

	}

	private double getDistance(Trace trace, Trace trace2) {
		int sim = 0;

		for (int i = 0; i < trace.getListTraceOriginal().size(); i++)
			if (trace.getListTraceOriginal().get(i).equals(trace2.getListTraceOriginal().get(i)))
				sim++;

		return sim;
	}

	private HashSet<Trace> getTraces(Multiset<Integer> key) {
		HashSet<Trace> equivalence = new HashSet<>();

		for (Trace trace : traceEquiv.keySet())
			if (this.multiSetEquiv.get(key) == this.traceEquiv.get(trace))
				equivalence.add(trace);

		return equivalence;
	}

	public void setOutputFolder(String outputFolder) {
		this.outputFolder = outputFolder;
	}

	private void writeToFile(String content, String name) {
		try {
			FileWriter myWriter = new FileWriter(name + ".dot");
			myWriter.write(content);
			myWriter.close();
			System.out.println("Successfully wrote to the file.");
		} catch (Exception e) {
			System.out.println("An error occurred.");
			e.printStackTrace();
		}
	}

//	public XLog generateComplement() {
//		Relabeler relabeler = new Relabeler(originalName);
//		XLog log = relabeler.createLog(latticeCollection, outputFolder, "complementLog", true);
//
//		return log;
//	}
//
//	public void computeSubgraphs() {
//		for (String label : labels) {
//			for (LatticeBS lattice : latticeCollection) {
//				HashSet<Pair<ElementBS, ElementBS>> subgraph = lattice.findsGraphs(label, originalName);
//
//				if (subgraph.size() > 1)
//					System.out.println(lattice.toDotSG(subgraph));
//			}
//		}
//	}

	public void findEquivalencesLog() {
//		HashSet<String> repetitiveTasks = new HashSet<>();
//		
//		for(String label : labels) {
//			for(LatticeBS lattice : latticeCollection) {
//				LinkedList<String> chain  = lattice.getChain(label);
//				
//				for(int i = 0; i < chain.size(); i++)
//					for(int j = i + 1; j < chain.size(); j++) {
//						String originalName1 = chain.get(i);
//						String originalName2 = chain.get(j);
//						
//						if(originalName.containsKey(originalName1))
//							originalName1 = originalName.get(originalName1);
//						if(originalName.containsKey(originalName2))
//							originalName2 = originalName.get(originalName2);
//						
//						if(originalName1.equals(originalName2)) {
//							repetitiveTasks.add(originalName1);
//						}
//					}
//			}
//		}
//		
//		System.out.println("Repetitive tasks = " + repetitiveTasks);

		HashMap<ElementBS, Integer> eqMap = getPrimeEqImConcurrent();

		HashMap<XEvent, Integer> eqMapEvents = new HashMap<>();
		for (LatticeBS lattice : latticeCollection)
			for (Entry<ElementBS, Integer> entry : eqMap.entrySet())
				if (lattice.containsElement(entry.getKey())) {
					HashSet<XEvent> classElements = lattice.getEdgeEventClassPrime(entry.getKey());

					for (XEvent e : classElements)
						eqMapEvents.put(e, entry.getValue());
				}

		Relabeler relabeler = new Relabeler(originalName);
		relabeler.relabel(eqMapEvents);
		relabeler.serializeLog(log, outputFolder, "Relabeled");
	}

	public HashMap<ElementBS, Integer> getPrimeEqImConcurrent() {
		HashMap<ElementBS, Integer> eqMap = new HashMap<>();
		HashBasedTable<String, HashSet<String>, Integer> equivalences = HashBasedTable
				.<String, HashSet<String>, Integer>create();
		int index = 0;

		for (String label : labels) {
			for (LatticeBS lattice : latticeCollection) {
				LinkedList<ElementBS> primes = lattice.getPrimes(label, originalName);

				for (int i = 0; i < primes.size(); i++) {
					ElementBS localRoot = primes.get(i).pre.getFirst();
					HashSet<String> localConc = lattice.getPostLabels(localRoot, originalName);
					int indexPrime = -1;

					if (equivalences.contains(label, localConc))
						indexPrime = equivalences.get(label, localConc);
					else
						indexPrime = index++;

					equivalences.put(label, localConc, indexPrime);
					eqMap.put(primes.get(i), indexPrime);
				}
			}
		}

		return eqMap;
	}

	public HashMap<ElementBS, Integer> getPrimeEqIncomparables() {
		HashMap<ElementBS, Integer> eqMap = new HashMap<>();
		HashBasedTable<String, HashSet<String>, Integer> equivalences = HashBasedTable
				.<String, HashSet<String>, Integer>create();
		int index = 0;
		float threshold = 0.7f;

		for (String label : labels) {
			for (LatticeBS lattice : latticeCollection) {
				LinkedList<ElementBS> primes = lattice.getPrimes(label, originalName);

				for (int i = 0; i < primes.size(); i++) {
					ElementBS prime = primes.get(i);
					HashSet<String> tunnelNames = lattice.getTunnelLabels(prime);

					int indexPrime = -1;

					HashSet<String> compatible = findCompatible(equivalences, label, tunnelNames, threshold);
					if (compatible != null)
						indexPrime = equivalences.get(label, compatible);
					else
						indexPrime = index++;

					equivalences.put(label, tunnelNames, indexPrime);
					eqMap.put(primes.get(i), indexPrime);
				}
			}
		}

		return eqMap;
	}

	private HashSet<String> findCompatible(HashBasedTable<String, HashSet<String>, Integer> equivalences, String label,
			HashSet<String> tunnelNames, float threshold) {
		// If exact
		// if(equivalences.contains(label, tunnelNames))
		// return tunnelNames;

		// If approximate
		for (Entry<HashSet<String>, Integer> eq : equivalences.row(label).entrySet()) {
			HashSet<String> intersection = new HashSet<>(tunnelNames);
			intersection.retainAll(eq.getKey());

			HashSet<String> union = new HashSet<>(tunnelNames);
			union.addAll(eq.getKey());

			float similarity = union.size() / intersection.size();

			if (similarity >= threshold)
				return eq.getKey();
		}

		return null;
	}

	public HashMap<ElementBS, Integer> getPrimeEqSequence() {
		HashMap<ElementBS, Integer> eqMap = new HashMap<>();
		HashBasedTable<String, HashSet<String>, Integer> equivalences = HashBasedTable
				.<String, HashSet<String>, Integer>create();
		int index = 0;
		float threshold = 0.5f;

		for (String label : labels) {
			for (LatticeBS lattice : latticeCollection) {
				LinkedList<ElementBS> primes = lattice.getPrimes(label, originalName);

				for (int i = 0; i < primes.size(); i++) {
					ElementBS prime = primes.get(i);
					ElementBS root = prime.pre.getFirst();

					HashSet<String> tunnelNames = new HashSet<>();

					for (ElementBS rNext : root.post) {
						if (rNext != prime) {
							HashSet<String> oneDimension = lattice.getTunnelLabels(root, rNext);
							oneDimension.remove(lattice.getLabelBetween(root, rNext));
							oneDimension.remove(label);

							tunnelNames.addAll(oneDimension);
						}
					}

					int indexPrime = -1;

					HashSet<String> compatible = findCompatibleSequence(equivalences, label, tunnelNames, threshold);
					if (compatible != null)
						indexPrime = equivalences.get(label, compatible);
					else {
						indexPrime = index++;
						equivalences.put(label, tunnelNames, indexPrime);
					}

					eqMap.put(primes.get(i), indexPrime);
				}
			}
		}

		return eqMap;
	}

	public HashMap<ElementBS, Integer> getPrimeEqReplay() {
		HashMap<ElementBS, Integer> eqMap = new HashMap<>();
		HashBasedTable<String, HashSet<String>, Integer> equivalencesTunnel = HashBasedTable
				.<String, HashSet<String>, Integer>create();
		Hashtable<Integer, HashSet<ElementBS>> elementsEquivalences = new Hashtable<>();

//		HashBasedTable<String, HashSet<String>, Integer> equivalencesNext = HashBasedTable.<String, HashSet<String>, Integer>create();
//		Hashtable<Integer, HashSet<ElementBS>> elementsEquivalencesNext = new Hashtable<>();

		int index = 0;
		int labelPrint = 0;
		int counter = 0;

		for (String label : labels) {
			for (LatticeBS lattice : latticeCollection) {
				LinkedList<ElementBS> primes = lattice.getPrimes(label, originalName);

				for (int i = 0; i < primes.size(); i++) {
					ElementBS prime = primes.get(i);
					ElementBS root = prime.pre.getFirst();

					// Testing
					if (labelPrint < 0) {
						Table<ElementBS, ElementBS, ElementBS> tunnels = lattice.getEdgeLatticeClassPrime(root, prime);

						HashSet<Pair<ElementBS, ElementBS>> toPrint = new HashSet<>();
						for (Cell<ElementBS, ElementBS, ElementBS> cell : tunnels.cellSet())
							toPrint.add(new Pair<ElementBS, ElementBS>(cell.getRowKey(), cell.getColumnKey()));

						writeToFile(lattice.toDotSG(toPrint), outputFolder + "/subGraph(" + label + ")-" + (counter++));
					}
					// END Testing

					LinkedHashSet<String> tunnel = lattice.getTunnelLabels(root, prime);
					tunnel.remove(lattice.getLabelBetween(root, prime));

					tunnel = translate2Original(tunnel);

					int indexPrime = -1;

					Entry<HashSet<String>, Integer> compatible = null;

					// Compatible by concurrent set
//					if(!tunnel.isEmpty()) {

					compatible = findCompatibleSubset(equivalencesTunnel, label, tunnel, elementsEquivalences, prime);

					if (compatible != null) {
						indexPrime = compatible.getValue();
						if (compatible.getKey().size() < tunnel.size())
							compatible.getKey().addAll(tunnel);
						elementsEquivalences.get(indexPrime).add(prime);
					} else {
						indexPrime = index++;
						equivalencesTunnel.put(label, tunnel, indexPrime);
						elementsEquivalences.put(indexPrime, new HashSet<>());
						elementsEquivalences.get(indexPrime).add(prime);
					}

					eqMap.put(prime, indexPrime);
//					} else {
//						
//						LinkedHashSet<String> nextLabels = new LinkedHashSet<>();
//						for(ElementBS e : prime.post) 
//							nextLabels.add(lattice.getLabelBetween(prime, e));
//						nextLabels = translate2Original(nextLabels);
//						
//					// Compatible by next element (no concurrency found)
//						compatible = findCompatibleFollow(equivalencesNext, label, nextLabels, elementsEquivalencesNext, prime);
//						
//						if (compatible != null) {
//							indexPrime = compatible.getValue();
//							if (compatible.getKey().size() < tunnel.size())
//								compatible.getKey().addAll(tunnel);
//							elementsEquivalencesNext.get(indexPrime).add(prime);
//						} else {
//							indexPrime = index++;
//							equivalencesNext.put(label, tunnel, indexPrime);
//							elementsEquivalencesNext.put(indexPrime, new HashSet<>());
//							elementsEquivalencesNext.get(indexPrime).add(prime);
//						}
//
//						eqMap.put(prime, indexPrime);
//					}

				}
			}

			labelPrint++;
		}

		return eqMap;
	}

	private LinkedHashSet<String> translate2Original(LinkedHashSet<String> tunnel) {
		LinkedHashSet<String> originalTunnel = new LinkedHashSet<>();

		for (String s : tunnel)
			if (originalName.containsKey(s))
				originalTunnel.add(originalName.get(s));
			else
				originalTunnel.add(s);

		return originalTunnel;
	}

	private Entry<HashSet<String>, Integer> findCompatibleFollow(
			HashBasedTable<String, HashSet<String>, Integer> equivalencesNext, String label,
			LinkedHashSet<String> nextLabels, Hashtable<Integer, HashSet<ElementBS>> elementsEquivalencesNext,
			ElementBS prime) {
		// If approximate
		for (Entry<HashSet<String>, Integer> eq : equivalencesNext.row(label).entrySet())
			if (!((eq.getKey().isEmpty() && !nextLabels.isEmpty())
					|| (!eq.getKey().isEmpty() && nextLabels.isEmpty()))) {
				double overlap = getOverlap(eq.getKey(), nextLabels);
				if ((eq.getKey().containsAll(nextLabels) || nextLabels.containsAll(eq.getKey())) && overlap > 0.2)
					return eq;
			}
//			else 
//				 for(ElementBS e : primesEquivalences.get(eq.getValue()))
//					 if(e.getEventLabels().containsAll(prime.getEventLabels()) || prime.getEventLabels().containsAll(e.getEventLabels()))
//						 return eq;

		return null;
	}

	private Entry<HashSet<String>, Integer> findCompatibleSubset(
			HashBasedTable<String, HashSet<String>, Integer> equivalences, String label, HashSet<String> tunnelNames,
			Hashtable<Integer, HashSet<ElementBS>> primesEquivalences, ElementBS prime) {
		// If approximate
		for (Entry<HashSet<String>, Integer> eq : equivalences.row(label).entrySet())
			if (!((eq.getKey().isEmpty() && !tunnelNames.isEmpty())
					|| (!eq.getKey().isEmpty() && tunnelNames.isEmpty()))) {
				double overlap = getOverlap(eq.getKey(), tunnelNames);
				if ((eq.getKey().containsAll(tunnelNames) || tunnelNames.containsAll(eq.getKey())) && overlap > 0.2)
					return eq;
			}
//			else 
//				 for(ElementBS e : primesEquivalences.get(eq.getValue()))
//					 if(e.getEventLabels().containsAll(prime.getEventLabels()) || prime.getEventLabels().containsAll(e.getEventLabels()))
//						 return eq;

		return null;
	}

	private HashSet<String> findCompatibleSequence(HashBasedTable<String, HashSet<String>, Integer> equivalences,
			String label, HashSet<String> tunnelNames, float threshold) {
		// If exact
		// if(equivalences.contains(label, tunnelNames))
		// return tunnelNames;

		// If approximate
		for (Entry<HashSet<String>, Integer> eq : equivalences.row(label).entrySet()) {
			if (eq.getKey().equals(tunnelNames))
				return eq.getKey();

			HashSet<String> intersection = new HashSet<>(tunnelNames);
			intersection.retainAll(eq.getKey());

			HashSet<String> union = new HashSet<>(tunnelNames);
			union.addAll(eq.getKey());

			if (intersection.size() == 0)
				continue;

			float similarity = intersection.size() / union.size();

			if (similarity >= threshold)
				return eq.getKey();
		}

		return null;
	}

	private double getOverlap(HashSet<String> set1, HashSet<String> set2) {
		HashSet<String> intersection = new HashSet<>(set1);
		intersection.retainAll(set2);

		HashSet<String> union = new HashSet<>(set1);
		union.addAll(set2);

		if (union.isEmpty())
			return 1.0d;

		return intersection.size() / (double) union.size();
	}

	public Table<ElementBS, ElementBS, ElementBS> findEquivalencesLattice(HashMap<ElementBS, Integer> equivalences)
			throws IOException {
		Table<ElementBS, ElementBS, ElementBS> classElements = HashBasedTable.<ElementBS, ElementBS, ElementBS>create();

		for (LatticeBS lattice : latticeCollection) {
			for (Entry<ElementBS, Integer> entry : equivalences.entrySet())
				if (lattice.containsElement(entry.getKey()))
					classElements.putAll(lattice.getEdgeLatticeClassPrime(entry.getKey()));

//			List<LatticeBS> relabeledLattice = new LinkedList<LatticeBS>();
//			relabeledLattice.add(lattice);
//			Relabeler relabeler = new Relabeler(originalName);
//			XLog log = relabeler.createLog(classElements, equivalences, relabeledLattice, outputFolder, false,
//					"relabelLatEvt" + threshold + "-id" + latticeId);
//
//			ModelConverter converter = new ModelConverter();
//			converter.toModel(log, outputFolder, "-RelabeledCompleted-Lat-Id" + (latticeId++));
		}

		return classElements;
	}

	public void serializeLog(Table<ElementBS, ElementBS, ElementBS> classElements,
			HashMap<ElementBS, Integer> equivalences) throws IOException {
		// Write to file
		Relabeler relabeler = new Relabeler(originalName);
		HashMap<String, Integer> duplicates = null;

		if (dynamic) {
			duplicates = relabeler.relabel(classElements, equivalences, this.log, latticeCollection);
			relabeler.serializeLog(log, outputFolder, "relabelEvtDynamic");
		} else {
			duplicates = relabeler.relabel(classElements, equivalences, this.log, latticeCollection);
//			relabeler.createLog(latticeCollection, outputFolder, "relabelEvt" + String.format("%.1f", threshold), true);
			relabeler.serializeLog(log, outputFolder, "relabelEvt" + String.format("%.1f", threshold));
		}
	}

	public void serializeLog() throws IOException {
		// Write to file
		Relabeler relabeler = new Relabeler(originalName);

		if (dynamic) {
			relabeler.serializeLog(log, outputFolder, "relabelEvtDynamic");
		} else {
			relabeler.createLog(latticeCollection, outputFolder, "relabelEvt" + String.format("%.1f", threshold), true);
//			relabeler.serializeLog(log, outputFolder, "relabelEvt" + String.format("%.1f", threshold));
		}
	}

	public int serializeLogsProgressively(Table<ElementBS, ElementBS, ElementBS> classElements,
			HashMap<ElementBS, Integer> equivalences) {
		LinkedList<LatticeBS> orderedLattices = new LinkedList<>();
		orderedLattices.addAll(latticeCollection);

		Collections.sort(orderedLattices, new Comparator<LatticeBS>() {

			@Override
			public int compare(LatticeBS o1, LatticeBS o2) {
				return o2.getNumberOfXtraces() - o1.getNumberOfXtraces();
			}
		});

		// Write to file
		Relabeler relabeler = new Relabeler(originalName);
		HashMap<String, Integer> duplicates = null;

		List<LatticeBS> subCollection = new LinkedList<>();

		Iterator<LatticeBS> iterator = orderedLattices.iterator();
		while (iterator.hasNext()) {
			LatticeBS lat = iterator.next();

			if (lat.getNumberOfXtraces() < 100)
				break;

			subCollection.add(lat);

			System.out.println("Number of XTraces: " + lat.getNumberOfXtraces());
			System.out.println(lat.toDot());

			duplicates = relabeler.relabel(classElements, equivalences, this.log, latticeCollection);
			relabeler.createLog(subCollection, outputFolder, "relabelEvtDynamic" + subCollection.size(), false);
		}

		return subCollection.size();
	}

	public void compareTrees(FileWriter myWriter3D, Table<ElementBS, ElementBS, ElementBS> classElements,
			HashMap<ElementBS, Integer> equivalences) throws IOException {
		CostModel<ElementBS> cost = new CostModel<ElementBS>() {

			@Override
			public float del(Node<ElementBS> node) {
				return node.getNodeData().getPathNumber();
			}

			@Override
			public float ins(Node<ElementBS> node) {
				return node.getNodeData().getPathNumber();
			}

			@Override
			public float ren(Node<ElementBS> node1, Node<ElementBS> node2) {
				HashSet<String> node1Set = new HashSet<>();
				HashSet<String> node2Set = new HashSet<>();
				HashSet<String> union = new HashSet<>();

				for (String s : node1.getNodeData().getLabels()) {
					String name = s;
					if (originalName.containsKey(s))
						name = originalName.get(s);

					node1Set.add(name);
					union.add(name);
				}

				for (String s : node2.getNodeData().getLabels()) {
					String name = s;
					if (originalName.containsKey(s))
						name = originalName.get(s);

					node2Set.add(name);
					union.add(name);
				}

				node1Set.retainAll(node2Set);
//				int diff = Math.abs(node1.getNodeData().getPathNumber() - node2.getNodeData().getPathNumber());

//				return diff + (union.size() - node1Set.size());
				return union.size() - node1Set.size();
			}

		};

//		APTED<CostModel<ElementBS>, ElementBS> apted = new APTED<>(cost);
//		LinkedList<LatticeBS> indexes = new LinkedList<>();
//		double[][] matrix = new double[latticeCollection.size()][latticeCollection.size()];
//		String[] names = new String[latticeCollection.size()];
//
//		int k = 0;
//		for (LatticeBS lattice : latticeCollection) {
//			indexes.add(lattice);
//			names[k] = k + "";
//			k++;
//		}
//
//		for (int i = 0; i < indexes.size(); i++) {
//			Node<ElementBS> tree1 = indexes.get(i).getTree();
//			for (int j = i + 1; j < indexes.size(); j++) {
//				Node<ElementBS> tree2 = indexes.get(j).getTree();
//				matrix[indexes.indexOf(indexes.get(i))][indexes.indexOf(indexes.get(j))] = apted
//						.computeEditDistance(tree1, tree2);
//				List<int[]> map = apted.computeEditMapping();
//
//				myWriter3D.write(indexes.indexOf(indexes.get(i)) + "," + indexes.indexOf(indexes.get(j)) + ","
//						+ apted.computeEditDistance(tree1, tree2) + "\n");
//				myWriter3D.flush();
//			}
//		}

		// PRINT lattices and PES
//		int j = 0;
//		for (LatticeBS lattice : indexes) {
//			writeToFile(lattice.getPES().toDot(), outputFolder + "/pes" + "-" + j);
//			writeToFile(lattice.toDotNonDist(), outputFolder + "/lattice" + "-" + j);
//			j++;
//		}
		// END PRINT lattices and PES

//		ClusteringAlgorithm alg = new DefaultClusteringAlgorithm();
//		Cluster cluster = alg.performClustering(matrix, names, new AverageLinkageStrategy());
//		System.out.println("distance = " + cluster.getDistance());

		// Display tree
//		DendrogramPanel dp = new DendrogramPanel();
//		dp.setModel(cluster);
//
//		JFrame frame = new JFrame();
//		frame.add(dp);
//		frame.setVisible(true);
		// END display tree

//		modelsByLevels(cluster, indexes, classElements, equivalences);
//        modelsByGroups(cluster, indexes, 5);
	}

	private HashSet<LatticeBS> createGroup(Cluster cluster, LinkedList<LatticeBS> indexes) {
		HashSet<LatticeBS> groups = new HashSet<>();

		for (String s : cluster.getLeafNames()) {
			int index = Integer.parseInt(s);
			groups.add(indexes.get(index));
		}

		return groups;
	}

	private HashMultimap<Integer, LatticeBS> createGroups(Cluster cluster, LinkedList<LatticeBS> indexes, int number) {
		int limit = indexes.size() / number;
		HashMultimap<Integer, LatticeBS> groups = HashMultimap.<Integer, LatticeBS>create();

		Queue<Cluster> queue = new LinkedList<>();
		queue.add(cluster);

		int groupLabel = 0;
		while (!queue.isEmpty()) {
			Cluster current = queue.poll();

			boolean flag = false;
			for (Cluster c : current.getChildren()) {
				if (groups.get(groupLabel).size() + c.countLeafs() <= limit) {
					for (String s : c.getLeafNames()) {
						int index = Integer.parseInt(s);
						groups.get(groupLabel).add(indexes.get(index));
						flag = true;
					}
				} else
					queue.add(c);
			}

			if (!flag || groups.get(groupLabel).size() >= limit) {
				boolean added = false;
				if (current.getChildren().size() == 0 && current.countLeafs() > 0
						&& groups.get(groupLabel).size() + current.countLeafs() <= limit)
					for (String s : current.getLeafNames()) {
						int index = Integer.parseInt(s);
						groups.get(groupLabel).add(indexes.get(index));
						added = true;
					}
				else
					groupLabel++;

				if (!added && current.getChildren().size() == 0 && current.countLeafs() > 0)
					for (String s : current.getLeafNames()) {
						int index = Integer.parseInt(s);
						groups.get(groupLabel).add(indexes.get(index));
						added = true;
					}
			}
		}

		return groups;
	}

	public List<LatticeBS> getLatticeCollection() {
		return latticeCollection;
	}

	public void printPES() {
		int j = 0;
		for (LatticeBS lattice : latticeCollection)
			writeToFile(lattice.getPES().toDot(), outputFolder + "/pes" + "-" + (j++));
	}

	public void printPES(HashMap<ElementBS, Integer> equivalences) {
		int j = 0;
		for (LatticeBS lattice : latticeCollection) {
			lattice.consolidateLabels();
			for (Entry<ElementBS, Integer> entry : equivalences.entrySet())
				if (lattice.containsElement(entry.getKey())) {
					ElementBS root = entry.getKey().pre.getFirst();
					ElementBS prime = entry.getKey();

					BitSet labelIndex = (BitSet) prime.code.clone();
					labelIndex.andNot(root.code);

					int index = labelIndex.nextSetBit(0);
					String name = lattice.consolidatedLabels.get(index);

					if (originalName.containsKey(name))
						name = originalName.get(name);
					name += "_c" + equivalences.get(prime);

					lattice.consolidatedLabels.put(index, name);
				}

			lattice.computePES();
			writeToFile(lattice.getPES().toDot(), outputFolder + "/pes" + "-" + (j++));
		}
	}

	public double[] getMinMaxAvgVar() {
		double[] minMaxAvg = new double[3];

		int min = Integer.MAX_VALUE;
		int max = 0;
		int avg = 0;

		for (Trace trace : this.blueprint.getTraces()) {
			if (trace.getXTraces().size() < min)
				min = trace.getXTraces().size();

			if (trace.getXTraces().size() > max)
				max = trace.getXTraces().size();

			avg += trace.getXTraces().size();
		}

		minMaxAvg[0] = min;
		minMaxAvg[1] = max;
		minMaxAvg[2] = avg / this.blueprint.getTraces().size();

		return minMaxAvg;
	}

	public double[] getMinMaxAvgCompleteness() {
		double[] minMaxAvg = new double[3];

		double min = 100;
		double max = 0;
		double avg = 0;

		for (LatticeBS trace : this.latticeCollection) {
			if (trace.getCompletenessFactor() < min)
				min = trace.getCompletenessFactor();

			if (trace.getCompletenessFactor() > max)
				max = trace.getCompletenessFactor();

			avg += trace.getCompletenessFactor();
		}

		minMaxAvg[0] = min * 100.0d;
		minMaxAvg[1] = max * 100.0d;
		minMaxAvg[2] = (avg / this.latticeCollection.size()) * 100.0d;

		return minMaxAvg;
	}

	public void combineLattices() {
		Pair<HashMap<ElementBS, Integer>, HashMultimap<Integer, ElementBS>> eqs = findEquivalencesLattices();
		HashMultimap<Integer, ElementBS> reverse = eqs.getSecond();
		System.out.println(reverse);

		TransitionSystem ts = TransitionSystemFactory.newTransitionSystem("tAll");
		HashMap<ElementBS, State> mapStates = new HashMap<>();
		HashMap<ElementBS, ElementBS> mapElements = new HashMap<>();

		for (Integer key : reverse.keySet()) {
			ElementBS single = reverse.get(key).iterator().next();
			ts.addState(single);
			mapElements.put(single, single);

			for (ElementBS st : reverse.get(key))
				if (st != single) {
//					ts.addState(st);
					mapStates.put(st, ts.getNode(single));
					mapElements.put(st, single);
				}
		}

		ElementBS botRep = null;
		HashSet<ElementBS> finalStates = new HashSet<>();
		for (LatticeBS lattice : latticeCollection) {
			for (ElementBS e1 : lattice.getElements())
				for (ElementBS e2 : e1.post)
					ts.addTransition(mapElements.get(e1), mapElements.get(e2), lattice.getLabelBetween(e1, e2));

			if (botRep == null)
				botRep = mapElements.get(lattice.getBottom());

			finalStates.add(mapElements.get(lattice.getTop()));
		}

		StartStateSet starts = new StartStateSet();
		starts.add(botRep);

		AcceptStateSet accepts = new AcceptStateSet();
		accepts.addAll(finalStates);

		DirectedGraphElementWeights w = new DirectedGraphElementWeights();
		HashSet<ElementBS> setValues = new HashSet<>(mapElements.values());
		for (ElementBS e : setValues)
			w.put(e, 3);

		GraphLayoutConnection layout = new GraphLayoutConnection(ts);
		layout.expandAll();

		Tsml tsml = new Tsml().marshall(ts, starts, accepts, w, layout);
		String text = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n" + tsml.exportElement(tsml);

		try {
			BufferedWriter bw;
			bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File("tsFull.tsml"))));
			bw.write(text);
			bw.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public Pair<HashMap<ElementBS, Integer>, HashMultimap<Integer, ElementBS>> findEquivalencesLattices() {
		int equivCounter = 0;
		HashMap<ElementBS, Integer> equivalences = new HashMap<>();
		HashMultimap<Integer, ElementBS> equivalencesReverse = HashMultimap.<Integer, ElementBS>create();
		HashMultimap<BitSet, ElementBS> mapHelper = HashMultimap.<BitSet, ElementBS>create();

		for (LatticeBS lattice : this.latticeCollection) {
			Queue<ElementBS> queue = new LinkedList<>();
			queue.add(lattice.bottom);

			HashSet<ElementBS> visited = new HashSet<>();

			while (!queue.isEmpty()) {
				ElementBS p = queue.poll();
				if (visited.contains(p))
					continue;

				visited.add(p);

				if (!mapHelper.containsKey(p.code)) {
					mapHelper.put(p.code, p);
					equivalences.put(p, equivCounter++);
					equivalencesReverse.put(equivalences.get(p), p);

					queue.addAll(p.post);
				} else {
					Set<ElementBS> candidates = new HashSet<>(mapHelper.get(p.code));
					Set<ElementBS> candidatesFiltered = new HashSet<ElementBS>();

					for (ElementBS c : candidates) {
						boolean found = false;

						for (ElementBS c1 : candidatesFiltered)
							if (equivalences.get(c).intValue() == equivalences.get(c1).intValue())
								found = true;

						if (!found) {
							candidatesFiltered.add(c);

							mapHelper.put(p.code, p);
							if (latEquivalent(p, c, equivalences)) {
								equivalences.put(p, equivalences.get(c));
								equivalencesReverse.put(equivalences.get(c), p);							
							} else {
								equivalences.put(p, equivCounter++);
								equivalencesReverse.put(equivalences.get(p), p);
							}
							
							queue.addAll(p.post);
						}
					}
				}

			}
		}

		return new Pair<>(equivalences, equivalencesReverse);
	}

	private boolean latEquivalent(ElementBS p, ElementBS c, HashMap<ElementBS, Integer> equivalences) {
		for (ElementBS e1 : p.pre) {
			boolean found = false;

			for (ElementBS e2 : c.pre)
				if (e1.code.equals(e2.code))
					if (equivalences.get(e1).intValue() == equivalences.get(e2).intValue()) {
						found = true;
						break;
					}

			if (!found)
				return false;
		}

		return true;
	}
}
