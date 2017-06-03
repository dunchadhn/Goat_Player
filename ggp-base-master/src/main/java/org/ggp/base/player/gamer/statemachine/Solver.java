package org.ggp.base.player.gamer.statemachine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Stack;

import org.apache.lucene.util.OpenBitSet;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.XStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class Solver extends XStateMachineGamer {

	private XStateMachine machine;
	private int self_index, visits;
	private List<Role> roles;
	private boolean solved;
	Stack<Move> winningMoves;
	private long leaves, finishBy;
	private HashSet<OpenBitSet> visited;
	private HashMap<OpenBitSet, Integer> valueMap;
	@Override
	public XStateMachine getInitialStateMachine() {
		return new XStateMachine();
	}

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		finishBy = timeout - 2500;
		machine = getStateMachine();
		self_index = machine.getRoles().indexOf(getRole());
		roles = machine.getRoles();
		solved = false;
		leaves = 0;
		winningMoves = new Stack<Move>();
		visited = new HashSet<OpenBitSet>();
		valueMap = new HashMap<OpenBitSet, Integer>();
		OpenBitSet init = machine.getInitialState();
		visited.add(init);
		visits = 0;
		/*
		double bfactor = 0;
		double depth = 0;
		int rounds = 20;
		for (int i = 0; i < rounds; ++i) {
			OpenBitSet state = init;
			double bfactor2 = 0;
			double iters = 0;
			while (!machine.isTerminal(state)) {
				bfactor2 += machine.getLegalMoves(state, self_index).size();
				state = machine.getRandomNextState(state);
				++depth;
				++iters;
			}
			bfactor += (bfactor2 / iters);
		}
		bfactor /= rounds;
		depth /= rounds;
		System.out.println("Avg Branching Factor: " + bfactor);
		System.out.println("AvgDepth: " + depth);
		solve(init);
		//if (solved) System.out.println("Game Solved!");
		System.out.println("Enumerated " + leaves + " leaves of estimated " + Math.pow(bfactor, depth));
		System.out.println("visited: " + visited.size());

		OpenBitSet state = init;
		int alpha = -1;
		for (Move move : machine.getLegalMoves(state, self_index)) {
			int minValue = 101;
			if (System.currentTimeMillis() > finishBy) return;

			for (List<Move> jointMove : machine.getLegalJointMoves(state, self_index, move)) {
				if (System.currentTimeMillis() > finishBy) return;
				OpenBitSet nextState = machine.getNextState(state, jointMove);
				if (!visited.contains(nextState)) {
					System.out.println("1");
					visited.add(nextState);
					int value;
					if (valueMap.containsKey(nextState)) value = valueMap.get(nextState);
					else {
						value = alphabeta(nextState, alpha, minValue);
						valueMap.put(nextState, value);
					}
					if (value == 0 || value <= alpha) {
						minValue = 0;
						break;
					}
					if (value < minValue) minValue = value;
				}

			}

			if (minValue > alpha) alpha = minValue;

			System.out.println(move + " " + minValue);
		}*/

	}

	protected void solve(OpenBitSet state) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		if (machine.isTerminal(state)) {
			++leaves;
			if (machine.getGoal(state, self_index) == 100) {
				solved = true;
				System.out.println("Game Solved!");
			}
			return;
		}

		for (List<Move> jointMove : machine.getLegalJointMoves(state)) {
			OpenBitSet nextState = machine.getNextState(state, jointMove);
			if (!visited.contains(nextState)) {
				visited.add(nextState);
				solve(nextState);
				if (solved) {
					winningMoves.push(jointMove.get(self_index));
					return;
				}
			}
		}
	}


	protected Move bestmove()
			throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		visits = 0;
		HashSet<OpenBitSet> path = new HashSet<OpenBitSet>();
		OpenBitSet state = getCurrentState();
		visited.add(state);
		path.add(state);
		List<Move> moves = machine.getLegalMoves(state, self_index);

		Move action = moves.get(self_index);
		int score = 0;

		/*for(Move m : moves) {
			for (List<Move> jointMove : machine.getLegalJointMoves(state, self_index, m)) {
				OpenBitSet nextState = machine.getNextState(state, jointMove);
				if (!path.contains(nextState)) {
					path.add(nextState);
					int result;
					if (!visited.contains(nextState)) {
						result = alphabeta(nextState, 0, 100, path);
						valueMap.put(nextState, result);
					} else {
						result = valueMap.get(nextState);
					}
					path.remove(nextState);
					System.out.println(jointMove.get(self_index) + " " + result);
					if (result == 100) {
						System.out.println("Visits: " + visits);
						return jointMove.get(self_index);
					} else if(result > score) {
						score = result;
						action = jointMove.get(self_index);
					}
				}

				if(System.currentTimeMillis() > finishBy) break;
			}

		}*/
		valueMap.put(state, score);
		System.out.println("Visits: " + visits);
		return action;
	}

	protected int alphabeta(OpenBitSet state, int alpha, int beta, HashSet<OpenBitSet> path) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		++visits;
		visited.add(state);

		if (machine.isTerminal(state))
			return machine.getGoal(state, self_index);

		for (Move m : machine.getLegalMoves(state, self_index)) {

			int minValue = beta;
			/*for (List<Move> jointMove: machine.getLegalJointMoves(state, self_index, m)) {
				OpenBitSet nextState = machine.getNextState(state, jointMove);
				if (!path.contains(nextState)) {
					path.add(nextState);
					int result;
					if (!visited.contains(nextState)) {
						result = alphabeta(nextState, alpha, minValue, path);
						valueMap.put(nextState, result);
					} else {
						result = valueMap.get(nextState);
					}
					path.remove(nextState);
					if (result == 0 || result <= alpha) {
						minValue = alpha;
						break;
					}
					if (result < minValue) minValue = result;
				}
			}

			if (minValue == 100 ||  minValue >= beta) return beta;
			if (minValue > alpha) alpha = minValue;*/

		}

		return alpha;

	}



	/*protected int alphabeta(OpenBitSet state, int alpha, int beta) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		if (machine.isTerminal(state))
			return machine.getGoal(state, self_index);


		List<Move> legals = machine.getLegalMoves(state, self_index);
		int size = legals.size();
		System.out.println(size);
		for(int i = 0; i < size; ++i) {
			Move move = legals.get(i);
			int minValue = beta;
			if (System.currentTimeMillis() > finishBy) return 0;

			for (List<Move> jointMove : machine.getLegalJointMoves(state, self_index, move)) {
				if (System.currentTimeMillis() > finishBy) return 0;
				OpenBitSet nextState = machine.getNextState(state, jointMove);
				if (!visited.contains(nextState)) {
					visited.add(nextState);
					int value;
					if (valueMap.containsKey(nextState)) value = valueMap.get(nextState);
					else {
						value = alphabeta(nextState, alpha, minValue);
						valueMap.put(nextState, value);
					}
					if (value == 0 || value <= alpha) {
						minValue = 0;
						break;
					}
					if (value < minValue) minValue = value;

				} else {
					System.out.println("Duplicate");
				}

			}


			if (minValue == 100 || minValue >= beta) return beta;
			if (minValue > alpha) {
				alpha = minValue;
			}
		}

		return alpha;
	}*/


	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		//System.out.println("New Turn: ");
		return bestmove();
	}

	@Override
	public void stateMachineStop() {
		// TODO Auto-generated method stub

	}

	@Override
	public void stateMachineAbort() {
		// TODO Auto-generated method stub

	}

	@Override
	public void preview(Game g, long timeout) throws GamePreviewException {
		// TODO Auto-generated method stub

	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "Solver";
	}

}
