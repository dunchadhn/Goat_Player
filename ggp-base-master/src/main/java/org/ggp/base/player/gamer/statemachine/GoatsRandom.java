package org.ggp.base.player.gamer.statemachine;
import java.util.List;
import java.util.Random;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class GoatsRandom extends the_men_who_stare_at_goats {

	@Override
	protected Move bestmove(Role role, StateMachine machine)
			throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		MachineState state = getCurrentState();
		List<Move> moves = machine.getLegalMoves(state, role);
		Random rand = new Random();
		return moves.get(rand.nextInt(moves.size()));
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "Random Player";
	}

}