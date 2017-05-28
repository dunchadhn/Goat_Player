package org.ggp.base.player.gamer.statemachine;

import java.util.HashMap;
import java.util.List;

import org.apache.lucene.util.OpenBitSet;
import org.ggp.base.util.statemachine.Move;

public class XNode {

	public static int nodeCount = 0;

	public XNode(OpenBitSet state) {
		this.state = state;
		this.isTerminal = false;
		this.children = new HashMap<List<Move>, XNode>();
		this.legalJointMoves = new HashMap<Move, List<List<Move>>>();
		this.utility = 0;
		this.visits = 0;
		++nodeCount;
	}
	public OpenBitSet state;
	public double utility;
	public double visits;
	public Move[] legalMoves;
	public HashMap<List<Move>, XNode> children;
	public HashMap<Move, List<List<Move>>> legalJointMoves;
	public boolean isTerminal;
}