package org.ggp.base.player.gamer.statemachine;
import java.util.ArrayList;
import java.util.HashMap;
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


public class GoatsMCTS extends the_men_who_stare_at_goats {

	private int num_visits;
	private MachineState root;
	private double[] rootInfo;
	private Random rn;
	private HashMap<MachineState, double[]> tree;
	private static final double GAMMA = .95;
	private static final double C_CONST = 40;

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		machine = getStateMachine();
		Role role = getRole();
		roles = machine.getRoles();
		self_index = roles.indexOf(role);
		rn = new Random();
		finishBy = timeout - 1000;
		tree = new HashMap<MachineState, double[]>();
		root = null;
	}

	protected void initializeMC(MachineState state) throws TransitionDefinitionException, MoveDefinitionException {
		root = state;
		double[] nodeInfo = tree.get(state);
		if (nodeInfo == null) {
			rootInfo = new double[2];
			tree.put(state, rootInfo);
		}
	}

	protected void noopTime(MachineState state) throws TransitionDefinitionException, MoveDefinitionException {
		if (root != null) {
			//Remove old states from tree to reduce memory usage
			for (List<Move> move : machine.getLegalJointMoves(root)) {//old root
	            tree.remove(machine.getNextState(state, move));
			}
		}
	}

	protected Move MonteTreeSearch(MachineState state) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		List<Move> legalMoves = machine.getLegalMoves(state, roles.get(self_index));
		if (legalMoves.size() == 1) {
			noopTime(state);
			return legalMoves.get(0);
		}

		initializeMC(state);
		ArrayList<MachineState> path;
		MachineState nextState;
		double val;
		double[] nodeInfo;

		while (System.currentTimeMillis() < finishBy) {
			nextState = selectFn(state);
			path = new ArrayList<MachineState>();
			path.add(nextState);
			Select(path);//last element is the node to playout from
			if (System.currentTimeMillis() >= finishBy) break;
			if (path.size() == 1) {
				Playout(path.get(0));
			} else {
				val = GAMMA * Playout(path.get(path.size() - 1));
				double visits;
				//only updates the first path.size() - 1 states, because updating the last state is handled by Playout
				for (int i = path.size() - 2; i >= 0; --i, val *= GAMMA) {
					nodeInfo = tree.get(path.get(i));
					visits = ++nodeInfo[1];
					nodeInfo[0] = (nodeInfo[0] * (visits - 1)  + val) / visits;
				}
			}
			++rootInfo[1];
		}
		return chooseBestMove(state);
	}

	/*
	 * Returns best candidate state according to UCT function. Do not pass in a terminal state or
	 * a state which has not yet been added to the tree
	 */
	protected MachineState selectFn(MachineState state) throws TransitionDefinitionException, MoveDefinitionException {
		MachineState nextState, maxState = null;
		double visits = tree.get(state)[1];
		double ubMax = -1;
        for (List<Move> move : machine.getLegalJointMoves(state)) {
            nextState = machine.getNextState(state, move);
            double[] nodeInfo = tree.get(nextState);
            if (nodeInfo == null) return nextState;
            double upperBound = nodeInfo[0] + C_CONST * Math.sqrt(Math.log(visits) / nodeInfo[1]);
            if (upperBound > ubMax) {
            	ubMax = upperBound;
            	maxState = nextState;
            }
        }
        return maxState;
	}

	protected void Select(ArrayList<MachineState> path) throws TransitionDefinitionException, MoveDefinitionException {
		MachineState state = path.get(0);
		while(tree.get(state) != null && !machine.isTerminal(state)) {
			state = selectFn(state);
			path.add(state);
		}
	}


	protected MachineState getSuccState(MachineState state) throws TransitionDefinitionException, MoveDefinitionException {
		List < List < Move> > jointMoves = machine.getLegalJointMoves(state);
		MachineState succState = machine.getNextState(state, jointMoves.get(rn.nextInt(jointMoves.size())));
		return succState;
		//TO-DO: Implement epsilon greedy successor state
	}

	/*
	 * Simulates moves until a terminal state is reached, choosing successor states according to getSuccState.
	 * Value of the terminal state is backpropogated
	 */
	protected double Playout(MachineState state) throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException {
		if (machine.isTerminal(state)) {
			double val = (double)machine.getGoal(state, roles.get(self_index));
			double[] nodeInfo = tree.get(state);
			if (nodeInfo == null) {
				nodeInfo = new double[2];
				nodeInfo[0] = val;
				nodeInfo[1] = Double.POSITIVE_INFINITY;
				tree.put(state,  nodeInfo);
			}
			return val;
		}
		double val = GAMMA * Playout(getSuccState(state));
		double[] nodeInfo = tree.get(state);
		if (nodeInfo == null) {
			nodeInfo = new double[2];
			nodeInfo[0] = val;
			nodeInfo[1] = 1;
			tree.put(state, nodeInfo);
		} else {
			double visits = ++nodeInfo[1];
			nodeInfo[0] = (nodeInfo[0] * (visits - 1)  + val) / visits;
		}
		return val;
	}


	protected Move chooseBestMove(MachineState state) throws TransitionDefinitionException, MoveDefinitionException {
		List< List <Move> > jointMoves = machine.getLegalJointMoves(state);
		MachineState nextState;
		Move maxMove = jointMoves.get(0).get(self_index);
		double maxScore = -1;
		for (List<Move> move : jointMoves) {
            nextState = machine.getNextState(state, move);
            double[] nodeInfo = tree.get(nextState);
            if (nodeInfo != null) {
            	System.out.println("Move: " + move.get(self_index) + " Score: " + nodeInfo[0] + " Visits: " + nodeInfo[1]);
            	if (nodeInfo[0] > maxScore) {
            		maxScore = nodeInfo[0];
            		maxMove = move.get(self_index);
            	}
            }
		}
		System.out.println("Max Score: " + maxScore);
		return maxMove;
	}




	protected int alphabeta(int player, MachineState state, int alpha, int beta, int d) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		if (machine.isTerminal(state)) {
			return machine.getGoal(state, roles.get(self_index));
		}
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

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		finishBy = timeout - 1000;
		return MonteTreeSearch(getCurrentState());
	}


	private StateMachine machine;
	private List<Role> roles;
	private int self_index, origin_player, mob_n, repetitions, num_simuls;
	private double[] weights;
	private double max_value, min_value, diff;
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
	private int NUM_FEATURES = 6;//Update


	protected int evalFn(int player, MachineState state, int fn) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		origin_player = player;
		switch (fn) {
		case 0: return goalProxFn(player, state);
		case 1: return nStepMobilityFn(player, state, mob_n);
		case 2: return nStepFocusFn(player, state, mob_n, false);
		//case 3: return nStepStateMobilityFn(player, state, mob_n + 1);
		case 4: return combo(player, state);
		case 5: return monteCarlo(player, state);
		case 6: return (monteCarlo(player, state) + goalProxFn(player, state)) / 2;
		default: return 1;
		}
	}

	protected double linearCombination(double[] arr1, double[] arr2) {
		double sum = 0;
		for (int i = 0; i < arr1.length; ++i) {
			sum +=  arr1[i] * arr2[i];
		}
		return 100 * ((sum - min_value) / diff);
	}

	protected int combo(int player, MachineState state) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		double[] features = getFeatures(player, state);
		return (int)linearCombination(weights, features);
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
		if (roles.size() == 1) return 0;
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
		return machine.getGoal(state, roles.get(self_index));
		/*int self_value = machine.getGoal(state, roles.get(self_index));
		if (roles.size() == 1) return self_value;
		int opp_value = 0;
		for (int i = (self_index + 1) % roles.size(); i != self_index; i = (i + 1) % roles.size()) {
			if (System.currentTimeMillis() >= finishBy) break;
			opp_value += machine.getGoal(state, roles.get(i));
		}
		opp_value /= (roles.size() - 1);
		return self_value - opp_value;*/
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
	public String getName() {
		return "GoatsMCTS Player";
	}

	protected void setMaxMin(double[] ws) {
		double max = 0, min = 0;
		for (int i = 0; i < ws.length; ++i) {
			if (ws[i] > 0) max += 100 * ws[i];
			else min += 100 * ws[i]; //really subtracting because ws[i] < 0
		}
		max_value = max;
		min_value = min;
		diff = max - min;
	}

	protected void printDoubleArray(double[] arr) {
		System.out.print("[");
		for (int i = 0; i < (arr.length - 1); ++i) {
			System.out.print(arr[i] + ", ");
		}
		System.out.println(arr[arr.length - 1] + "]");
	}

	protected void tdLearning(double[] weights) {
		//
	}

	protected double[] getFeatures(int player, MachineState state) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		double[] features = new double[NUM_FEATURES];
		int index = 0;
		for (int i = 0; i < roles.size(); ++i) {
			double goalVal = (double)machine.getGoal(state, roles.get(i));
			features[index++] = goalVal;
			features[index++] = 100 - goalVal;
		}
		for (int i = 0; i < roles.size(); ++i) {
			double mobility = ((double)machine.getLegalMoves(state, roles.get(i)).size()) / machine.findActions(roles.get(i)).size();
			features[index++] = 100 * mobility;
			features[index++] = 100 * (1 - mobility);
		}
		//add others later
		return features;
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

	/*The values of the arrays will be pairwise summed, and stored
	in arr1*/
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

	protected int getData(double [][]X, double[]Y) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		int i;
		for (i = 0; i < num_simuls && System.currentTimeMillis() < finishBy; ++i) {
			int nDepth = 0;
			MachineState state = machine.getInitialState();
			double[] features = new double[NUM_FEATURES];
	        while(!machine.isTerminal(state)) {
	        	if (System.currentTimeMillis() >= finishBy) return i;
	            addArrays(features, getFeatures(self_index, state));
	            nDepth++;
	            state = machine.getNextStateDestructively(state, machine.getRandomJointMove(state));
	        }
	        divideArray(features, nDepth);
	        X[i] = features;
	        Y[i] = (double) machine.getGoal(state, roles.get(self_index));
		}
		return i;
	}

	protected double correlation(double[] X, double[] Y) {
		double sx = 0.0;
	    double sy = 0.0;
	    double sxx = 0.0;
	    double syy = 0.0;
	    double sxy = 0.0;

	    int n = X.length;
	    for (int i = 0; i < n && System.currentTimeMillis() < finishBy; ++i) {
	      double x = X[i];
	      double y = Y[i];
	      sx += x;
	      sy += y;
	      sxx += x * x;
	      syy += y * y;
	      sxy += x * y;
	    }

	    // covariation
	    double cov = sxy / n - sx * sy / n / n;
	    // standard error of x
	    double sigmax = Math.sqrt(sxx / n -  sx * sx / n / n);
	    if (sigmax == 0) return 0;
	    // standard error of y
	    double sigmay = Math.sqrt(syy / n -  sy * sy / n / n);
	    if (sigmay == 0) return 0;


	    // correlation is just a normalized covariation
	    return cov / sigmax / sigmay;
	}

	protected void minMax(double[] minMax, double[] arr) {
		assert arr.length > 0;
		double min = arr[0];
		double max = arr[0];
		for (int i = 0; i < arr.length; ++i) {
			if (arr[i] > max) max = arr[i];
			if (arr[i] < min) min = arr[i];
		}
		minMax[0] = min;
		minMax[1] = max;
	}

//Better normalization scheme?
//dot product can range between -100 * NUM_FEATURES and 100 * NUM_FEATURES
	protected void normalize(double[] ws) {
		double[] minMax = new double[2];
		minMax(minMax, ws);
		double min = minMax[0];
		double max = minMax[1];
		double diff = max - min;
		for (int i = 0; i < ws.length; ++i) {
			ws[i] = (ws[i] - min) / diff;
		}
		double sum = 0;
		for (int i = 0; i < ws.length; ++i) {
			sum += ws[i];
		}
		for (int i = 0; i < ws.length; ++i) {
			ws[i] /= sum;
		}
		setMaxMin(ws);
	}

	protected double[] learnWeights() throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		double [][]X = new double[num_simuls][NUM_FEATURES];
		double []Y = new double[num_simuls];
		int datapts = getData(X, Y);
		double[] ws = new double[NUM_FEATURES];
		/*TO-DO: Start off by using only a part of the data, then
		 * continue using more as long as we have time remaining
		 */
		for (int f = 0; f < NUM_FEATURES; ++f) {
			double[] X_feature = new double[datapts];
			for (int i = 0; i < datapts; ++i)
				X_feature[i] = X[i][f];
			//print feature array
			ws[f] = correlation(X_feature, Y);//shouldn't we multiply by slope of line?
		}
		normalize(ws);
		return ws;
	}

	protected Move bestmove()
			throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		MachineState state = getCurrentState();
		List<Move> moves = machine.getLegalMoves(state, roles.get(self_index));
		if (moves.size() == 1) return moves.get(0);//TO-DO: Don't do this
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

	protected ArrayList<Integer> newArrayList(int defaultVal, int size) {
		ArrayList<Integer> arr = new ArrayList<Integer>(size);
		for (int i = 0; i < size; ++i)
			arr.add(defaultVal);
		return arr;
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


}

