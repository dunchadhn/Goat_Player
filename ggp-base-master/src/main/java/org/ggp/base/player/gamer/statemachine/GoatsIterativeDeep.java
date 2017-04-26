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
		mob_n = 0;
		repetitions = 6;
		//add focus heuristic for player and mobility heuristic for opponent; let simulations parameterize weights
		weights = new double[]{1, 0, 0};

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
		int depth = 1;
		int maxdepth = 100000;
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
				if(System.currentTimeMillis() >= finishBy) break;
			}
			if (score >= bestScore) {
				bestScore = score;
				bestActions = actions;
			}
			System.out.println(num_actions + " Actions Deliberated of " + jointMoves.size() + " actions");
			if(System.currentTimeMillis() >= finishBy) break;
			depth += 1;
		}
		Random rn = new Random();
		return bestActions.get(rn.nextInt(bestActions.size()));
	}


	protected int alphabeta(int player, MachineState state, int alpha, int beta, int d) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		if (machine.isTerminal(state))
			return machine.getGoal(state, roles.get(self_index));
		if (d == 0) {
			int heuristic = evalFn(player, state, COMBO_FN);
			return (heuristic == 0 ? UNKNOWN_DEFAULT : heuristic);
		}

		List<List<Move>> jointMoves = machine.getLegalJointMoves(state);
		int score;
		if (player == self_index) score = 0;
		else score = 100;
		int nextPlayer = (player + 1) % roles.size();
		if (nextPlayer == self_index) d -= 1;

		if (player == self_index) {
			for (List<Move> jointMove: jointMoves) {
				if(System.currentTimeMillis() >= finishBy) return score;//conservative estimate
				MachineState nextState = machine.getNextState(state, jointMove);
				int result = alphabeta(nextPlayer % roles.size(), nextState, alpha, beta, d);
				if (result == 100 ||  result >= beta) return 100;
				if (result > alpha) alpha = result;
				if (result > score) score = result;
			}
		} else {
			int num_moves = 0;
			for (List<Move> jointMove: jointMoves) {
				if(System.currentTimeMillis() >= finishBy) {
					//conservative estimate
					return (int) (score * (double)num_moves / jointMoves.size());
				}
				MachineState nextState = machine.getNextState(state, jointMove);
				int result = alphabeta(nextPlayer, nextState, alpha, beta, d);
				if (result == 0 || score <= alpha) return 0;
				if (result < beta) beta = result;
				if (result < score) score = result;
				num_moves++;
			}
		}

		return score;
	}

	private StateMachine machine;
	private List<Role> roles;
	private int self_index, origin_player, mob_n, repetitions;
	private double[] weights;
	private long finishBy;
	private boolean self_focus;

	private static final int GOAL_FN = 0;
	private static final int MOB_FN = 1;
	private static final int FOCUS_FN = 2;
	private static final int STATE_MOB_FN = 3;
	private static final int COMBO_FN = 4;
	private static final int MONTE_CARLO_FN = 5;
	private static final int DEFAULT = 5;
	private static final int MONTE_GOAL_FN = 6;
	private static final int NUM_FNS = 3;
	private static final double DISCOUNT_FACTOR = .9;
	private static final int UNKNOWN_DEFAULT = 1;


	protected int evalFn(int player, MachineState state, int fn) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		origin_player = player;
		switch (fn) {
		case 0: return goalProxFn(player, state);
		case 1: return nStepMobilityFn(player, state, mob_n);
		case 2: return nStepFocusFn(player, state, mob_n, false);
		//case 3: return nStepStateMobilityFn(player, state, mob_n + 1);
		case 4: return weightedCombo(player, state);
		case 5: return monteCarlo(player, state);
		case 6: return (monteCarlo(player, state) + goalProxFn(player, state)) / 2;
		default: return 1;
		}
	}

	protected int monteCarlo(int player, MachineState state) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		double[] avgScores = new double[roles.size()];
		double[] avgDepth = new double[1];

		machine.getAverageDiscountedScoresFromRepeatedDepthCharges(state, avgScores, avgDepth, DISCOUNT_FACTOR, repetitions);
		//System.out.println("Avg Score: " + avgScores[player] + " Avg Depth: " + avgDepth[0]);
		double self_value = avgScores[self_index];

		//Net Score i.e. Goal Value for our player - Avg Goal Value of Opponents
		double opp_value = 0;
		for (int i = (self_index + 1) % roles.size(); i != self_index; i = (i + 1) % roles.size()) {
			if (System.currentTimeMillis() >= finishBy) break;
			opp_value += machine.getGoal(state, roles.get(i));
		}
		opp_value /= (roles.size() - 1);
		return (int)(self_value - opp_value);
	}

    protected MachineState performDepthCharge(MachineState state, final int[] theDepth) throws TransitionDefinitionException, MoveDefinitionException {
        int nDepth = 0;
        while(!machine.isTerminal(state)) {
        	if (System.currentTimeMillis() >= finishBy) break;
            nDepth++;
            state = machine.getNextStateDestructively(state, machine.getRandomJointMove(state));
        }
        if(theDepth != null)
            theDepth[0] = nDepth;
        return state;
    }

    protected void getAverageDiscountedScoresFromRepeatedDepthCharges(final MachineState state, final double[] avgScores,
    		final double[] avgDepth, final double discountFactor, final int repetitions) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        avgDepth[0] = 0;
        for (int j = 0; j < avgScores.length; j++) {
            avgScores[j] = 0;
        }
        final int[] depth = new int[1];
        int i;
        for (i = 0; i < repetitions; i++) {
        	if (System.currentTimeMillis() >= finishBy) break;
            MachineState stateForCharge = state.clone();
            stateForCharge = performDepthCharge(stateForCharge, depth);
            avgDepth[0] += depth[0];
            final double accumulatedDiscountFactor = Math.pow(discountFactor, depth[0]);
            for (int j = 0; j < avgScores.length; j++) {
                avgScores[j] += machine.getGoal(stateForCharge, roles.get(j)) * accumulatedDiscountFactor;
            }
        }
        if (i == 0) return;
        avgDepth[0] /= i;
        for (int j = 0; j < avgScores.length; j++) {
            avgScores[j] /= i;
        }
    }

	protected int nStepMobilityFn(int player, MachineState state, int n) throws MoveDefinitionException, TransitionDefinitionException {
		int mobility = (int) (100 * (nStepActions(player, state, n) /
				machine.findActions(roles.get(origin_player)).size()));
		return mobility;
	}

	//Assumes origin_player and opponents moves are uniformly randomly distributed
	//Consider changing to origin_player maximizing possible moves
	protected double nStepActions(int player, MachineState state, int n) throws MoveDefinitionException, TransitionDefinitionException {
		if (machine.isTerminal(state))
			return 0;
		if (n == 0) {
			return machine.getLegalMoves(state, roles.get(origin_player)).size();
		}

		List<List<Move>> jointMoves = machine.getLegalJointMoves(state);
		double totalMoves = 0;
		int nextPlayer = (player + 1) % roles.size();
		if (nextPlayer == origin_player) n-= 1;
		for (List<Move> jointMove: jointMoves) {
			if (System.currentTimeMillis() >= finishBy) break;
			MachineState nextState = machine.getNextState(state, jointMove);
			totalMoves += nStepActions(nextPlayer, nextState, n);
		}
		return totalMoves / jointMoves.size();
	}

	protected int nStepFocusFn(int player, MachineState state, int n, boolean self_focus) throws MoveDefinitionException, TransitionDefinitionException {
		if (self_focus) return 100 - nStepMobilityFn(player, state, n); //Reduce player's mobility
		//Reduce the average mobility of all opponents
		double opp_focus = 0;
		for (int i = (player + 1) % roles.size(); i != player; i = (i + 1) % roles.size()) {
			if (System.currentTimeMillis() >= finishBy) break;
			origin_player = i;
			opp_focus += (100 - nStepMobilityFn(player, state, (n == 0 ? 1 : n)));
		}
		return (int) opp_focus / (roles.size() - 1);
	}

	private static double sigmoid(double x) {
	    return 1 / (1 + Math.exp(-x));
	}

	//problematic to map score to {0, ..., 100} unless you know total number of states in the game
	protected int nStepStateMobilityFn(int player, MachineState state, int n) throws MoveDefinitionException, TransitionDefinitionException {
		HashSet<MachineState> uniqueStates = new HashSet<MachineState>();
		statesReachable(player, state, n, uniqueStates);
		return (int) (10 * sigmoid(uniqueStates.size() - 10));
	}

	//Only adds states which are reachable by origin_player
	protected void statesReachable(int player, MachineState state, int n,
			HashSet<MachineState> uniqueStates) throws MoveDefinitionException, TransitionDefinitionException {
		if (machine.isTerminal(state) || n == 0 || (System.currentTimeMillis() >= finishBy))
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

	protected int goalProxFn(int player, MachineState state) throws GoalDefinitionException {
		int self_value = machine.getGoal(state, roles.get(self_index));
		int opp_value = 0;
		for (int i = (self_index + 1) % roles.size(); i != self_index; i = (i + 1) % roles.size()) {
			if (System.currentTimeMillis() >= finishBy) break;
			opp_value += machine.getGoal(state, roles.get(i));
		}
		opp_value /= (roles.size() - 1);
		return self_value - opp_value;
	}

	protected int weightedCombo(int player, MachineState state) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		double total = 0;
		for (int i = 0; i < NUM_FNS; i++) {
			if (System.currentTimeMillis() >= finishBy) break;
			total += weights[i] * evalFn(player, state, i);
		}
		return (int) total;
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		finishBy = timeout - 1000;
		return bestmove();
	}


	@Override
	public String getName() {
		return "IterativeDeep Player";
	}

}