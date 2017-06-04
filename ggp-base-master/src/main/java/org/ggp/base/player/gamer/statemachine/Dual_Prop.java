package org.ggp.base.player.gamer.statemachine;


import java.util.ArrayList;
import java.util.HashMap;
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
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.ThreadStateMachine;
import org.ggp.base.util.statemachine.XStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;


public class Dual_Prop extends FactorGamer {
	protected Player p;
	private XStateMachine machine;
	private XStateMachine no_step_machine;
	private ThreadStateMachine[] no_step_machines;
	private List<Role> roles;
	private int self_index, num_threads;
	private volatile int depthCharges, last_depthCharges;
	private long finishBy;
	private volatile DualNode root;
	private volatile OpenBitSet start_state;
	private List<DualNode> path;
	private CompletionService<Struct> executor;
	private ThreadPoolExecutor thread_pool;
	private Thread thread;
	//private Thread solver;
	private ThreadStateMachine[] thread_machines;
	private ThreadStateMachine background_machine;
	private ThreadStateMachine background_no_step;
	private ThreadStateMachine solver_machine;
	private volatile int num_charges, num_per;
	private HashMap<OpenBitSet, Integer> graph;
	private HashMap<OpenBitSet, DualNode> orig_graph;
	private List<DualNode> nodes;
	//private volatile double total_select = 0;
	//private volatile double total_expand = 0;
	private volatile double total_background = 0;
	private volatile double total_threadpool = 0;
	//private volatile double total_playout = 0;
	private volatile int loops = 0;
	//private volatile double total_backpropagate = 0;
	private volatile int play_loops = 0;
	private volatile double sum_x = 0;
	private volatile double sum_x2 = 0;
	private volatile int n = 0;
	private boolean no_step = false;

	private volatile double C_CONST = 50;

	public class Struct {
		public double v;
		public List<DualNode> p;
		public int n;

		public Struct(double val, List<DualNode> arr, int num) {
			this.v = val;
			this.p = arr;
			this.n = num;
		}
	}

	@Override
	public XStateMachine getInitialStateMachine() {
		return new XStateMachine();
	}

	@Override
	public void stateMachineMetaGame(long timeout, OpenBitSet currentState, Role role)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException, InterruptedException, ExecutionException {
		initialize(timeout, currentState, role);
		/*OpenBitSet state = machine.getInitialState();
		double time = 0;
		double runs = 0;
		while(System.currentTimeMillis() < finishBy) {
			double start = System.currentTimeMillis();
			background_machine.Playout(new DualNode(state));
			time += (System.currentTimeMillis() - start);
			++runs;
		}
		System.out.println("Avg Playout: " + time/runs);*/
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
		System.out.println("Num states: " + graph.size());
		System.out.println("C_CONST: " + C_CONST);
		System.out.println("Depth Charges: " + depthCharges);
		//System.out.println("Avg Select: " + total_select/loops);
		//System.out.println("Avg Expand: " + total_expand/loops);
		//System.out.println("Avg Backprop: " + total_backpropagate/depthCharges);
		//System.out.println("Avg Playout: " + total_playout/play_loops);
		System.out.println("Avg Background: " + total_background/loops);
		System.out.println("Avg Threadpool: " + total_threadpool/play_loops);
		System.out.println("Number of playouts per thread: " + num_per);
		last_depthCharges = 0;
	}

	protected void initialize(long timeout, OpenBitSet currentState, Role role) throws MoveDefinitionException, TransitionDefinitionException, InterruptedException {
		num_charges = 1;//Runtime.getRuntime().availableProcessors();
		num_per = Runtime.getRuntime().availableProcessors();
		num_threads = Runtime.getRuntime().availableProcessors();

		graph = new HashMap<OpenBitSet, Integer>();
		orig_graph = new HashMap<OpenBitSet, DualNode>();
		nodes = new ArrayList<DualNode>();
		machine = getStateMachine();
		roles = machine.getRoles();
		self_index = roles.indexOf(role);
		PropNet p = machine.getPropNet();
		PropNet no_step_p = PropNet.removeStepCounter(p);
		no_step_machines = new ThreadStateMachine[num_threads];
		no_step = false;
		if (no_step_p != null) {
			no_step_machine = new XStateMachine();
			no_step_machine.initialize(no_step_p);
			no_step_machine.getInitialState();
    		for (int i = 0; i < num_threads; ++i) {
    			no_step_machines[i] = new ThreadStateMachine(no_step_machine,self_index);
    		}
    		background_no_step = new ThreadStateMachine(no_step_machine,self_index);
    		no_step = true;
    		System.out.println("Found Step Counter!");
		}

		if(no_step) {
			OpenBitSet state = no_step_machine.getInitialState();
			root = new DualNode(state);
			graph.put(state, 0);
			nodes.add(root);
		} else {
			root = new DualNode(currentState);
			orig_graph.put(currentState, root);
		}


		start_state = currentState;

		thread_pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(num_threads);
		executor = new ExecutorCompletionService<Struct>(thread_pool);
		thread_machines = new ThreadStateMachine[num_threads];
		for (int i = 0; i < num_threads; ++i) {
			thread_machines[i] = new ThreadStateMachine(machine,self_index);
		}
		background_machine = new ThreadStateMachine(machine,self_index);
		//solver_machine = new ThreadStateMachine(machine,self_index);
		Expand(root, start_state);
		thread = new Thread(new runMCTS());
		//solver = new Thread(new solver());
		depthCharges = 0;
		last_depthCharges = 0;
		thread.start();
		//solver.start();

		finishBy = timeout - 3000;
		System.out.println("NumThreads: " + num_threads);
	}

	@Override
	public MoveStruct stateMachineSelectMove(long timeout, OpenBitSet currentState, List<Move> moves)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException, InterruptedException, ExecutionException {
		//More efficient to use Compulsive Deliberation for one player games
		//Use two-player implementation for two player games
		depthCharges = 0;
		//total_select = 0;
		//total_expand = 0;
		//total_playout = 0;
		total_background = 0;
		total_threadpool = 0;
		loops = 0;
		//total_backpropagate = 0;
		play_loops = 0;
		System.out.println("Background Depth Charges: " + last_depthCharges);
		finishBy = timeout - 3000;
		return MCTS(currentState, moves);
	}

	protected void initializeMCTS(OpenBitSet currentState, List<Move> moves) throws MoveDefinitionException, TransitionDefinitionException, InterruptedException {
		if (no_step) {
			start_state = currentState;
			OpenBitSet small_state = no_step_machine.getNextState(root.state, moves);
			if (root == null) System.out.println("NULL ROOT");
			if (root.state.equals(small_state)) return;
			for (List<Move> jointMove : no_step_machine.getLegalJointMoves(root.state)) {
				OpenBitSet nextState = no_step_machine.getNextState(root.state, jointMove);
				if (small_state.equals(nextState)) {
					root = nodes.get(graph.get(root.childrenStates.get(jointMove)));
					if (root == null) System.out.println("NOT IN MAP");
					return;
				}
			}
			System.out.println("ERROR. Current State not in tree");
			root = new DualNode(small_state);
			graph.put(small_state, nodes.size());
			nodes.add(root);
		} else {
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
			root = new DualNode(currentState);
			orig_graph.put(currentState, root);
		}
	}

	protected MoveStruct MCTS(OpenBitSet currentState, List<Move> moves) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException, InterruptedException, ExecutionException {
		initializeMCTS(currentState, moves);
		thread_pool.getQueue().clear();
		orig_graph.clear();
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
		System.out.println("Num states: " + graph.size());
		System.out.println("C_CONST: " + C_CONST);
		System.out.println("Depth Charges: " + depthCharges);
		System.out.println("Number of Select/Expand Loops " + loops);
		/*System.out.println("Avg Select: " + total_select/loops);
		System.out.println("Avg Expand: " + total_expand/loops);
		System.out.println("Avg Backprop: " + total_backpropagate/depthCharges);
		System.out.println("Avg Playout: " + total_playout/play_loops);*/
		System.out.println("Avg Background: " + total_background/loops);
		System.out.println("Avg Threadpool: " + total_threadpool/play_loops);
		System.out.println("Number of playouts per thread: " + num_per);
		last_depthCharges = 0;
		return bestMove(root);
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
			DualNode root_thread;
			while (true) {
				double start = System.currentTimeMillis();
				root_thread = root;
				OpenBitSet curr_state = start_state;
				path = new ArrayList<DualNode>();
				path.add(root_thread);
				//double select_start = System.currentTimeMillis();
				try {
					curr_state = Select(root_thread, path, curr_state);
				} catch (MoveDefinitionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				//total_select += (System.currentTimeMillis() - select_start);
				DualNode n = path.get(path.size() - 1);
				//double expand_start = System.currentTimeMillis();
				try {
					Expand(n, path, curr_state);
				} catch (MoveDefinitionException | TransitionDefinitionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				n = path.get(path.size() - 1);
				//total_expand += (System.currentTimeMillis() - expand_start);
				// spawn off multiple threads
				executor.submit(new RunMe(n, path, num_per,curr_state));

				while(true) {
					Future<Struct> f = executor.poll();
					if (f == null) break;
					Struct s = null;
			        try {
						s = f.get();
					} catch (InterruptedException | ExecutionException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
			        //double back_start = System.currentTimeMillis();
			        int num = s.n;
			        Backpropogate(s.v,s.p, num);
			        //total_backpropagate += (System.currentTimeMillis() - back_start);
			        depthCharges += num;
			        last_depthCharges += num;
			    }
				total_background += (System.currentTimeMillis() - start);
				++loops;
			}
		}
	}

	public class RunMe implements Callable<Struct> {
		private DualNode node;
		private List<DualNode> p;
		private int num;
		private OpenBitSet curr_state;

		public RunMe(DualNode n, List<DualNode> arr, int number, OpenBitSet curr) {
			this.node = n;
			this.p = arr;
			this.num = number;
			this.curr_state = curr;
		}
		@Override
		public Struct call() throws InterruptedException{
			double start = System.currentTimeMillis();
			double val = 0;
			double curr = 0;
			int thread_ind = (int) (Thread.currentThread().getId() % num_threads);
			for (int i = 0; i < num; ++i) {
				//double start = System.currentTimeMillis();
				try {
					curr = Playout(node, thread_ind, curr_state);
				} catch (MoveDefinitionException | TransitionDefinitionException | GoalDefinitionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				val += curr;
				sum_x += curr;
				sum_x2 += (curr*curr);
				++n;
				//++play_loops;
				//total_playout += (System.currentTimeMillis() - start);
			}
			++play_loops;
			total_threadpool += (System.currentTimeMillis() - start);
			Struct s = new Struct(val, p, num);
			return s;
	    }
	}

	public double Playout(DualNode n, int thread_ind, OpenBitSet curr_state) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		if (n.isSolved) return n.solvedValue;
		if (no_step) {
			OpenBitSet state = n.state;
			while(!thread_machines[thread_ind].isTerminal(curr_state)) {
				List<Move> moves = no_step_machines[thread_ind].getRandomJointMove(state);
				state = no_step_machines[thread_ind].getNextState(state, moves);
				curr_state = thread_machines[thread_ind].getNextState(curr_state, moves);
			}
			return thread_machines[thread_ind].getCurrGoal(curr_state, self_index);
		} else {
			OpenBitSet state = n.state;
			while(!thread_machines[thread_ind].isTerminal(state)) {
				state = thread_machines[thread_ind].getRandomNextState(state);
			}
			return thread_machines[thread_ind].getCurrGoal(state, self_index);
		}
	}

	protected MoveStruct bestMove(DualNode n) throws MoveDefinitionException {
		if(no_step) {
			double maxValue = Double.NEGATIVE_INFINITY;
			Move maxMove = n.legalMoves[0];
			int size = n.legalMoves.length;
			for(int i = 0; i < size; ++i) {
				Move move = n.legalMoves[i];
				double minValue = Double.POSITIVE_INFINITY;
				double visits = 0;
				for (List<Move> jointMove : n.legalJointMoves.get(move)) {
					Integer ind = graph.get(n.childrenStates.get(jointMove));
					if (ind != null) {
						DualNode succNode = nodes.get(ind);
						if (succNode.updates != 0) {
							double nodeValue = succNode.utility / succNode.updates;
							if (nodeValue < minValue) {
								visits = succNode.updates;
								minValue = nodeValue;
							}
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
			return new MoveStruct(maxMove,maxValue);
		} else {
			double maxValue = Double.NEGATIVE_INFINITY;
			Move maxMove = n.legalMoves[0];
			int size = n.legalMoves.length;
			for(int i = 0; i < size; ++i) {
				Move move = n.legalMoves[i];
				double minValue = Double.POSITIVE_INFINITY;
				double visits = 0;
				for (List<Move> jointMove : n.legalJointMoves.get(move)) {
					DualNode succNode = n.children.get(jointMove);
					if (succNode.updates != 0) {
						double nodeValue = succNode.utility / succNode.updates;
						if (nodeValue < minValue) {
							visits = succNode.updates;
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
			return new MoveStruct(maxMove,maxValue);
		}
	}

	protected void Backpropogate(double val, List<DualNode> path, int num) {
		if (no_step) {
			int size = path.size();
			DualNode nod = path.get(size - 1);
			if (background_no_step.isTerminal(nod.state)) {
				nod.isTerminal = true;
			}
			for (int i = 0; i < size; ++i) {
				nod = path.get(i);
				nod.utility += val;
				nod.updates += num;
			}
			double mean_square = sum_x / n;
			mean_square *= mean_square;
			double avg_square = sum_x2 / n;
			if (avg_square > mean_square) C_CONST = Math.sqrt(avg_square - mean_square);
			if (C_CONST < 50) C_CONST = 50;
		} else {
			int size = path.size();
			DualNode nod = path.get(size - 1);
			if (background_machine.isTerminal(nod.state)) {
				nod.isTerminal = true;
			}
			for (int i = 0; i < size; ++i) {
				nod = path.get(i);
				nod.utility += val;
				nod.updates += num;
			}
			double mean_square = sum_x / n;
			mean_square *= mean_square;
			double avg_square = sum_x2 / n;
			if (avg_square > mean_square) C_CONST = Math.sqrt(avg_square - mean_square);
			if (C_CONST < 50) C_CONST = 50;
		}
	}

	protected OpenBitSet Select(DualNode n, List<DualNode> path, OpenBitSet curr_state) throws MoveDefinitionException {
		if (no_step) {
			while(true) {
				++n.visits;
				if (background_machine.isTerminal(curr_state)) return curr_state;
				if (n.childrenStates.isEmpty()) return curr_state;
				double maxValue = Double.NEGATIVE_INFINITY;
				double parentVal = C_CONST * Math.sqrt(Math.log(n.visits));
				DualNode maxChild = null;
				List<Move> maxMove = null;
				int size = n.legalMoves.length;
				for(int i = 0; i < size; ++i) {
					Move move = n.legalMoves[i];
					double minValue = Double.NEGATIVE_INFINITY;
					DualNode minChild = null;
					List<Move> minMove = null;
					for (List<Move> jointMove : n.legalJointMoves.get(move)) {
						DualNode succNode = nodes.get(graph.get(n.childrenStates.get(jointMove)));
						if (succNode.visits == 0) {
							curr_state = background_machine.getNextState(curr_state, jointMove);
							++succNode.visits;
							path.add(succNode);
							return curr_state;
						}
						double nodeValue = uctMin(succNode, parentVal);
						if (nodeValue > minValue) {
							minValue = nodeValue;
							minChild = succNode;
							minMove = jointMove;
						}
					}
					minValue = uctMax(minChild, parentVal);
					if (minValue > maxValue) {
						maxValue = minValue;
						maxChild = minChild;
						maxMove = minMove;
					}
				}
				path.add(maxChild);
				n = maxChild;
				curr_state = background_machine.getNextState(curr_state, maxMove);
			}
		} else {
			while(true) {
				++n.visits;
				if (background_machine.isTerminal(n.state)) return curr_state;
				if (n.children.isEmpty()) return curr_state;
				double maxValue = Double.NEGATIVE_INFINITY;
				double parentVal = C_CONST * Math.sqrt(Math.log(n.visits));
				DualNode maxChild = null;
				int size = n.legalMoves.length;
				for(int i = 0; i < size; ++i) {
					Move move = n.legalMoves[i];
					double minValue = Double.NEGATIVE_INFINITY;
					DualNode minChild = null;
					for (List<Move> jointMove : n.legalJointMoves.get(move)) {
						DualNode succNode = n.children.get(jointMove);
						if (succNode.visits == 0) {
							++succNode.visits;
							path.add(succNode);
							return curr_state;
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
	}


	protected double uctMin(DualNode n, double parentVisits) {
		double value = n.utility / n.visits;
		return -value + (parentVisits / Math.sqrt(n.visits));
	}

	protected double uctMax(DualNode n, double parentVisits) {
		double value = n.utility / n.visits;
		return value + (parentVisits / Math.sqrt(n.visits));
	}

	protected void Expand(DualNode n, List<DualNode> path, OpenBitSet curr_state) throws MoveDefinitionException, TransitionDefinitionException {
		if(no_step) {
			if (n.children.isEmpty() && !background_machine.isTerminal(curr_state)) {
				List<Move> moves = background_no_step.getLegalMoves(n.state, self_index);
				int size = moves.size();
				if (size < 1) {
					System.out.println("Size less than 1!!!!!!!!!!");
				}
				n.legalMoves = moves.toArray(new Move[size]);
				for (int i = 0; i < size; ++i) {
					Move move = n.legalMoves[i];
					n.legalJointMoves.put(move, new ArrayList<List<Move>>());
				}
				for (List<Move> jointMove: background_no_step.getLegalJointMoves(n.state)) {
					OpenBitSet state = background_no_step.getNextState(n.state, jointMove);
					Integer index = graph.get(state);
					if(index == null) {
						DualNode child = new DualNode(state);
						graph.put(state, nodes.size());
						nodes.add(child);
					}
					n.legalJointMoves.get(jointMove.get(self_index)).add(jointMove);
					n.childrenStates.put(jointMove, state);
				}
				path.add(nodes.get(graph.get(n.childrenStates.get(background_no_step.getRandomJointMove(n.state)))));
			} else if (!background_machine.isTerminal(curr_state)) {
				System.out.println("ERROR. Tried to expand node that was previously expanded");
			}
		} else {
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
					OpenBitSet state = background_machine.getNextState(n.state, jointMove);
					DualNode child = orig_graph.get(state);
					if(child == null) {
						child = new DualNode(state);
						orig_graph.put(state, child);
					}
					n.legalJointMoves.get(jointMove.get(self_index)).add(jointMove);
					n.children.put(jointMove, child);
				}
				path.add(n.children.get(background_machine.getRandomJointMove(n.state)));
			} else if (!background_machine.isTerminal(n.state)) {
				System.out.println("ERROR. Tried to expand node that was previously expanded");
			}
		}
	}

	protected void Expand(DualNode n, OpenBitSet curr_state) throws MoveDefinitionException, TransitionDefinitionException {//Assume only expand from max node
		if(no_step) {
			if (n.children.isEmpty() && !machine.isTerminal(curr_state)) {
				List<Move> moves = no_step_machine.getLegalMoves(n.state, self_index);
				int size = moves.size();
				n.legalMoves = moves.toArray(new Move[size]);
				for (int i = 0; i < size; ++i) {
					Move move = n.legalMoves[i];
					n.legalJointMoves.put(move, new ArrayList<List<Move>>());
				}
				for (List<Move> jointMove: no_step_machine.getLegalJointMoves(n.state)) {
					OpenBitSet state = no_step_machine.getNextState(n.state, jointMove);
					Integer index = graph.get(state);
					if(index == null) {
						DualNode child = new DualNode(state);
						graph.put(state, nodes.size());
						nodes.add(child);
					}
					n.legalJointMoves.get(jointMove.get(self_index)).add(jointMove);
					n.childrenStates.put(jointMove, state);
				}
			} else if (!machine.isTerminal(curr_state)) {
				System.out.println("ERROR. Tried to expand node that was previously expanded");
			}
		} else {
			if (n.children.isEmpty() && !machine.isTerminal(n.state)) {
				List<Move> moves = machine.getLegalMoves(n.state, self_index);
				int size = moves.size();
				n.legalMoves = moves.toArray(new Move[size]);
				for (int i = 0; i < size; ++i) {
					Move move = n.legalMoves[i];
					n.legalJointMoves.put(move, new ArrayList<List<Move>>());
				}
				for (List<Move> jointMove: machine.getLegalJointMoves(n.state)) {
					OpenBitSet state = machine.getNextState(n.state, jointMove);
					DualNode child = orig_graph.get(state);
					if(child == null) {
						child = new DualNode(state);
						orig_graph.put(state, child);
					}
					n.legalJointMoves.get(jointMove.get(self_index)).add(jointMove);
					n.children.put(jointMove, child);
				}
			} else if (!machine.isTerminal(n.state)) {
				System.out.println("ERROR. Tried to expand node that was previously expanded");
			}
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	public void stateMachineStop() {
		thread_pool.shutdownNow();
		thread.stop();
	}

	@SuppressWarnings("deprecation")
	@Override
	public void stateMachineAbort() {
		// TODO Auto-generated method stub
		thread_pool.shutdownNow();
		thread.stop();
	}


	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "Dual_Prop Player";
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

}



