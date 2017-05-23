package org.ggp.base.player.gamer.statemachine;

import java.util.HashMap;
import java.util.List;

import org.ggp.base.util.statemachine.Move;

public class BitNode {

	public static int nodeCount = 0;

	BitNode(BitMachineState state) {
		this.state = state;
		this.isTerminal = false;
		this.children = new HashMap<List<Move>, BitNode>();
		this.legalJointMoves = new HashMap<Move, List<List<Move>>>();
		this.utility = 0;
		this.visits = 0;
		++nodeCount;
	}
	public BitMachineState state;
	public double utility;
	public double visits;
	public Move[] legalMoves;
	public HashMap<List<Move>, BitNode> children;
	public HashMap<Move, List<List<Move>>> legalJointMoves;
	public boolean isTerminal;
}
