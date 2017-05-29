package org.ggp.base.player.gamer.statemachine;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.lucene.util.OpenBitSet;
import org.ggp.base.apps.player.Player;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.XStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;


public class X_MCTS_threadpool extends XStateMachineGamer {
	protected Player p;
	private XStateMachine machine;
	private List<Role> roles;
	private int self_index, num_threads, depthCharges, last_depthCharges;
	private long finishBy;
	private XNode root;
	private List<XNode> path;
	private CompletionService<Double> executor;
	private Thread thread;

	private static final double C_CONST = 50;

	@Override
	public XStateMachine getInitialStateMachine() {
		return new XStateMachine();
	}

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException, InterruptedException, ExecutionException {
		initialize(timeout);
		Thread.sleep(finishBy - System.currentTimeMillis());
		//doMCTS();
		System.out.println("Depth Charges: " + depthCharges);
		last_depthCharges = 0;
		bestMove(root);
	}

	protected void initialize(long timeout) throws MoveDefinitionException, TransitionDefinitionException, InterruptedException {
		machine = getStateMachine();
		machine.setMainThreadId();
		roles = machine.getRoles();
		self_index = roles.indexOf(getRole());
		root = new XNode(machine.getInitialState());
		root = new XNode(getCurrentState());
		Expand(root);
		num_threads = Runtime.getRuntime().availableProcessors() * 12;
		executor = new ExecutorCompletionService<Double>(Executors.newFixedThreadPool(num_threads));
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
		System.out.println("Depth Charges: " + depthCharges);
		last_depthCharges = 0;
		//doMCTS();
		return bestMove(root);
	}

	public void doMCTS() throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		depthCharges = 0;
		double selectAvg = 0;
		double expandAvg = 0;
		double playoutAvg = 0;
		double backpropAvg = 0;
		while (System.currentTimeMillis() < finishBy) {
			path = new ArrayList<XNode>();
			path.add(root);
			Long t1 = System.currentTimeMillis();
			Select(root, path);
			Long t2 = System.currentTimeMillis();
			selectAvg += (t2 - t1);
			XNode n = path.get(path.size() - 1);
			Expand(n, path);
			Long t3 = System.currentTimeMillis();
			expandAvg += (t3 - t2);
			// spawn off multiple threads
			for(int i = 0; i < num_threads; ++i) {
				executor.submit(new RunMe(n));
			}
			//double val = Playout(n);
			Long t4 = System.currentTimeMillis();
			playoutAvg += (t4 - t3);
			double sum = 0;
			for (int i = 0; i < num_threads; ++i) {
				Future<Double> f = null;
		        try {
					f = executor.take();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		        try {
					sum += f.get();
				} catch (InterruptedException | ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		    }
			Backpropogate(sum, path);
			backpropAvg += (System.currentTimeMillis() - t4);
			depthCharges += num_threads;
			//System.out.println(depthCharges);
		}
		selectAvg /= depthCharges; expandAvg /= depthCharges; playoutAvg /= depthCharges; backpropAvg /= depthCharges;
		System.out.println("Select: " + selectAvg + " Expand: " + expandAvg + " Playout: " + playoutAvg + " BackProp: " + backpropAvg);
		System.out.println("Depth Charges: " + depthCharges);
	}


	public class runMCTS implements Runnable {
		@Override
		public void run() {
			machine.setMainThreadId();
			XNode root_thread;
			while (true) {
				root_thread = root;
				path = new ArrayList<XNode>();
				path.add(root_thread);
				try {
					Select(root_thread, path);
				} catch (MoveDefinitionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				XNode n = path.get(path.size() - 1);
				try {
					Expand(n, path);
				} catch (MoveDefinitionException | TransitionDefinitionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				// spawn off multiple threads
				for(int i = 0; i < num_threads; ++i) {
					executor.submit(new RunMe(n));
				}
				double sum = 0;
				for (int i = 0; i < num_threads; ++i) {
					Future<Double> f = null;
			        try {
						f = executor.take();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
			        try {
						sum += f.get();
					} catch (InterruptedException | ExecutionException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
			    }
				Backpropogate(sum,path);
				depthCharges += num_threads;
				last_depthCharges += num_threads;
			}
		}
	}

	public class RunMe implements Callable<Double> {
		private XNode node;

		public RunMe(XNode n) {
			this.node = n;
		}
		@Override
		public Double call() {
	    	double val = 0;
			try {
				val = Playout(node);
			} catch (MoveDefinitionException | TransitionDefinitionException | GoalDefinitionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
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

	protected void Backpropogate(double val, List<XNode> path) {
		XNode nod = path.get(path.size() - 1);
		if (machine.isTerminal(nod.state)) {
			nod.isTerminal = true;
		}
		for (int i = path.size() - 1; i >= 0; --i) {
			nod = path.get(i);
			nod.utility += val;
			nod.visits += num_threads;
		}
	}

	protected double Playout(XNode n) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		OpenBitSet state = n.state;
		while(!machine.isTerminal(state)) {
			state = machine.getRandomNextState(state);
		}
		return machine.getGoal(state, self_index);
	}

	protected void Select(XNode n, List<XNode> path) throws MoveDefinitionException {
		if (machine.isTerminal(n.state)) return;
		if (n.children.isEmpty()) return;
		double maxValue = Double.NEGATIVE_INFINITY;
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


	protected double uctMin(XNode n, double parentVisits) {
		double value = n.utility / n.visits;
		return -value + C_CONST * Math.sqrt(Math.log(parentVisits) / n.visits);
	}

	protected double uctMax(XNode n, double parentVisits) {
		double value = n.utility / n.visits;
		return value + C_CONST * Math.sqrt(Math.log(parentVisits) / n.visits);
	}

	protected void Expand(XNode n, List<XNode> path) throws MoveDefinitionException, TransitionDefinitionException {
		if (n.children.isEmpty() && !machine.isTerminal(n.state)) {
			List<Move> moves = machine.getLegalMoves(n.state, self_index);
			int size = moves.size();
			if (size < 1) {
				System.out.println("Size less than 1!!!!!!!!!!");
			}
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
			path.add(n.children.get(machine.getRandomJointMove(n.state)));
		} else if (!machine.isTerminal(n.state)) {
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
		//thread.stop();
	}

	@SuppressWarnings("deprecation")
	@Override
	public void stateMachineAbort() {
		// TODO Auto-generated method stub
		//thread.stop();
	}


	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "X_MCTS_threadpool Player";
	}

	@Override
	public void preview(Game g, long timeout) throws GamePreviewException {
		// TODO Auto-generated method stub

	}




}



