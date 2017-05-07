package org.ggp.base.player.gamer.statemachine;

import java.util.HashMap;
import java.util.List;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;

public class Node {
	Node(MachineState state) {
		this.state = state;
		this.isTerminal = false;
		this.children = new HashMap<List<Move>, Node>();
		this.utility = 0;
		this.visits = 0;
	}
	public MachineState state;
	public double utility;
	public double visits;
	public HashMap<List<Move>, Node> children;
	public boolean isTerminal;
}
