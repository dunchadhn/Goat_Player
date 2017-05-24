package org.ggp.base.player.gamer;

import java.util.List;
import java.util.Random;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.util.statemachine.BitStateMachine;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;
import org.ggp.base.util.statemachine.verifier.BitStateMachineVerifier;

/**
 * SampleLegalGamer is a minimal gamer which always plays the first
 * legal move it identifies, regardless of the state of the game.
 *
 * For your first players, you should extend the class SampleGamer
 * The only function that you are required to override is :
 * public Move stateMachineSelectMove(long timeout)
 *
 */
public final class SampleLegalGamer2 extends SampleGamer
{


	private BitStateMachine machine2;
	private StateMachine machine;
	private MachineState refState;
	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		machine = new CachedStateMachine(new ProverStateMachine());
		machine.initialize(getMatch().getGame().getRules());
		machine2 = getStateMachine();

		if (!BitStateMachineVerifier.checkMachineConsistency(machine, machine2, timeout - System.currentTimeMillis() - 1000)) {
			System.out.println("NOT CONSISTENT");
			System.exit(0);
		}
	}



	/**
	 * This function is called at the start of each round
	 * You are required to return the Move your player will play
	 * before the timeout.
	 *
	 */
	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{

		long start = System.currentTimeMillis();
		List<Move> moves2 = machine2.getLegalMoves(getCurrentState(), getRole());
		/*Set<Move> moves2Set = new HashSet<>(moves2);
		List<Move> moves = machine.getLegalMoves(getCurrentState(), getRole());
		Set<Move> movesSet = new HashSet<>(moves);

		if (!movesSet.equals(moves2Set)) {
			System.out.println(getRole());
			System.out.println("Correct moves: " + moves);
			System.out.println("Wrong moves: " + moves2);
		} else {
			//System.out.println("Legal Moves Correct");
		}

		assert(moves.equals(moves2));*/

		// SampleLegalGamer is very simple : it picks the first legal move
		Move selection = moves2.get((new Random().nextInt(moves2.size())));
		//System.out.println(selection);
/*
		List<Move> randomMove = machine2.getRandomJointMove(getCurrentState());

		MachineState nextState2 = machine2.getNextState(getCurrentState(), randomMove);
		MachineState nextState = machine.getNextState(getCurrentState(), randomMove);

		if(!nextState.equals(nextState2)) {
			System.out.println("Move " + randomMove);
			System.out.println("Correct next: " + nextState.getContents());
			System.out.println("Wrong next: " + nextState2.getContents());
			System.out.println("FAILURE");
		}


		if(machine.isTerminal(nextState) != machine2.isTerminal(nextState)) {
			System.out.println("Move " + randomMove);
			System.out.println("Correct isTerminal: " + machine.isTerminal(nextState));
			System.out.println("Wrong isTerminal: " + machine2.isTerminal(nextState));
			System.out.println("FAILURE");
		}

		if(machine.getGoal(getCurrentState(),getRole()) != machine2.getGoal(getCurrentState(), getRole())) {
			System.out.println(getRole());
			System.out.println("Correct goal: " + machine.getGoal(getCurrentState(),getRole()));
			System.out.println("Wrong goal: " + machine2.getGoal(getCurrentState(), getRole()));
			System.out.println("FAILURE");
		}*/
		// We get the end time
		// It is mandatory that stop<timeout
		long stop = System.currentTimeMillis();

		/**
		 * These are functions used by other parts of the GGP codebase
		 * You shouldn't worry about them, just make sure that you have
		 * moves, selection, stop and start defined in the same way as
		 * this example, and copy-paste these two lines in your player
		 */
		notifyObservers(new GamerSelectedMoveEvent(moves2, selection, stop - start));
		return selection;
	}
}