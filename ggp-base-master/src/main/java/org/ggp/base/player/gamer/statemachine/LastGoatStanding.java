package org.ggp.base.player.gamer.statemachine;


import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
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


public class LastGoatStanding extends FactorGamer {
	protected Player p;
	private XStateMachine machine;
	private Stack<data> stack;
	private List<Role> roles;
	private int self_index, num_threads;
	private volatile int depthCharges, last_depthCharges;
	private long finishBy;
	private volatile XNode root;
	private List<XNode> path;
	private CompletionService<Struct> executor;
	private ThreadPoolExecutor thread_pool;
	private Thread thread;
	private Thread solver;
	private ThreadStateMachine[] thread_machines;
	private ThreadStateMachine background_machine;
	private ThreadStateMachine solver_machine;
	private volatile int num_per;
	private ConcurrentHashMap<OpenBitSet, Double> valueMap;
	private volatile double total_background = 0;
	private volatile double total_threadpool = 0;
	private volatile double loops = 0;
	private volatile double play_loops = 0;
	private int num_players = 1;
	private boolean single = true;
	private int buffer = 2500;

	public class Struct {
		public double v;
		public List<XNode> p;
		public int n;

		public Struct(double val, List<XNode> arr, int num) {
			this.v = val;
			this.p = arr;
			this.n = num;
		}
	}

	public void init(XStateMachine mac, int players) {
		machine = mac;
		num_players = players;
		single = false;
		buffer = 3000;
	}

	@Override
	public XStateMachine getInitialStateMachine() {
		return new XStateMachine();
	}

	@Override
	public void stateMachineMetaGame(long timeout, OpenBitSet curr, Role role)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException, InterruptedException, ExecutionException {
		initialize(timeout, curr, role);

		int num_rests = (int) ((finishBy - System.currentTimeMillis()) / 1000);
		if (num_rests < 0) {
			return;
		}
		for (int i = 0; i < num_rests; ++i) {
			Thread.sleep(1000);
			double avg_back = total_background/loops;
			double avg_threadpool = total_threadpool/play_loops;
			double num = 10 * num_threads * (avg_back/avg_threadpool);
			num_per = (int) num;
			if (num_per < 1) num_per = 1;
		}
		System.out.println("Depth Charges: " + depthCharges);
		System.out.println("Avg Background: " + total_background/loops);
		System.out.println("Avg Threadpool: " + total_threadpool/play_loops);
		System.out.println("Number of playouts per thread: " + num_per);
		bestMove(root);
		last_depthCharges = 0;
	}

	protected void initialize(long timeout, OpenBitSet curr, Role role) throws MoveDefinitionException, TransitionDefinitionException, InterruptedException {
		valueMap = new ConcurrentHashMap<OpenBitSet, Double>();
		if(single) {
			machine = getStateMachine();
		}
		roles = machine.getRoles();
		self_index = roles.indexOf(role);
		root = new XNode(curr);

		num_per = Runtime.getRuntime().availableProcessors() / num_players;
		num_threads = (Runtime.getRuntime().availableProcessors()) / num_players;
		thread_pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(num_threads);
		executor = new ExecutorCompletionService<Struct>(thread_pool);
		thread_machines = new ThreadStateMachine[num_threads];
		for (int i = 0; i < num_threads; ++i) {
			thread_machines[i] = new ThreadStateMachine(machine,self_index);
		}
		background_machine = new ThreadStateMachine(machine,self_index);
		solver_machine = new ThreadStateMachine(machine,self_index);
		Expand(root);
		thread = new Thread(new runMCTS());
		solver = new Thread(new solver());
		depthCharges = 0;
		last_depthCharges = 0;
		thread.start();
		solver.start();

		finishBy = timeout - buffer;
		System.out.println("NumThreads: " + num_threads);
	}

	@Override
	public MoveStruct stateMachineSelectMove(long timeout, OpenBitSet curr, List<Move> moves)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException, InterruptedException, ExecutionException {

		depthCharges = 0;
		total_background = 0;
		total_threadpool = 0;
		loops = 0;
		play_loops = 0;
		System.out.println("Background Depth Charges: " + last_depthCharges);
		finishBy = timeout - buffer;
		return MCTS(curr, moves);
	}

	protected void initializeMCTS(OpenBitSet currentState, List<Move> moves) throws MoveDefinitionException, TransitionDefinitionException, InterruptedException {
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

	protected MoveStruct MCTS(OpenBitSet curr, List<Move> moves) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException, InterruptedException, ExecutionException {
		initializeMCTS(curr, moves);
		if (!solver.isAlive()) {
			solver = new Thread(new solver());
		}
		if (!thread.isAlive()) {
			if (valueMap.get(root.state) == null) valueMap.clear();
			thread = new Thread(new runMCTS());
		}
		thread_pool.getQueue().clear();
		//valueMap.clear();
		int num_rests = (int) ((finishBy - System.currentTimeMillis()) / 1000);
		if (num_rests < 0) {
			return bestMove(root);
		}
		for (int i = 0; i < num_rests; ++i) {
			Thread.sleep(1000);
			double avg_back = total_background/loops;
			double avg_threadpool = total_threadpool/play_loops;
			double num = 10 * num_threads * (avg_back/avg_threadpool);
			num_per = (int) num;
			if (num_per < 1) num_per = 1;
		}
		System.out.println("Depth Charges: " + depthCharges);
		System.out.println("Number of Select/Expand Loops " + loops);
		System.out.println("Avg Background: " + total_background/loops);
		System.out.println("Avg Threadpool: " + total_threadpool/play_loops);
		System.out.println("Number of playouts per thread: " + num_per);
		last_depthCharges = 0;
		return bestMove(root);
	}

	public class solver implements Runnable {
		@Override
		public void run() {
			try {
				while(true) {
					if (Solver() > -1) break;
					if (valueMap.get(root.state) == null) valueMap.clear();
				}
			} catch (MoveDefinitionException | TransitionDefinitionException | GoalDefinitionException e) {
				e.printStackTrace();
			}
		}
	}

	public class runMCTS implements Runnable {
		@Override
		public void run() {
			XNode root_thread;
			while (true) {
				double start = System.currentTimeMillis();
				root_thread = root;
				path = new ArrayList<XNode>();
				path.add(root_thread);
				try {
					Select(root_thread, path);
				} catch (MoveDefinitionException e) {
					e.printStackTrace();
				}
				XNode n = path.get(path.size() - 1);
				try {
					Expand(n, path);
				} catch (MoveDefinitionException | TransitionDefinitionException e) {
					e.printStackTrace();
				}
				n = path.get(path.size() - 1);
				executor.submit(new RunMe(n, path, num_per));

				while(true) {
					Future<Struct> f = executor.poll();
					if (f == null) break;
					Struct s = null;
			        try {
						s = f.get();
					} catch (InterruptedException | ExecutionException e) {
						e.printStackTrace();
					}
			        if (s != null) {
				        int num = s.n;
				        Backpropogate(s.v,s.p, num);
				        depthCharges += num;
				        last_depthCharges += num;
			        }
			    }
				total_background += (System.currentTimeMillis() - start);
				++loops;
			}
		}
	}

	public class RunMe implements Callable<Struct> {
		private XNode node;
		private List<XNode> p;
		private int num;

		public RunMe(XNode n, List<XNode> arr, int number) {
			this.node = n;
			this.p = arr;
			this.num = number;
		}
		@Override
		public Struct call() throws InterruptedException{
			double start = System.currentTimeMillis();
			double val = 0;
			double curr = 0;
			Double c = valueMap.get(node.state);
			if (c != null) {
				val += (curr * num);
				node.sum_x += (curr*num);
				node.sum_x2 += num*(curr*curr);
				node.n += num;
			} else {
				int thread_ind = (int) (Thread.currentThread().getId() % num_threads);
				ThreadStateMachine mac = thread_machines[thread_ind];
				for (int i = 0; i < num; ++i) {
					try {
						c = valueMap.get(node.state);
						if (c == null) {
							curr = mac.Playout(node);
						} else {
							curr = c;
						}
					} catch (MoveDefinitionException | TransitionDefinitionException | GoalDefinitionException e) {
						e.printStackTrace();
					}
					val += curr;
					node.sum_x += curr;
					node.sum_x2 += (curr*curr);
					++node.n;
				}
			}

			++play_loops;
			Struct s = new Struct(val, p, num);
			total_threadpool += (System.currentTimeMillis() - start);
			return s;
	    }
	}

	protected MoveStruct bestMove(XNode n) throws MoveDefinitionException, TransitionDefinitionException {
		Expand(n);
		double maxValue = Double.NEGATIVE_INFINITY;
		Move maxMove = n.legalMoves[0];
		int size = n.legalMoves.length;
		for(int i = 0; i < size; ++i) {
			Move move = n.legalMoves[i];
			double minValue = Double.POSITIVE_INFINITY;
			double visits = 0;
			for (List<Move> jointMove : n.legalJointMoves.get(move)) {
				XNode succNode = n.children.get(jointMove);
				double nodeValue;
				if (valueMap.containsKey(succNode.state)) {
					nodeValue = valueMap.get(succNode.state);

				} else if (succNode.updates != 0) {
					nodeValue = succNode.utility / succNode.updates;
					if (nodeValue < minValue) {
						visits = succNode.updates;
						minValue = nodeValue;
					}
				} else nodeValue = 1;
			}
			System.out.println("Move: " + move + " Value: " + (minValue == Double.POSITIVE_INFINITY ? "N/A" : String.valueOf(minValue)) + " Visits: " + visits);
			if (minValue > maxValue && minValue != Double.POSITIVE_INFINITY) {
				maxValue = minValue;
				maxMove = move;
			}
		}
		System.out.println(getName() + " Max Move: " + maxMove + " Max Value: " + maxValue);
		return new MoveStruct(maxMove,maxValue);
	}

	protected void Backpropogate(double val, List<XNode> path, int num) {
		int size = path.size();
		XNode nod = path.get(size - 1);
		double mean_square = nod.sum_x / nod.n;
		mean_square *= mean_square;
		double avg_square = nod.sum_x2 / nod.n;
		if (avg_square > mean_square) nod.C_CONST = Math.sqrt(avg_square - mean_square);
		if (nod.C_CONST < 60) nod.C_CONST = 60;
		for (int i = 0; i < size; ++i) {
			nod = path.get(i);
			nod.utility += val;
			nod.updates += num;
		}
	}

	protected void Select(XNode n, List<XNode> path) throws MoveDefinitionException {
		while(true) {
			++n.visits;
			if (background_machine.isTerminal(n.state)) return;
			if (n.children.isEmpty()) return;

			double maxValue = Double.NEGATIVE_INFINITY;
			double parentVal = n.C_CONST * Math.sqrt(Math.log(n.visits));
			XNode maxChild = null;
			int size = n.legalMoves.length;
			for(int i = 0; i < size; ++i) {
				Move move = n.legalMoves[i];
				double minValue = Double.NEGATIVE_INFINITY;
				XNode minChild = null;
				for (List<Move> jointMove : n.legalJointMoves.get(move)) {
					XNode succNode = n.children.get(jointMove);
					if (succNode.visits == 0) {
						++succNode.visits;
						path.add(succNode);
						return;
					}
					double nodeValue;
					Double val = valueMap.get(succNode.state);
					if (val != null) nodeValue = -val;
					else nodeValue = uctMin(succNode, parentVal);
					if (nodeValue > minValue) {
						minValue = nodeValue;
						minChild = succNode;
					}
				}
				Double val = valueMap.get(minChild.state);
				if (val != null) minValue = val;
				else minValue = uctMax(minChild, parentVal);
				if (minValue > maxValue) {
					maxValue = minValue;
					maxChild = minChild;
				}
			}
			path.add(maxChild);
			if (valueMap.containsKey(maxChild.state)) return;
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
		if (!n.expanded && !background_machine.isTerminal(n.state)) {
			if(n.started.getAndSet(true)) {
				while(true) {
					if (n.expanded) return;
				}
			}
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
				OpenBitSet state = background_machine.getNextState(n.state, jointMove);
				XNode child = new XNode(state);
				n.legalJointMoves.get(jointMove.get(self_index)).add(jointMove);
				n.children.put(jointMove, child);
			}
			n.expanded = true;
			path.add(n.children.get(background_machine.getRandomJointMove(n.state)));
		} else if (!background_machine.isTerminal(n.state)) {
			//System.out.println("ERROR. Tried to expand node that was previously expanded");
		}
	}

	protected void Expand(XNode n) throws MoveDefinitionException, TransitionDefinitionException {//Assume only expand from max node
		if (!n.expanded && !machine.isTerminal(n.state)) {
			if(n.started.getAndSet(true)) {
				while(true) {
					if (n.expanded) return;
				}
			}
			List<Move> moves = machine.getLegalMoves(n.state, self_index);
			int size = moves.size();
			n.legalMoves = moves.toArray(new Move[size]);
			for (int i = 0; i < size; ++i) {
				Move move = n.legalMoves[i];
				n.legalJointMoves.put(move, new ArrayList<List<Move>>());
			}
			for (List<Move> jointMove: machine.getLegalJointMoves(n.state)) {
				OpenBitSet state = machine.getNextState(n.state, jointMove);
				XNode child = new XNode(state);
				n.legalJointMoves.get(jointMove.get(self_index)).add(jointMove);
				n.children.put(jointMove, child);
			}
			n.expanded = true;
		} else if (!machine.isTerminal(n.state)) {
			//System.out.println("ERROR. Tried to expand node that was previously expanded");
		}
	}

	protected void Expand_solver(XNode n) throws MoveDefinitionException, TransitionDefinitionException {//Assume only expand from max node
		if (!n.expanded && !solver_machine.isTerminal(n.state)) {
			if(n.started.getAndSet(true)) {
				while(true) {
					if (n.expanded) return;
				}
			}
			List<Move> moves = solver_machine.getLegalMoves(n.state, self_index);
			int size = moves.size();
			n.legalMoves = moves.toArray(new Move[size]);
			for (int i = 0; i < size; ++i) {
				Move move = n.legalMoves[i];
				n.legalJointMoves.put(move, new ArrayList<List<Move>>());
			}
			for (List<Move> jointMove: solver_machine.getLegalJointMoves(n.state)) {
				OpenBitSet state = solver_machine.getNextState(n.state, jointMove);
				XNode child = new XNode(state);
				n.legalJointMoves.get(jointMove.get(self_index)).add(jointMove);
				n.children.put(jointMove, child);
			}
			n.expanded = true;
		} else if (!solver_machine.isTerminal(n.state)) {
			//System.out.println("ERROR. Tried to expand node that was previously expanded");
		}
	}


	@SuppressWarnings("deprecation")
	@Override
	public void stateMachineStop() {
		thread_pool.shutdownNow();
		thread.stop();
		//solver.stop();
		root = null;
		valueMap = null;
		stack = null;
		System.gc();
	}

	@SuppressWarnings("deprecation")
	@Override
	public void stateMachineAbort() {
		// TODO Auto-generated method stub
		thread_pool.shutdownNow();
		thread.stop();
		//solver.stop();
		root = null;
		valueMap = null;
		stack = null;
		System.gc();
	}


	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "LastGoatStanding Player";
	}

	@Override
	public void preview(Game g, long timeout) throws GamePreviewException {
		// TODO Auto-generated method stub

	}

	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException, InterruptedException, ExecutionException {
		// TODO Auto-generated method stub

	}

	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException, InterruptedException, ExecutionException {
		// TODO Auto-generated method stub
		return null;
	}

	protected int Solver() throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		System.out.println("Starting solver!");
		int alpha = 0;
		XNode root_thread = root;
		Expand(root_thread);
		if (solver_machine.isTerminal(root_thread.state)) {
			return solver_machine.getGoal(root_thread.state, self_index);
		}
		for (Move move : root_thread.legalMoves) {

			int minValue = 100;
			for (List<Move> jointMove : root_thread.legalJointMoves.get(move)) {
				XNode child = root_thread.children.get(jointMove);
				int result = iterative(child, alpha, minValue, root_thread);
				if (result == -1) return -1;
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
				System.out.println("GAME SOLVED");
				System.out.println();
				return minValue;
			}
			if (minValue > alpha) {
				alpha = minValue;
			}
		}
		System.out.println();
		System.out.println("GAME SOLVED, MAX SCORE: " + alpha);
		System.out.println();
		valueMap.put(root_thread.state, (double) alpha);
		return alpha;
	}

	public class data {
		public XNode n;
		public int alpha;
		public int beta;
		public int min;
		public int joint_ind = 0;
		public int joint_max = -1;
		public int value = 0;
		public int moves_ind = 0;
		public int moves_max = -1;

		public data(XNode node, int a, int m) {
			n = node;
			alpha = a;
			beta = m;
		}
	}

	protected int iterative(XNode node, int alpha, int beta, XNode solver_root) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		stack = new Stack<data>();
		data first = new data(node, alpha, beta);
		stack.push(first);
		while(!stack.isEmpty()) {
			if (!solver_root.equals(root)) {
				stack.clear();
				return -1;
			}
			data d = stack.pop();
			if (solver_machine.isTerminal(d.n.state)) {
				int val = solver_machine.getGoal(d.n.state, self_index);
				valueMap.put(d.n.state, (double)val);
				d.value = val;
				continue;
			}
			if (!valueMap.containsKey(d.n.state)) {
				if (d.moves_max < 0) {
					Expand_solver(d.n);
					d.moves_max = d.n.legalMoves.length - 1;
					Move move = d.n.legalMoves[0];
					d.min = d.beta;
					d.joint_max = d.n.legalJointMoves.get(move).size();
				} else if (d.joint_max > -1) {
					XNode child = d.n.children.get(d.n.legalJointMoves.get(d.n.legalMoves[d.moves_ind]).get(d.joint_ind));
					double val = valueMap.get(child.state);
					if (val <= d.alpha) {
						d.min = d.alpha;
						d.joint_ind = d.joint_max;
					} else if (val == 0) {
						d.min = 0;
						d.joint_ind = d.joint_max;
					} else if (val < d.min) {
						d.min = (int) val;
						++d.joint_ind;
					} else {
						++d.joint_ind;
					}
				}
				if ((d.moves_ind == d.moves_max) && (d.joint_ind == d.joint_max)) {
					if (d.min >= d.beta) {
						valueMap.put(d.n.state, (double) d.beta);
						d.value = d.beta;
						continue;
					}
					if (d.min == 100) {
						valueMap.put(d.n.state, (double) 100);
						d.value = 100;
						continue;
					}
					if (d.min > d.alpha) {
						d.alpha = d.min;
					}
					valueMap.put(d.n.state, (double) d.alpha);
					d.value = d.alpha;
					continue;
				}
				if (d.joint_ind == d.joint_max) {
					if (d.min >= d.beta) {
						valueMap.put(d.n.state, (double) d.beta);
						d.value = d.beta;
						continue;
					}
					if (d.min == 100) {
						valueMap.put(d.n.state, (double) 100);
						d.value = 100;
						continue;
					}
					if (d.min > d.alpha) {
						d.alpha = d.min;
					}
					++d.moves_ind;
					d.min = d.beta;
					Move move = d.n.legalMoves[d.moves_ind];
					d.joint_max = d.n.legalJointMoves.get(move).size();
					d.joint_ind = 0;
				}
				XNode child = d.n.children.get(d.n.legalJointMoves.get(d.n.legalMoves[d.moves_ind]).get(d.joint_ind));
				stack.push(d);
				stack.push(new data(child,d.alpha,d.min));
			}
		}
		return first.value;
	}
}

