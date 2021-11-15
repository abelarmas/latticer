package uni.melb.au.lattice;

import java.util.HashMap;
import java.util.LinkedList;

import com.google.common.collect.HashMultiset;

public class Node {
	HashMultiset<String> acts;
	private HashMap<String, Node> next; 
	private HashMap<String, Node> previous; 
	LinkedList<String> sequence;
			
	public Node(LinkedList<String> activities) {
		this.next = new HashMap<>();
		this.previous = new HashMap<>();
		this.sequence = new LinkedList<>(activities);
		this.acts = HashMultiset.<String> create(activities);
	}

	public Node get(String activity, LinkedList<Node> nodes) {
		if(next.containsKey(activity))
			return next.get(activity);
		
		for(Node node : nodes) {
			HashMultiset<String> clone = HashMultiset.<String> create(this.acts);
			clone.add(activity);
			
			if(node.acts.equals(clone)) {
				next.put(activity, node);
				node.previous.put(activity, this);
				return node;
			}
		}
		
		LinkedList<String> clone = new LinkedList<>(sequence);
		clone.add(activity);
		
		Node newNext = new Node(clone);
		next.put(activity, newNext);
		newNext.previous.put(activity, this);
		nodes.add(newNext);
		
		return newNext;
	}
	
	public String toString() {
		return this.acts.toString();
	}
	
	public void addNext(String act, Node nxt) {
		if(!this.next.containsKey(act))
			this.next.put(act, nxt);
		else if(!this.next.get(act).equals(nxt)) 
			this.next.put(act, nxt);
	}
	
	public void addPrevious(String act, Node prev) {
		if(!this.previous.containsKey(act))
			this.previous.put(act, prev);
		else if(!this.previous.get(act).equals(prev)) 
			this.previous.put(act, prev);
	}
	
	public HashMap<String, Node> getNext() {
		return next;
	}
	
	public HashMap<String, Node> getPrevious() {
		return previous;
	}
}
