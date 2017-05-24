package org.ggp.base.player.gamer.statemachine.men_who_stare_at_goats;


import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.lucene.util.OpenBitSet;
import org.ggp.base.apps.player.Player;
import org.ggp.base.player.gamer.XStateMachineGamer;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.XStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;


public class X_the_men_who_stare_at_goats extends XStateMachineGamer {
	protected Player p;

	@Override
	public XStateMachine getInitialStateMachine() {
		return new XStateMachine();
	}

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException, InterruptedException, ExecutionException{
		// TODO Auto-generated method stub

	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException, InterruptedException, ExecutionException {
		XStateMachine machine = getStateMachine();
		//More efficient to use Compulsive Deliberation for one player games
		//Use two-player implementation for two player games
		Role role = getRole();
		return bestmove(role, machine);

	}

	@Override
	public void stateMachineStop() {


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
		// TODO Auto-generated method stub
		return "X_the_men_who_stare_at_goats Player";
	}

	protected Move bestmove(Role role, XStateMachine machine)
			throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		// TODO Auto-generated method stub
		OpenBitSet state = getCurrentState();
		List<Move> moves = machine.getLegalMoves(state, role);
		return moves.get(0);
	}


}

