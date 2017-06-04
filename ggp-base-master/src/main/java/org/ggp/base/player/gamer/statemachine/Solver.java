package org.ggp.base.player.gamer.statemachine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
	private int self_index;
	private List<Role> roles;
	private long finishBy;
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

	}



	protected Move bestmove() throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		valueMap = new HashMap<OpenBitSet, Integer>(); //need to recreate the map?
		OpenBitSet state = getCurrentState();
		int alpha = 0;
		List<Move> legals = machine.getLegalMoves(state, self_index);
		Move bestMove = legals.get(0);

		HashMap<Move, List<List<Move>>> moveMap = new HashMap<Move, List<List<Move>>>();
		for (Move m : legals) moveMap.put(m, new ArrayList<List<Move>>());
		for (List<Move> jointMove : machine.getLegalJointMoves(state)) {
			moveMap.get(jointMove.get(self_index)).add(jointMove);
		}

		for (Move move : legals) {

			int minValue = 100;
			if (!machine.getLegalJointMoves(state, self_index, move).equals(moveMap.get(move))) {
				System.out.println(machine.getLegalJointMoves(state, self_index, move).toString());
				System.out.println(moveMap.get(move).toString());
				System.exit(0);
			}
			//for (List<Move> jointMove : machine.getLegalJointMoves(state, self_index, move)) {
			for (List<Move> jointMove : moveMap.get(move)) {
				OpenBitSet nextState = machine.getNextState(state, jointMove);
				int result;
				if (valueMap.containsKey(nextState)) result = valueMap.get(nextState);
				else {
					result = alphabeta(nextState, alpha, minValue);
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

			System.out.println(move + " " + minValue);
			if (minValue == 100) {
				return move;
			}
			if (minValue > alpha) {
				alpha = minValue;
				bestMove = move;
			}
		}

		return bestMove;

	}
	protected int alphabeta(OpenBitSet state, int alpha, int beta) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		if (machine.isTerminal(state)) return machine.getGoal(state, self_index);

		List<Move> legals = machine.getLegalMoves(state, self_index);
		HashMap<Move, List<List<Move>>> moveMap = new HashMap<Move, List<List<Move>>>();
		for (Move m : legals) moveMap.put(m, new ArrayList<List<Move>>());
		for (List<Move> jointMove : machine.getLegalJointMoves(state)) {
			moveMap.get(jointMove.get(self_index)).add(jointMove);
		}

		for (Move move : legals) {
			int minValue = beta;
			if (!machine.getLegalJointMoves(state, self_index, move).equals(moveMap.get(move))) {
				System.out.println(machine.getLegalJointMoves(state, self_index, move).toString());
				System.out.println(moveMap.get(move).toString());
				System.exit(0);
			}
			//for (List<Move> jointMove : machine.getLegalJointMoves(state, self_index, move)) {
			for (List<Move> jointMove : moveMap.get(move)) {
				OpenBitSet nextState = machine.getNextState(state, jointMove);
				int result;
				if (valueMap.containsKey(nextState)) result = valueMap.get(nextState);
				else {
					result = alphabeta(nextState, alpha, minValue);
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

	/*public List<List<Move>> getLegalJointMoves(OpenBitSet state, int rIndex, Move m) throws MoveDefinitionException {
    	setState(state, null);

    	int size = roles.length;
        List<List<Move>> jointMoves = new ArrayList<List<Move>>();

    	for (int i = 0; i < rIndex; ++i) {
    		List<Move> moves = new ArrayList<Move>();
    		int roleIndex = rolesIndexMap[i];
    		int nextRoleIndex = rolesIndexMap[i + 1];

    		for (int j = roleIndex; j < nextRoleIndex; ++j) {
    			if (currLegals.fastGet(j)) {
    				moves.add(legalArray[j]);
    			}
    		}
    		jointMoves.add(moves);
    	}

    	List<Move> rMoves = new ArrayList<Move>();
    	rMoves.add(m);
    	jointMoves.add(rMoves);

    	for (int i = rIndex + 1; i < size; ++i) {
    		List<Move> moves = new ArrayList<Move>();
    		int roleIndex = rolesIndexMap[i];
    		int nextRoleIndex = (i == (size - 1) ? legalArray.length : rolesIndexMap[i + 1]);

    		for (int j = roleIndex; j < nextRoleIndex; ++j) {
    			if (currLegals.fastGet(j)) {
    				moves.add(legalArray[j]);
    			}
    		}
    		jointMoves.add(moves);
    	}


        List<List<Move>> crossProduct = new ArrayList<List<Move>>();
        crossProductLegalMoves(jointMoves, crossProduct, new ArrayDeque<Move>());//

        return crossProduct;
    }*/
/*
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
