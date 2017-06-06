package org.ggp.base.player.gamer.statemachine;

import java.util.HashMap;
import java.util.List;

import org.apache.lucene.util.OpenBitSet;
import org.ggp.base.util.statemachine.Move;

public class XNode {
	public XNode(OpenBitSet state) {
		this.state = state;
		this.isTerminal = false;
		this.isSolved = false;
		this.children = new HashMap<List<Move>, XNode>();
		this.legalJointMoves = new HashMap<Move, List<List<Move>>>();
		this.utility = 0;
		this.visits = 0;
		this.updates = 0;
		this.solvedValue = 0;
		this.sum_x = 0;
		this.sum_x2 = 0;
		this.n = 0;
		this.C_CONST = 50;
		this.expanded = false;
		this.started = false;
	}
	public volatile OpenBitSet state;
	public volatile double utility;
	public volatile double visits;
	public volatile double updates;
	public volatile Move[] legalMoves;
	public volatile HashMap<List<Move>, XNode> children;
	public volatile HashMap<Move, List<List<Move>>> legalJointMoves;
	public volatile boolean isTerminal;
	public volatile boolean isSolved;
	public volatile int solvedValue;
	public volatile double sum_x;
	public volatile double sum_x2;
	public volatile int n;
	public volatile double C_CONST;
	public volatile boolean expanded;
	public volatile boolean started;
}