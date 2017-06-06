package org.ggp.base.player.gamer.statemachine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.lucene.util.OpenBitSet;
import org.ggp.base.player.gamer.Gamer;
import org.ggp.base.player.gamer.exception.AbortingException;
import org.ggp.base.player.gamer.exception.MetaGamingException;
import org.ggp.base.player.gamer.exception.MoveSelectionException;
import org.ggp.base.player.gamer.exception.StoppingException;
import org.ggp.base.util.Pair;
import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlDistinct;
import org.ggp.base.util.gdl.grammar.GdlFunction;
import org.ggp.base.util.gdl.grammar.GdlLiteral;
import org.ggp.base.util.gdl.grammar.GdlPool;
import org.ggp.base.util.gdl.grammar.GdlRule;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.gdl.grammar.GdlTerm;
import org.ggp.base.util.logging.GamerLogger;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.factory.OptimizingPropNetFactory;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.XStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;


/**
 * The base class for Gamers that rely on representing games as state machines.
 * Almost every player should subclass this class, since it provides the common
 * methods for interpreting the match history as transitions in a state machine,
 * and for keeping an up-to-date view of the current state of the game.
 *
 * See @SimpleSearchLightGamer, @HumanGamer, and @RandomGamer for examples.
 *
 * @author evancox
 * @author Sam
 */
public abstract class FactorGamer extends Gamer
{
    // =====================================================================
    // First, the abstract methods which need to be overridden by subclasses.
    // These determine what state machine is used, what the gamer does during
    // metagaming, and how the gamer selects moves.

    /**
     * Defines which state machine this gamer will use.
     * @return
     */
    public abstract XStateMachine getInitialStateMachine();

    /**
     * Defines the metagaming action taken by a player during the START_CLOCK
     * @param timeout time in milliseconds since the era when this function must return
     * @throws TransitionDefinitionException
     * @throws MoveDefinitionException
     * @throws GoalDefinitionException
     * @throws ExecutionException
     */
    public abstract void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException, InterruptedException, ExecutionException;

    /**
     * Defines the algorithm that the player uses to select their move.
     * @param timeout time in milliseconds since the era when this function must return
     * @return Move - the move selected by the player
     * @throws TransitionDefinitionException
     * @throws MoveDefinitionException
     * @throws GoalDefinitionException
     * @throws ExecutionException
     */
    public abstract Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException, InterruptedException, ExecutionException;

    /**
     * Defines any actions that the player takes upon the game cleanly ending.
     */
    public abstract void stateMachineStop();

    /**
     * Defines any actions that the player takes upon the game abruptly ending.
     */
    public abstract void stateMachineAbort();

    // =====================================================================
    // Next, methods which can be used by subclasses to get information about
    // the current state of the game, and tweak the state machine on the fly.

	/**
	 * Returns the current state of the game.
	 * @param player_ind
	 */
	public final OpenBitSet getCurrentState()
	{
		return currentState;
	}

	/**
	 * Returns the role that this gamer is playing as in the game.
	 */
	public final Role getRole()
	{
		return role;
	}

	/**
	 * Returns the state machine.  This is used for calculating the next state and other operations, such as computing
	 * the legal moves for all players, whether states are terminal, and the goal values of terminal states.
	 */
	public final XStateMachine getStateMachine()
	{
		return stateMachine;
	}

    /**
     * Cleans up the role, currentState and stateMachine. This should only be
     * used when a match is over, and even then only when you really need to
     * free up resources that the state machine has tied up. Currently, it is
     * only used in the Proxy, for players designed to run 24/7.
     */
    protected final void cleanupAfterMatch() {
        role = null;
        currentState = null;
        stateMachine = null;
        setMatch(null);
        setRoleName(null);
        machines = null;
        players = null;
        exec = null;
    	threadpool = null;
    	currentStates = null;
    	single_prop = false;
    	num_threads = 0;
    }

    /**
     * Switches stateMachine to newStateMachine, playing through the match
     * history to the current state so that currentState is expressed using
     * a MachineState generated by the new state machine.
     *
     * This is not done in a thread-safe fashion with respect to the rest of
     * the gamer, so be careful when using this method.
     *
     * @param newStateMachine the new state machine
     */
   /* protected final void switchStateMachine(StateMachine newStateMachine) {
        try {
            MachineState newCurrentState = newStateMachine.getInitialState();
            Role newRole = newStateMachine.getRoleFromConstant(getRoleName());

            // Attempt to run through the game history in the new machine
            List<List<GdlTerm>> theMoveHistory = getMatch().getMoveHistory();
            for(List<GdlTerm> nextMove : theMoveHistory) {
                List<Move> theJointMove = new ArrayList<Move>();
                for(GdlTerm theSentence : nextMove)
                    theJointMove.add(newStateMachine.getMoveFromTerm(theSentence));
                newCurrentState = newStateMachine.getNextStateDestructively(newCurrentState, theJointMove);
            }

            // Finally, switch over if everything went well.
            role = newRole;
            currentState = newCurrentState;
            stateMachine = newStateMachine;
        } catch (Exception e) {
            GamerLogger.log("GamePlayer", "Caught an exception while switching state machine!");
            GamerLogger.logStackTrace("GamePlayer", e);
        }
    }*/

    /**
     * A function that can be used when deserializing gamers, to bring a
     * state machine gamer back to the internal state that it has when it
     * arrives at a particular game state.
     */
	/*public final void resetStateFromMatch() {
        stateMachine = getInitialStateMachine();
        stateMachine.initialize(getMatch().getGame().getRules());
        currentState = stateMachine.getMachineStateFromSentenceList(getMatch().getMostRecentState());
        role = stateMachine.getRoleFromConstant(getRoleName());
	}*/

    // =====================================================================
    // Finally, methods which are overridden with proper state-machine-based
	// semantics. These basically wrap a state-machine-based view of the world
	// around the ordinary metaGame() and selectMove() functions, calling the
	// new stateMachineMetaGame() and stateMachineSelectMove() functions after
	// doing the state-machine-related book-keeping.

	/**
	 * A wrapper function for stateMachineMetaGame. When the match begins, this
	 * initializes the state machine and role using the match description, and
	 * then calls stateMachineMetaGame.
	 */
	@Override
	public final void metaGame(long timeout) throws MetaGamingException
	{
		try
		{
			System.out.println("Initialized");
			List<Gdl> description = getMatch().getGame().getRules();
        	description = sanitizeDistinct(description);
        	PropNet prop = OptimizingPropNetFactory.create(description);
        	role = new Role(getRoleName());
        	List<PropNet> prop_list = new ArrayList<PropNet>();
        	prop_list.add(prop);
        	//prop_list = PropNet.factor_propnet(prop,role);
        	if (prop_list.size() == 1) {
        		single_prop = true;
        		stateMachine = getInitialStateMachine();
        		stateMachine.initialize(prop_list.get(0));
        		currentState = stateMachine.getInitialState();
        		Pair<PropNet,Integer> p = PropNet.removeStepCounter(prop);
        		//if (p != null) {
        		if(false) {
        			solverMachine = new XStateMachine();
        			solverMachine.initialize(p.left);
        			solverState = solverMachine.getInitialState();
        			solver = new Solver(solverMachine, p.right);
        			solve = true;
        			task = new FutureTask<Boolean>(new Run_Solver_Meta(timeout));
					solver_thread = new Thread(task);
					solver_thread.start();
        			stateMachineMetaGame(timeout, currentState,role);
        			if (task.get()) {
        				game_solved = true;
        			}
        		} else {
        			stateMachineMetaGame(timeout, currentState,role);
        		}
        	} else {
        		num_threads = prop_list.size();
        		threadpool = (ThreadPoolExecutor) Executors.newFixedThreadPool(num_threads);
        		exec = new ExecutorCompletionService<MoveStruct>(threadpool);
        		machines = new XStateMachine[num_threads];
        		currentStates = new OpenBitSet[num_threads];
        		players = new Factor_MCTS_threadpool[num_threads];
        		for (int i = 0; i < num_threads; ++i) {
        			exec.submit(new RunMeta(prop_list.get(i), timeout, i));
        		}
        		for (int i = 0; i < num_threads; ++i) {
        			exec.take();
        		}
        	}
		}
		catch (Exception e)
		{
		    GamerLogger.logStackTrace("GamePlayer", e);
			throw new MetaGamingException(e);
		}
	}


	public class Run_Solver_Meta implements Callable<Boolean> {
		private long timeout;

		public Run_Solver_Meta(long time) {
			timeout = time;
		}

		@Override
		public Boolean call() {
			try {
				return solver.stateMachineMetaGame1(timeout, solverState, role);
			} catch (TransitionDefinitionException | MoveDefinitionException | GoalDefinitionException | InterruptedException | ThreadDeath e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return game_solved;
		}

	}

	public class RunMeta implements Callable<MoveStruct> {
		private PropNet prop;
		private long timeout;
		private int ind;

		public RunMeta(PropNet propnet, long time, int i) {
			prop = propnet;
			timeout = time;
			ind = i;
		}

		@Override
		public MoveStruct call() throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException, InterruptedException, ExecutionException {
			machines[ind] = new XStateMachine();
    		machines[ind].initialize(prop);
    		currentStates[ind] = machines[ind].getInitialState();
    		players[ind] = new Factor_MCTS_threadpool();
    		players[ind].init(machines[ind], machines.length);
    		//MachineState convertedState = stateMachine.toGdl(currentState);
    		//getMatch().appendState(convertedState.getContents());

    		players[ind].stateMachineMetaGame(timeout, currentStates[ind], role);
    		return null;
		}

	}

	/**
	 * A wrapper function for stateMachineSelectMove. When we are asked to
	 * select a move, this advances the state machine up to the current state
	 * and then calls stateMachineSelectMove to select a move based on that
	 * current state.
	 */
	@Override
	public final GdlTerm selectMove(long timeout) throws MoveSelectionException
	{
		try
		{
			List<GdlTerm> lastMoves = getMatch().getMostRecentMoves();
			if (single_prop) {
				if (solve) {
					FutureTask<MoveStruct> futureTask = new FutureTask<MoveStruct>(new Run_Select_Solver(timeout,lastMoves));
					solver_thread = new Thread(futureTask);
					solver_thread.start();
					List<Move> moves = new ArrayList<Move>();
					if (lastMoves != null)
					{
						for (GdlTerm sentence : lastMoves)
						{
							moves.add(stateMachine.getMoveFromTerm(sentence));
						}

						currentState = stateMachine.getNextState(currentState, moves);
					}
					if (game_solved) {
						MoveStruct m2 = futureTask.get();
						System.out.println("USING SOLVER MAX MOVE");
						System.out.println("Max Move: " + m2.move + " Max Value: " + m2.score);
						return m2.move.getContents();
					}
					MoveStruct m1 = stateMachineSelectMove(timeout, currentState, moves);
					MoveStruct m2 = futureTask.get();
					if (m2.solved) {
						game_solved = true;
						System.out.println("USING SOLVER MAX MOVE");
						System.out.println("Max Move: " + m2.move + " Max Value: " + m2.score);
						return m2.move.getContents();
					}
					return m1.move.getContents();
				} else {
					List<Move> moves = new ArrayList<Move>();
					if (lastMoves != null)
					{
						for (GdlTerm sentence : lastMoves)
						{
							moves.add(stateMachine.getMoveFromTerm(sentence));
						}

						currentState = stateMachine.getNextState(currentState, moves);
					}
					return stateMachineSelectMove(timeout, currentState, moves).move.getContents();
				}
			} else {
				for (int i = 0; i < num_threads; ++i) {
        			exec.submit(new RunSelect(lastMoves, timeout, i));
        		}

				Move bestMove = null;
				double bestScore = -1;

        		for (int i = 0; i < num_threads; ++i) {
        			Future<MoveStruct> f = exec.take();
        			MoveStruct s = null;
        			s = f.get();
        			if (s.score > bestScore) {
        				bestMove = s.move;
        				bestScore = s.score;
        			}
        		}
        		return bestMove.getContents();
			}
		}
		catch (Exception e)
		{
		    GamerLogger.logStackTrace("GamePlayer", e);
			throw new MoveSelectionException(e);
		}
	}

	public class Run_Select_Solver implements Callable<MoveStruct> {
		long timeout;
		List<GdlTerm> lastMoves;
		public Run_Select_Solver(long time, List<GdlTerm> m) {
			timeout = time;
			lastMoves = m;
		}

		@Override
		public MoveStruct call() throws Exception {
			List<Move> moves = new ArrayList<Move>();
			if (lastMoves != null)
			{
				for (GdlTerm sentence : lastMoves)
				{
					moves.add(solverMachine.getMoveFromTerm(sentence));
				}

				solverState = solverMachine.getNextState(solverState, moves);
			}
			return solver.stateMachineSelectMove(timeout, solverState, moves);
		}

	}

	public class RunSelect implements Callable<MoveStruct> {
		private List<GdlTerm> lastMoves;
		private long timeout;
		private int ind;
		public RunSelect(List<GdlTerm> last, long time, int i) {
			lastMoves = last;
			timeout = time;
			ind = i;
		}
		@Override
		public MoveStruct call() throws Exception {
			List<Move> moves = new ArrayList<Move>();
			if (lastMoves != null)
			{
				for (GdlTerm sentence : lastMoves)
				{
					moves.add(machines[ind].getMoveFromTerm(sentence));
				}

				currentStates[ind] = machines[ind].getNextState(currentStates[ind], moves);
				//getMatch().appendState(stateMachine.toGdl(currentState).getContents());
			}

			return players[ind].stateMachineSelectMove(timeout, currentStates[ind], moves);
		}
	}

	@Override
	public void stop() throws StoppingException {
		try {
			if (single_prop) stateMachineStop();
			else {
				for(Factor_MCTS_threadpool x: players) {
					x.stateMachineStop();
				}
			}
		}
		catch (Exception e)
		{
			GamerLogger.logStackTrace("GamePlayer", e);
			throw new StoppingException(e);
		}
	}

	@Override
	public void abort() throws AbortingException {
		try {
			if (single_prop) stateMachineAbort();
			else {
				for(Factor_MCTS_threadpool x: players) {
					x.stateMachineAbort();
				}
			}
		}
		catch (Exception e)
		{
			GamerLogger.logStackTrace("GamePlayer", e);
			throw new AbortingException(e);
		}
	}

	private void sanitizeDistinctHelper(Gdl gdl, List<Gdl> in, List<Gdl> out) {
	    if (!(gdl instanceof GdlRule)) {
	        out.add(gdl);
	        return;
	    }
	    GdlRule rule = (GdlRule) gdl;
	    for (GdlLiteral lit : rule.getBody()) {
	        if (lit instanceof GdlDistinct) {
	            GdlDistinct d = (GdlDistinct) lit;
	            GdlTerm a = d.getArg1();
	            GdlTerm b = d.getArg2();
	            if (!(a instanceof GdlFunction) && !(b instanceof GdlFunction)) continue;
	            if (!(a instanceof GdlFunction && b instanceof GdlFunction)) return;
	            GdlSentence af = ((GdlFunction) a).toSentence();
	            GdlSentence bf = ((GdlFunction) b).toSentence();
	            if (!af.getName().equals(bf.getName())) return;
	            if (af.arity() != bf.arity()) return;
	            for (int i = 0; i < af.arity(); i++) {
	                List<GdlLiteral> ruleBody = new ArrayList<>();
	                for (GdlLiteral newLit : rule.getBody()) {
	                    if (newLit != lit) ruleBody.add(newLit);
	                    else ruleBody.add(GdlPool.getDistinct(af.get(i), bf.get(i)));
	                }
	                GdlRule newRule = GdlPool.getRule(rule.getHead(), ruleBody);
	                in.add(newRule);
	            }
	            return;
	        }
	    }
	    for (GdlLiteral lit : rule.getBody()) {
	        if (lit instanceof GdlDistinct) {
	            System.out.println("distinct rule added: " + rule);
	            break;
	        }
	    }
	    out.add(rule);
	}

	private List<Gdl> sanitizeDistinct(List<Gdl> description) {
	    List<Gdl> out = new ArrayList<>();
	    for (int i = 0; i < description.size(); i++) {
	        sanitizeDistinctHelper(description.get(i), description, out);
	    }
	    return out;
	}

    // Internal state about the current state of the state machine.
    private Role role;
    private OpenBitSet currentState;
    private OpenBitSet solverState;
    private XStateMachine stateMachine;
    private XStateMachine solverMachine;
    private Solver solver;
    private XStateMachine[] machines;
    private Factor_MCTS_threadpool[] players;
    private CompletionService<MoveStruct> exec;
	private ThreadPoolExecutor threadpool;
	private OpenBitSet[] currentStates;
	private boolean single_prop = false;
	private int num_threads;
	private Thread solver_thread;
	private boolean solve = false;
	private boolean game_solved = false;
	private FutureTask<Boolean> task;

	public MoveStruct stateMachineSelectMove(long timeout, OpenBitSet curr) throws TransitionDefinitionException,
			MoveDefinitionException, GoalDefinitionException, InterruptedException, ExecutionException {
		// TODO Auto-generated method stub
		return null;
	}

	public void stateMachineMetaGame(long timeout, OpenBitSet curr, Role role) throws TransitionDefinitionException,
			MoveDefinitionException, GoalDefinitionException, InterruptedException, ExecutionException {
		// TODO Auto-generated method stub

	}

	public MoveStruct stateMachineSelectMove(long timeout, OpenBitSet curr, List<Move> moves)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException,
			InterruptedException, ExecutionException {
		// TODO Auto-generated method stub
		return null;
	}

	public Boolean stateMachineMetaGame1(long timeout, OpenBitSet curr, Role role) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException, InterruptedException {
		return true;
	}

}
