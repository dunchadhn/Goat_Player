package org.ggp.base.player.gamer.statemachine;
import java.util.List;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class GoatsLegal extends the_men_who_stare_at_goats {

	@Override
	protected Move bestmove(Role role, StateMachine machine)
			throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		MachineState state = getCurrentState();
		List<Move> moves = machine.getLegalMoves(state, role);
		return moves.get(0);
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "Legal Player";
	}

}