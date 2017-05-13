package org.ggp.base.player.gamer.statemachine;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;


public class MCTS_threadpool extends the_men_who_stare_at_goats {
	private StateMachine machine;
	private List<StateMachine> machines;
	private List<Role> roles;
	private int self_index, num_threads, depthCharges;
	private long finishBy;
	private Node root;
	private List<Node> path;
	private ExecutorService executor;
	private List<Future<?>> futures;
	private Node n;
	private Lock lock;

	private static final double C_CONST = 50;

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException, InterruptedException, ExecutionException {
		initialize(timeout);
		runMCTS();
		bestMove(root);
	}

	protected void initialize(long timeout) throws MoveDefinitionException, TransitionDefinitionException, InterruptedException {
		machine = getStateMachine();
		roles = machine.getRoles();
		self_index = roles.indexOf(getRole());
		root = new Node(machine.getInitialState());
		Expand(root);
		num_threads = Runtime.getRuntime().availableProcessors() * 12;
		executor = Executors.newFixedThreadPool(num_threads);
		machines = new ArrayList<StateMachine>();
		long curr_time = System.currentTimeMillis();
		for(int i = 0; i < num_threads; i++) {
			StateMachine stateMachine = getInitialStateMachine();
			stateMachine.initialize(getMatch().getGame().getRules());
			machines.add(stateMachine);
		}
		long displacement = System.currentTimeMillis() - curr_time;
		finishBy = timeout - 2500 - displacement;
		System.out.println("NumThreads: " + num_threads);
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException, InterruptedException, ExecutionException {
		//More efficient to use Compulsive Deliberation for one player games
		//Use two-player implementation for two player games
		finishBy = timeout - 2500;
		return MCTS();
	}

	protected void initializeMCTS() throws MoveDefinitionException, TransitionDefinitionException, InterruptedException {
		MachineState currentState = getCurrentState();
		if (root == null) System.out.println("NULL ROOT");
		if (root.state == currentState) return;
		for (List<Move> jointMove : machine.getLegalJointMoves(root.state)) {
			MachineState nextState = machine.getNextState(root.state, jointMove);
			if (currentState == nextState) {
				root = root.children.get(jointMove);
				if (root == null) System.out.println("NOT IN MAP");
				return;
			}
		}
		System.out.println("ERROR. Current State not in tree");
		root = new Node(currentState);
	}

	protected Move MCTS() throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException, InterruptedException, ExecutionException {
		initializeMCTS();
		runMCTS();
		return bestMove(root);
	}

	protected void runMCTS() throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException, InterruptedException, ExecutionException {
		depthCharges = 0;
		while (System.currentTimeMillis() < finishBy) {
			path = new ArrayList<Node>();
			futures = new ArrayList<> ();
			lock = new ReentrantLock();
			path.add(root);
			Select(root, path);
			n = path.get(path.size() - 1);
			Expand(n, path);
			// spawn off multiple threads
			for(int i = 0; i < num_threads; ++i) {
				RunMe r = new RunMe();
				futures.add(executor.submit(r));
			}
			depthCharges += futures.size();
			for (Future<?> f : futures) {
		        f.get(); //blocks until the runnable completes
		    }
		}
		System.out.println("20 Depth Charges: " + depthCharges);
	}

	public class RunMe implements Runnable {
		@Override
		public void run() {
	    	double val = 0;
			try {
				val = Playout(n);
			} catch (MoveDefinitionException | TransitionDefinitionException | GoalDefinitionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    	lock.lock();
	    	Backpropogate(val, path);
	    	lock.unlock();
	    }
	}

	protected Move bestMove(Node n) throws MoveDefinitionException {
		double maxValue = Double.NEGATIVE_INFINITY;
		Move maxMove = n.legalMoves.get(0);
		for(Move move: n.legalMoves) {
			double minValue = Double.POSITIVE_INFINITY;
			double visits = 0;
			for (List<Move> jointMove : n.legalJointMoves.get(move)) {
				Node succNode = n.children.get(jointMove);
				if (succNode.visits != 0) {
					double nodeValue = succNode.utility / succNode.visits;
					if (nodeValue < minValue) {
						minValue = nodeValue;
						visits = succNode.visits;
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

	protected void Backpropogate(double val, List<Node> path) {
		Node nod = path.get(path.size() - 1);
		if (machine.isTerminal(nod.state)) {
			nod.isTerminal = true;
		}
		for (int i = path.size() - 1; i >= 0; --i) {
			nod = path.get(i);
			nod.utility += val;
			++nod.visits;
		}
	}

	protected double Playout(Node n) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		int threadId = (int) (Thread.currentThread().getId() % num_threads);
		StateMachine machine = machines.get(threadId);
		MachineState state = n.state;
		while(!machine.isTerminal(state)) {
			state = machine.getRandomNextState(state);
		}
		return machine.getGoal(state, roles.get(self_index));
	}

	protected void Select(Node n, List<Node> path) throws MoveDefinitionException {
		if (machine.isTerminal(n.state)) return;
		if (n.children.isEmpty()) return;
		double maxValue = Double.NEGATIVE_INFINITY;
		Node maxChild = null;
		for(Move move: n.legalMoves) {
			double minValue = Double.NEGATIVE_INFINITY;
			Node minChild = null;
			for (List<Move> jointMove : n.legalJointMoves.get(move)) {
				Node succNode = n.children.get(jointMove);
				if (succNode.visits == 0) {
					path.add(succNode);
					return;
				}
				double nodeValue = uctMin(succNode, n.visits);
				if (nodeValue > minValue) {
					minValue = nodeValue;
					minChild = succNode;
				}
			}
			minValue = uctMax(minChild, n.visits);
			if (minValue > maxValue) {
				maxValue = minValue;
				maxChild = minChild;
			}
		}
		path.add(maxChild);
		Select(maxChild, path);
	}


	protected double uctMin(Node n, double parentVisits) {
		double value = n.utility / n.visits;
		return -value + C_CONST * Math.sqrt(Math.log(parentVisits) / n.visits);
	}

	protected double uctMax(Node n, double parentVisits) {
		double value = n.utility / n.visits;
		return value + C_CONST * Math.sqrt(Math.log(parentVisits) / n.visits);
	}

	protected void Expand(Node n, List<Node> path) throws MoveDefinitionException, TransitionDefinitionException {
		if (n.children.isEmpty() && !machine.isTerminal(n.state)) {
			n.legalMoves = machine.getLegalMoves(n.state, roles.get(self_index));
			for (Move move: n.legalMoves) {
				n.legalJointMoves.put(move, new ArrayList<List<Move>>());
			}
			for (List<Move> jointMove: machine.getLegalJointMoves(n.state)) {
				Node child = new Node(machine.getNextState(n.state, jointMove));
				n.legalJointMoves.get(jointMove.get(self_index)).add(jointMove);
				n.children.put(jointMove, child);
			}
			path.add(n.children.get(machine.getRandomJointMove(n.state)));
		} else if (!machine.isTerminal(n.state)) {
			System.out.println("ERROR. Tried to expand node that was previously expanded");
		}
	}

	protected void Expand(Node n) throws MoveDefinitionException, TransitionDefinitionException {//Assume only expand from max node
		if (n.children.isEmpty() && !machine.isTerminal(n.state)) {
			n.legalMoves = machine.getLegalMoves(n.state, roles.get(self_index));
			for (Move move: n.legalMoves) {
				n.legalJointMoves.put(move, new ArrayList<List<Move>>());
			}
			for (List<Move> jointMove: machine.getLegalJointMoves(n.state)) {
				Node child = new Node(machine.getNextState(n.state, jointMove));
				n.legalJointMoves.get(jointMove.get(self_index)).add(jointMove);
				n.children.put(jointMove, child);
			}
		} else if (!machine.isTerminal(n.state)) {
			System.out.println("ERROR. Tried to expand node that was previously expanded");
		}
	}

	@Override
	public void stateMachineStop() {
		//executor.shutdownNow();
	}

	@Override
	public void stateMachineAbort() {
		// TODO Auto-generated method stub
		//executor.shutdownNow();
	}


	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "MCTS_threadpool Player";
	}




}

