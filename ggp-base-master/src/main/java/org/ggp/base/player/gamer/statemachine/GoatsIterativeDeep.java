package org.ggp.base.player.gamer.statemachine;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

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
		mob_n = 1;
		weights = new int[]{1, 1, 1};

	}

	protected Move bestmove()
			throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		MachineState state = getCurrentState();
		List<Move> moves = machine.getLegalMoves(state, roles.get(self_index));
		if (moves.size() == 1) return moves.get(0);
		List<List<Move>> jointMoves = machine.getLegalJointMoves(state);

		List<Move> bestActions = new ArrayList<Move>();
		bestActions.add(jointMoves.get(0).get(self_index));
		int bestScore = 0;
		int depth = 3;
		int maxdepth = 20;
		while (depth < maxdepth) {
			int score = 0;
			List<Move> actions = new ArrayList<Move>();
			System.out.println("Depth: " + depth + " BestScore: " + bestScore);
			int num_actions = 0;
			for(List<Move> jointMove : jointMoves) {
				int nextPlayer = self_index + 1;
				MachineState nextState = machine.getNextState(state, jointMove);
				int result = alphabeta(nextPlayer % roles.size(), nextState, 0, 100, depth);
				System.out.println(jointMove.get(self_index) + " " + result);
				if(result == 100) {
					return jointMove.get(self_index);
				} else if(result > score) {
					score = result;
					actions = new ArrayList<Move>();
					actions.add(jointMove.get(self_index));
				} else if (result == score) {
					actions.add(jointMove.get(self_index));
				}
				num_actions += 1;
				if(System.currentTimeMillis() > finishBy) break;
			}
			if (score >= bestScore) {
				bestScore = score;
				bestActions = actions;
			}
			System.out.println(num_actions + " Actions Deliberated of " + jointMoves.size() + " actions");
			if(System.currentTimeMillis() > finishBy) break;
			depth += 1;
		}
		Random rn = new Random();
		return bestActions.get(rn.nextInt(bestActions.size()));
	}

	protected int nStepMobilityFn(int player, MachineState state, int n) throws MoveDefinitionException, TransitionDefinitionException {
		origin_player = player;
		int mobility = (int) (100 * (nStepActions(player, state, n) /
				machine.findActions(roles.get(player)).size()));
		//System.out.println("Mobility: " + mobility);
		return mobility;

		//HashSet<MachineState> uniqueStates = new HashSet<MachineState>();
		//statesReachable(player, state, n, uniqueStates);
		//How to map to {0, ..., 100}?
		//return uniqueStates.size()
	}

	protected int nStepFocusFn(int player, MachineState state, int n) throws MoveDefinitionException, TransitionDefinitionException {
		return 100 - nStepMobilityFn(player, state, n);
	}
	//Only adds states which are reachable by origin_player
	protected void statesReachable(int player, MachineState state, int n,
			HashSet<MachineState> uniqueStates) throws MoveDefinitionException, TransitionDefinitionException {
		if (machine.isTerminal(state) || n == 0)
			return;

		List<List<Move>> jointMoves = machine.getLegalJointMoves(state);
		int nextPlayer = player + 1;
		if (nextPlayer == origin_player) {
			n -= 1;
			for (List<Move> jointMove: jointMoves) {
				MachineState nextState = machine.getNextState(state, jointMove);
				uniqueStates.add(nextState);
				statesReachable(nextPlayer % roles.size(), nextState, n, uniqueStates);
			}
		} else {
			for (List<Move> jointMove: jointMoves) {
				MachineState nextState = machine.getNextState(state, jointMove);
				statesReachable(nextPlayer % roles.size(), nextState, n, uniqueStates);
			}
		}
	}

	//Assumes origin_player and opponents moves are uniformly randomly distributed
	//Consider changing to origin_player maximizing possible moves
	protected double nStepActions(int player, MachineState state, int n) throws MoveDefinitionException, TransitionDefinitionException {
		if (machine.isTerminal(state))
			return 0;
		if (n == 0)
			return machine.getLegalMoves(state, roles.get(origin_player)).size();

		List<List<Move>> jointMoves = machine.getLegalJointMoves(state);
		double totalMoves = 0;
		int nextPlayer = player + 1;
		if (nextPlayer == origin_player) n-= 1;
		for (List<Move> jointMove: jointMoves) {
			MachineState nextState = machine.getNextState(state, jointMove);
			totalMoves += nStepActions(nextPlayer % roles.size(), nextState, n);
		}
		return totalMoves / jointMoves.size();
	}

	protected int goalProxFn(int player, MachineState state) throws GoalDefinitionException {
		return machine.getGoal(state, roles.get(self_index));
	}

	protected int weightedCombo(int player, MachineState state) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		int total = 0;
		int numFns = 2;
		for (int i = 0; i < numFns; i++) {
			total += weights[i] * evalFn(player, state, i);
		}
		return total;
	}

	protected int evalFn(int player, MachineState state, int fn) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		switch (fn) {
		case 0: return goalProxFn(player, state);
		case 1: return nStepMobilityFn(player, state, mob_n);
		case 2: return nStepFocusFn(player, state, mob_n);
		default: return 0;
		}
	}
	protected int alphabeta(int player, MachineState state, int alpha, int beta, int d) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		if (machine.isTerminal(state)) {
			return machine.getGoal(state, roles.get(self_index));
		}
		if (d == 0) {
			return evalFn(player, state, DEFAULT);
		}

		List<List<Move>> jointMoves = machine.getLegalJointMoves(state);
		int score;
		if (player == self_index) score = 0;
		else score = 100;
		int nextPlayer = player + 1;
		if (nextPlayer == self_index) d -=1;
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
	private int self_index, origin_player, mob_n;
	private int weights[];
	private long finishBy;

	private static final int GOAL_FN = 0;
	private static final int MOBILITY_FN = 1;
	private static final int FOCUS_FN = 2;
	private static final int DEFAULT = 3;
	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		finishBy = timeout - 500;
		return bestmove();
	}


	@Override
	public String getName() {
		return "IterativeDeep Player";
	}

}