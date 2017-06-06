package org.ggp.base.player.gamer.statemachine;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.lucene.util.OpenBitSet;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.XStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class Solver extends FactorGamer {

	private XStateMachine machine;
	private int self_index;
	private List<Role> roles;
	private long finishBy;
	private int step_count;
	private OpenBitSet currentState;
	private HashMap<OpenBitSet, Integer> valueMap;
	@Override
	public XStateMachine getInitialStateMachine() {
		return new XStateMachine();
	}

	public Solver(XStateMachine mac, int total_steps) {
		machine = mac;
		step_count = total_steps - 1;
	}

	@Override
	public void stateMachineMetaGame(long timeout, OpenBitSet curr, Role role)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		currentState = curr;
		self_index = machine.getRoles().indexOf(role);
		roles = machine.getRoles();
		finishBy = timeout - 3000;
		bestmove(curr);
	}



	protected MoveStruct bestmove(OpenBitSet curr) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		valueMap = new HashMap<OpenBitSet, Integer>(); //need to recreate the map?
		OpenBitSet state = curr;
		int alpha = 0;
		List<Move> legals = machine.getLegalMoves(state, self_index);
		Move bestMove = legals.get(0);

		for (Move move : legals) {

			int minValue = 100;
			for (List<Move> jointMove : machine.getLegalJointMoves(state, self_index, move)) {
				if (System.currentTimeMillis() > finishBy) return new MoveStruct(bestMove,alpha);
				OpenBitSet nextState = machine.getNextState(state, jointMove);
				int result;
				if (valueMap.containsKey(nextState)) result = valueMap.get(nextState);
				else {
					result = alphabeta(nextState, alpha, minValue, step_count - 1);
					valueMap.put(nextState, result);
				}
				if (result <= alpha) {
					minValue = alpha;
					break;
				}
				if (result == 0) {
					minValue = 0;
					break;
				}
				if (result < minValue) {
					minValue = result;
				}
			}

			if (minValue == 100) {
				System.out.println();
				System.out.println("OUTSIDE SOLVER FOUND 100");
				System.out.println();
				return new MoveStruct(move, 100);
			}
			if (minValue > alpha) {
				alpha = minValue;
				bestMove = move;
			}
		}

		return new MoveStruct(bestMove,alpha);

	}


	protected int alphabeta(OpenBitSet state, int alpha, int beta, int steps) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		if (machine.isTerminal(state) || steps == 0) return machine.getGoal(state, self_index);

		List<Move> legals = machine.getLegalMoves(state, self_index);

		for (Move move : legals) {
			int minValue = beta;

			for (List<Move> jointMove : machine.getLegalJointMoves(state, self_index, move)) {
				if (System.currentTimeMillis() > finishBy) return alpha;
				OpenBitSet nextState = machine.getNextState(state, jointMove);
				int result;
				if (valueMap.containsKey(nextState)) result = valueMap.get(nextState);
				else {
					result = alphabeta(nextState, alpha, minValue, steps - 1);
					valueMap.put(nextState, result);
				}
				if (result <= alpha) {
					minValue = alpha;
					break;
				}
				if (result == 0) {
					minValue = 0;
					break;
				}
				if (result < minValue) minValue = result;
			}
			if (minValue >= beta) return beta;
			if (minValue == 100) return 100;
			if (minValue > alpha) alpha = minValue;
		}

		return alpha;
	}


	/*protected void solve(OpenBitSet state) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
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
	}*/



	@Override
	public MoveStruct stateMachineSelectMove(long timeout, OpenBitSet curr, List<Move> moves)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		if (!curr.equals(currentState)) {
			--step_count;
			currentState = curr;
		}
		finishBy = timeout - 3000;
		return bestmove(curr);
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

	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException, InterruptedException, ExecutionException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException, InterruptedException, ExecutionException {
		// TODO Auto-generated method stub

	}

}
