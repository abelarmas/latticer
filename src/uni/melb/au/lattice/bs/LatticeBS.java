package uni.melb.au.lattice.bs;

import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;

import org.deckfour.xes.model.XEvent;
import org.processmining.framework.util.Pair;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

import at.unisalzburg.dbresearch.apted.node.Node;
import uni.melb.au.log.Trace;
import uni.melb.au.utils.PrimeEventStructure;

public class LatticeBS {
	LinkedList<String> labels;
	LinkedList<String> globalLabels;
	ElementBS bottom;
	ElementBS top;
	private LinkedList<ElementBS> elements;
	Table<ElementBS, ElementBS, ElementBS> table;
	Table<ElementBS, ElementBS, ElementBS> tableReverse;
	HashMap<ElementBS, Integer> pathsNode;
	HashMap<ElementBS, Integer> pathsNodeInverse;
	HashMap<ElementBS, Integer> linearization;
	HashMultimap<ElementBS, ElementBS> newConnections;
	HashMap<Integer, String> consolidatedLabels;

	Table<ElementBS, ElementBS, HashSet<XEvent>> tableEvents;

	HashMap<Integer, HashSet<BitSet>> bitsetSize;
	
	// PES and mapping
	private PrimeEventStructure<Integer> pes;
	HashBiMap<ElementBS, Integer> primeEvents;
	private double completenessFactor;
	
	HashMap<String, String> originalName = new HashMap<>();
	private Set<Trace> traces;

	public LatticeBS(Set<Trace> traceEquivalence, LinkedList<String> globalLabels) {
		this.tableEvents = HashBasedTable.<ElementBS, ElementBS, HashSet<XEvent>>create();
		this.globalLabels = globalLabels;
		this.newConnections = HashMultimap.<ElementBS, ElementBS>create();
		this.traces = traceEquivalence;
		
		labels = new LinkedList<>();
		for (Trace trace : traceEquivalence) {
			for (String name : trace.getListTrace())
				if (!labels.contains(name))
					labels.add(name);
		
			this.originalName.putAll(trace.getOriginalName());
		}

		Collections.sort(labels);
		initialize();

		for (Trace trace : traceEquivalence) {
			ElementBS last = bottom;
			for (int i = 0; i < trace.getListTrace().size(); i++) {
				String activity = trace.getListTrace().get(i);
				
				BitSet next = (BitSet) last.code.clone();

				if (globalLabels == null)
					next.set(labels.indexOf(activity));
				else
					next.set(globalLabels.indexOf(activity));

				ElementBS nextBS = findElement(next);
				if (!last.post.contains(nextBS)) {
					last.post.add(nextBS);
					nextBS.pre.add(last);
				}

				if (!tableEvents.contains(last, nextBS))
					tableEvents.put(last, nextBS, new HashSet<>());
				tableEvents.get(last, nextBS).addAll(trace.getXEvents(i));

				last = nextBS;
			}
		}

		for (ElementBS elem : elements) {
			if (elem.pre.size() > 1)
				elem.setNaturalJoin(true);

			if (elem.post.isEmpty())
				this.top = elem;
		}
		
//		createTree();
	}

	private ElementBS findElement(BitSet next) {
		for (ElementBS n : elements)
			if (n.code.equals(next))
				return n;

		ElementBS newElement = new ElementBS(next, this);
		this.elements.add(newElement);

		return newElement;
	}

	private void initialize() {
		this.bottom = new ElementBS(new BitSet(labels.size()), this);

//		BitSet t = new BitSet(labels.size());
//		t.flip(0, labels.size());
//		this.top = new ElementBS(t, this);

		this.elements = new LinkedList<>();
		this.elements.add(bottom);
//		this.elements.add(top);

		this.table = HashBasedTable.<ElementBS, ElementBS, ElementBS>create();
		this.tableReverse = HashBasedTable.<ElementBS, ElementBS, ElementBS>create();
	}

	public void lcaLcs() {
		boolean[][] tclosure = new boolean[elements.size()][elements.size()];
		HashBiMap<ElementBS, Integer> index = HashBiMap.<ElementBS, Integer>create();

		for (int i = 0; i < elements.size(); i++)
			index.put(elements.get(i), i);

		for (ElementBS e : elements)
			for (ElementBS f : elements)
				tclosure[index.get(e)][index.get(f)] = e.post.contains(f);

		for (int k = 0; k < elements.size(); k++)
			for (int i = 0; i < elements.size(); i++)
				for (int j = 0; j < elements.size(); j++)
					tclosure[i][j] = tclosure[i][j] || (tclosure[i][k] && tclosure[k][j]);

		for (ElementBS e : elements)
			for (ElementBS f : elements) {
				if (e == f) {
					table.put(e, e, e);

					tableReverse.put(e, e, e);
				} else if (tclosure[index.get(e)][index.get(f)]) {
					table.put(e, f, e);
					table.put(f, e, e);

					tableReverse.put(e, f, f);
					tableReverse.put(f, e, f);
				} else if (tclosure[index.get(f)][index.get(e)]) {
					table.put(e, f, f);
					table.put(f, e, f);

					tableReverse.put(e, f, e);
					tableReverse.put(f, e, e);
				} else {
					ElementBS lca = null;
					ElementBS lcs = null;

					for (ElementBS g : elements) {
						if (tclosure[index.get(g)][index.get(e)] && tclosure[index.get(g)][index.get(f)]) {
							if (lca == null)
								lca = g;
							else if (tclosure[index.get(lca)][index.get(g)])
								lca = g;
						}

						if (tclosure[index.get(e)][index.get(g)] && tclosure[index.get(f)][index.get(g)]) {
							if (lcs == null)
								lcs = g;
							else if (tclosure[index.get(g)][index.get(lcs)])
								lcs = g;
						}
					}

					table.put(e, f, lca);
					table.put(f, e, lca);

					tableReverse.put(e, f, lcs);
					tableReverse.put(f, e, lcs);
				}
			}

//		for(Cell<ElementBS, ElementBS, ElementBS> cell : table.cellSet()) 
//			System.out.println("LCA of " + cell.getRowKey() + " and " + cell.getColumnKey() + " is " + cell.getValue());
//		
//		for(Cell<ElementBS, ElementBS, ElementBS> cell : tableReverse.cellSet()) 
//			System.out.println("LHS of " + cell.getRowKey() + " and " + cell.getColumnKey() + " is " + cell.getValue());
	}

	public Set<Trace> getTraces() {
		return this.traces;
	}
	
	public void lcaTree(ElementBS node) {
		makeSet(node);
		node.ancestor = node;

		for (ElementBS child : node.post) {
			lcaTree(child);
			union(node, child);
			find(node).ancestor = node;
		}

		node.flag = true;

		for (ElementBS v : elements)
			if (v.flag) {
				table.put(node, v, find(v).ancestor);

				if (v != node)
					table.put(v, node, find(v).ancestor);

				System.out.println("Tarjan's Lowest Common Ancestor of {" + node.code + ", " + v.code + "}: "
						+ find(v).ancestor.code);
			}
	}

	public void union(ElementBS x, ElementBS y) {
		ElementBS xRoot = find(x);
		ElementBS yRoot = find(y);

		if (xRoot.rank > yRoot.rank)
			yRoot.parent = xRoot;
		else if (xRoot.rank < yRoot.rank)
			xRoot.parent = yRoot;
		else if (xRoot.rank == yRoot.rank) {
			yRoot.parent = xRoot;
			xRoot.rank = xRoot.rank++;
		}
	}

	public ElementBS find(ElementBS x) {
		if (x.parent != x)
			x.parent = find(x.parent);

		return x.parent;
	}

	private void makeSet(ElementBS node) {
		node.parent = node;
		node.rank = 1;
	}

	public boolean isDistributive() {
		if(table.isEmpty() || tableReverse.isEmpty())
			lcaLcs();
		
		for (ElementBS x : elements) {
			for (ElementBS y : elements) {
				if (x != y) {
					for (ElementBS z : elements) {
						if (z != y && z != x) {

							// Distributive rule Y
							ElementBS yJz = tableReverse.get(y, z);
							ElementBS xMyJz = table.get(x, yJz);

							ElementBS xMy = table.get(x, y);
							ElementBS zMx = table.get(z, x);
							ElementBS xMyJxMz = tableReverse.get(xMy, zMx);

							if (xMyJz != xMyJxMz) {
								System.out.println("It is not distributive! x= " + x + " - y= " + y + "- z= " + z);
								return false;
							}
						}
					}
				}
			}
		}

		return true;
	}

	public void completeBlueprint(LatticeNonDistMakerBS blueprint) {
		boolean altered = true;
		
		createConnections();

		while (altered) {
			LinkedList<ElementBS> newToAdd = new LinkedList<>();

			for (ElementBS e : this.elements) {
				for (ElementBS orig : blueprint.lattice.elements) {
					if (e.code.equals(orig.code) && e.pre.size() < orig.pre.size())
						for (ElementBS predOrig : orig.pre) {
							boolean found = false;

							for (ElementBS predE : e.pre)
								if (predE.code.equals(predOrig.code))
									found = true;

							if (!found) {
								ElementBS newElement = new ElementBS(predOrig.code, this);
								newElement.setSynthetic(true);
								newToAdd.add(newElement);
								break;
							}
						}

					if (!newToAdd.isEmpty())
						break;
				}

				if (!newToAdd.isEmpty())
					break;
			}

			elements.addAll(newToAdd);
			createConnections();

			if (newToAdd.isEmpty())
				altered = false;
		}
	}

	public void complete() {
		LinkedList<ElementBS> elems2Check = new LinkedList<>(elements);
		this.bitsetSize = new HashMap<>();
		for (ElementBS e : this.elements) {
			if(!bitsetSize.containsKey(e.code.cardinality()))
				bitsetSize.put(e.code.cardinality(), new HashSet<>());
			bitsetSize.get(e.code.cardinality()).add(e.code);
		}
		
		while (!elems2Check.isEmpty()) {
			LinkedList<ElementBS> newToAdd = new LinkedList<>();
			
			for (ElementBS x : elements) {
				for (ElementBS y : elems2Check) {
					if(x == y)
						continue; 
					
					BitSet union = (BitSet) x.code.clone();
					union.or(y.code);

					BitSet intersection = (BitSet) x.code.clone();
					intersection.and(y.code);

					boolean foundU = false;
					boolean foundI = false;
					
//					for (ElementBS n : elements) {
//						if (n.code.equals(intersection))
//							foundI = true;
//
//						if (n.code.equals(union))
//							foundU = true;
//					}
					
					foundI = bitsetSize.get(intersection.cardinality()).contains(intersection);
					foundU = bitsetSize.get(union.cardinality()).contains(union);

					for (ElementBS n : newToAdd) {
						if (n.code.equals(intersection))
							foundI = true;

						if (n.code.equals(union))
							foundU = true;
					}

					if (!foundU) {
						ElementBS newElement = new ElementBS(union, this);
						newElement.setSynthetic(true);
						newToAdd.add(newElement);
						bitsetSize.get(union.cardinality()).add(union);
						
//						System.out.println(" ********** Union: " + newElement);
//						printAttributes(x);
//						printAttributes(y);
					}

					if (!foundI) {
						ElementBS newElement = new ElementBS(intersection, this);
						newElement.setSynthetic(true);
						newToAdd.add(newElement);
						bitsetSize.get(intersection.cardinality()).add(intersection);

//						System.out.println(" ********** Intersection: " + newElement);
//						printAttributes(x);
//						printAttributes(y);
					}
				}
			}

			elements.addAll(newToAdd);
			elems2Check= new LinkedList<>(newToAdd);
		}

		createConnections();
	}

//	private void printAttributes(ElementBS x) {
//		x.computeEvtAtt();
//
//		System.out.println(" ------- " + x + " ------- ");
//		System.out.println("numerical = " + x.getNumericalAtt());
//		System.out.println("time = " + x.getTimeAtt());
//		System.out.println("string = " + x.getStringAtt());
//		System.out.println(" ------------------------- ");
//
//	}

	private Table<ElementBS, ElementBS, Integer> createTree() {
		ElementBS[] extension = getLinearExtension();
		BitSet[] codes = new BitSet[extension.length];

		for (int i = 0; i < extension.length; i++) {
			ElementBS e = extension[i];
			if (e.post.size() > 0) {
				codes[i] = (BitSet) e.post.getFirst().code.clone();
				codes[i].andNot(e.code);
			}
		}

//		LinkedList<ElementBS> marks = new LinkedList<>();
		Table<ElementBS, ElementBS, Integer> edges = HashBasedTable.<ElementBS, ElementBS, Integer>create();

		Queue<ElementBS> queue = new LinkedList<>();
		queue.add(top);

		while (!queue.isEmpty()) {
			ElementBS current = queue.poll();
			if (current.post.isEmpty())
				for (ElementBS p : current.pre) {
					edges.put(p, current, getValue(codes, p, current));
					queue.add(p);
				}
			else {
				Integer v = edges.row(current).values().iterator().next();

				for (ElementBS p : current.pre) {
					Integer descendant = getValue(codes, p, current);

					if (v > descendant) {
						edges.put(p, current, descendant);
						queue.add(p);
					}
				}
			}
		}

//		for (int i = 0; i < extension.length; i++) {
//			ElementBS current = extension[i];
//			HashMap<String, ElementBS> map = new HashMap<>();
//
//			for (ElementBS next : current.pre)
//				if (!marks.contains(next))
//					map.put(getLabelBetween(current, next), next);
//
//			LinkedList<String> ordered = new LinkedList<>(map.keySet());
//			Collections.sort(ordered);
//			ElementBS[] orderedElements = new ElementBS[ordered.size()];
//
//			for (int j = 0; j < orderedElements.length; j++)
//				orderedElements[j] = map.get(ordered.get(j));
//
//			for (int k = 0; k < orderedElements.length; k++)
//				if (!marks.contains(orderedElements[k])) {
//					edges.put(current, orderedElements[k], false);
//					edges.put(orderedElements[k], current, false);
//					marks.add(orderedElements[k]);
//				}
//		}

		return edges;
	}

	private Integer getValue(BitSet[] codes, ElementBS p, ElementBS next) {
		BitSet b = (BitSet) next.code.clone();
		b.andNot(p.code);

		for (int i = 0; i < codes.length; i++)
			if (codes[i] != null && codes[i].equals(b))
				return i;

		return -1;
	}

	private ElementBS[] getLinearExtension() {
		this.linearization = new HashMap<>();

		LinkedList<ElementBS> meetIrreducibles = new LinkedList<>();
		HashBiMap<ElementBS, String> map = HashBiMap.<ElementBS, String>create();

		for (ElementBS e : elements)
			if (e.post.size() <= 1) {
				meetIrreducibles.add(e);
				map.put(e, getLabelOccurrence(e));
			}

		LinkedList<Entry<ElementBS, String>> sorted = new LinkedList<>(map.entrySet());
		Collections.sort(sorted, new Comparator<Entry<ElementBS, String>>() {

			@Override
			public int compare(Entry<ElementBS, String> o1, Entry<ElementBS, String> o2) {
				if (o1.getKey().code.cardinality() == o2.getKey().code.cardinality())
					return o1.getValue().compareTo(o2.getValue());
				return o1.getKey().code.cardinality() - o2.getKey().code.cardinality();
			}
		});

		ElementBS[] orderedElem = new ElementBS[sorted.size()];

		int i = 0;
		for (Entry<ElementBS, String> entry : sorted) {
			orderedElem[i] = entry.getKey();
			linearization.put(orderedElem[i], i++);
		}

		return orderedElem;
	}

	private String getLabelOccurrence(ElementBS e) {
		if (!e.post.isEmpty())
			return getUniqueLabelBetween(e, e.post.getFirst());

		return "";
	}

	private void createConnections() {
		for (ElementBS m : elements) {
			for (ElementBS n : elements) {
				BitSet diff = (BitSet) n.code.clone();
				diff.andNot(m.code);

				if (n.code.cardinality() == m.code.cardinality() + 1 && !m.post.contains(n)
						&& diff.cardinality() == 1) {
					m.post.add(n);
					n.pre.add(m);
					newConnections.put(m, n);
				}
			}
		}
	}

//	private void connect(ElementBS newElement) {
//		for (ElementBS n : elements) {
//			if (n.code.cardinality() == newElement.code.cardinality() + 1) {
//				newElement.post.add(n);
//				n.pre.add(newElement);
//			}
//
//			if (n.code.cardinality() + 1 == newElement.code.cardinality()) {
//				newElement.pre.add(n);
//				n.post.add(newElement);
//			}
//		}
//	}

	public String toDot() {
		HashMap<ElementBS, Integer> map = new HashMap<>();
		for (ElementBS node : elements)
			map.put(node, map.size());

		StringBuilder b = new StringBuilder();
		b.append("digraph BP {\n");

		// standard style for nodes and edges
		b.append("graph [fontname=\"Helvetica\" nodesep=0.3 ranksep=\"0.2 equally\" fontsize=10];\n");
		b.append("node [fontname=\"Helvetica\" fontsize=8 ];\n");
		b.append("edge [fontname=\"Helvetica\" fontsize=8 color=white arrowhead=none weight=\"20.0\"];\n\n");

		// first print all conditions
		b.append("node [shape=circle];\n");

		for (ElementBS n : elements) {
			if (n.isSynthetic())
				b.append("  c" + map.get(n) + " [color=yellow, style=filled]\n");
			else
				b.append("  c" + map.get(n) + " []\n");

			String nodeLabel = n.toString();

			b.append("  c" + map.get(n) + "_l [shape=none];\n");
			b.append("  c" + map.get(n) + "_l -> c" + map.get(n) + " [headlabel=\"" + nodeLabel + "-"
					+ (pathsNode.get(n) * pathsNodeInverse.get(n)) + "\"]\n");
		}

		// finally, print all edges
		b.append("\n\n");
		b.append(" edge [fontname=\"Helvetica\" fontsize=8 arrowhead=normal color=black];\n");
		for (ElementBS n : elements)
			for (ElementBS next : n.post) {
				if (isNewPath(n, next))
					b.append("  c" + map.get(n) + " -> c" + map.get(next) + "[label=\"" + getLabelBetween(n, next)
							+ "\",color=yellow]\n");
				else
					b.append("  c" + map.get(n) + " -> c" + map.get(next) + "[label=\"" + getLabelBetween(n, next)
							+ "\"]\n");
			}

		b.append("}");
		return b.toString();
	}

	public String toDotNonDist() {
		HashMap<ElementBS, Integer> map = new HashMap<>();
		for (ElementBS node : elements)
			map.put(node, map.size());

		StringBuilder b = new StringBuilder();
		b.append("digraph BP {\n");

		// standard style for nodes and edges
		b.append("graph [fontname=\"Helvetica\" nodesep=0.3 ranksep=\"0.2 equally\" fontsize=10];\n");
		b.append("node [fontname=\"Helvetica\" fontsize=8 ];\n");
		b.append("edge [fontname=\"Helvetica\" fontsize=8 color=white arrowhead=none weight=\"20.0\"];\n\n");

		// first print all conditions
		b.append("node [shape=circle];\n");

		for (ElementBS n : elements) {
			if (n.isSynthetic())
				b.append("  c" + map.get(n) + " [color=yellow, style=filled]\n");
			else
				b.append("  c" + map.get(n) + " []\n");

			String nodeLabel = n.toString();

			b.append("  c" + map.get(n) + "_l [shape=none];\n");
			b.append("  c" + map.get(n) + "_l -> c" + map.get(n) + " [headlabel=\"" + nodeLabel + "\"]\n");
		}

		// finally, print all edges
		b.append("\n\n");
		b.append(" edge [fontname=\"Helvetica\" fontsize=8 arrowhead=normal color=black];\n");
		for (ElementBS n : elements)
			for (ElementBS next : n.post) {
				if (isNewPath(n, next))
					b.append("  c" + map.get(n) + " -> c" + map.get(next) + "[label=\"" + getLabelBetween(n, next)
							+ "\",color=yellow]\n");
				else
					b.append("  c" + map.get(n) + " -> c" + map.get(next) + "[label=\"" + getLabelBetween(n, next)
							+ "\"]\n");
			}

		b.append("}");
		return b.toString();
	}

	private String toDotTree(Table<ElementBS, ElementBS, Integer> edges) {
		HashMap<ElementBS, Integer> map = new HashMap<>();
		for (ElementBS node : elements)
			map.put(node, map.size());

		StringBuilder b = new StringBuilder();
		b.append("digraph BP {\n");

		// standard style for nodes and edges
		b.append("graph [fontname=\"Helvetica\" nodesep=0.3 ranksep=\"0.2 equally\" fontsize=10];\n");
		b.append("node [fontname=\"Helvetica\" fontsize=8 ];\n");
		b.append("edge [fontname=\"Helvetica\" fontsize=8 color=white arrowhead=none weight=\"20.0\"];\n\n");

		// first print all conditions
		b.append("node [shape=circle];\n");

		for (ElementBS n : elements) {
			if (n.isSynthetic())
				b.append("  c" + map.get(n) + " [color=yellow, style=filled]\n");
			else
				b.append("  c" + map.get(n) + " []\n");

			String nodeLabel = n.toString();

			b.append("  c" + map.get(n) + "_l [shape=none];\n");
			b.append("  c" + map.get(n) + "_l -> c" + map.get(n) + " [headlabel=\"" + nodeLabel + "\"]\n");
		}

		// finally, print all edges
		b.append("\n\n");
		b.append(" edge [fontname=\"Helvetica\" fontsize=8 arrowhead=normal color=black];\n");
		for (ElementBS n : elements)
			for (ElementBS next : n.post)
				if (edges.contains(n, next))
					b.append("  c" + map.get(n) + " -> c" + map.get(next) + "[label=\"" + getLabelBetween(n, next)
							+ "\"]\n");

		b.append("}");
		return b.toString();
	}
	
	public int getNumberOfXtraces() {
		int sum = 0;
		
		for(Trace t : traces)
			sum += t.getXTraces().size();
		
		return sum;
	}
	
	public int getSize() {
		return this.elements.size();
	}

	public double countPathsDP() {
		HashMap<ElementBS, Integer> parents = new HashMap<>();

		for (ElementBS elem : this.elements)
			parents.put(elem, elem.pre.size());

		Queue<ElementBS> queue = new LinkedList<ElementBS>();
		queue.add(bottom);

		LinkedList<ElementBS> topOrdered = new LinkedList<>();

		while (!queue.isEmpty()) {
			ElementBS u = queue.poll();

			topOrdered.add(u);

			for (ElementBS e : u.post) {
				parents.put(e, parents.get(e) - 1);

				if (parents.get(e) == 0)
					queue.add(e);
			}
		}

		this.pathsNode = new HashMap<>();
		pathsNode.put(this.top, 1);

		for (int i = topOrdered.size() - 1; i >= 0; i--)
			for (ElementBS e : topOrdered.get(i).post) {
				if (!pathsNode.containsKey(topOrdered.get(i)))
					pathsNode.put(topOrdered.get(i), pathsNode.get(e));
				else
					pathsNode.put(topOrdered.get(i), pathsNode.get(topOrdered.get(i)) + pathsNode.get(e));
			}

		if(pathsNode.get(bottom) == null) {
			System.out.println(this.toDotNonDist());
		}
		
		return pathsNode.get(bottom);
	}

	public double countPathsDPInverse() {
		HashMap<ElementBS, Integer> parents = new HashMap<>();

		for (ElementBS elem : this.elements)
			parents.put(elem, elem.post.size());

		Queue<ElementBS> queue = new LinkedList<ElementBS>();
		queue.add(top);

		LinkedList<ElementBS> topOrdered = new LinkedList<>();

		while (!queue.isEmpty()) {
			ElementBS u = queue.poll();

			topOrdered.add(u);

			for (ElementBS e : u.pre) {
				parents.put(e, parents.get(e) - 1);

				if (parents.get(e) == 0)
					queue.add(e);
			}
		}

		this.pathsNodeInverse = new HashMap<>();
		pathsNodeInverse.put(this.bottom, 1);

		for (int i = topOrdered.size() - 1; i >= 0; i--)
			for (ElementBS e : topOrdered.get(i).pre) {
				if (!pathsNodeInverse.containsKey(topOrdered.get(i)))
					pathsNodeInverse.put(topOrdered.get(i), pathsNodeInverse.get(e));
				else
					pathsNodeInverse.put(topOrdered.get(i),
							pathsNodeInverse.get(topOrdered.get(i)) + pathsNodeInverse.get(e));
			}

		return pathsNodeInverse.get(top);
	}

	public double countPaths(ElementBS source, ElementBS target, double pathCount) {
		if (source == target)
			pathCount++;
		else
			for (ElementBS next : source.post)
				pathCount = countPaths(next, target, pathCount);

		return pathCount;
	}

	public ElementBS getBottom() {
		return this.bottom;
	}

	public String getLabel(int index) {
		if (globalLabels == null)
			return labels.get(index);

		return globalLabels.get(index);
	}

	public String getLabelBetween(ElementBS current, ElementBS next) {
		BitSet cloneSynth = (BitSet) next.code.clone();
		cloneSynth.andNot(current.code);

		if (consolidatedLabels != null && consolidatedLabels.containsKey(cloneSynth.nextSetBit(0)))
			return consolidatedLabels.get(cloneSynth.nextSetBit(0));

		if (globalLabels != null)
			return globalLabels.get(cloneSynth.nextSetBit(0));

		if(labels.get(cloneSynth.nextSetBit(0)) == null)
			System.out.println("Error");
		
		return labels.get(cloneSynth.nextSetBit(0));
	}

	public String getUniqueLabelBetween(ElementBS current, ElementBS next) {
		BitSet cloneSynth = (BitSet) current.code.clone();
		cloneSynth.xor(next.code);

		if (globalLabels != null)
			return globalLabels.get(cloneSynth.nextSetBit(0));

		return labels.get(cloneSynth.nextSetBit(0));
	}

	public LinkedList<ElementBS> getNodesPerPathLim(double pathLim) {
		if (this.pathsNodeInverse == null)
			countPathsDPInverse();

		LinkedList<ElementBS> nodes = new LinkedList<>();
		for (ElementBS node : elements)
			if (node.isSynthetic())
				if (this.pathsNodeInverse.get(node) >= pathLim)
					nodes.add(node);

		return nodes;
	}

	public LinkedList<ElementBS> getLargestPathNode() {
		if (this.pathsNodeInverse == null)
			countPathsDPInverse();

		if (this.pathsNode == null)
			countPathsDP();

		ElementBS largest = null;
		int number = 0;
		LinkedList<ElementBS> nodes = new LinkedList<>();
		for (ElementBS node : elements)
			if (node.isSynthetic())
				if ((this.pathsNode.get(node) * this.pathsNodeInverse.get(node)) > number) {
					largest = node;
					number = this.pathsNodeInverse.get(node);
				}

		if (largest == null) {
			return nodes;
		}

		nodes.add(largest);
		return nodes;
	}

	public LinkedList<ElementBS> getSmallestPathNode() {
		if (this.pathsNodeInverse == null)
			countPathsDPInverse();

		if (this.pathsNode == null)
			countPathsDP();

		ElementBS smallest = null;
		int number = Integer.MAX_VALUE;
		LinkedList<ElementBS> nodes = new LinkedList<>();
		for (ElementBS node : elements)
			if (node.isSynthetic())
				if ((this.pathsNode.get(node) * this.pathsNodeInverse.get(node)) < number) {
					smallest = node;
					number = this.pathsNodeInverse.get(node);
				}

		if (smallest == null)
			return nodes;

		nodes.add(smallest);
		return nodes;
	}

	public LinkedList<ElementBS> divideUpperJoin() {
		LinkedList<ElementBS> nodes = new LinkedList<>();

		ElementBS toppest = null;
		int size = 0;

		for (ElementBS node : elements) {
			if (node.isNaturalJoin() && !node.isSynthetic() && node.code.cardinality() > size) {
				toppest = node;
				size = node.code.cardinality();
			}
		}

		nodes.add(toppest);
		return nodes;
	}

	public LinkedList<ElementBS> getLCAs(LinkedList<ElementBS> selectNodes2Split) {
		LinkedList<ElementBS> nodes = new LinkedList<>();

		for (ElementBS synth : selectNodes2Split)
			for (ElementBS node : elements) {
				BitSet cloneSynth = (BitSet) synth.code.clone();
				cloneSynth.and(node.code);
				if (cloneSynth.equals(synth.code) && node.isNaturalJoin() && !node.isSynthetic() && node.pre.size() > 1)
					nodes.add(node);
			}

		return nodes;
	}

	public LinkedList<String> getLabels(ElementBS lca) {
		LinkedList<String> labelsTrace = new LinkedList<>();

		for (int i = lca.code.nextSetBit(0); i >= 0; i = lca.code.nextSetBit(i + 1))
			if (globalLabels == null)
				labelsTrace.add(this.labels.get(i));
			else
				labelsTrace.add(this.globalLabels.get(i));

		return labelsTrace;
	}

	public boolean containsSynthetic() {
		for (ElementBS e : elements)
			if (e.isSynthetic())
				return true;

		return false;
	}

	public boolean containsNewPaths() {
		return !this.newConnections.isEmpty();
	}

	public boolean isNewPath(ElementBS one, ElementBS two) {
		if (this.newConnections == null)
			return false;
		return this.newConnections.containsEntry(one, two);
	}

	public Set<LinkedList<ElementBS>> getComplement() {
		HashMap<ElementBS, Integer> parents = new HashMap<>();

		for (ElementBS elem : this.elements)
			parents.put(elem, elem.pre.size());

		Queue<ElementBS> queue = new LinkedList<ElementBS>();
		queue.add(bottom);

		LinkedList<ElementBS> topOrdered = new LinkedList<>();

		while (!queue.isEmpty()) {
			ElementBS u = queue.poll();

			topOrdered.add(u);

			for (ElementBS e : u.post) {
				parents.put(e, parents.get(e) - 1);

				if (parents.get(e) == 0)
					queue.add(e);
			}
		}

		this.pathsNode = new HashMap<>();
		pathsNode.put(this.top, 1);

		HashMultimap<ElementBS, LinkedList<ElementBS>> tracesPaths = HashMultimap
				.<ElementBS, LinkedList<ElementBS>>create();
		LinkedList<ElementBS> topSingleton = new LinkedList<>();
		topSingleton.add(this.top);
		tracesPaths.put(this.top, topSingleton);

		for (int i = topOrdered.size() - 1; i >= 0; i--)
			for (ElementBS e : topOrdered.get(i).post) {
				if (!pathsNode.containsKey(topOrdered.get(i)))
					pathsNode.put(topOrdered.get(i), pathsNode.get(e));
				else
					pathsNode.put(topOrdered.get(i), pathsNode.get(topOrdered.get(i)) + pathsNode.get(e));

				for (LinkedList<ElementBS> path : tracesPaths.get(e)) {
					LinkedList<ElementBS> newPath = new LinkedList<>(path);
					newPath.add(0, topOrdered.get(i));
					tracesPaths.put(topOrdered.get(i), newPath);
				}
			}

		return tracesPaths.get(bottom);

	}

	public boolean isJoinOfPrimes(ElementBS elementBS) {
		for (ElementBS e2 : elements) {
			BitSet clone = (BitSet) elementBS.code.clone();
			clone.or(e2.code);

			if (elementBS != e2 && !(clone.equals(elementBS.code) || clone.equals(e2.code)))
				return false;
		}

		return true;
	}

	public HashSet<Pair<ElementBS, ElementBS>> findsGraphs(String label, HashMap<String, String> originalName) {
		HashSet<Pair<ElementBS, ElementBS>> sg = new HashSet<>();

		Queue<ElementBS> queue = new LinkedList<>();
		queue.add(bottom);

		for (ElementBS e : elements) {
			for (ElementBS f : e.post) {
				String newName = getLabelBetween(e, f);
				if (originalName.containsKey(newName))
					newName = originalName.get(newName);

				if (newName.equals(label))
					sg.add(new Pair<ElementBS, ElementBS>(e, f));
			}
		}

		return sg;
	}

	public String toDotSG(HashSet<Pair<ElementBS, ElementBS>> subgraph) {
		HashSet<ElementBS> nodesSG = new HashSet<>();
		for (Pair<ElementBS, ElementBS> pair : subgraph) {
			nodesSG.add(pair.getFirst());
			nodesSG.add(pair.getSecond());
		}

		HashMap<ElementBS, Integer> map = new HashMap<>();
		for (ElementBS node : nodesSG)
			map.put(node, map.size());

		StringBuilder b = new StringBuilder();
		b.append("digraph BP {\n");

		// standard style for nodes and edges
		b.append("graph [fontname=\"Helvetica\" nodesep=0.3 ranksep=\"0.2 equally\" fontsize=10];\n");
		b.append("node [fontname=\"Helvetica\" fontsize=8 ];\n");
		b.append("edge [fontname=\"Helvetica\" fontsize=8 color=white arrowhead=none weight=\"20.0\"];\n\n");

		// first print all conditions
		b.append("node [shape=circle];\n");

		for (ElementBS n : nodesSG) {
			if (n.isSynthetic())
				b.append("  c" + map.get(n) + " [color=yellow, style=filled]\n");
			else
				b.append("  c" + map.get(n) + " []\n");

			String nodeLabel = n.toString();

			b.append("  c" + map.get(n) + "_l [shape=none];\n");
			b.append("  c" + map.get(n) + "_l -> c" + map.get(n) + " [headlabel=\"" + nodeLabel + "\"]\n");
		}

		// finally, print all edges
		b.append("\n\n");
		b.append(" edge [fontname=\"Helvetica\" fontsize=8 arrowhead=normal color=black];\n");
		for (ElementBS n : nodesSG)
			for (ElementBS next : n.post) {
				if (!nodesSG.contains(next))
					continue;

				if (isNewPath(n, next))
					b.append("  c" + map.get(n) + " -> c" + map.get(next) + "[label=\"" + getLabelBetween(n, next)
							+ "\",color=yellow]\n");
				else
					b.append("  c" + map.get(n) + " -> c" + map.get(next) + "[label=\"" + getLabelBetween(n, next)
							+ "\"]\n");
			}

		b.append("}");

		return b.toString();
	}

	public void computePES() {
		LinkedList<ElementBS> primes = new LinkedList<>();
		this.primeEvents = HashBiMap.<ElementBS, Integer>create();
		for (ElementBS e : elements)
			if (e.pre.size() == 1) {
				primeEvents.put(e, primes.size());
				primes.add(e);
			}

		List<String> labels = new LinkedList<>();
		List<Integer> sources = new LinkedList<>();
		for (ElementBS e : bottom.post)
			sources.add(primes.indexOf(e));

		BitSet[] causality = new BitSet[primes.size()];
		BitSet[] dcausality = new BitSet[primes.size()];
		for (int i = 0; i < primes.size(); i++) {
			causality[i] = new BitSet();
			dcausality[i] = new BitSet();
		}

		Multimap<Integer, Integer> conc = HashMultimap.<Integer, Integer>create();
		for (ElementBS e : primes)
			for (ElementBS f : primes)
				if (e != f && isLessThan(e, f)) {
					causality[primes.indexOf(e)].set(primes.indexOf(f));
					dcausality[primes.indexOf(e)].set(primes.indexOf(f));
				} else if (!isLessThan(f, e))
					conc.put(primes.indexOf(e), primes.indexOf(f));

		// Add labels
		for (ElementBS e : primes)
			labels.add(getLabelBetween(e.pre.getFirst(), e));

		// Transitive reductions
		for (int j = 0; j < dcausality.length; j++)
			for (int i = 0; i < dcausality.length; i++)
				if (dcausality[i].get(j))
					for (int k = 0; k < dcausality.length; k++)
						if (dcausality[j].get(k))
							dcausality[i].set(k, false);

		BitSet[] invcausality = new BitSet[primes.size()];
		BitSet[] concurrency = new BitSet[primes.size()];
		BitSet[] conflict = new BitSet[primes.size()];

		for (int i = 0; i < primes.size(); i++) {
			invcausality[i] = new BitSet();
			concurrency[i] = new BitSet();
			conflict[i] = new BitSet();
		}

		for (Entry<Integer, Integer> entry : conc.entries())
			concurrency[entry.getKey()].set(entry.getValue());

		for (int i = 0; i < primes.size(); i++)
			for (int j = causality[i].nextSetBit(0); j >= 0; j = causality[i].nextSetBit(j + 1))
				invcausality[j].set(i);

		for (int i = 0; i < primes.size(); i++) {
			BitSet union = (BitSet) causality[i].clone();
			union.or(invcausality[i]);
			union.or(concurrency[i]);
			union.set(i); // Remove IDENTITY
			conflict[i].flip(0, primes.size());
			conflict[i].xor(union);
		}

		List<Integer> sinks = new LinkedList<>();
		for (int i = 0; i < causality.length; i++)
			if (causality[i].cardinality() == 0)
				sinks.add(i);

		System.out.println("Creating object!");

		this.pes = new PrimeEventStructure<Integer>(labels, causality, dcausality, invcausality, concurrency, conflict,
				sources, sinks);

		System.out.println("Set object!");
	}

	private LinkedList<ElementBS> getPrimes() {
		LinkedList<ElementBS> primes = new LinkedList<>();

		for (ElementBS e : elements)
			if (e.pre.size() == 1)
				primes.add(e);

		return primes;
	}

	private boolean isLessThan(ElementBS e, ElementBS f) {
		BitSet copy = (BitSet) f.code.clone();
		copy.and(e.code);

		return copy.equals(e.code);
	}

	public LinkedList<String> getChain(String label) {
		LinkedList<String> chain = new LinkedList<>();
		LinkedList<String> visited = new LinkedList<>();

		ElementBS current = bottom;
		ElementBS previous = null;

		while (current != null) {
			if (previous != null) {
				String labelBtw = getLabelBetween(previous, current);

				if (label.equals(labelBtw)) {
					chain.addAll(visited);
					visited = new LinkedList<>();
				} else
					visited.add(labelBtw);
			}

			previous = current;

			if (current.post.isEmpty())
				current = null;
			else
				current = current.post.getFirst();
		}

		return chain;
	}

	public LinkedList<ElementBS> getPrimes(String label, HashMap<String, String> originalName) {
		LinkedList<ElementBS> primes = new LinkedList<>();
		for (ElementBS e : this.primeEvents.keySet()) {
			String event = getLabelBetween(e.pre.getFirst(), e);
			
			if (originalName.containsKey(event))
				event = originalName.get(event);

			if(event == null)
				System.out.println("Error");
			
			if (event.equals(label))
				primes.add(e);
		}

		return primes;
	}

	public HashSet<String> getPostLabels(ElementBS node, HashMap<String, String> originalName) {
		HashSet<String> conc = new HashSet<>();

		for (ElementBS next : node.post) {
			String name = getLabelBetween(node, next);

			if (originalName.containsKey(name))
				name = originalName.get(name);

			conc.add(name);
		}

		return conc;
	}

	public boolean containsElement(ElementBS element) {
		return this.elements.contains(element);
	}

	public HashSet<XEvent> getEdgeEventClassPrime(ElementBS key) {
		BitSet test = (BitSet) key.code.clone();
		test.andNot(key.pre.getFirst().code);

		HashSet<XEvent> classEq = new HashSet<>();
		for (ElementBS e : elements)
			if (e.code.cardinality() > 0) {
				for (ElementBS pre : e.pre) {
					BitSet code = (BitSet) e.code.clone();
					code.andNot(pre.code);

					if (code.equals(test) && tableEvents.contains(pre, e))
						classEq.addAll(tableEvents.get(pre, e));
				}
			}

		return classEq;
	}

	public Table<ElementBS, ElementBS, ElementBS> getEdgeLatticeClassPrime(ElementBS key) {
		BitSet test = (BitSet) key.code.clone();
		test.andNot(key.pre.getFirst().code);

		Table<ElementBS, ElementBS, ElementBS> classEq = HashBasedTable.<ElementBS, ElementBS, ElementBS>create();
		for (ElementBS e : elements)
			if (e.code.cardinality() > 0)
				for (ElementBS pre : e.pre) {
					BitSet code = (BitSet) e.code.clone();
					code.andNot(pre.code);

					if (code.equals(test))
						classEq.put(pre, e, key);
				}

		return classEq;
	}

	public HashSet<XEvent> getEvents(ElementBS first, ElementBS elementBS) {
		return tableEvents.get(first, elementBS);
	}

	public HashSet<String> getTunnelLabels(ElementBS prime) {
		Table<ElementBS, ElementBS, ElementBS> equivClass = getEdgeLatticeClassPrime(prime);

		BitSet union = null;
		BitSet intersection = null;

		for (Cell<ElementBS, ElementBS, ElementBS> entry : equivClass.cellSet())
			if (entry.getRowKey().code.cardinality() >= prime.code.cardinality()) {

				if (union == null)
					union = (BitSet) entry.getColumnKey().code.clone();
				else
					union.or(entry.getColumnKey().code);

				if (intersection == null)
					intersection = (BitSet) entry.getRowKey().code.clone();
				else
					intersection.and(entry.getRowKey().code);
			}

		BitSet labelsBS = (BitSet) union.clone();
		labelsBS.andNot(intersection);

		HashSet<String> tunnel = new HashSet<>();
		for (int i = labelsBS.nextSetBit(0); i >= 0; i = labelsBS.nextSetBit(i + 1))
			tunnel.add(getLabel(i));

		return tunnel;
	}

	public Table<ElementBS, ElementBS, ElementBS> getEdgeLatticeClassPrime(ElementBS root, ElementBS key) {
		BitSet test = (BitSet) key.code.clone();
		test.andNot(root.code);

		Table<ElementBS, ElementBS, ElementBS> classEq = HashBasedTable.<ElementBS, ElementBS, ElementBS>create();
		for (ElementBS e : elements)
			if (e.code.cardinality() > 0)
				for (ElementBS pre : e.pre) {
					BitSet code = (BitSet) e.code.clone();
					code.andNot(pre.code);

					if (code.equals(test))
						classEq.put(pre, e, key);
				}

		return classEq;
	}

	public LinkedHashSet<String> getTunnelLabels(ElementBS root, ElementBS prime) {
		Table<ElementBS, ElementBS, ElementBS> equivClass = getEdgeLatticeClassPrime(root, prime);

		BitSet union = null;
		BitSet intersection = null;

		for (Cell<ElementBS, ElementBS, ElementBS> entry : equivClass.cellSet())
			if (entry.getRowKey().code.cardinality() >= root.code.cardinality()) {

				if (union == null)
					union = (BitSet) entry.getColumnKey().code.clone();
				else
					union.or(entry.getColumnKey().code);

				if (intersection == null)
					intersection = (BitSet) entry.getRowKey().code.clone();
				else
					intersection.and(entry.getRowKey().code);
			}

		BitSet labelsBS = (BitSet) union.clone();
		labelsBS.andNot(intersection);

		LinkedHashSet<String> tunnel = new LinkedHashSet<>();
		for (int i = labelsBS.nextSetBit(0); i >= 0; i = labelsBS.nextSetBit(i + 1))
			tunnel.add(getLabel(i));

		return tunnel;
	}

	public LinkedList<ElementBS> getLCA(LinkedList<ElementBS> lowestLCAs) {
		LinkedList<ElementBS> singleLCA = new LinkedList<>();

		ElementBS gcd = lowestLCAs.getFirst();
		for (ElementBS e : lowestLCAs)
			if (e.code.cardinality() > gcd.code.cardinality())
				gcd = e;

		singleLCA.add(gcd);

		return singleLCA;
	}

	public void setCompletenessFactor(double completenessFactor) {
		this.completenessFactor = completenessFactor;
	}

	public double getCompletenessFactor() {
		return this.completenessFactor;
	}

	public HashSet<ElementBS> getJoins() {
		HashSet<ElementBS> joins = new HashSet<>();

		for (ElementBS e : this.elements)
			if (e.pre.size() > 1)
				joins.add(e);

		return joins;
	}

	public PrimeEventStructure<Integer> getPES() {
		return this.pes;
	}

	public void consolidateLabels() {
		this.consolidatedLabels = new HashMap<>();

		for (int i = this.top.code.nextSetBit(0); i >= 0; i = this.top.code.nextSetBit(i + 1))
			if (globalLabels != null)
				consolidatedLabels.put(i, this.globalLabels.get(i));
	}

	public HashMultimap<ElementBS, ElementBS> getIndependenceRelations() {
		HashMultimap<ElementBS, ElementBS> independenceRel = HashMultimap.<ElementBS, ElementBS>create();

		LinkedList<ElementBS> primes = getPrimes();
		for (ElementBS e : primes)
			for (ElementBS e1 : primes) {
				BitSet eClone = (BitSet) e.code.clone();
				eClone.and(e1.code);

				if (!eClone.equals(e.code) && !eClone.equals(e1.code))
					independenceRel.put(e, e1);
			}

		return independenceRel;
	}

	// Dependence relation for POD
	public HashMultimap<ElementBS, ElementBS> getDependenceRelations() {
		HashMultimap<ElementBS, ElementBS> dependenceRel = HashMultimap.<ElementBS, ElementBS>create();

		LinkedList<ElementBS> primes = getPrimes();
		for (ElementBS e : primes)
			for (ElementBS e1 : primes) {
				BitSet eClone = (BitSet) e.code.clone();
				eClone.and(e1.code);

				if (eClone.equals(e.code))
					dependenceRel.put(e, e1);
			}

		return dependenceRel;
	}

	public LinkedList<ElementBS> getElements() {
		return this.elements;
	}

	public Node<ElementBS> getTree() {
		countPathsDPInverse();

		Table<ElementBS, ElementBS, Integer> treeEdges = createTree();

		Node<ElementBS> rootTree1 = new Node<ElementBS>(this.top);
		Queue<Node<ElementBS>> queue = new LinkedList<>();
		queue.add(rootTree1);

		while (!queue.isEmpty()) {
			Node<ElementBS> current = queue.poll();
			current.getNodeData().setTranslation(getLabels(current.getNodeData()));
			current.getNodeData().setPathNumber(this.pathsNodeInverse.get(current.getNodeData()));

			for (ElementBS next : current.getNodeData().pre)
				if (treeEdges.contains(next, current.getNodeData())) {
					Node<ElementBS> child = new Node<ElementBS>(next);
					current.addChild(child);
					queue.add(child);
				}
		}

		return rootTree1;
	}

	public Node<ElementBS> getRelabeledTree(Table<ElementBS, ElementBS, ElementBS> classElements,
			HashMap<ElementBS, Integer> equivalences, HashMap<String, String> originalName) {
		Queue<ElementBS> queue = new LinkedList<>();
		queue.add(this.bottom);

		HashMap<Integer, String> mapLabels = new HashMap<>();

		for (ElementBS e : elements) {
			for (ElementBS f : e.post) {
				ElementBS one = f;
				ElementBS two = e;

				if (classElements.contains(two, one)) {
					one = classElements.get(two, one);
					two = one.pre.getFirst();
				}

				BitSet labelIndex = (BitSet) one.code.clone();
				labelIndex.andNot(two.code);

				int index = labelIndex.nextSetBit(0);
				String name = getLabel(index);

				if (originalName.containsKey(name))
					name = originalName.get(name);
				name += "_c" + equivalences.get(one);

				mapLabels.put(index, name);
			}
		}

		for (ElementBS e : elements) {
			HashSet<String> relabeled = new HashSet<>();

			for (int i = e.code.nextSetBit(0); i >= 0; i = e.code.nextSetBit(i + 1))
				relabeled.add(mapLabels.get(i));

			e.setTranslationRelabeled(relabeled);
		}

		return getTree();
	}

	public ElementBS getTop() {
		return this.top;
	}

	public void expand() {
		HashSet<ElementBS> newToAdd = new HashSet<>();
		
		while(true) {
		
			for (ElementBS e : elements) {
				if (e.pre != null && e.pre.size() > 1 && e.post.size() == 1) {
					for (ElementBS pre : e.pre) {
						
						Pair<ElementBS, ElementBS> primeRoot = getPrime(pre, e);
						
						LinkedHashSet<String> tunnel = getTunnelLabels(primeRoot.getFirst(), primeRoot.getSecond());
						tunnel.remove(getLabelBetween(pre, e));
						tunnel = translateForMap(tunnel, originalName);
	
						String next = getLabelBetween(e, e.post.getFirst());
						next = translateForMap(next, originalName);
						
						if (tunnel.contains(next)) {
							BitSet diff = (BitSet) e.code.clone();
							diff.andNot(pre.code);
							
							BitSet newCode = (BitSet) e.post.getFirst().code.clone();
							newCode.flip(diff.nextSetBit(0));
							
							if(!contains(newCode)) {
								ElementBS newElement = new ElementBS(newCode, this);
								newElement.setSynthetic(true);
								newToAdd.add(newElement);
								
								newElement.pre.add(pre);
								pre.post.add(newElement);
								
								newElement.post.add(e.post.getFirst());
								e.post.getFirst().pre.add(newElement);
								
								newConnections.put(pre, newElement);
								newConnections.put(newElement, e.post.getFirst());
								
								break;
							}
						}
					}
				}
				
				if(!newToAdd.isEmpty())
					break;
			}
			
			if(newToAdd.isEmpty())
				break;
			
			this.elements.addAll(newToAdd);
			newToAdd = new HashSet<>();
		}
		
		System.out.println(newToAdd.size());
	}

	private boolean contains(BitSet newCode) {
		for(ElementBS e : elements)
			if(e.code.equals(newCode))
				return true;
				
		return false;
	}

	private Pair<ElementBS, ElementBS> getPrime(ElementBS pre, ElementBS e) {
		BitSet test = (BitSet) e.code.clone();
		test.andNot(pre.code);
		
		for(ElementBS p : getPrimes()) {
			BitSet test2 = (BitSet) p.code.clone();
			test2.andNot(p.pre.getFirst().code);
			
			if(test.equals(test2))
				return new Pair<ElementBS, ElementBS>(p.pre.getFirst(), p);
		}
		
		return null;
	}

	private String translateForMap(String s, HashMap<String, String> originalName) {
		if (originalName.containsKey(s))
			return originalName.get(s);
		
		return s;
	}

	private LinkedHashSet<String> translateForMap(LinkedHashSet<String> tunnel, HashMap<String, String> originalName) {
		LinkedHashSet<String> originalTunnel = new LinkedHashSet<>();

		for (String s : tunnel)
			if (originalName.containsKey(s))
				originalTunnel.add(originalName.get(s));
			else
				originalTunnel.add(s);

		return originalTunnel;
	}
}
