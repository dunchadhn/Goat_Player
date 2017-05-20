package org.ggp.base.player.gamer.statemachine;


import java.util.List;
import java.util.concurrent.ExecutionException;

import org.ggp.base.apps.player.Player;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;


public class the_men_who_stare_at_goats extends StateMachineGamer {
	protected Player p;

	@Override
	public StateMachine getInitialStateMachine() {
		return new CachedStateMachine(new DifferentialPropNetStateMachine());
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
		StateMachine machine = getStateMachine();
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
		return "the_men_who_stare_at_goats Player";
	}

	protected Move bestmove(Role role, StateMachine machine)
			throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		// TODO Auto-generated method stub
		MachineState state = getCurrentState();
		List<Move> moves = machine.getLegalMoves(state, role);
		return moves.get(0);
	}


}
