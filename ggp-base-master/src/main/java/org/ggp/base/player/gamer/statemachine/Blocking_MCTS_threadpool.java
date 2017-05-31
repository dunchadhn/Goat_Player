package org.ggp.base.player.gamer.statemachine;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.lucene.util.OpenBitSet;
import org.ggp.base.apps.player.Player;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.ThreadStateMachine;
import org.ggp.base.util.statemachine.XStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;


public class Blocking_MCTS_threadpool extends XStateMachineGamer {
	protected Player p;
	private XStateMachine machine;
	private List<Role> roles;
	private int self_index, num_threads;
	private volatile int depthCharges, last_depthCharges;
	private long finishBy;
	private volatile XNode root, n;
	private List<XNode> path;
	private CompletionService<Double> executor;
	private ThreadPoolExecutor thread_pool;
	private Thread thread;
	private Thread solver;
	private ThreadStateMachine[] thread_machines;
	private ThreadStateMachine background_machine;
	private ThreadStateMachine solver_machine;
	//private volatile double total_select = 0;
	//private volatile double total_expand = 0;
	//private volatile double total_playout = 0;
	private volatile int loops = 0;
	//private volatile double total_backpropagate = 0;
	//private volatile int play_loops = 0;

	private static final double C_CONST = 50;

	public class Struct {
		public double v;
		public List<XNode> p;

		public Struct(double val, List<XNode> arr) {
			this.v = val;
			this.p = arr;
		}
	}

	@Override
	public XStateMachine getInitialStateMachine() {
		return new XStateMachine();
	}

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException, InterruptedException, ExecutionException {
		initialize(timeout);
		/*OpenBitSet state = machine.getInitialState();
		OpenBitSet next = state;
		double time = 0;
		double runs = 0;
		while(System.currentTimeMillis() < finishBy) {
			double start = System.currentTimeMillis();
			double val = Playout(new XNode(state));
			time += (System.currentTimeMillis() - start);
			++runs;
		}
		System.out.println("Avg Playout: " + time/runs);*/
		//doMCTS();
		Thread.sleep(finishBy - System.currentTimeMillis());
		System.out.println("Depth Charges: " + depthCharges);
		/*System.out.println("Avg Select: " + total_select/loops);
		System.out.println("Avg Expand: " + total_expand/loops);
		System.out.println("Avg Backprop: " + total_backpropagate/depthCharges);
		System.out.println("Avg Playout: " + total_playout/play_loops);*/
		last_depthCharges = 0;
	}

	protected void initialize(long timeout) throws MoveDefinitionException, TransitionDefinitionException, InterruptedException {
		machine = getStateMachine();
		roles = machine.getRoles();
		self_index = roles.indexOf(getRole());
		root = new XNode(getCurrentState());

		num_threads = Runtime.getRuntime().availableProcessors() * 8;
		thread_pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(num_threads);
		executor = new ExecutorCompletionService<Double>(thread_pool);
		thread_machines = new ThreadStateMachine[num_threads];
		for (int i = 0; i < num_threads; ++i) {
			thread_machines[i] = new ThreadStateMachine(machine);
		}
		background_machine = new ThreadStateMachine(machine);
		//solver_machine = new ThreadStateMachine(machine);
		Expand(root);
		thread = new Thread(new runMCTS());
		//solver = new Thread(new solver());
		depthCharges = 0;
		last_depthCharges = 0;
		thread.start();
		//solver.start();

		finishBy = timeout - 2500;
		System.out.println("NumThreads: " + num_threads);
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException, InterruptedException, ExecutionException {
		//More efficient to use Compulsive Deliberation for one player games
		//Use two-player implementation for two player games
		depthCharges = 0;
		//total_select = 0;
		//total_expand = 0;
		//total_playout = 0;
		loops = 0;
		//total_backpropagate = 0;
		//play_loops = 0;
		System.out.println("Background Depth Charges: " + last_depthCharges);
		finishBy = timeout - 2500;
		return MCTS();
	}

	protected void initializeMCTS() throws MoveDefinitionException, TransitionDefinitionException, InterruptedException {
		OpenBitSet currentState = getCurrentState();
		if (root == null) System.out.println("NULL ROOT");
		if (root.state.equals(currentState)) return;
		for (List<Move> jointMove : machine.getLegalJointMoves(root.state)) {
			OpenBitSet nextState = machine.getNextState(root.state, jointMove);
			if (currentState.equals(nextState)) {
				root = root.children.get(jointMove);
				if (root == null) System.out.println("NOT IN MAP");
				return;
			}
		}
		System.out.println("ERROR. Current State not in tree");
		root = new XNode(currentState);
	}

	protected Move MCTS() throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException, InterruptedException, ExecutionException {
		initializeMCTS();
		Thread.sleep(finishBy - System.currentTimeMillis());
		//doMCTS();
		System.out.println("Depth Charges: " + depthCharges);
		System.out.println("Number of Select/Expand Loops " + loops);
		/*System.out.println("Avg Select: " + total_select/loops);
		System.out.println("Avg Expand: " + total_expand/loops);
		System.out.println("Avg Backprop: " + total_backpropagate/depthCharges);
		System.out.println("Avg Playout: " + total_playout/play_loops);*/
		last_depthCharges = 0;
		Move m = bestMove(root);
		return m;
	}

	public class solver implements Runnable {
		@Override
		public void run() {

		}
	}

	protected int alphabeta(int player, OpenBitSet state, int alpha, int beta) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		if (solver_machine.isTerminal(state))
			return solver_machine.getGoal(state, roles.get(self_index));

		List<List<Move>> jointMoves = solver_machine.getLegalJointMoves(state);
		int score = 100;
		if (player == self_index) score = 0;
		int nextPlayer = player + 1;

		if (player == self_index) {
			for (List<Move> jointMove: jointMoves) {
				OpenBitSet nextState = solver_machine.getNextState(state, jointMove);
				int result = alphabeta(nextPlayer % roles.size(), nextState, alpha, beta);
				if (result == 100 ||  result >= beta) return 100;
				if (result > alpha) alpha = result;
				if (result > score) score = result;
				if(System.currentTimeMillis() > finishBy) return 0;
			}
		} else {
			for (List<Move> jointMove: jointMoves) {
				OpenBitSet nextState = solver_machine.getNextState(state, jointMove);
				int result = alphabeta(nextPlayer % roles.size(), nextState, alpha, beta);
				if (result == 0 || score <= alpha) return 0;
				if (result < beta) beta = result;
				if (result < score) score = result;
				if(System.currentTimeMillis() > finishBy) return 0;
			}
		}

		return score;

	}


	public class runMCTS implements Runnable {
		@Override
		public void run() {
			XNode root_thread;
			while (true) {
				root_thread = root;
				path = new ArrayList<XNode>();
				path.add(root_thread);
				//double select_start = System.currentTimeMillis();
				try {
					Select(root_thread, path);
				} catch (MoveDefinitionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				//total_select += (System.currentTimeMillis() - select_start);
				n = path.get(path.size() - 1);
				//double expand_start = System.currentTimeMillis();
				try {
					Expand(n, path);
				} catch (MoveDefinitionException | TransitionDefinitionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				//total_expand += (System.currentTimeMillis() - expand_start);
				++loops;
				// spawn off multiple threads
				for(int i = 0; i < num_threads; ++i) {
					executor.submit(new RunMe());
				}
				double sum = 0;
				for (int i = 0; i < num_threads; ++i) {
					Future<Double> f = null;
					try {
						f = executor.take();
					} catch (InterruptedException e1) {
						break;
					}
					try {
						sum += f.get();
					} catch (InterruptedException | ExecutionException e) {
						// TODO Auto-generated catch block
						break;
					}
			    }
				//double back_start = System.currentTimeMillis();
				Backpropogate(sum,path);
				//total_backpropagate += (System.currentTimeMillis() - back_start);
				depthCharges += num_threads;
		        last_depthCharges += num_threads;
			}
		}
	}

	public class RunMe implements Callable<Double> {
		@Override
		public Double call() {
	    	double val = 0;
	    	//double start = System.currentTimeMillis();
			try {
				val = Playout(n);
			} catch (MoveDefinitionException | TransitionDefinitionException | GoalDefinitionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			//++play_loops;
			//total_playout += (System.currentTimeMillis() - start);
			return val;
	    }
	}

	protected Move bestMove(XNode n) throws MoveDefinitionException {
		double maxValue = Double.NEGATIVE_INFINITY;
		Move maxMove = n.legalMoves[0];
		int size = n.legalMoves.length;
		for(int i = 0; i < size; ++i) {
			Move move = n.legalMoves[i];
			double minValue = Double.POSITIVE_INFINITY;
			double visits = 0;
			for (List<Move> jointMove : n.legalJointMoves.get(move)) {
				XNode succNode = n.children.get(jointMove);
				if (succNode.visits != 0) {
					double nodeValue = succNode.utility / succNode.visits;
					if (nodeValue < minValue) {
						visits = succNode.visits;
						minValue = nodeValue;
					}
				}
			}
			System.out.println("Move: " + move + " Value: " + (minValue == Double.POSITIVE_INFINITY ? "N/A" : String.valueOf(minValue)) + " Visits: " + visits);
			if (minValue > maxValue && minValue != Double.POSITIVE_INFINITY) {
				maxValue = minValue;
				maxMove = move;
			}
		}
		System.out.println(getName() + " Max Move: " + maxMove + " Max Value: " + maxValue);
		return maxMove;
	}

	protected void Backpropogate(double val, List<XNode> path) {
		int size = path.size();
		XNode nod = path.get(size - 1);
		if (background_machine.isTerminal(nod.state)) {
			nod.isTerminal = true;
		}
		for (int i = 0; i < size; ++i) {
			nod = path.get(i);
			nod.utility += val;
			nod.visits += num_threads;
		}
	}

	protected double Playout(XNode n) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		int thread_ind = (int) (Thread.currentThread().getId() % num_threads);
		ThreadStateMachine mac = thread_machines[thread_ind];
		OpenBitSet state = n.state;
		while(!mac.isTerminal(state)) {
			state = mac.getRandomNextState(state);
		}
		return mac.getGoal(state, self_index);
	}

	protected void Select(XNode n, List<XNode> path) throws MoveDefinitionException {
		while(true) {
			if (background_machine.isTerminal(n.state)) return;
			if (n.children.isEmpty()) return;
			double maxValue = Double.NEGATIVE_INFINITY;
			double parentVal = C_CONST * Math.sqrt(Math.log(n.visits));
			XNode maxChild = null;
			int size = n.legalMoves.length;
			for(int i = 0; i < size; ++i) {
				Move move = n.legalMoves[i];
				double minValue = Double.NEGATIVE_INFINITY;
				XNode minChild = null;
				for (List<Move> jointMove : n.legalJointMoves.get(move)) {
					XNode succNode = n.children.get(jointMove);
					if (succNode.visits == 0) {
						path.add(succNode);
						return;
					}
					double nodeValue = uctMin(succNode, parentVal);
					if (nodeValue > minValue) {
						minValue = nodeValue;
						minChild = succNode;
					}
				}
				minValue = uctMax(minChild, parentVal);
				if (minValue > maxValue) {
					maxValue = minValue;
					maxChild = minChild;
				}
			}
			path.add(maxChild);
			n = maxChild;
		}
	}


	protected double uctMin(XNode n, double parentVisits) {
		double value = n.utility / n.visits;
		return -value + (parentVisits / Math.sqrt(n.visits));
	}

	protected double uctMax(XNode n, double parentVisits) {
		double value = n.utility / n.visits;
		return value + (parentVisits / Math.sqrt(n.visits));
	}

	protected void Expand(XNode n, List<XNode> path) throws MoveDefinitionException, TransitionDefinitionException {
		if (n.children.isEmpty() && !background_machine.isTerminal(n.state)) {
			List<Move> moves = background_machine.getLegalMoves(n.state, self_index);
			int size = moves.size();
			if (size < 1) {
				System.out.println("Size less than 1!!!!!!!!!!");
			}
			n.legalMoves = moves.toArray(new Move[size]);
			for (int i = 0; i < size; ++i) {
				Move move = n.legalMoves[i];
				n.legalJointMoves.put(move, new ArrayList<List<Move>>());
			}
			for (List<Move> jointMove: background_machine.getLegalJointMoves(n.state)) {
				XNode child = new XNode(background_machine.getNextState(n.state, jointMove));
				n.legalJointMoves.get(jointMove.get(self_index)).add(jointMove);
				n.children.put(jointMove, child);
			}
			path.add(n.children.get(background_machine.getRandomJointMove(n.state)));
		} else if (!background_machine.isTerminal(n.state)) {
			System.out.println("ERROR. Tried to expand node that was previously expanded");
		}
	}

	protected void Expand(XNode n) throws MoveDefinitionException, TransitionDefinitionException {//Assume only expand from max node
		if (n.children.isEmpty() && !machine.isTerminal(n.state)) {
			List<Move> moves = machine.getLegalMoves(n.state, self_index);
			int size = moves.size();
			n.legalMoves = moves.toArray(new Move[size]);
			for (int i = 0; i < size; ++i) {
				Move move = n.legalMoves[i];
				n.legalJointMoves.put(move, new ArrayList<List<Move>>());
			}
			for (List<Move> jointMove: machine.getLegalJointMoves(n.state)) {
				XNode child = new XNode(machine.getNextState(n.state, jointMove));
				n.legalJointMoves.get(jointMove.get(self_index)).add(jointMove);
				n.children.put(jointMove, child);
			}
		} else if (!machine.isTerminal(n.state)) {
			System.out.println("ERROR. Tried to expand node that was previously expanded");
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	public void stateMachineStop() {
		thread.stop();
	}

	@SuppressWarnings("deprecation")
	@Override
	public void stateMachineAbort() {
		// TODO Auto-generated method stub
		thread.stop();
	}


	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "Blocking_MCTS_threadpool Player";
	}

	@Override
	public void preview(Game g, long timeout) throws GamePreviewException {
		// TODO Auto-generated method stub

	}




}




