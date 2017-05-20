package org.ggp.base.player.gamer.statemachine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.And;
import org.ggp.base.util.propnet.architecture.components.Constant;
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


    @Override
    public boolean isTerminal(MachineState state) {
    	setState(state, null);
    	return propNet.getTerminalProposition().getCurrentValue();
    }


    @Override
    public int getGoal(MachineState state, Role role)
            throws GoalDefinitionException {
    	propNet.renderToFile("preGoal.dot");
    	setState(state, null);
    	propNet.renderToFile("postGoal.dot");
        List<Role> roles = propNet.getRoles();
        Set<Proposition> rewards = propNet.getGoalPropositions().get(role);
        for(Proposition reward: rewards) {
        	if (reward.getValue())
        		return getGoalValue(reward);
        }
        System.out.println("ERROR! Reward not defined in state " + state.toString());
        System.exit(0);
        return 0;
    }


    protected void setInit(boolean val, Queue<Component> queue) {
    	Proposition init = propNet.getInitProposition();
    	if (init.getLastPropagatedOutputValue() == val) return;
    	init.setValue(val);
    	init.setLastPropagatedOutputValue(val);
    	for(Component c: init.getOutputs()) {
        	c.edit_T(val);
			queue.add(c);
        }
    }

    protected void setConstants(Queue<Component> q) {
    	for (Component c: propNet.getComponents()) {
    		if (c instanceof Constant) {
    			for (Component out : c.getOutputs()) {
    				if (c instanceof And) {
    					And a = (And) c;
    	    			a.edit_T(c.getValue());
    	    		} else if (c instanceof Or) {
    	    			Or o = (Or) c;
    	    			o.edit_T(c.getValue());
    	    		}
    				q.add(c);
    			}
    			c.setLastPropagatedOutputValue(c.getValue());
    			c.setCurrentValue(c.getValue());
<<<<<<< HEAD
    			if(c.getCurrentValue()) {
    				for (Component out : c.getOutputs()) {
    					c.edit_T(c.getCurrentValue());
    					q.add(c);
    				}
    			}
    			else {
    				for (Component out : c.getOutputs()) {
    					q.add(c);
    				}
    			}
=======
>>>>>>> parent of 1dd3dfb... Differential working
    		}
    	}
    }

    @Override
    public MachineState getInitialState() {
    	//System.out.println("InitialState");
    	clearPropNet();
    	Proposition init = propNet.getInitProposition();
        init.setValue(true);
        init.setLastPropagatedOutputValue(true);
        propNet.renderToFile("init.dot");
        Queue<Component> queue = new LinkedList<Component>();
        setConstants(queue);//Constants don't change throughout the game, so we set them once here
        for(Component c: init.getOutputs()) {
        	c.edit_T(true);
			queue.add(c);
        }
        for(Proposition p: propNet.getBasePropositions().values()) {
        	for (Component c : p.getOutputs())
        		queue.add(c);
        }
        rawPropagate(queue);
        propNet.renderToFile("initProp.dot");
        MachineState state = getStateFromBase();
        queue = new LinkedList<Component>();
        setInit(false, queue);
        propagate(queue);
        propNet.renderToFile("initState.dot");
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

    @Override
    public PropNet getPropNet() {
    	return propNet;
    }

    @Override
    public List<Move> getLegalMoves(MachineState state, Role role)//Change such that we don't have to keep updating legal moves
            throws MoveDefinitionException {
    	System.out.println("legalMoves" + kLegal);
    	propNet.renderToFile("initLegal" + kLegal + ".dot");
    	setState(state, null);
        Set<Proposition> actions = propNet.getLegalPropositions().get(role);
        List<Move> moves = new ArrayList<>();
        for(Proposition action : actions) {
    		if (action.getValue()) {
    			moves.add(getMoveFromProposition(action));
    		}
    	}
<<<<<<< HEAD
=======
        propNet.renderToFile("postLegal" + kLegal + ".dot");
        ++kLegal;
>>>>>>> parent of 1dd3dfb... Differential working
        return moves;
    }



	protected void setBases(MachineState state, Queue<Component> q) {
    	if (state == null) return;
    	Map<GdlSentence, Proposition> bases = propNet.getBasePropositions();
    	for (GdlSentence s: bases.keySet()) {
    		Proposition p = bases.get(s);

    		boolean val = state.getContents().contains(s);
    		if (val == p.getLastPropagatedOutputValue()) continue;

    		p.setLastPropagatedOutputValue(val);
    		p.setValue(val);

    		for (Component c : p.getOutputs()) {
    			c.edit_T(val);
    			q.add(c);
    		}
    	}
    }


	protected void setActions(List<Move> moves, Queue<Component> q) {
    	if(moves == null || moves.isEmpty()) return;
    	List<GdlSentence> actions = toDoes(moves);
    	Map<GdlSentence, Proposition> inputs = propNet.getInputPropositions();
    	for (GdlSentence s : inputs.keySet()) {
    		Proposition p = inputs.get(s);

    		boolean val = actions.contains(s);
    		if (val == p.getLastPropagatedOutputValue()) continue;

    		p.setLastPropagatedOutputValue(val);
    		p.setValue(val);

    		for (Component c : p.getOutputs()) {
    			c.edit_T(val);
    			q.add(c);
    		}
    	}
    }

    protected void setState(MachineState state, List<Move> moves) {
    	Queue<Component> q = new LinkedList<Component>();
    	setInit(false, q);
    	setBases(state, q);
    	propNet.renderToFile("postBases.dot");
    	setActions(moves, q);
    	propNet.renderToFile("postActions.dot");
    	propagate(q);
    	propNet.renderToFile("postProp.dot");
    }

    @Override
	public MachineState getNextState(MachineState state, List<Move> moves) {
    	System.out.println("getNextState" + kNext);
    	propNet.renderToFile("preSet" + kNext + ".dot");
    	setState(state, moves);
    	propNet.renderToFile("postNet" + kNext + ".dot");
    	currentState = getStateFromBase();
<<<<<<< HEAD
=======
    	propNet.renderToFile("newState" + kNext + ".dot");
    	++kNext;
>>>>>>> parent of 1dd3dfb... Differential working
    	return currentState;
    }


    //Propagates normally (ignoring lastPropagatedOutputValue). This version of propagate
    //is only called during getInitialState()
    protected void rawPropagate(Queue<Component> queue) {
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

    		boolean val = c.getCurrentValue();
    		c.setLastPropagatedOutputValue(val);
    		for(Component out: c.getOutputs()) {
    			if (out instanceof And) {
					And a = (And) out;
	    			a.edit_T(val);
	    		} else if (out instanceof Or) {
	    			Or o = (Or) out;
	    			o.edit_T(val);
	    		}
				queue.add(out);
			}
    	}
    }

    //Differential Propagation
    public void propagate(Queue<Component> queue) {
    	//int i = 1;
    	while(!queue.isEmpty()) {
    		Component c = queue.remove();
    		boolean val = (new Random()).nextBoolean();
    		if (c instanceof Proposition) {
    			Proposition p = (Proposition) c;
    			val = c.getSingleInput().getCurrentValue();
    			p.setValue(val);
    		}
    		else if (c instanceof Not) {
    			val = !c.getSingleInput().getCurrentValue();
    			c.setCurrentValue(val);
    		}
    		else if (c instanceof And) {
    			And a = (And) c;
    			val = a.getValue();
    			c.setCurrentValue(val);
    		}
    		else if (c instanceof Or) {
    			Or o = (Or) c;
    			val = o.getValue();
    			c.setCurrentValue(val);
    		}
    		else if (c instanceof Transition) {
    			val = c.getSingleInput().getCurrentValue();
    			c.setCurrentValue(val);
    			//propNet.renderToFile("prop" + i + ".dot");
    			//++i;
    			assert c.getCurrentValue() == val;
    			continue;
    		}

    		//assert c.getCurrentValue() == val;
    		boolean lastVal = c.getLastPropagatedOutputValue();
    		if (lastVal == val) continue;

    		if (c instanceof Proposition) ((Proposition)c).setValue(val);
    		else c.setCurrentValue(val);

    		c.setLastPropagatedOutputValue(val);

    		for(Component out: c.getOutputs()) {
				if (out instanceof And) {
					And a = (And) out;
	    			a.edit_T(val);
	    		} else if (out instanceof Or) {
	    			Or o = (Or) out;
	    			o.edit_T(val);
	    		}
				queue.add(out);
			}
    		//propNet.renderToFile("prop" + i + ".dot");
    		//++i;
    	}
    }





    private boolean clearPropNet() {
    	for (Component c: propNet.getComponents()) {
    		if (c instanceof And) {
				And a = (And) c;
    			a.set(0);
    		} else if (c instanceof Or) {
    			Or o = (Or) c;
    			o.set(0);
    		}
    		c.setCurrentValue(false);
    		c.setLastPropagatedOutputValue(false);
    	}
    	for (Proposition p: propNet.getPropositions()) p.setValue(false);
    	return true;
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
