package org.ggp.base.player.gamer.statemachine.sample;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import org.ggp.base.apps.player.Player;
import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.XStateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.ThreadStateMachine;
import org.ggp.base.util.statemachine.XStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;
import org.ggp.base.util.statemachine.verifier.BitStateMachineVerifier;

public class LegalTest extends XStateMachineGamer {

	Player p;
	private List<Role> roles;
	private int self_index;
	private ThreadStateMachine machine;

	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		// We get the current start time
		long start = System.currentTimeMillis();

		Move selection = null;
		List<Move> moves = machine.getLegalMoves(getCurrentState(), self_index);
		/*for (Move m : moves) {
			if (m.getContents().toString().equals("h")) {
				selection = m;
				break;
			}
		}*/

		// SampleLegalGamer is very simple : it picks the first legal move
		//System.out.println("numMoves: " + moves.size());
		selection = moves.get((new Random()).nextInt(moves.size()));

		// We get the end time
		// It is mandatory that stop<timeout
		long stop = System.currentTimeMillis();

		/**
		 * These are functions used by other parts of the GGP codebase
		 * You shouldn't worry about them, just make sure that you have
		 * moves, selection, stop and start defined in the same way as
		 * this example, and copy-paste these two lines in your player
		 */
		notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
		return selection;
	}

	@Override
	public XStateMachine getInitialStateMachine() {
		return new XStateMachine();
	}

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException, InterruptedException, ExecutionException {
		StateMachine prover = new ProverStateMachine();
		prover.initialize(getMatch().getGame().getRules());
		roles = getStateMachine().getRoles();
		self_index = roles.indexOf(getRole());
		machine = new ThreadStateMachine(getStateMachine(),self_index);
		if (!BitStateMachineVerifier.checkMachineConsistency(prover, machine, timeout - System.currentTimeMillis())) {
			System.out.println("FAILURE");
			System.exit(0);
		}
		System.out.println("SUCCESS");


	}

	@Override
	public void stateMachineStop() {
		// TODO Auto-generated method stub

	}

	@Override
	public void stateMachineAbort() {
		// TODO Auto-generated method stub

	}

	@Override
	public void preview(Game g, long timeout) throws GamePreviewException {
		// TODO Auto-generated method stub

	}

	@Override
	public String getName() {
		return "LegalTest";
	}
}

