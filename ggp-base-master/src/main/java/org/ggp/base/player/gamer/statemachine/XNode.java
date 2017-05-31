package org.ggp.base.player.gamer.statemachine;

import java.util.HashMap;
import java.util.List;

import org.apache.lucene.util.OpenBitSet;
import org.ggp.base.util.statemachine.Move;

public class XNode {
	public XNode(OpenBitSet state) {
		this.state = state;
		this.isTerminal = false;
		this.children = new HashMap<List<Move>, XNode>();
		this.legalJointMoves = new HashMap<Move, List<List<Move>>>();
		this.utility = 0;
		this.visits = 0;
		this.updates = 0;
	}
	public volatile OpenBitSet state;
	public volatile double utility;
	public volatile double visits;
	public volatile double updates;
	public volatile Move[] legalMoves;
	public volatile HashMap<List<Move>, XNode> children;
	public volatile HashMap<Move, List<List<Move>>> legalJointMoves;
	public volatile boolean isTerminal;
}