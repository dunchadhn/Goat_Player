package org.ggp.base.player.gamer.statemachine.Heuristic;

import java.util.List;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;

public class Features {
	private static StateMachine machine;
	private static List<Role> roles;
	private static int numFeatures;

	public static void initialize(StateMachine stateMachine, List<Role> playerRoles) {
		machine = stateMachine;
		roles = playerRoles;
		numFeatures = roles.size() * 2;
	}

	public static double[] getFeatures(MachineState state, int player) throws MoveDefinitionException, GoalDefinitionException {
		double[] features = new double[numFeatures];
		int index = -1;
		//0-step mobility/focus
		for (int i = 0; i < roles.size(); ++i)
			features[++index] = mobilityFn(state, i);
		//states reachable

		//depth

		//Goal
		for (int i = 0; i < roles.size(); ++i)
			features[index++] = goalFn(state, i);
		//Distance to win

		//Game longevity

		return features;
	}

	private static double mobilityFn(MachineState state, int player) throws MoveDefinitionException {
		return machine.getLegalMoves(state, roles.get(player)).size();
	}

	private static double goalFn(MachineState state, int player) throws GoalDefinitionException {
		return machine.getGoal(state, roles.get(player));
	}
}
