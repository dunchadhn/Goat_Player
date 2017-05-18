package org.ggp.base.player.gamer.statemachine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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
import org.ggp.base.util.propnet.architecture.components.Transition;
import org.ggp.base.util.propnet.factory.OptimizingPropNetFactory;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.query.ProverQueryBuilder;



@SuppressWarnings("unused")
public class DifferentialPropNetStateMachine extends StateMachine {
    /** The underlying proposition network  */
    private PropNet propNet;
    /** The topological ordering of the propositions */
    private List<Proposition> ordering;
    /** The player roles */
    private List<Role> roles;
    private MachineState currentState;

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
            currentState = null;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Computes if the state is terminal. Should return the value
     * of the terminal proposition for the state.
     *
     * function propterminalp (state,propnet)
     * {markbases(state,propnet);
  	 * return propmarkp(propnet.terminal)}
     */
    @Override
    public boolean isTerminal(MachineState state) {
    	if(state != null && currentState != null) {
    		if(!state.equals(currentState)) {
    			markBases(state.getContents());
    			currentState = getStateFromBase();
    		}
    	} else {
    		markBases(state.getContents());
			currentState = getStateFromBase();
    	}
        return propMarkP(propNet.getTerminalProposition());
    }

    /**
     * Computes the goal for a role in the current state.
     * Should return the value of the goal proposition that
     * is true for that role. If there is not exactly one goal
     * proposition true for that role, then you should throw a
     * GoalDefinitionException because the goal is ill-defined.
     *
     * {markbases(state,propnet);
  	 * var roles = propnet.roles;
  	 * var rewards = seq();
  	 * for (var i=0; i<roles.length; i++)
     * 		{if (role==roles[i]) {rewards = propnet.rewards[i]; break}};
  	 * for (var i=0; i<rewards.length; i++)
     * 		{if (propmarkp(rewards[i])) {return rewards[i].name}};
  	 * return 0}
     */
    @Override
    public int getGoal(MachineState state, Role role)
            throws GoalDefinitionException {
    	if(state != null && currentState != null) {
    		if(!state.equals(currentState)) {
    			markBases(state.getContents());
    			currentState = getStateFromBase();
    		}
    	} else {
    		markBases(state.getContents());
			currentState = getStateFromBase();
    	}
        List<Role> roles = propNet.getRoles();
        Set<Proposition> rewards = new HashSet<Proposition>();
        rewards = propNet.getGoalPropositions().get(role);
        for(Proposition reward: rewards) {
        	if(propMarkP(reward)) {
        		return getGoalValue(reward);
        	}
        }
        return 0;
    }

    /**
     * Returns the initial state. The initial state can be computed
     * by only setting the truth value of the INIT proposition to true,
     * and then computing the resulting state.
     */
    @Override
    public MachineState getInitialState() {

    	Proposition init = propNet.getInitProposition();
        init.setValue(true);
        Queue<Component> queue = new LinkedList<Component>();
        for(Component c: init.getOutputs()) {
        	if (c instanceof And) {
				And a = (And) c;
				a.edit_T(init.getCurrentValue());
			} else if (c instanceof Or) {
				Or o = (Or) c;
				o.edit_T(init.getCurrentValue());
			}
			queue.add(c);
        }
        propagate(queue);
        propNet.renderToFile("ticInit.dot");
        if(1 == 1) {
        	return null;
        }
        MachineState state = getStateFromBase();
        propNet.renderToFile("ticInitPost.dot");
        init.setValue(false);
        MachineState returnState = state.clone();
        markBases(state.getContents());
        currentState = state;
        propNet.renderToFile("ticPost.dot");
        return returnState;
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

    @Override
    public PropNet getPropNet() {
    	return propNet;
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
    	if(state != null && currentState != null) {
    		if(!state.equals(currentState)) {
    			markBases(state.getContents());
    			currentState = getStateFromBase();
    		}
    	} else {
    		markBases(state.getContents());
			currentState = getStateFromBase();
    	}
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
/*    public MachineState getNextState(MachineState state, List<Move> moves)
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
*/

    public MachineState getNextState(MachineState state, List<Move> moves) {
    	propNet.renderToFile("ticPre.dot");
    	differentialPropagate(state, moves);
    	propNet.renderToFile("ticPost.dot");
    	currentState = getStateFromBase();
    	return currentState;
    }

    public void differentialPropagate(MachineState state, List<Move> moves) {
    	Map<GdlSentence, Proposition> bases = propNet.getBasePropositions();
    	List<GdlSentence> actions;
    	markBases(state.getContents());
    	if(!moves.isEmpty()) {
    		actions = toDoes(moves);
        	markActions(actions);
    	}
    }

    public void propagate(Queue<Component> queue) {
    	while(!queue.isEmpty()) {
    		Component c = queue.remove();
    		if (c instanceof Proposition) {
    			Proposition p = (Proposition) c;
    			p.setValue(c.getSingleInput().getCurrentValue());
    		}
    		else if (c instanceof Not) {
    			c.setCurrentValue(!c.getSingleInput().getCurrentValue());
    		}
    		else if (c instanceof And) {
    			And a = (And) c;
    			c.setCurrentValue(a.getValue());
    		}
    		else if (c instanceof Or) {
    			Or o = (Or) c;
    			c.setCurrentValue(o.getValue());
    		}
    		else if (c instanceof Transition) {
    			c.setCurrentValue(c.getSingleInput().getCurrentValue());
    			continue;
    		}

    		if(c.getCurrentValue() != c.getLastPropagatedOutputValue()) {
				c.setLastPropagatedOutputValue(c.getCurrentValue());
				for(Component out: c.getOutputs()) {
					if (out instanceof And) {
						And a = (And) out;
		    			a.edit_T(c.getLastPropagatedOutputValue());
		    		} else if (out instanceof Or) {
		    			Or o = (Or) out;
		    			o.edit_T(c.getLastPropagatedOutputValue());
		    		}
					queue.add(out);
				}
			}
    	}
    }

    /**
     * function markbases (vector,propnet)
     *    {var props = propnet.bases;
     *    for (var i=0; i<props.length; i++)
     *      {props[i].mark = vector[i]};
  	 *	 return true}
     */
    private void markBases(Set<GdlSentence> vector) {
		propNet.renderToFile("ticMarkPre.dot");
    	Queue<Component> queue = new LinkedList<Component>();
    	clearPropNet();
    	Map<GdlSentence, Proposition> props = propNet.getBasePropositions();
    	for(GdlSentence sentence : vector) {
    		props.get(sentence).setValue(true);
    	}
    	for(Proposition p: props.values()) {
    		if(p.getCurrentValue() != p.getLastPropagatedOutputValue()) {
    			p.setLastPropagatedOutputValue(p.getCurrentValue());
    			for(Component c: p.getOutputs()) {
    				if (c instanceof And) {
    					And a = (And) c;
    					a.edit_T(p.getCurrentValue());
    				} else if (c instanceof Or) {
    					Or o = (Or) c;
    					o.edit_T(p.getCurrentValue());
    				}
    				queue.add(c);
    			}
    		}
    	}
    	propNet.renderToFile("ticMarkPost.dot");
    	propagate(queue);
    }

    /**
	 *	function markactions (vector,propnet)
	 *	  {var props = propnet.actions;
	 * 	  for (var i=0; i<props.length; i++)
	 *     	{props[i].mark = vector[i]};
	 * 	  return true}
     */
    private void markActions(List<GdlSentence> moves) {
    	Queue<Component> queue = new LinkedList<Component>();
    	for (Proposition p : propNet.getInputPropositions().values()) {
    		p.setValue(false);
    	}
    	for (int i=0; i<moves.size(); ++i) {
    		GdlSentence sentence = moves.get(i);
    		Proposition p = propNet.getInputPropositions().get(sentence);
			GdlSentence pSentence = p.getName();
			if (sentence.equals(pSentence)) {
				p.setValue(true);
			}
    	}
    	for(Proposition p: propNet.getInputPropositions().values()) {
    		if(p.getCurrentValue() != p.getLastPropagatedOutputValue()) {
    			p.setLastPropagatedOutputValue(p.getCurrentValue());
    			for(Component c: p.getOutputs()) {
    				if (c instanceof And) {
    					And a = (And) c;
    					a.edit_T(p.getCurrentValue());
    				} else if (c instanceof Or) {
    					Or o = (Or) c;
    					o.edit_T(p.getCurrentValue());
    				}
    				queue.add(c);
    			}
    		}
    	}
    	propagate(queue);
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
		return c.getCurrentValue();
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
