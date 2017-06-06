package org.ggp.base.player.gamer.statemachine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

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
	private int step_count;
	private OpenBitSet currentState;
	private HashMap<OpenBitSet, Integer> valueMap;
	private HashSet<OpenBitSet> path;
	private Thread helper;
	private FutureTask<MoveStruct> ft;
	private volatile boolean solved = false;
	private long finishby;
	@Override
	public XStateMachine getInitialStateMachine() {
		return new XStateMachine();
	}

	public Solver(XStateMachine mac, int total_steps) {
		machine = mac;
		step_count = total_steps - 1;
	}

	@Override
	public Boolean stateMachineMetaGame1(long timeout, OpenBitSet curr, Role role)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException, InterruptedException {
		currentState = curr;
		self_index = machine.getRoles().indexOf(role);
		roles = machine.getRoles();
		ft = new FutureTask<MoveStruct>(new Solver_Helper(curr));
		helper = new Thread(ft);
		valueMap = new HashMap<OpenBitSet, Integer>();
		helper.start();
		finishby = timeout - 3000;
		Thread.sleep(finishby - System.currentTimeMillis());
		return solved;
	}

	public class Solver_Helper implements Callable<MoveStruct> {
		private OpenBitSet state;

		public Solver_Helper(OpenBitSet curr) {
			state = curr;
		}

		@Override
		public MoveStruct call() throws Exception {
			// TODO Auto-generated method stub
			return bestmove(state);
		}

	}



	protected MoveStruct bestmove(OpenBitSet curr) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException, InterruptedException, ThreadDeath {
		//path = new HashSet<OpenBitSet>();
		//path.add(curr);
		OpenBitSet state = curr;
		int alpha = 0;
		List<Move> legals = machine.getLegalMoves(state, self_index);
		Move bestMove = legals.get(0);

		for (Move move : legals) {

			int minValue = 100;
			for (List<Move> jointMove : machine.getLegalJointMoves(state, self_index, move)) {
				OpenBitSet nextState = machine.getNextState(state, jointMove);
				int result;
				if (valueMap.containsKey(nextState)) result = valueMap.get(nextState);
				else {
					//if (path.contains(nextState)) continue;
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
				System.out.println("OUTSIDE SOLVER SOLVED");
				System.out.println();
				solved = true;
				return new MoveStruct(move, 100);
			}
			if (minValue > alpha) {
				alpha = minValue;
				bestMove = move;
			}
		}
		System.out.println();
		System.out.println("OUTSIDE SOLVER SOLVED, NO 100");
		System.out.println();
		solved = true;
		return new MoveStruct(bestMove,alpha);

	}


	protected int alphabeta(OpenBitSet state, int alpha, int beta, int steps) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException, InterruptedException, ThreadDeath {
		if (machine.isTerminal(state) || steps == 0) return machine.getGoal(state, self_index);

		List<Move> legals = machine.getLegalMoves(state, self_index);

		for (Move move : legals) {
			int minValue = beta;

			for (List<Move> jointMove : machine.getLegalJointMoves(state, self_index, move)) {
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

	public class data {
		public OpenBitSet state;
		public int alpha;
		public int beta;
		public int min;
		public int joint_ind = 0;
		public int joint_max = -1;
		public int value = 0;
		public int moves_ind = 0;
		public int moves_max = -1;
		public int steps;
		public List<Move> legals;
		public HashMap<Move, List<List<Move>>> jointMoves;

		public data(OpenBitSet s, int a, int m, int st) {
			state = s;
			alpha = a;
			beta = m;
			steps = st;
		}
	}

	protected int iterative(OpenBitSet state, int alpha, int beta, int steps) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException, InterruptedException, ThreadDeath {
		Stack<data> stack = new Stack<data>();
		data first = new data(state, alpha, beta, steps);
		stack.push(first);
		//path.add(state);
		while(!stack.isEmpty()) {
			data d = stack.pop();
			//path.remove(d.state);
			if (machine.isTerminal(d.state) || d.steps == 0) {
				int val = machine.getGoal(d.state, self_index);
				valueMap.put(d.state, val);
				d.value = val;
				continue;
			}
			if (!valueMap.containsKey(d.state)) {
				if (d.moves_max < 0) {
					d.legals = machine.getLegalMoves(d.state, self_index);
					d.moves_max = d.legals.size() - 1;
					Move move = d.legals.get(0);
					d.min = d.beta;
					d.jointMoves = new HashMap<Move, List<List<Move>>>();
					for (Move m: d.legals) {
						d.jointMoves.put(m,machine.getLegalJointMoves(d.state, self_index, move));
					}

					d.joint_max = d.jointMoves.get(move).size();
				} else if (d.joint_max > -1) {
					OpenBitSet nextState = machine.getNextState(d.state, d.jointMoves.get(d.legals.get(d.moves_ind)).get(d.joint_ind));
					int val = valueMap.get(nextState);
					if (val <= d.alpha) {
						d.min = d.alpha;
						d.joint_ind = d.joint_max;
					} else if (val == 0) {
						d.min = 0;
						d.joint_ind = d.joint_max;
					} else if (val < d.min) {
						d.min = val;
						++d.joint_ind;
					} else {
						++d.joint_ind;
					}
				}
				if ((d.moves_ind == d.moves_max) && (d.joint_ind == d.joint_max)) {
					if (d.min >= d.beta) {
						valueMap.put(d.state, d.beta);
						d.value = d.beta;
						continue;
					}
					if (d.min == 100) {
						valueMap.put(d.state, 100);
						d.value = 100;
						continue;
					}
					if (d.min > d.alpha) {
						d.alpha = d.min;
					}
					valueMap.put(d.state, d.alpha);
					d.value = d.alpha;
					continue;
				}
				if (d.joint_ind == d.joint_max) {
					if (d.min >= d.beta) {
						valueMap.put(d.state, d.beta);
						d.value = d.beta;
						continue;
					}
					if (d.min == 100) {
						valueMap.put(d.state, 100);
						d.value = 100;
						continue;
					}
					if (d.min > d.alpha) {
						d.alpha = d.min;
					}
					++d.moves_ind;
					Move move = d.legals.get(d.moves_ind);
					d.min = d.beta;
					d.joint_max = d.jointMoves.get(move).size();
					d.joint_ind = 0;
				}
				OpenBitSet nextState = machine.getNextState(d.state, d.jointMoves.get(d.legals.get(d.moves_ind)).get(d.joint_ind));
				stack.push(d);
				//path.add(d.state);
				//if (path.contains(nextState)) continue;
				stack.push(new data(nextState,d.alpha,d.min,d.steps - 1));
				//path.add(nextState);
			}
		}
		return first.value;
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



	@SuppressWarnings("deprecation")
	@Override
	public MoveStruct stateMachineSelectMove(long timeout, OpenBitSet curr, List<Move> moves)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException, InterruptedException, ExecutionException, ThreadDeath {
		if(!currentState.equals(curr)) {
			--step_count;
			currentState = curr;
		}
		if (solved) {
			MoveStruct m = bestmove(curr);
			m.solved = true;
			return m;
		}
		helper.stop();
		ft= new FutureTask<MoveStruct>(new Solver_Helper(curr));
		helper = new Thread(ft);
		valueMap.clear();
		helper.start();
		finishby = timeout - 3000;
		Thread.sleep(finishby - System.currentTimeMillis());
		if (ft.isDone()) {
			MoveStruct m = ft.get();
			m.solved = true;
			return m;
		} else {
			return new MoveStruct(moves.get(0), -100);
		}
	}

	@Override
	public void stateMachineStop() {
		// TODO Auto-generated method stub
		helper.stop();
	}

	@Override
	public void stateMachineAbort() {
		// TODO Auto-generated method stub
		helper.stop();
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
