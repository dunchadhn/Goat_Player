package org.ggp.base.player.gamer.statemachine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.apache.lucene.util.OpenBitSet;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.util.Pair;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.XStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class Heuristic extends XStateMachineGamer {

	private XStateMachine machine;
	private int self_index, num_features;
	private List<Role> roles;
	private long finishBy, dataFinishBy;
	private HashMap<OpenBitSet, Integer> valueMap;
	private OpenBitSet initState;

	@Override
	public XStateMachine getInitialStateMachine() {
		return new XStateMachine();
	}

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException, InterruptedException, ExecutionException {
		machine = getStateMachine();
		roles = machine.getRoles();
		self_index = roles.indexOf(getRole());
		initState = getCurrentState();
		num_features = getNumFeatures();

		long metaTime = timeout - System.currentTimeMillis() - 2500;
		dataFinishBy = 3 * metaTime / 4 + System.currentTimeMillis();
		activateFeatures();
		System.exit(0);

	}

	protected int getNumFeatures() throws MoveDefinitionException, GoalDefinitionException {
		List<Double> features = new ArrayList<Double>();
		for (int i = 0; i < roles.size(); ++i) {
			List<Move> rMoves = machine.getLegalMoves(initState, i);
			features.add((double)rMoves.size());
		}

		for (int i = 0; i < roles.size(); ++i) {
			features.add((double)0);
		}

		return features.size();
	}

	protected void getData(List<Pair<double[], Double>> cumulData, List<Pair<double[], Double>> indivData) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {

		while (System.currentTimeMillis() < dataFinishBy) {
			OpenBitSet state = initState;
			double[] cumulative_features = new double[num_features];
			List<double[]> indiv_features = new ArrayList<double[]>();

			do {
				System.out.println("HEY");
				state = machine.getRandomNextState(state);
				double[] features = getFeatures(state);
				addArrays(cumulative_features, features);
				indiv_features.add(features);
				System.out.println("BOO");

			} while (!machine.isTerminal(state));

			System.out.println("HERE");
			System.out.println(machine.isTerminal(state));
			double goalVal = (double)machine.getGoal(state, self_index);
			cumulData.add(Pair.of(cumulative_features, goalVal));
			int size = indiv_features.size();
			for (int i = 0; i < size; ++i) {
				indivData.add(Pair.of(indiv_features.get(i), goalVal));
			}
		}


	}

	List<Integer> activateFeatures() throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		SimpleRegression[] cumulCorrelations = new SimpleRegression[num_features];
		SimpleRegression[] indivCorrelations = new SimpleRegression[num_features];
		getCorrelations(cumulCorrelations, indivCorrelations);

		for (int i = 0; i < num_features; ++i) {
			System.out.println(cumulCorrelations[i].getR());
		}
		System.out.println();
		for (int i = 0; i < num_features; ++i) {
			System.out.println(indivCorrelations[i].getR());
		}

		return null;
	}

	protected void getCorrelations(SimpleRegression[] cumulCorrelations, SimpleRegression[] indivCorrelations) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		List<Pair<double[], Double>> cumulData = new ArrayList<Pair<double[], Double>>();
		List<Pair<double[], Double>> indivData = new ArrayList<Pair<double[], Double>>();

		getData(cumulData, indivData);

		for (int i = 0; i < num_features; ++i) {
			SimpleRegression rCumul = new SimpleRegression();
			int size = cumulData.size();
			for (int j = 0; j < size; ++j) {
				Pair<double[], Double> datum = cumulData.get(j);
				rCumul.addData((datum.left)[i], datum.right);
			}
			cumulCorrelations[i] = rCumul;
		}

		for (int i = 0; i < num_features; ++i) {
			SimpleRegression rIndiv = new SimpleRegression();
			int size = indivData.size();
			for (int j = 0; j < size; ++j) {
				Pair<double[], Double> datum = indivData.get(j);
				rIndiv.addData((datum.left)[i], datum.right);
			}
			indivCorrelations[i] = rIndiv;
		}
	}

	protected void addArrays(double[] arr1, double[] arr2) {
		int size = arr1.length;
		if (arr1.length != arr2.length) {
			System.out.println("arr1.length != arr2.length");
			System.exit(0);
		}
		for (int i = 0; i < size; ++i) {
			arr1[i] += arr2[i];
		}
	}

	protected double[] getFeatures(OpenBitSet state) throws MoveDefinitionException, GoalDefinitionException {
		double[] features = new double[num_features];
		int index = 0;
		for (int i = 0; i < roles.size(); ++i) {
			List<Move> rMoves = machine.getLegalMoves(state, i);
			features[index++] = ((double)rMoves.size());
		}

		for (int i = 0; i < roles.size(); ++i) {
			features[index++] = ((double)machine.getGoal(state, i));
		}

		return features;
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException, InterruptedException, ExecutionException {
		// TODO Auto-generated method stub
		return null;
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
		return "Heuristic Player";
	}

}
