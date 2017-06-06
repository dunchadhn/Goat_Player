package org.ggp.base.player.gamer.statemachine;

import org.ggp.base.util.statemachine.Move;

public class MoveStruct {
	public Move move;
	public double score;

	public MoveStruct(Move m, double s) {
		move = m;
		score = s;
	}
}