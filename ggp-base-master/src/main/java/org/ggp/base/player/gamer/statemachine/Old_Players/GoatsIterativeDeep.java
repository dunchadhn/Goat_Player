package org.ggp.base.player.gamer.statemachine.Old_Players;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.ggp.base.player.gamer.statemachine.men_who_stare_at_goats.the_men_who_stare_at_goats;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;


public class GoatsIterativeDeep extends the_men_who_stare_at_goats {

	private StateMachine machine;
	private List<Role> roles;
	private int self_index, origin_player, mob_n, repetitions, num_simuls, correlationN;
	private double[] weights;
	private double[][] maxMin;
	private double[][] correlationParams;
	private double maxScore, minScore, diff;
	private long finishBy;

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
	private static final double UNKNOWN_CONSTANT = 5;
	private static final int NUM_CORR_PARAMS = 5;
	private int NUM_GOAL_FEATURES;
	private int NUM_FEATURES;
	private ArrayList<double[]> weightsHistory;

/*
 * INITIALIZATION & METAGAME
 */
	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		initializeGlobals();
		finishBy = timeout - 1000;
		initFeatures();
		learnWeights();//TO-DO: Keeping learning weights after meta-game
		System.out.println("Time Left: " + (timeout - System.currentTimeMillis()));
		for (int i = 0; i < maxMin.length; ++i) {
			printDoubleArray(maxMin[i], maxMin[i].length);
		}
		System.out.println("Max: " + maxScore + " Min: " + minScore);
		printDoubleArray(weights, weights.length);
	}

	protected void initializeGlobals() {
		machine = getStateMachine();
		Role role = getRole();
		roles = machine.getRoles();
		self_index = roles.indexOf(role);
		num_simuls = 1000000;
		NUM_GOAL_FEATURES = roles.size();
		correlationN = 0;
		weightsHistory = new ArrayList<double[]>();
	}

	protected void initFeatures() throws GoalDefinitionException, MoveDefinitionException {
		MachineState state = machine.getInitialState();
		ArrayList<Double> features = new ArrayList<Double>();
		for (int i = 0; i < roles.size(); ++i) {
			double goalVal = (double)machine.getGoal(state, roles.get(i));
			features.add(goalVal);
		}
		for (int i = 0; i < roles.size(); ++i) {
			double mobility = ((double)machine.getLegalMoves(state, roles.get(i)).size()) / machine.findActions(roles.get(i)).size();
			features.add(100 * mobility);
			features.add(100 * (1 - mobility));
		}
		NUM_FEATURES = features.size();
		maxMin = new double[NUM_FEATURES][2];//first index is min, second is max
		for (int i = 0; i < NUM_GOAL_FEATURES; ++i) {
			maxMin[i][1] = 100;
			maxMin[i][0] = 0;
		}
		for (int i = NUM_GOAL_FEATURES; i < features.size(); ++i) {
			maxMin[i][0] = features.get(i);
			maxMin[i][1] = features.get(i);
		}
		weights = new double[NUM_FEATURES];
	}

/*
 * PARAMETERIZING WEIGHTS / HEURISTICS
 */

	protected void tdLearning(double[] weights) {
		//
	}

	protected double[] getFeatures(MachineState state) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		double[] features = new double[NUM_FEATURES];
		int index = 0;
		for (int i = 0; i < roles.size(); ++i) {
			double goalVal = (double)machine.getGoal(state, roles.get(i));
			features[index++] = goalVal;
		}
		for (int i = 0; i < roles.size(); ++i) {
			double mobility = ((double)machine.getLegalMoves(state, roles.get(i)).size()) / machine.findActions(roles.get(i)).size();
			features[index++] = 100 * mobility;
			features[index++] = 100 * (1 - mobility);
		}
		//add others later
		return features;
	}

	protected int getData(double [][]X, double[]Y) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		int i, nDepth, player;
		double goalVal;
		for (i = 0; i < num_simuls && System.currentTimeMillis() < finishBy; i += nDepth) {
			nDepth = 0; player = 0;
			MachineState state = machine.getInitialState();
			double[] features;
	        while(!machine.isTerminal(state)) {
	        	if (System.currentTimeMillis() >= finishBy) return i;
	        	if (player == self_index) {
	        		features = getFeatures(state);
		        	updateMaxMin(features);
		        	X[i + nDepth++] = features;
	        	}
	        	player = (player + 1) % roles.size();
	            state = machine.getNextStateDestructively(state, machine.getRandomJointMove(state));
	        }
	        goalVal = (double) machine.getGoal(state, roles.get(self_index));
	        for (int j = 0; j < nDepth; ++j)
	        	Y[i + j] = goalVal;
		}
		return i;
	}

	protected void learnWeights() throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		double [][]X = new double[num_simuls][NUM_FEATURES];
		double []Y = new double[num_simuls];
		int datapts = getData(X, Y);
		System.out.println("Data Points: " + datapts);
		correlationParams = new double[NUM_FEATURES][NUM_CORR_PARAMS];
		for (int f = 0; f < NUM_FEATURES; ++f) {
			double[] X_feature = new double[datapts];
			for (int i = 0; i < datapts; ++i)
				X_feature[i] = X[i][f];
			weights[f] = correlation(f, X_feature, Y);//shouldn't we multiply by slope of line?
		}
		weightsHistory.add(weights.clone());
		updateMaxMinSum(X, datapts);
	}

/*
 * FIND BEST MOVE
 */
	protected Move bestmove()
			throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		MachineState state = getCurrentState();
		List<Move> moves = machine.getLegalMoves(state, roles.get(self_index));
		if (moves.size() == 1) {
			//learnWeights();
			//printDoubleArray(weights, weights.length);
			return moves.get(0);
		}
		List<List<Move>> jointMoves = machine.getLegalJointMoves(state);
		ArrayList<Integer> actionValues = newArrayList(0, jointMoves.size());

		int depth = 1;
		int maxdepth = 100000;
		while (depth < maxdepth) {
			System.out.println("Depth: " + depth);
			int num_actions = 0;
			for(int i = 0; i < jointMoves.size(); ++i) {
				List<Move> jointMove = jointMoves.get(i);
				int nextPlayer = self_index + 1;
				MachineState nextState = machine.getNextState(state, jointMove);
				int result = alphabeta(nextPlayer % roles.size(), nextState, 0, 100, depth);
				System.out.println(jointMove.get(self_index) + " " + result);
				if(result == 100) {
					return jointMove.get(self_index);
				}
				if(System.currentTimeMillis() >= finishBy) break;
				actionValues.set(i, result);
				num_actions += 1;
			}
			System.out.println(num_actions + " Actions Deliberated of " + jointMoves.size() + " actions");
			System.out.println("Action Values: " + actionValues);
			if(System.currentTimeMillis() >= finishBy) break;
			depth += 1;
		}
		List<Integer> bestActions = findBestActions(actionValues);
		Random rn = new Random();
		int bestMoveIndex = bestActions.get(rn.nextInt(bestActions.size()));
		return jointMoves.get(bestMoveIndex).get(self_index);
	}

	protected ArrayList<Integer> findBestActions(ArrayList<Integer> actionValues) {
		ArrayList<Integer> bestIndices = new ArrayList<Integer>();
		int max = -1;
		for (int i = 0; i < actionValues.size(); ++i) {
			if (actionValues.get(i) > max) {
				max = actionValues.get(i);
				bestIndices = new ArrayList<Integer>();
				bestIndices.add(i);
			} else if (actionValues.get(i) == max) {
				bestIndices.add(i);
			}
		}
		return bestIndices;
	}

	protected int alphabeta(int player, MachineState state, int alpha, int beta, int d) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		if (machine.isTerminal(state)) {
			return machine.getGoal(state, roles.get(self_index));
		}
		if (d == 0) {
			return (int) evalFn(player, state, COMBO_FN);
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

/*
 * HEURISTICS
 */
	protected int evalFn(int player, MachineState state, int fn) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		origin_player = player;
		switch (fn) {
		case 0: return goalProxFn(state);
		//case 3: return nStepStateMobilityFn(player, state, mob_n + 1);
		case 4: return combo(player, state);
		case 5: return monteCarlo(player, state);
		default: return 1;
		}
	}

	protected double linearComb(double[] features) {
		double sum = 0;
		double diff, fraction;
		for (int i = 0; i < features.length; ++i) {
			diff = (maxMin[i][1] - maxMin[i][0]);
			if (diff != 0) {
				fraction = (features[i] - maxMin[i][0]) / diff;
				if (fraction < 0) fraction = 0;
				if (fraction > 1) fraction = 1;
				sum += weights[i] * fraction;
			}
		}
		return sum;
	}

	protected int combo(int player, MachineState state) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		double val = linearComb(getFeatures(state));
		double fraction = (val - minScore) / diff;
		if (fraction < 0) fraction = 0;
		else if (fraction > 1) fraction = 1;
		return (int)(98 * fraction + 1);
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

	protected double[] nStepMobilityFn(MachineState state, int n) throws MoveDefinitionException, TransitionDefinitionException {
		double[] mobility = nStepActions(state, n);
		for (int i = 0; i < roles.size(); ++i)
			mobility[i] *= ((((double)100) / machine.findActions(roles.get(origin_player)).size()));
		return mobility;
	}

	//Assumes origin_player and opponents moves are uniformly randomly distributed
	//Consider changing to origin_player maximizing possible moves
	protected double[] nStepActions(MachineState state, int n) throws MoveDefinitionException, TransitionDefinitionException {
		double[] numActions = new double[roles.size()];
		if (machine.isTerminal(state))
			return numActions;
		if (n == 0) {
			for (int i = 0; i < roles.size(); ++i) {
				numActions[i] = machine.getLegalMoves(state, roles.get(i)).size();
			}
			return numActions;
		}

		//Average number of actions, assuming everyone moves randomly
		//Could be updated to max estimated state value for MCTS
		List<List<Move>> jointMoves = machine.getLegalJointMoves(state);
		MachineState nextState;
		for (List<Move> jointMove: jointMoves) {
			nextState = machine.getNextState(state, jointMove);
			addArrays(nStepActions(nextState, n - 1), numActions);
		}
		divideArray(numActions, jointMoves.size());
		return numActions;
	}

	protected double[] nStepFocusFn(MachineState state, int n) throws MoveDefinitionException, TransitionDefinitionException {
		double[] focus = nStepMobilityFn(state, n);
		for (int i = 0; i < roles.size(); ++i) {
			focus[i] = 100 - focus[i];
		}
		return focus;
	}

	//problematic to map score to {0, ..., 100} unless you know total number of states in the game
	protected int nStepStateMobilityFn(int player, MachineState state, int n) throws MoveDefinitionException, TransitionDefinitionException {
		HashSet<MachineState> uniqueStates = new HashSet<MachineState>();
		statesReachable(player, state, n, uniqueStates);
		return 0;
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

	protected int goalProxFn(MachineState state) throws GoalDefinitionException {
		//return machine.getGoal(state, roles.get(self_index));
		int self_value = machine.getGoal(state, roles.get(self_index));
		if (roles.size() == 1) return self_value;
		int opp_value = 0;
		for (int i = (self_index + 1) % roles.size(); i != self_index; i = (i + 1) % roles.size()) {
			if (System.currentTimeMillis() >= finishBy) break;
			opp_value += machine.getGoal(state, roles.get(i));
		}
		opp_value /= (roles.size() - 1);
		return self_value - opp_value;
	}

	protected double longevityHeuristic(int player, MachineState state) throws TransitionDefinitionException, MoveDefinitionException {
		int longev_simuls = 5;
		int i;
		double depthAvg = 0;
		for (i = 0; i < longev_simuls && System.currentTimeMillis() < finishBy; ++i) {
			int nDepth = 0;
	        while(!machine.isTerminal(state)) {
	        	if (System.currentTimeMillis() >= finishBy) return (depthAvg / (i > 0 ? i : 1));
	            state = machine.getNextStateDestructively(state, machine.getRandomJointMove(state));
	            nDepth++;
	        }
	        depthAvg += nDepth;
		}
		return depthAvg / i;
	}

/*
 * SUPPORTING FUNCTIONS
 */

	/*
	 * The values of the arrays will be pairwise summed, and stored in arr1
	*/
	protected void addArrays(double[] arr1, double[] arr2) {
		assert arr1.length == arr2.length;//Remove during game
		for (int i = 0; i < arr1.length; ++i) {
			arr1[i] += arr2[i];
		}
	}

	protected void divideArray(double[] arr, int d) {
		assert d != 0;//Remove during game
		for (int i = 0; i < arr.length; ++i) {
			arr[i] /= d;
		}
	}

	protected ArrayList<Integer> newArrayList(int defaultVal, int size) {
		ArrayList<Integer> arr = new ArrayList<Integer>(size);
		for (int i = 0; i < size; ++i)
			arr.add(defaultVal);
		return arr;
	}

	protected void printDoubleArray(double[] arr, int length) {
		System.out.print("[");
		for (int i = 0; i < (length - 1); ++i) {
			System.out.print(arr[i] + ", ");
		}
		System.out.println(arr[length - 1] + "]");
	}

	protected void updateMaxMin(double[] features) {
		for (int i = NUM_GOAL_FEATURES; i < features.length; ++i) {
			if (features[i] > maxMin[i][1])
				maxMin[i][1] = features[i];
			if (features[i] < maxMin[i][0])
				maxMin[i][0] = features[i];
		}
	}

	protected double[] linearCombDefaultGoal(double[] features) {
		double[] bounds = new double[2];//second index is upper bound
		double diff, fraction;
		for (int i = 0; i < NUM_GOAL_FEATURES; ++i) {
			if (weights[i] > 0)
				bounds[1] += weights[i];
			else
				bounds[0] += weights[i];
		}
		for (int i = NUM_GOAL_FEATURES; i < features.length; ++i) {
			diff = (maxMin[i][1] - maxMin[i][0]);
			if (diff != 0) {
				fraction = (features[i] - maxMin[i][0]) / diff;
				if (weights[i] > 0)
					bounds[1] += weights[i] * fraction;
				else
					bounds[0] += weights[i] * fraction;
			}
		}
		return bounds;
	}

	protected void updateMaxMinSum(double[][]X, int length) {
		for (int i = 0; i < length; ++i) {
			double[] bounds = linearCombDefaultGoal(X[i]);
			if (bounds[1] > maxScore) maxScore =  bounds[1];
			if (bounds[0] < minScore) minScore =  bounds[0];
		}
		diff = maxScore - minScore;

	}

	protected double correlation(int f, double[] X, double[] Y) {
	    int n = X.length;
	    for (int i = 0; i < n; ++i) {
	      double x = X[i];
	      double y = Y[i];
	      correlationParams[f][0] += x;//sx
	      correlationParams[f][1] += y;//sy
	      correlationParams[f][2] += x * x;//sxx
	      correlationParams[f][3] += y * y;//syy
	      correlationParams[f][4] += x * y;//sxy
	    }
	    correlationN += n;

	    // covariation
	    double cov = correlationParams[f][4] / correlationN - correlationParams[f][0] * correlationParams[f][1] / correlationN / correlationN;
	    // standard error of x
	    double sigmax = Math.sqrt(correlationParams[f][2] / correlationN -  correlationParams[f][0] * correlationParams[f][0] / correlationN / correlationN);
	    if (sigmax == 0) return 0;
	    // standard error of y
	    double sigmay = Math.sqrt(correlationParams[f][3] / correlationN -  correlationParams[f][1] * correlationParams[f][1] / correlationN / correlationN);
	    if (sigmay == 0) return 0;

	    // correlation is just a normalized covariation
	    return cov / sigmax / sigmay;
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

	@Override
	public void stateMachineStop() {
		System.out.println(weightsHistory);
	}

	@Override
	public void stateMachineAbort() {
		System.out.println(weightsHistory);
	}

}