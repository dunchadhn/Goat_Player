package org.ggp.base.player.gamer.statemachine;
import java.util.ArrayList;
import java.util.List;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;


public class MCTS2 extends the_men_who_stare_at_goats {
	private StateMachine machine;
	private List<Role> roles;
	private int self_index;
	private long finishBy, depthCharges;
	private Node2 root;

	private static final double C_CONST = 50;

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		initialize(timeout);
		runMCTS();
		bestMove(root);
	}

	protected void initialize(long timeout) throws MoveDefinitionException, TransitionDefinitionException {
		machine = getStateMachine();
		roles = machine.getRoles();
		self_index = roles.indexOf(getRole());
		root = new Node2(machine.getInitialState());
		Expand(root);
		finishBy = timeout - 1000;
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		//More efficient to use Compulsive Deliberation for one player games
		//Use two-player implementation for two player games
		finishBy = timeout - 1000;
		return MCTS();
	}

	protected void initializeMCTS() throws MoveDefinitionException, TransitionDefinitionException {
		MachineState currentState = getCurrentState();
		if (root == null) System.out.println("NULL ROOT");
		if (root.state == currentState) return;
		for (List<Move> jointMove : machine.getLegalJointMoves(root.state)) {
			MachineState nextState = machine.getNextState(root.state, jointMove);
			if (currentState == nextState) {
				root = root.children.get(jointMove);
				if (root == null) System.out.println("NOT IN MAP");
				return;
			}
		}
		System.out.println("ERROR. Current State not in tree");
		root = new Node2(currentState);
	}

	protected Move MCTS() throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		initializeMCTS();
		runMCTS();
		return bestMove(root);
	}

	protected void runMCTS() throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		depthCharges = 0;
		while (System.currentTimeMillis() < finishBy) {
			List<Node2> path = new ArrayList<Node2>();
			path.add(root);
			Select(root, path);
			Node2 n = path.get(path.size() - 1);
			Expand(n, path);
			double val = Playout(n);
			Backpropogate(val, path);
			++depthCharges;
		}
		System.out.println("Depth Charges: " + depthCharges);
	}

	protected Move bestMove(Node2 n) throws MoveDefinitionException {
		double maxValue = Double.NEGATIVE_INFINITY;
		Move maxMove = n.legalMoves.get(0);
		for(Move move: n.legalMoves) {
			double minValue = Double.POSITIVE_INFINITY;
			for (List<Move> jointMove : n.legalJointMoves.get(move)) {
				Node2 succNode = n.children.get(jointMove);
				if (succNode.visits != 0) {
					double nodeValue = succNode.utility / succNode.visits;
					if (nodeValue < minValue) minValue = nodeValue;
				}
				System.out.println("Move: " + move + " Value: " + (succNode.visits > 0 ? minValue : Double.NaN) + " Visits: " + succNode.visits);
			}
			if (minValue > maxValue) {
				maxValue = minValue;
				maxMove = move;
			}
		}
		System.out.println("Max Move: " + maxMove + " Max Value: " + maxValue);
		return maxMove;
	}

	protected void Backpropogate(double val, List<Node2> path) {
		Node2 n = path.get(path.size() - 1);
		if (machine.isTerminal(n.state)) {
			n.isTerminal = true;
		}
		for (int i = path.size() - 1; i >= 0; --i) {
			n = path.get(i);
			n.utility += val;
			++n.visits;
		}
	}

	protected double Playout(Node2 n) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		MachineState state = n.state;
		while(!machine.isTerminal(state)) {
			state = machine.getRandomNextState(state);
		}
		return machine.getGoal(state, roles.get(self_index));
	}


	protected void Select(Node2 n, List<Node2> path) throws MoveDefinitionException {
		if (machine.isTerminal(n.state)) return;
		if (n.children.isEmpty()) return;
		double maxValue = Double.NEGATIVE_INFINITY;
		Node2 maxChild = null;
		assert n.legalMoves == machine.getLegalMoves(n.state, roles.get(self_index));//remove during game
		for(Move move: n.legalMoves) {
			double minValue = Double.NEGATIVE_INFINITY;
			Node2 minChild = null;
			assert n.legalJointMoves.get(move) == machine.getLegalJointMoves(n.state, roles.get(self_index), move);//remove during game
			for (List<Move> jointMove : n.legalJointMoves.get(move)) {
				Node2 succNode = n.children.get(jointMove);
				if (succNode.visits == 0) {
					path.add(succNode);
					return;
				}
				double nodeValue = uctMin(succNode, n.visits);
				if (nodeValue > minValue) {
					minValue = nodeValue;
					minChild = succNode;
				}
			}
			minValue = uctMax(minChild, n.visits);
			if (minValue > maxValue) {
				maxValue = minValue;
				maxChild = minChild;
			}
		}
		path.add(maxChild);
		Select(maxChild, path);
	}


	protected double uctMin(Node2 n, double parentVisits) {
		double value = n.utility / n.visits;
		return -value + C_CONST * Math.sqrt(Math.log(parentVisits) / n.visits);
	}

	protected double uctMax(Node2 n, double parentVisits) {
		double value = n.utility / n.visits;
		return value + C_CONST * Math.sqrt(Math.log(parentVisits) / n.visits);
	}

	protected void Expand(Node2 n, List<Node2> path) throws MoveDefinitionException, TransitionDefinitionException {
		if (n.children.isEmpty() && !machine.isTerminal(n.state)) {
			n.legalMoves = machine.getLegalMoves(n.state, roles.get(self_index));
			for (Move move: n.legalMoves) {
				n.legalJointMoves.put(move, new ArrayList<List<Move>>());
			}
			for (List<Move> jointMove: machine.getLegalJointMoves(n.state)) {
				Node2 child = new Node2(machine.getNextState(n.state, jointMove));
				n.legalJointMoves.get(jointMove.get(self_index)).add(jointMove);
				n.children.put(jointMove, child);
			}
			path.add(n.children.get(machine.getRandomJointMove(n.state)));
		} else if (!machine.isTerminal(n.state)) {
			System.out.println("ERROR. Tried to expand node that was previously expanded");
		}
	}

	protected void Expand(Node2 n) throws MoveDefinitionException, TransitionDefinitionException {//Assume only expand from max node
		if (n.children.isEmpty() && !machine.isTerminal(n.state)) {
			n.legalMoves = machine.getLegalMoves(n.state, roles.get(self_index));
			for (Move move: n.legalMoves) {
				n.legalJointMoves.put(move, new ArrayList<List<Move>>());
			}
			for (List<Move> jointMove: machine.getLegalJointMoves(n.state)) {
				Node2 child = new Node2(machine.getNextState(n.state, jointMove));
				n.legalJointMoves.get(jointMove.get(self_index)).add(jointMove);
				n.children.put(jointMove, child);
			}
		} else if (!machine.isTerminal(n.state)) {
			System.out.println("ERROR. Tried to expand node that was previously expanded");
		}
	}

	@Override
	public void stateMachineStop() {


	}

	@Override
	public void stateMachineAbort() {
		// TODO Auto-generated method stub

	}


	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "MCTS2 Player";
	}




}

