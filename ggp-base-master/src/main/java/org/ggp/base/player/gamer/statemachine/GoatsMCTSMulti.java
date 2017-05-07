package org.ggp.base.player.gamer.statemachine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;


	public class GoatsMCTSMulti extends the_men_who_stare_at_goats {

		private int self_index;
		private StateMachine machine;
		private List<Role> roles;
		private double finishBy;
		private MachineState root;
		private Random rn;
		private ArrayList< HashMap<MachineState, double[]> > trees;
		private static final double GAMMA = .99;
		private static final double C_CONST = 40;//Set Dynamically

		@Override
		public void stateMachineMetaGame(long timeout)
				throws TransitionDefinitionException, MoveDefinitionException,
				GoalDefinitionException {
			finishBy = timeout - 1000;
			initGlobals();
		}

		protected void initGlobals() {
			machine = getStateMachine();
			Role role = getRole();
			roles = machine.getRoles();
			self_index = roles.indexOf(role);
			rn = new Random();
			trees = new ArrayList< HashMap<MachineState, double[]> >(roles.size());//Change to one hashmap
			for (int i = 0; i < roles.size(); ++i)
				trees.add(new HashMap<MachineState, double[]>());
			root = null;
		}

		protected void initializeMC(MachineState state) throws TransitionDefinitionException, MoveDefinitionException {
			root = state;
			for (int i = 0; i < roles.size(); ++i) {
				double[] nodeInfo = trees.get(i).get(state);
				if (nodeInfo == null)
					trees.get(i).put(state, new double[2]);
			}
		}

		protected void cleanUp(MachineState state) throws MoveDefinitionException, TransitionDefinitionException {
			if (state != null) {
				MachineState nextState;
				for (int i = 0; i < roles.size(); ++i) {
					//Remove old states from tree to reduce memory usage
					HashMap<MachineState, double[]> tree = trees.get(i);
					for (List<Move> move : machine.getLegalJointMoves(root)) {//old root
						nextState = machine.getNextState(root, move);
						if (tree.containsKey(nextState))
							tree.remove(nextState);
					}
				}
			}
		}
		protected void noopTime(MachineState state) throws TransitionDefinitionException, MoveDefinitionException {
			//
		}

		protected Move MonteTreeSearch(MachineState state) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
			//cleanUp(root);
			root = state;
			List<Move> legalMoves = machine.getLegalMoves(state, roles.get(self_index));
			if (legalMoves.size() == 1) {
				noopTime(state);
				return legalMoves.get(0);
			}
			double test = 0;
			initializeMC(state);
			ArrayList<MachineState> path;
			int nextPlayer, nDepth;
			double[] values;
			int depthCharges = 0;
			while (System.currentTimeMillis() < finishBy) {
				path = new ArrayList<MachineState>();
				path.add(state);
				nextPlayer = Select(self_index, path); //last element is the node to playout from
				values = new double[roles.size()];
				if (machine.isTerminal(path.get(path.size() - 1))) {
					nDepth = handleTerminal(path.get(path.size() - 1), values);
					path.remove(path.size() - 1);
				} else {
					Expand(path.get(path.size() - 1));
					if (System.currentTimeMillis() >= finishBy) break;
					nDepth = Playout(nextPlayer, path.get(path.size() - 1), values);
				}
				System.out.println(values[0] + ", " + values[1]);
				test += values[1];
				Backpropogate(path, values, nDepth);
				++depthCharges;
			}
			System.out.println("Average: " + (test / depthCharges));
			System.out.println("Depth Charges: " + depthCharges);
			return chooseBestMove(self_index, state);
		}

		protected int handleTerminal(MachineState state, double[] values) throws GoalDefinitionException {
			System.out.println(state);
			double[] nodeInfo = trees.get(self_index).get(state);
			double val;
			if (nodeInfo == null) {
				Expand(state);
				for (int i = 0; i < roles.size(); ++i) {
					nodeInfo = trees.get(i).get(state);
					nodeInfo[1] = 0;
					val = machine.getGoal(state, roles.get(i));
					nodeInfo[0] = val;
				}
			}
			for (int i = 0; i < roles.size(); ++i)
				values[i] = machine.getGoal(state, roles.get(i));
			return 1;
		}

		protected void Backpropogate(ArrayList<MachineState> path, double[] values, int nDepth) {
			double[] nodeInfo;
			for (int i = 0; i < roles.size(); ++i) {
				nodeInfo = trees.get(i).get(path.get(path.size() - 1));
				values[i] *= Math.pow(GAMMA, nDepth);
				++nodeInfo[1];
				nodeInfo[0] += values[i];
				for (int j = path.size() - 2; j >= 0; --j, values[i] *= GAMMA) {
					nodeInfo = trees.get(i).get(path.get(j));
					++nodeInfo[1];
					nodeInfo[0] += values[i];
				}
			}
		}

		/*
		 * Adds a node to the tree
		 */
		protected void Expand(MachineState state) {
			for (int i = 0; i < roles.size(); ++i) {
				if (trees.get(i).get(state) != null) System.out.println("ERROR!");
				else trees.get(i).put(state, new double[2]);
			}
		}
		/*
		 * Returns best candidate state according to UCT function. Do not pass in a terminal state or
		 * a state which has not yet been added to the tree
		 */
		protected MachineState selectFn(int player, MachineState state) throws TransitionDefinitionException, MoveDefinitionException {
			MachineState nextState, maxState = null;
			double visits = trees.get(player).get(state)[1];
			double ubMax = -1;
			double upperBound;
			//Works for turn-based games, in future, should take expectation
	        for (List<Move> move : machine.getLegalJointMoves(state)) {
	            nextState = machine.getNextState(state, move);
	            double[] nodeInfo = trees.get(player).get(nextState);
	            if (checkUnvisited(nextState, 0)) {
	            	return nextState;
	            }
	            /*if (checkUnvisited(nextState, (player == self_index ? roles.size() - 1 : 0))) {
	            	return nextState;
	            }*/
	            if (nodeInfo[1] == 0) upperBound = nodeInfo[0];
	            else upperBound = nodeInfo[0] / nodeInfo[1] + C_CONST * Math.sqrt(Math.log(visits) / nodeInfo[1]);
	            //if (upperBound >= 100 && nodeInfo[1] != 0) upperBound = 99;//try removing this
	            if (upperBound > ubMax) {
	            	ubMax = upperBound;
	            	maxState = nextState;
	            }
	        }
	        return maxState;
		}

		protected boolean checkUnvisited(MachineState state, int depth) throws TransitionDefinitionException, MoveDefinitionException {
			double[] nodeInfo = trees.get(self_index).get(state);
			if (nodeInfo == null) return true;
			if (depth == 0) return false;
			MachineState nextState;
			for (List<Move> move : machine.getLegalJointMoves(state)) {
	            nextState = machine.getNextState(state, move);
	            if (checkUnvisited(nextState, depth - 1)) return true;
			}
			return false;
		}

		/*
		 * The first element of path is the state to begin Select process from.
		 * "player" is the player whose value we are maximizing. Adds each state
		 * returned by selectFn to the path. The last element of path is the node
		 * AFTER a leaf node i.e. node from which to begin Playout. Returns the
		 * player whose value who is the maximizer for the given leaf node.
		 */
		protected int Select(int player, ArrayList<MachineState> path) throws TransitionDefinitionException, MoveDefinitionException {
			MachineState state = path.get(0);
			int nextPlayer = player;
			while(trees.get(player).get(state) != null && !machine.isTerminal(state)) {
				state = selectFn(nextPlayer, state);
				path.add(state);
				nextPlayer = (player + 1) % roles.size();
			}
			return nextPlayer;
		}

		protected MachineState getSuccState(int player, MachineState state) throws TransitionDefinitionException, MoveDefinitionException {
			List < List < Move> > jointMoves = machine.getLegalJointMoves(state);
			MachineState succState = machine.getNextStateDestructively(state, jointMoves.get(rn.nextInt(jointMoves.size())));
			return succState;
			//TO-DO: Implement epsilon greedy successor state
		}

		protected int Playout(int player, MachineState state, double[] values) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
			int nDepth = 0;
			while (!machine.isTerminal(state)) {
				state = getSuccState(player, state);
				player = (player + 1) % roles.size();
				++nDepth;
			}
			for (int i = 0; i < roles.size(); ++i)
				values[i] = (double)machine.getGoal(state, roles.get(i));
			return nDepth;
		}


		protected void printChildVals(MachineState state) throws MoveDefinitionException, TransitionDefinitionException {
			if (!machine.isTerminal(state)) {
				double[] nodeInfo;
				for (List<Move> move : machine.getLegalJointMoves(state)) {
		            MachineState nextState = machine.getNextState(state, move);
		            for (int i = 0; i < roles.size(); ++i) {
		            	nodeInfo = trees.get(i).get(nextState);
			            if (nodeInfo == null) {
			            	System.out.println("    Move: " + move + " was not visited");
			            } else {
			            	System.out.println("    Move: " + move + " Score: " +
				            		(nodeInfo[1] == 0 ? nodeInfo[0] : nodeInfo[0] / nodeInfo[1]) + " Visits: " + nodeInfo[1]);
			            }
		            }
				}
			}
		}

		protected double assignVal(int player, MachineState state) {
			double[] nodeInfo = trees.get(player).get(state);
			double val;
			double opp_val = 0;
            if (nodeInfo != null) {
            	if (nodeInfo[1] == 0)//0 visits indicates terminal state
	            	val = nodeInfo[0];
	            else
	            	val = nodeInfo[0] / nodeInfo[1];
            	return val;
            	/*for (int i = (player + 1) % roles.size(); i != player; i = (i + 1) % roles.size()) {
            		nodeInfo = trees.get(i).get(state);
            		opp_val += (nodeInfo[1] == 0 ? nodeInfo[0] : nodeInfo[0] / nodeInfo[1]);
            	}
            	opp_val /= (roles.size() - 1);
            	return val - opp_val;*/

            } else {
            	return -1;
            }
		}

		protected Move chooseBestMove(int player, MachineState state) throws TransitionDefinitionException, MoveDefinitionException {
			List< List <Move> > jointMoves = machine.getLegalJointMoves(state);
			MachineState nextState;
			double val;
			double[] nodeInfo;
			Move maxMove = jointMoves.get(0).get(self_index);
			double maxScore = Double.NEGATIVE_INFINITY;
			for (List<Move> move : jointMoves) {
	            nextState = machine.getNextState(state, move);
	            val = assignVal(player, nextState);
	            nodeInfo = trees.get(player).get(nextState);
	            System.out.println("Move: " + move.get(player) + " Score: " + val + " Visits: " + (nodeInfo == null ? 0 : nodeInfo[1]));
	            printChildVals(nextState);
	            if (val > maxScore) {
            		maxScore = val;
            		maxMove = move.get(player);
            	}
			}
			System.out.println("Max Score: " + maxScore);
			return maxMove;
		}


		@Override
		public Move stateMachineSelectMove(long timeout)
				throws TransitionDefinitionException, MoveDefinitionException,
				GoalDefinitionException {
			finishBy = timeout - 1000;
			return MonteTreeSearch(getCurrentState());
		}

		@Override
		public String getName() {
			return "GoatsMCTSMulti Player";
		}

		@Override
		public void stateMachineStop() {
			System.out.println(getMatch().getGoalValues());

		}

		@Override
		public void stateMachineAbort() {
			System.out.println(getMatch().getGoalValues());

		}

}
