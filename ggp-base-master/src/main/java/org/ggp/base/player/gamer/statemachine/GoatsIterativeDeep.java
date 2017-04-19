package org.ggp.base.player.gamer.statemachine;
import java.util.List;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;


public class GoatsIterativeDeep extends the_men_who_stare_at_goats {

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		machine = getStateMachine();
		Role role = getRole();
		roles = machine.getRoles();
		self_index = roles.indexOf(role);

	}

	protected Move bestmove()
			throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		MachineState state = getCurrentState();
		List<Move> moves = machine.getLegalMoves(state, roles.get(self_index));
		if (moves.size() == 1) return moves.get(0);
		List<List<Move>> jointMoves = machine.getLegalJointMoves(state);

		Move bestAction = jointMoves.get(0).get(self_index);
		int bestScore = 0;
		int depth = 1;
		int maxdepth = 10;
		while (depth < maxdepth) {
			int score = 0;
			Move action = bestAction;
			//System.out.println("Depth: " + depth + "BestScore: " + bestScore);
			for(List<Move> jointMove : jointMoves) {
				int nextPlayer = self_index + 1;
				MachineState nextState = machine.getNextState(state, jointMove);
				int result = alphabeta(nextPlayer % roles.size(), nextState, 0, 100, depth);
				//System.out.println(jointMove.get(self_index) + " " + result);
				if(result == 100) {
					return jointMove.get(self_index);
				} else if(result > score) {
					score = result;
					action = jointMove.get(self_index);
				}
				if(System.currentTimeMillis() > finishBy) break;
			}
			if (score >= bestScore) {
				bestScore = score;
				bestAction = action;
			}
			if(System.currentTimeMillis() > finishBy) break;
			depth += 1;
		}
		return bestAction;
	}

	protected int alphabeta(int player, MachineState state, int alpha, int beta, int d) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		if (machine.isTerminal(state))
			return machine.getGoal(state, roles.get(self_index));
		if (d == 0) {
			return 1;
		}

		List<List<Move>> jointMoves = machine.getLegalJointMoves(state);
		int score;
		if (player == self_index) score = 0;
		else score = 100;
		int nextPlayer = player + 1;
		if (nextPlayer == roles.size()) d -=1;
		if (player == self_index) {
			for (List<Move> jointMove: jointMoves) {
				MachineState nextState = machine.getNextState(state, jointMove);
				int result = alphabeta(nextPlayer % roles.size(), nextState, alpha, beta, d);
				if (result == 100 ||  result >= beta) return 100;
				if (result > alpha) alpha = result;
				if (result > score) score = result;
				if(System.currentTimeMillis() > finishBy) return 0;
			}
		} else {
			for (List<Move> jointMove: jointMoves) {
				MachineState nextState = machine.getNextState(state, jointMove);
				int result = alphabeta(nextPlayer % roles.size(), nextState, alpha, beta, d);
				if (result == 0 || score <= alpha) return 0;
				if (result < beta) beta = result;
				if (result < score) score = result;
				if(System.currentTimeMillis() > finishBy) return 0;
			}
		}

		return score;

	}

	private StateMachine machine;
	private List<Role> roles;
	private int self_index;
	private long finishBy;
	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		finishBy = timeout - 100;
		return bestmove();
	}


	@Override
	public String getName() {
		return "IterativeDeep Player";
	}

}