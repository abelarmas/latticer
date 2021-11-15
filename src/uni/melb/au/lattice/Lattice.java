package uni.melb.au.lattice;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;

import uni.melb.au.lattice.utils.Pair;
import uni.melb.au.log.Trace;

public class Lattice {
	Node root;
	Node leaf;
	private LinkedList<Node> nodes;

	public Lattice() {
		this.root = new Node(new LinkedList<>());
		this.nodes = new LinkedList<>();
		this.nodes.add(this.root);
	}

	public Lattice(HashSet<Trace> traceEquivalence) {
		this();

		for (Trace trace : traceEquivalence) {
			Node last = root;
			for (String activity : trace.getListTraceOriginal())
				last = last.get(activity, nodes);
		}

		findLeaf();
	}

	private void findLeaf() {
		for (Node node : nodes)
			if (node.getNext().isEmpty())
				this.leaf = node;
	}

	public String toString() {
		return nodes.toString();
	}

	public String toDot() {
		HashMap<Node, Integer> map = new HashMap<>();
		for (Node node : nodes)
			map.put(node, map.size());

		StringBuilder b = new StringBuilder();
		b.append("digraph BP {\n");

		// standard style for nodes and edges
		b.append("graph [fontname=\"Helvetica\" nodesep=0.3 ranksep=\"0.2 equally\" fontsize=10];\n");
		b.append("node [fontname=\"Helvetica\" fontsize=8 ];\n");
		b.append("edge [fontname=\"Helvetica\" fontsize=8 color=white arrowhead=none weight=\"20.0\"];\n\n");

		// first print all conditions
		b.append("node [shape=circle];\n");

		for (Node n : nodes) {
			b.append("  c" + map.get(n) + " []\n");

			String nodeLabel = n.toString();

			b.append("  c" + map.get(n) + "_l [shape=none];\n");
			b.append("  c" + map.get(n) + "_l -> c" + map.get(n) + " [headlabel=\"" + nodeLabel + "\"]\n");
		}

		// finally, print all edges
		b.append("\n\n");
		b.append(" edge [fontname=\"Helvetica\" fontsize=8 arrowhead=normal color=black];\n");
		for (Node n : nodes)
			for (Node next : n.getNext().values())
				b.append("  c" + map.get(n) + " -> c" + map.get(next) + "\n");

		b.append("}");
		return b.toString();
	}

	public boolean isDistributive() {
		for (Node x : nodes) {
			for (Node y : nodes) {
				if (x != y) {
					for (Node z : nodes) {
						if (z != y && z != x) {
							// Distributive rule X
//							Node xJy = findJoin(x, y);
//							Node yJz = findJoin(y, z);
//							Node zJx = findJoin(z, x);
//							Node xMyMz = findMeet(xJy, findMeet(yJz, zJx));
//
//							Node xMy = findMeet(x, y);
//							Node yMz = findMeet(y, z);
//							Node zMx = findMeet(z, x);
//							Node xJyJz = findJoin(xMy, findJoin(yMz, zMx));
//
//							if (xMyMz != xJyJz) {
//								System.out.println("It is not distributive!");
//								return false;
//							}

							// Distributive rule Y
							Node yJz = findJoin(y, z);
							Node xMyMz = findMeet(x, yJz);

							Node xMy = findMeet(x, y);
							Node zMx = findMeet(z, x);
							Node xJyJz = findJoin(xMy, zMx);

							if (xMyMz != xJyJz) {
								System.out.println("It is not distributive!");
								return false;
							}
						}
					}
				}
			}
		}

		return true;
	}

	private Node findMeet(Node x, Node y) {
		HashSet<Node> historyX = getHistory(x);
		HashSet<Node> historyY = getHistory(y);
		historyX.retainAll(historyY);

		Queue<Node> queue = new LinkedList<>();
		queue.addAll(historyX);

		Node meet = null;

		while (!queue.isEmpty()) {
			Node current = queue.poll();

			if (current.sequence.size() <= x.sequence.size() && current.sequence.size() <= y.sequence.size()) {
				if (Multisets.containsOccurrences(x.acts, current.acts)
						&& Multisets.containsOccurrences(y.acts, current.acts))
					if (meet == null || current.sequence.size() > meet.sequence.size())
						meet = current;
			}
		}

		return meet;
	}

	private HashSet<Node> getHistory(Node x) {
		Queue<Node> queue = new LinkedList<>();
		HashSet<Node> visited = new HashSet<>();
		queue.add(x);

		while (!queue.isEmpty()) {
			Node current = queue.poll();

			if (!visited.contains(current)) {
				visited.add(current);
				queue.addAll(current.getPrevious().values());
			}
		}

		return visited;
	}

	private HashSet<Node> getFuture(Node x) {
		Queue<Node> queue = new LinkedList<>();
		HashSet<Node> visited = new HashSet<>();
		queue.add(x);

		while (!queue.isEmpty()) {
			Node current = queue.poll();

			if (!visited.contains(current)) {
				visited.add(current);
				queue.addAll(current.getNext().values());
			}
		}

		return visited;
	}

	private Node findJoin(Node x, Node y) {
		HashSet<Node> futureX = getFuture(x);
		HashSet<Node> futureY = getFuture(y);
		futureX.retainAll(futureY);

		Queue<Node> queue = new LinkedList<>();
		queue.addAll(futureX);

		Node join = null;

		while (!queue.isEmpty()) {
			Node current = queue.poll();

			if (Multisets.containsOccurrences(current.acts, x.acts)
					&& Multisets.containsOccurrences(current.acts, y.acts))
				if (join == null || current.sequence.size() < join.sequence.size())
					join = current;
		}

		return join;
	}

	public void complete() {

		while (true) {
			HashSet<Node> toAdd = new HashSet<>();

			for (Node node1 : nodes) {
				for (Node node2 : nodes) {
					if (node1 != node2 && node1.acts.size() == node2.acts.size()) {
						Node newIntersection = intersect(node1, node2);
						Node newUnion = union(node1, node2);

						if (newIntersection != null) {
							toAdd.add(newIntersection);
							toConnectPast(newIntersection);
						}

						if (newUnion != null) {
							toAdd.add(newUnion);
							toConnectFuture(newUnion);
						}

						if (!toAdd.isEmpty())
							break;
					}
				}

				if (!toAdd.isEmpty())
					break;
			}

			if (!toAdd.isEmpty()) {
				for (Node n : toAdd)
					if (!nodes.contains(n))
						nodes.add(n);
			} else
				break;
		}
	}

	private void toConnectFuture(Node newNode) {
		for (Node node : nodes) {
			if (node.acts.size() == newNode.acts.size() + 1) {
				Multiset<String> diff = Multisets.difference(HashMultiset.<String>create(node.acts), newNode.acts);

				if (diff.size() == 1) {
					newNode.addNext(diff.iterator().next(), node);
					node.addPrevious(diff.iterator().next(), newNode);
				}
			}
		}
	}

	private void toConnectPast(Node newNode) {
		for (Node node : nodes) {
			if (node.acts.size() == newNode.acts.size() - 1) {
				Multiset<String> diff = Multisets.difference(newNode.acts, HashMultiset.<String>create(node.acts));

				if (diff.size() == 1) {
					newNode.addPrevious(diff.iterator().next(), node);
					node.addNext(diff.iterator().next(), newNode);
				}
			}
		}
	}

	private Node union(Node node1, Node node2) {
		Multiset<String> union = Multisets.union(node1.acts, node2.acts);
		Node join = findJoin(node1, node2);

		if (join == null || !union.equals(join.acts)) {
			Multiset<String> diff1 = Multisets.difference(union, HashMultiset.<String>create(node1.acts));
			Multiset<String> diff2 = Multisets.difference(union, HashMultiset.<String>create(node2.acts));

			Node newUnion = findNode(union);

			if (diff1.size() == 1 && diff2.size() == 1) {
				node1.addNext(diff1.iterator().next(), newUnion);
				node2.addNext(diff2.iterator().next(), newUnion);

				newUnion.addPrevious(diff1.iterator().next(), node1);
				newUnion.addPrevious(diff2.iterator().next(), node2);

				return newUnion;
			}
		}

		return null;
	}

	private Node intersect(Node node1, Node node2) {
		Multiset<String> intersection = Multisets.intersection(node1.acts, node2.acts);
		Node meet = findMeet(node1, node2);

		if (meet == null || !intersection.equals(meet.acts)) {
			Multiset<String> diff1 = Multisets.difference(HashMultiset.<String>create(node1.acts), intersection);
			Multiset<String> diff2 = Multisets.difference(HashMultiset.<String>create(node2.acts), intersection);

			Node newIntersection = findNode(intersection);

			if (diff1.size() == 1 && diff2.size() == 1) {
				newIntersection.addNext(diff1.iterator().next(), node1);
				newIntersection.addNext(diff2.iterator().next(), node2);

				node1.addPrevious(diff1.iterator().next(), newIntersection);
				node2.addPrevious(diff2.iterator().next(), newIntersection);

				return newIntersection;
			}
		}

		return null;
	}

	private Node findNode(Multiset<String> intersection) {
		for (Node node : nodes)
			if (HashMultiset.<String>create(node.acts).equals(intersection))
				return node;

		return new Node(new LinkedList<>(intersection));
	}

//	public void computePaths() {
//		int[] paths = new int[nodes.size()];
//		int[] pathsOut = new int[nodes.size()];
//		Queue<Node> queue = new LinkedList<>();
//
//		for (Node prev : leaf.getPrevious().values())
//			queue.offer(prev);
//
//		paths[nodes.indexOf(leaf)] = 1;
//		pathsOut[nodes.indexOf(leaf)] = 1;
//
//		while (!queue.isEmpty()) {
//			Node node = queue.poll();
//			int outgoing = getParentsPaths(node, pathsOut);
//
//			if (node == root) {
//				paths[nodes.indexOf(node)] = getParentsPaths(node, paths);
//				pathsOut[nodes.indexOf(node)] = node.getNext().size();
//				break;
//			}
//			
//			if (node.getNext().size() == 1) {
//				paths[nodes.indexOf(node)] = getParentsPaths(node, paths);
//				pathsOut[nodes.indexOf(node)] = 1;
//			} else {
//				int incoming = node.getPrevious().size();
//				
//				paths[nodes.indexOf(node)] = outgoing * incoming;
//				pathsOut[nodes.indexOf(node)] = node.getNext().size();
//			}
//			
//			for (Node prev : node.getPrevious().values())
//				if (!queue.contains(prev))
//					queue.offer(prev);
//		}
//
//		System.out.println(Arrays.toString(paths));
//	}
	
	public void computePaths() {
		int[] paths = new int[nodes.size()];
		Queue<Pair<Node,Node>> queue = new LinkedList<>();
		queue.offer(new Pair<>(root, null));
		HashSet<Node> visited = new HashSet<>();

		while (!queue.isEmpty()) {
			Pair<Node,Node> node = queue.poll();
			Node current = node.getElement1();
			Node previous = node.getElement2();

			if(visited.contains(current))
				continue;
			
			visited.add(current);
			
			if(previous == null) 
				paths[nodes.indexOf(current)] = 1;
			else if(current.getPrevious().size() == 1) 
				paths[nodes.indexOf(current)] = paths[nodes.indexOf(previous)];
			else 
				paths[nodes.indexOf(current)] = getChildrenPaths(current, paths);
				
			for (Node next : current.getNext().values())
				queue.offer(new Pair<Node, Node>(next, current));
		}

		System.out.println(Arrays.toString(paths));
	}

	private int getChildrenPaths(Node node, int[] paths) {
		int sum = 0;

		for (Node next : node.getPrevious().values())
			sum += paths[nodes.indexOf(next)];

		return sum;
	}

	public int getSize() {
		return this.nodes.size();
	}

}
