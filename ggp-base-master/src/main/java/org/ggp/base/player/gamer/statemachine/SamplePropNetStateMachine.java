package org.ggp.base.player.gamer.statemachine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.And;
import org.ggp.base.util.propnet.architecture.components.Not;
import org.ggp.base.util.propnet.architecture.components.Or;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.factory.OptimizingPropNetFactory;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.query.ProverQueryBuilder;



@SuppressWarnings("unused")
public class SamplePropNetStateMachine extends StateMachine {
    /** The underlying proposition network  */
    private PropNet propNet;
    /** The topological ordering of the propositions */
    private List<Proposition> ordering;
    /** The player roles */
    private List<Role> roles;

    /**
     * Initializes the PropNetStateMachine. You should compute the topological
     * ordering here. Additionally you may compute the initial state here, at
     * your discretion.
     */
    @Override
    public void initialize(List<Gdl> description) {
        try {
        	System.out.println("Initialized");
            propNet = OptimizingPropNetFactory.create(description);
            roles = propNet.getRoles();
            ordering = getOrdering();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Computes if the state is terminal. Should return the value
     * of the terminal proposition for the state.
     */
    @Override
    public boolean isTerminal(MachineState state) {
        // TODO: Compute whether the MachineState is terminal.
        return false;
    }

    /**
     * Computes the goal for a role in the current state.
     * Should return the value of the goal proposition that
     * is true for that role. If there is not exactly one goal
     * proposition true for that role, then you should throw a
     * GoalDefinitionException because the goal is ill-defined.
     */
    @Override
    public int getGoal(MachineState state, Role role)
            throws GoalDefinitionException {
        // TODO: Compute the goal for role in state.
        return -1;
    }

    /**
     * Returns the initial state. The initial state can be computed
     * by only setting the truth value of the INIT proposition to true,
     * and then computing the resulting state.
     */
    @Override
    public MachineState getInitialState() {
    	propNet.renderToFile("ticInit.dot");
    	Proposition init = propNet.getInitProposition();
        init.setValue(true);
        MachineState state = getStateFromBase();
        init.setValue(false);
        propNet.renderToFile("ticPost.dot");
        return state;
    }

    /**
     * Computes all possible actions for role.
     */
    @Override
    public List<Move> findActions(Role role)
            throws MoveDefinitionException {
        Set<Proposition> actions = propNet.getLegalPropositions().get(role);
        List<Move> moves = new ArrayList<>();
        for(Proposition action : actions) {
        	moves.add(getMoveFromProposition(action));
        }
        return moves;
    }

    /**
     * Computes the legal moves for role in state.
     *
     *  function proplegals (role,state,propnet)
	 *	 {markbases(state,propnet);
	 *	  var roles = propnet.roles;
	 *	  var legals = seq();
	 *	  for (var i=0; i<roles.length; i++)
	 *	      {if (role==roles[i]) {legals = propnet.legals[i]; break}};
	 *	  var actions = seq();
	 *	  for (var i=0; i<legals.length; i++)
	 *	      {if (propmarkp(legals[i]))
	 *	          {actions[actions.length]=legals[i]}};
	 *	  return actions}
     */
    @Override
    public List<Move> getLegalMoves(MachineState state, Role role)
            throws MoveDefinitionException {
    	markBases(state.getContents());
        Set<Proposition> actions = propNet.getLegalPropositions().get(role);
        List<Move> moves = new ArrayList<>();
        for(Proposition action : actions) {
    		if (propMarkP(action)) {
    			moves.add(getMoveFromProposition(action));
    		}
    	}
        return moves;
    }

    /**
     * Computes the next state given state and the list of moves.
     *
     * function propnext (move,state,propnet)
 	 * {markactions(move,propnet);
  	 *	markbases(state,propnet);
	 *	var bases = propnet.bases;
	 *	var nexts = seq();
	 *	for (var i=0; i<bases.length; i++)
	 *	  {nexts[i] = propmarkp(bases[i].source.source)};
	 *	return nexts}
     */
    @Override
    public MachineState getNextState(MachineState state, List<Move> moves)
            throws TransitionDefinitionException {
    	List<GdlSentence> actions = toDoes(moves);
    	markBases(state.getContents());
    	markActions(actions);
    	Map<GdlSentence, Proposition> bases = propNet.getBasePropositions();

    	Set<GdlSentence> nextState = new HashSet<>();
    	for (Proposition p : bases.values()) {
    		boolean mark = propMarkP(p.getSingleInput().getSingleInput());
    		if (mark) {
    			nextState.add(p.getName());
    		}
    	}
        return new MachineState(nextState);
    }

    /**
     * function markbases (vector,propnet)
     *    {var props = propnet.bases;
     *    for (var i=0; i<props.length; i++)
     *      {props[i].mark = vector[i]};
  	 *	 return true}
     */
    private boolean markBases(Set<GdlSentence> vector) {
    	clearPropNet();
    	Map<GdlSentence, Proposition> props = propNet.getBasePropositions();
    	for(GdlSentence sentence : vector) {
    		props.get(sentence).setValue(true);
    	}
    	return true;
    }

    /**
	 *	function markactions (vector,propnet)
	 *	  {var props = propnet.actions;
	 * 	  for (var i=0; i<props.length; i++)
	 *     	{props[i].mark = vector[i]};
	 * 	  return true}
     */
    private boolean markActions(List<GdlSentence> moves) {
    	for (Proposition p : propNet.getInputPropositions().values()) {
    		if (p.getValue()) {
    			System.out.println("Action already marked: " + p.getName());
    		}
    	}
    	for (int i=0; i<moves.size(); ++i) {
    		GdlSentence sentence = moves.get(i);
    		Proposition p = propNet.getInputPropositions().get(sentence);
			GdlSentence pSentence = p.getName();
			if (sentence.equals(pSentence)) {
				p.setValue(true);
			}
    	}
    	return true;
    }

    /**
	 *	function clearpropnet (propnet)
	 *	  {var props = propnet.bases;
	 * 	  for (var i=0; i<props.length; i++)
	 *     	{props[i].mark = false};
	 * 	  return true}
     */
    private boolean clearPropNet() {
    	Map<GdlSentence, Proposition> props = propNet.getBasePropositions();
    	for(Proposition p : props.values()) {
    		p.setValue(false);
    	}
    	for (Proposition p : propNet.getInputPropositions().values()) {
    		p.setValue(false);
    	}
    	return true;
    }

    /**
	 *   function propmarkp (p)
	 *   {if (p.type=='base') {return p.mark};
	 *    if (p.type=='input') {return p.mark};
	 *    if (p.type=='view') {return propmarkp(p.source)};
	 *    if (p.type=='negation') {return propmarknegation(p)};
	 *    if (p.type=='conjunction') {return propmarkconjunction(p)};
	 *    if (p.type=='disjunction') {return propmarkdisjunction(p)};
	 *    return false}
     */
    private boolean propMarkP(Component c) {
		if (c instanceof Proposition) {
			Proposition p = (Proposition) c;
			if (propNet.getBasePropositions().get(p.getName()) != null) return c.getValue();
			if (propNet.getInputPropositions().get(p.getName()) != null) return c.getValue();
			if (propNet.getInitProposition().equals(p)) return c.getValue();
			return propMarkP(c.getSingleInput());
		}
		if (c instanceof Not) return propMarkNegation(c);
		if (c instanceof And) return propMarkConjunction(c);
		if (c instanceof Or) return propMarkDisjunction(c);
		return false;
    }

    /**
    function propmarknegation (p)
    {return !propmarkp(p.source)}
    */
    private boolean propMarkNegation(Component c) {
    	return !propMarkP(c.getSingleInput());
    }

    /**
	 *  function propmarkconjunction (p)
	 *   {var sources = p.sources;
	 *    for (var i=0; i<sources.length; i++)
	 *        {if (!propmarkp(sources[i])) {return false}};
	 *    return true}
     */
    private boolean propMarkConjunction(Component p) {
    	Set<Component> sources = p.getInputs();
    	for (Component source : sources) {
    		if (!propMarkP(source)) {
    			return false;
    		}
    	}
    	return true;
    }

    /**
	 *   function propmarkdisjunction (p)
	 *    {var sources = p.sources;
	 *     for (var i=0; i<sources.length; i++)
	 *         {if (propmarkp(sources[i])) {return true}};
	 *     return false}
     */
    private boolean propMarkDisjunction(Component p) {
    	Set<Component> sources = p.getInputs();
    	for (Component source : sources) {
    		if (propMarkP(source)) {
    			return true;
    		}
    	}
    	return false;
    }

    /**
     * This should compute the topological ordering of propositions.
     * Each component is either a proposition, logical gate, or transition.
     * Logical gates and transitions only have propositions as inputs.
     *
     * The base propositions and input propositions should always be exempt
     * from this ordering.
     *
     * The base propositions values are set from the MachineState that
     * operations are performed on and the input propositions are set from
     * the Moves that operations are performed on as well (if any).
     *
     * @return The order in which the truth values of propositions need to be set.
     */
    public List<Proposition> getOrdering()
    {
        // List to contain the topological ordering.
        List<Proposition> order = new LinkedList<Proposition>();

        // All of the components in the PropNet
        List<Component> components = new ArrayList<Component>(propNet.getComponents());

        // All of the propositions in the PropNet.
        List<Proposition> propositions = new ArrayList<Proposition>(propNet.getPropositions());

        // TODO: Compute the topological ordering.

        return order;
    }

    /* Already implemented for you */
    @Override
    public List<Role> getRoles() {
        return roles;
    }

    /* Helper methods */

    /**
     * The Input propositions are indexed by (does ?player ?action).
     *
     * This translates a list of Moves (backed by a sentence that is simply ?action)
     * into GdlSentences that can be used to get Propositions from inputPropositions.
     * and accordingly set their values etc.  This is a naive implementation when coupled with
     * setting input values, feel free to change this for a more efficient implementation.
     *
     * @param moves
     * @return
     */
    private List<GdlSentence> toDoes(List<Move> moves)
    {
        List<GdlSentence> doeses = new ArrayList<GdlSentence>(moves.size());
        Map<Role, Integer> roleIndices = getRoleIndices();

        for (int i = 0; i < roles.size(); i++)
        {
            int index = roleIndices.get(roles.get(i));
            doeses.add(ProverQueryBuilder.toDoes(roles.get(i), moves.get(index)));
        }
        return doeses;
    }

    /**
     * Takes in a Legal Proposition and returns the appropriate corresponding Move
     * @param p
     * @return a PropNetMove
     */
    public static Move getMoveFromProposition(Proposition p)
    {
        return new Move(p.getName().get(1));
    }

    /**
     * Helper method for parsing the value of a goal proposition
     * @param goalProposition
     * @return the integer value of the goal proposition
     */
    private int getGoalValue(Proposition goalProposition)
    {
        GdlRelation relation = (GdlRelation) goalProposition.getName();
        GdlConstant constant = (GdlConstant) relation.get(1);
        return Integer.parseInt(constant.toString());
    }

    /**
     * A Naive implementation that computes a PropNetMachineState
     * from the true BasePropositions.  This is correct but slower than more advanced implementations
     * You need not use this method!
     * @return PropNetMachineState
     */
    public MachineState getStateFromBase()
    {
        Set<GdlSentence> contents = new HashSet<GdlSentence>();
        for (Proposition p : propNet.getBasePropositions().values())
        {
            p.setValue(p.getSingleInput().getValue());
            if (p.getValue())
            {
                contents.add(p.getName());
            }

        }
        return new MachineState(contents);
    }
}