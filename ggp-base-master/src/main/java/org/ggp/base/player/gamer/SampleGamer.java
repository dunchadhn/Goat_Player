package org.ggp.base.player.gamer;

import org.ggp.base.apps.player.detail.DetailPanel;
import org.ggp.base.apps.player.detail.SimpleDetailPanel;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.BitDifferentialPropNetStateMachine;
import org.ggp.base.util.statemachine.BitStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

/**
 * SampleGamer is a simplified version of the StateMachineGamer, dropping some
 * advanced functionality so the example gamers can be presented concisely.
 * This class implements 7 of the 8 core functions that need to be implemented
 * for any gamer.
 *
 * If you want to quickly create a gamer of your own, extend this class and
 * add the last core function : public Move stateMachineSelectMove(long timeout)
 */

public abstract class SampleGamer extends BitStateMachineGamer
{
	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		// Sample gamers do no metagaming at the beginning of the match.
	}



	/** This will currently return "SampleGamer"
	 * If you are working on : public abstract class MyGamer extends SampleGamer
	 * Then this function would return "MyGamer"
	 */
	@Override
	public String getName() {
		return getClass().getSimpleName();
	}

	// This is the default State Machine
	@Override
	public BitStateMachine getInitialStateMachine() {
		return new BitDifferentialPropNetStateMachine();
	}

	// This is the defaul Sample Panel
	@Override
	public DetailPanel getDetailPanel() {
		return new SimpleDetailPanel();
	}



	@Override
	public void stateMachineStop() {
		// Sample gamers do no special cleanup when the match ends normally.
	}

	@Override
	public void stateMachineAbort() {
		// Sample gamers do no special cleanup when the match ends abruptly.
	}

	@Override
	public void preview(Game g, long timeout) throws GamePreviewException {
		// Sample gamers do no game previewing.
	}
}