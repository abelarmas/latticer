package uni.melb.au.tree;

import at.unisalzburg.dbresearch.apted.costmodel.StringUnitCostModel;

import java.util.List;

import at.unisalzburg.dbresearch.apted.costmodel.PerEditOperationStringNodeDataCostModel;
import at.unisalzburg.dbresearch.apted.distance.APTED;
import at.unisalzburg.dbresearch.apted.node.Node;
import at.unisalzburg.dbresearch.apted.node.StringNodeData;
import at.unisalzburg.dbresearch.apted.parser.BracketStringInputParser;

public class TreeMain {

	public static void main(String[] args) {
		// Parse the input and transform to Node objects storing node information in MyNodeData.
		BracketStringInputParser parser = new BracketStringInputParser();
		Node<StringNodeData> t1 = parser.fromString("{A{D}{C{G}}{B{E{H}}{F}}}");
		Node<StringNodeData> t2 = parser.fromString("{A{B{F}}{D}}");
		// Initialise APTED.
		APTED<PerEditOperationStringNodeDataCostModel, StringNodeData> apted = new APTED<>(new PerEditOperationStringNodeDataCostModel(1, 10, 0));
		// Execute APTED.
		float result = apted.computeEditDistance(t1, t2);
		System.out.println("Edit distance = " + result);
		List<int[]> mapping = apted.computeEditMapping();
		
		for(int[] indexes : mapping)
			System.out.println(indexes[0] + " -- " + indexes[1]);
		
		System.out.println();
		System.out.println(t1.getChildren());
		System.out.println(t2.getChildren());
	}

}
