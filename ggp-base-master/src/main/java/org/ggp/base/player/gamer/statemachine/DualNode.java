package org.ggp.base.player.gamer.statemachine;

import java.util.HashMap;
import java.util.List;

import org.apache.lucene.util.OpenBitSet;
import org.ggp.base.util.statemachine.Move;

public class DualNode {
	public DualNode(OpenBitSet state) {
		this.state = state;
		this.isSolved = false;
		this.childrenStates = new HashMap<List<Move>, Integer>();
		this.children = new HashMap<List<Move>, DualNode>();
		this.legalJointMoves = new HashMap<Move, List<List<Move>>>();
		this.utility = 0;
		this.visits = 0;
		this.updates = 0;
		this.solvedValue = 0;
		this.sum_x = 0;
		this.sum_x2 = 0;
		this.n = 0;
		this.C_CONST = 50;
	}
	public volatile OpenBitSet state;
	public volatile double utility;
	public volatile long visits;
	public volatile long updates;
	public volatile Move[] legalMoves;
	public volatile HashMap<List<Move>, Integer> childrenStates;
	public volatile HashMap<List<Move>, DualNode> children;
	public volatile HashMap<Move, List<List<Move>>> legalJointMoves;
	public volatile boolean isSolved;
	public volatile double solvedValue;
	public volatile double sum_x;
	public volatile double sum_x2;
	public volatile long n;
	public volatile double C_CONST;
}
