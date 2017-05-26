package org.ggp.base.player.gamer.statemachine.threadpools;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.ggp.base.player.gamer.statemachine.Nodes.BitNode;
import org.ggp.base.player.gamer.statemachine.men_who_stare_at_goats.BIT_the_men_who_stare_at_goats;
import org.ggp.base.util.statemachine.BitMachineState;
import org.ggp.base.util.statemachine.BitStateMachine;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;


public class Beta_MCTS_threadpool extends BIT_the_men_who_stare_at_goats {
	private BitStateMachine machine;
	private List<Role> roles;
	private int self_index, num_threads, depthCharges, last_depthCharges;
	private long finishBy;
	private BitNode root;
	private List<BitNode> path;
	private CompletionService<Struct> executor;
	private Thread thread;

	private static final double C_CONST = 50;

	public class Struct {
		public List<BitNode> path;
		public double val;
		public Struct(List<BitNode> l, double v) {
			this.path = l;
			this.val = v;
		}
	}

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException, InterruptedException, ExecutionException {
		initialize(timeout);
		Thread.sleep(finishBy - System.currentTimeMillis());
		System.out.println("Depth Charges: " + depthCharges);
		last_depthCharges = 0;
		bestMove(root);
	}

	protected void initialize(long timeout) throws MoveDefinitionException, TransitionDefinitionException, InterruptedException {
		machine = getStateMachine();
		roles = machine.getRoles();
		self_index = roles.indexOf(getRole());
		root = new BitNode(machine.getInitialState());
		Expand(root);
		num_threads = Runtime.getRuntime().availableProcessors() * 2;
		executor = new ExecutorCompletionService<Struct>(Executors.newFixedThreadPool(num_threads));
		thread = new Thread(new runMCTS());
		depthCharges = 0;
		last_depthCharges = 0;
		thread.start();

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
		System.out.println("Background Depth Charges: " + last_depthCharges);
		finishBy = timeout - 2500;
		return MCTS();
	}

	protected void initializeMCTS() throws MoveDefinitionException, TransitionDefinitionException, InterruptedException {
		BitMachineState currentState = getCurrentState();
		if (root == null) System.out.println("NULL ROOT");
		if (root.state.equals(currentState)) return;
		for (List<Move> jointMove : machine.getLegalJointMoves(root.state)) {
			BitMachineState nextState = machine.getNextState(root.state, jointMove);
			if (currentState.equals(nextState)) {
				root = root.children.get(jointMove);
				if (root == null) System.out.println("NOT IN MAP");
				return;
			}
		}
		System.out.println("ERROR. Current State not in tree");
		root = new BitNode(currentState);
	}

	protected Move MCTS() throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException, InterruptedException, ExecutionException {
		initializeMCTS();
		Thread.sleep(finishBy - System.currentTimeMillis());
		System.out.println("Depth Charges: " + depthCharges);
		last_depthCharges = 0;
		return bestMove(root);
	}

	public class runMCTS implements Runnable {
		@Override
		public void run() {
			BitNode root_thread;
			while (true) {
				root_thread = root;
				path = new ArrayList<BitNode>();
				path.add(root_thread);
				try {
					Select(root_thread, path);
				} catch (MoveDefinitionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				BitNode n = path.get(path.size() - 1);
				try {
					Expand(n, path);
				} catch (MoveDefinitionException | TransitionDefinitionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				// spawn off multiple threads
				for(int i = 0; i < num_threads; ++i) {
					executor.submit(new RunMe(path, n));
				}
				depthCharges += num_threads;
				last_depthCharges += num_threads;
				while(true) {
					Future<Struct> f = executor.poll();
			        if (f == null) {
			        	break;
			        }
			        Struct s = null;
			        try {
						s = f.get();
					} catch (InterruptedException | ExecutionException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
			        Backpropogate(s.val,s.path);
			    }
			}
		}
	}

	public class RunMe implements Callable<Struct> {
		private List<BitNode> list;
		private BitNode node;
		private Struct s;

		public RunMe(List<BitNode> l, BitNode n) {
			this.list = l;
			this.node = n;
		}
		@Override
		public Struct call() {
	    	double val = 0;
			try {
				val = Playout(node);
			} catch (MoveDefinitionException | TransitionDefinitionException | GoalDefinitionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			s = new Struct(list, val);
			return s;
	    }
	}

	protected Move bestMove(BitNode n) throws MoveDefinitionException {
		double maxValue = Double.NEGATIVE_INFINITY;
		Move maxMove = n.legalMoves[0];
		int size = n.legalMoves.length;
		for(int i = 0; i < size; ++i) {
			Move move = n.legalMoves[i];
			double minValue = Double.POSITIVE_INFINITY;
			double visits = 0;
			for (List<Move> jointMove : n.legalJointMoves.get(move)) {
				BitNode succNode = n.children.get(jointMove);
				if (succNode.updates != 0) {
					double nodeValue = succNode.utility / succNode.updates;
					if (nodeValue < minValue) {
						minValue = nodeValue;
						visits = succNode.updates;
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

	protected void Backpropogate(double val, List<BitNode> path) {
		BitNode node = path.get(path.size() - 1);
		if (machine.isTerminal(node.state)) {
			node.isTerminal = true;
		}
		for (int i = path.size() - 1; i >= 0; --i) {
			node = path.get(i);
			node.utility += val;
			++node.updates;
		}
	}

	protected double Playout(BitNode node) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		BitMachineState state = node.state;
		while(!machine.isTerminal(state)) {
			state = machine.getRandomNextState(state);
		}
		return machine.getGoal(state, roles.get(self_index));
	}

	protected void Select(BitNode n, List<BitNode> path) throws MoveDefinitionException {
		++n.visits;
		if (machine.isTerminal(n.state)) return;
		if (n.children.isEmpty()) return;
		double maxValue = Double.NEGATIVE_INFINITY;
		BitNode maxChild = null;
		int size = n.legalMoves.length;
		for(int i = 0; i < size; ++i) {
			Move move = n.legalMoves[i];
			double minValue = Double.NEGATIVE_INFINITY;
			BitNode minChild = null;
			for (List<Move> jointMove : n.legalJointMoves.get(move)) {
				BitNode succNode = n.children.get(jointMove);
				if (succNode.visits == 0) {
					path.add(succNode);
					++succNode.visits;
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


	protected double uctMin(BitNode n, double parentVisits) {
		double value = n.utility / n.visits;
		return -value + C_CONST * Math.sqrt(Math.log(parentVisits) / n.visits);
	}

	protected double uctMax(BitNode n, double parentVisits) {
		double value = n.utility / n.visits;
		return value + C_CONST * Math.sqrt(Math.log(parentVisits) / n.visits);
	}

	protected void Expand(BitNode n, List<BitNode> path) throws MoveDefinitionException, TransitionDefinitionException {
		if (n.children.isEmpty() && !machine.isTerminal(n.state)) {
			List<Move> moves = machine.getLegalMoves(n.state, roles.get(self_index));
			int size = moves.size();
			n.legalMoves = moves.toArray(new Move[size]);
			for (int i = 0; i < size; ++i) {
				Move move = n.legalMoves[i];
				n.legalJointMoves.put(move, new ArrayList<List<Move>>());
			}
			for (List<Move> jointMove: machine.getLegalJointMoves(n.state)) {
				BitNode child = new BitNode(machine.getNextState(n.state, jointMove));
				n.legalJointMoves.get(jointMove.get(self_index)).add(jointMove);
				n.children.put(jointMove, child);
			}
			path.add(n.children.get(machine.getRandomJointMove(n.state)));
		} else if (!machine.isTerminal(n.state)) {
			System.out.println("ERROR. Tried to expand node that was previously expanded");
		}
	}

	protected void Expand(BitNode n) throws MoveDefinitionException, TransitionDefinitionException {//Assume only expand from max node
		if (n.children.isEmpty() && !machine.isTerminal(n.state)) {
			List<Move> moves = machine.getLegalMoves(n.state, roles.get(self_index));
			int size = moves.size();
			n.legalMoves = moves.toArray(new Move[size]);
			for (int i = 0; i < size; ++i) {
				Move move = n.legalMoves[i];
				n.legalJointMoves.put(move, new ArrayList<List<Move>>());
			}
			for (List<Move> jointMove: machine.getLegalJointMoves(n.state)) {
				BitNode child = new BitNode(machine.getNextState(n.state, jointMove));
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
		return "Beta_MCTS_threadpool Player";
	}




}


