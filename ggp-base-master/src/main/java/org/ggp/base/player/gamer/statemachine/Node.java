package org.ggp.base.player.gamer.statemachine;

import java.util.HashMap;
import java.util.List;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;

public class Node {

	public static int nodeCount = 0;

	Node(MachineState state) {
		this.state = state;
		this.isTerminal = false;
		this.children = new HashMap<List<Move>, Node>();
		this.legalJointMoves = new HashMap<Move, List<List<Move>>>();
		this.utility = 0;
		this.visits = 0;
		++nodeCount;
	}
	public MachineState state;
	public double utility;
	public double visits;
	public List<Move> legalMoves;
	public HashMap<List<Move>, Node> children;
	public HashMap<Move, List<List<Move>>> legalJointMoves;
	public boolean isTerminal;
}
