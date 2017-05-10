package org.ggp.base.player.gamer.statemachine;

import java.util.HashMap;
import java.util.List;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;

public class Node2 {
	Node2(MachineState state) {
		this.state = state;
		this.isTerminal = false;
		this.children = new HashMap<List<Move>, Node2>();
		this.legalJointMoves = new HashMap<Move, List<List<Move>>>();
		this.visits = 0;
	}
	public MachineState state;
	public double[] utilities;
	public double visits;
	public List<Move> legalMoves;
	public HashMap<List<Move>, Node2> children;
	public HashMap<Move, List<List<Move>>> legalJointMoves;
	public boolean isTerminal;
}
