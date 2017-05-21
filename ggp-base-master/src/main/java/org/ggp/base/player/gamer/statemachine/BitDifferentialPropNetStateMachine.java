package org.ggp.base.player.gamer.statemachine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.apache.lucene.util.OpenBitSet;
import org.ggp.base.util.Pair;
import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.gdl.grammar.GdlTerm;
import org.ggp.base.util.propnet.architecture.BitPropNet;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.components.Constant;
import org.ggp.base.util.propnet.architecture.components.Not;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.architecture.components.Transition;
import org.ggp.base.util.propnet.factory.BitOptimizingPropNetFactory;
import org.ggp.base.util.statemachine.BitStateMachine;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;



@SuppressWarnings("unused")
public class BitDifferentialPropNetStateMachine extends BitStateMachine {
    /** The underlying proposition network  */
    private BitPropNet propNet;
    /** The player roles */
    private List<Role> roles;
    private BitMachineState currentState;

    /**
     * Initializes the PropNetStateMachine. You should compute the topological
     * ordering here. Additionally you may compute the initial state here, at
     * your discretion.
     */
    @Override
    public void initialize(List<Gdl> description) {
        try {
        	System.out.println("Initialized");
            propNet = BitOptimizingPropNetFactory.create(description);
            roles = propNet.getRoles();
            currentState = null;
            for(Component c: propNet.getComponents()) {
            	c.crystallize();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public boolean isTerminal(BitMachineState state) {
    	setState(state, null);
    	return propNet.getTerminalProposition().getCurrentValue();
    }


    @Override
    public int getGoal(BitMachineState state, Role role)
            throws GoalDefinitionException {
    	setState(state, null);
        List<Role> roles = propNet.getRoles();
        Proposition[] rewards = propNet.getGoalPropositions().get(role);
        int size = rewards.length;
        for(int i = 0; i < size; ++i) {
        	Proposition reward = rewards[i];
        	if (reward.getCurrentValue())
        		return getGoalValue(reward);
        }
        System.out.println("ERROR! Reward not defined in state " + state.toString());
        System.exit(0);
        return 0;
    }


    protected void setInit(boolean val, Queue<Component> queue) {
    	Proposition init = propNet.getInitProposition();
    	if (init.getLastPropagatedOutputValue() == val) return;
    	init.setCurrentValue(val);
    	init.setLastPropagatedOutputValue(val);
    	Component[] outputs = init.getOutputs_arr();
    	int size = init.getOutputsSize();
    	for(int i = 0; i < size; ++i) {
    		outputs[i].edit_T(val);
			queue.add(outputs[i]);
        }
    }

    protected void setConstants(Queue<Component> q) {
    	for (Component c: propNet.getComponents()) {
    		if (c instanceof Constant) {
    			boolean val = c.getCurrentValue();
    			c.setLastPropagatedOutputValue(val);
    			Component[] outputs = c.getOutputs_arr();
		    	int size = c.getOutputsSize();
    			if(val) {
    		    	for(int i = 0; i < size; ++i) {
    		    		outputs[i].edit_T(val);
    					q.add(outputs[i]);
    		        }
    			}
    			else {
    				for(int i = 0; i < size; ++i) {
    					q.add(outputs[i]);
    		        }
    			}
    		}
    	}
    }

    private int kInit = 1;
    @Override
    public BitMachineState getInitialState() {
    	clearPropNet();
    	Proposition init = propNet.getInitProposition();
        init.setCurrentValue(true);
        init.setLastPropagatedOutputValue(true);
        Queue<Component> queue = new LinkedList<Component>();
        setConstants(queue);//Constants don't change throughout the game, so we set them once here
        Component[] outputs = init.getOutputs_arr();
    	int size = init.getOutputsSize();
    	for(int i = 0; i < size; ++i) {
    		outputs[i].edit_T(true);
			queue.add(outputs[i]);
        }

    	Proposition[] bases = propNet.getBasePropositions();
    	size = bases.length;
        for(int i = 0; i < size; ++i) {//Don't add redundant states
        	Proposition p = bases[i];
        	outputs = p.getOutputs_arr();
        	int size_2 = p.getOutputsSize();
        	for(int j = 0; j < size_2; ++j) {
    			queue.add(outputs[j]);
            }
        }

        Proposition[] inputs = propNet.getInputPropositions();
    	size = inputs.length;
        for(int i = 0; i < size; ++i) {
        	Proposition p = inputs[i];
        	outputs = p.getOutputs_arr();
        	int size_2 = p.getOutputsSize();
        	for(int j = 0; j < size_2; ++j) {
    			queue.add(outputs[j]);
            }
        }
        rawPropagate(queue);
        BitMachineState state = getStateFromBase();
        queue = new LinkedList<Component>();
        setInit(false, queue);
        propagate(queue);
        return state;
    }


    /**
     * Computes all possible actions for role.
     */
    @Override
    public List<Move> findActions(Role role)
            throws MoveDefinitionException {
        Proposition[] actions = propNet.getLegalPropositions().get(role);
        List<Move> moves = new ArrayList<>();

    	int size = actions.length;
        for(int i = 0; i < size; ++i) {
        	Proposition action = actions[i];
        	moves.add(getMoveFromProposition(action));
        }
        return moves;
    }

    @Override
    public BitPropNet getPropNet() {
    	return propNet;
    }

    @Override
    public List<Move> getLegalMoves(BitMachineState state, Role role)//Change such that we don't have to keep updating legal moves
            throws MoveDefinitionException {
    	setState(state, null);
    	Proposition[] actions = propNet.getLegalPropositions().get(role);
        List<Move> moves = new ArrayList<>();

        int size = actions.length;
        for(int i = 0; i < size; ++i) {
        	Proposition action = actions[i];
    		if (action.getCurrentValue()) {
    			moves.add(getMoveFromProposition(action));
    		}
    	}
        return moves;
    }



	protected void setBases(BitMachineState state, Queue<Component> q) {
    	if (state == null) return;
    	Proposition[] bases = propNet.getBasePropositions();
    	int size = bases.length;
    	OpenBitSet bitSet = state.getContents();

    	for (int i = 0; i < size; ++i) {
    		Proposition p = bases[i];

    		boolean val = bitSet.fastGet(i);
    		if (val == p.getLastPropagatedOutputValue()) continue;

    		p.setLastPropagatedOutputValue(val);
    		p.setCurrentValue(val);

    		Component[] outputs = p.getOutputs_arr();
        	int size_2 = p.getOutputsSize();
        	for(int j = 0; j < size_2; ++j) {
        		outputs[j].edit_T(val);
    			q.add(outputs[j]);
            }
    	}
    }


	protected void setActions(List<Move> moves, Queue<Component> q) {
    	if(moves == null || moves.isEmpty()) return;
    	Proposition[] inputs = propNet.getInputPropositions();
    	int size = inputs.length;
    	OpenBitSet actions = toDoes(moves, size, inputs);
    	for (int i = 0; i < size; ++i) {
    		Proposition p = inputs[i];

    		boolean val = actions.fastGet(i);
    		if (val == p.getLastPropagatedOutputValue()) continue;

    		p.setLastPropagatedOutputValue(val);
    		p.setCurrentValue(val);

    		Component[] outputs = p.getOutputs_arr();
        	int size_2 = p.getOutputsSize();
        	for(int j = 0; j < size_2; ++j) {
        		outputs[j].edit_T(val);
    			q.add(outputs[j]);
            }
    	}
    }

    protected void setState(BitMachineState state, List<Move> moves) {
    	Queue<Component> q = new LinkedList<Component>();
    	setInit(false, q);
    	setBases(state, q);
    	setActions(moves, q);
    	propagate(q);
    }

    @Override
	public BitMachineState getNextState(BitMachineState state, List<Move> moves) {
    	setState(state, moves);
    	currentState = getStateFromBase();
    	return currentState;
    }


    //Propagates normally (ignoring lastPropagatedOutputValue). This version of propagate
    //is only called during getInitialState()
    protected void rawPropagate(Queue<Component> queue) {
    	while(!queue.isEmpty()) {
    		Component c = queue.remove();
    		if (c instanceof Not) {
    			c.setCurrentValue(!c.getSingleInput_arr().getCurrentValue());
    		}
    		else {
    			c.setCurrentValue(c.getSingleInput_arr().getCurrentValue());
    		}
    		if (c instanceof Transition) {
    			continue;
    		}

    		boolean val = c.getCurrentValue();
    		boolean last_val = c.getLastPropagatedOutputValue();
    		c.setLastPropagatedOutputValue(val);

    		Component[] outputs = c.getOutputs_arr();
        	int size = c.getOutputsSize();

    		if(val != last_val) {
            	for(int i = 0; i < size; i++) {
            		outputs[i].edit_T(val);
        			queue.add(outputs[i]);
                }
			} else {
				for(int i = 0; i < size; i++) {
        			queue.add(outputs[i]);
                }
			}
    	}
    }

    //Differential Propagation
    public void propagate(Queue<Component> queue) {
    	while(!queue.isEmpty()) {
    		Component c = queue.remove();
    		boolean val = false;
    		if (c instanceof Not) {
    			c.setCurrentValue(!c.getSingleInput_arr().getCurrentValue());
    			val = c.getCurrentValue();
    		}
    		else {
    			c.setCurrentValue(c.getSingleInput_arr().getCurrentValue());
    			val = c.getCurrentValue();
    		}
    		if (c instanceof Transition) {
    			c.setLastPropagatedOutputValue(val);
    			continue;
    		}

    		boolean lastVal = c.getLastPropagatedOutputValue();
    		if (lastVal == val) continue;

    		c.setLastPropagatedOutputValue(val);

    		Component[] outputs = c.getOutputs_arr();
        	int size = c.getOutputsSize();

            for(int i = 0; i < size; i++) {
            	outputs[i].edit_T(val);
        		queue.add(outputs[i]);
            }
    	}
    }





    private boolean clearPropNet() {
    	for (Component c: propNet.getComponents()) {
    		c.set(0);
    		c.setCurrentValue(false);
    		c.setLastPropagatedOutputValue(false);
    	}
    	return true;
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
    private OpenBitSet toDoes(List<Move> moves, int size, Proposition[] inputs)
    {
    	OpenBitSet doeses = new OpenBitSet(size);
    	HashMap< Pair<GdlTerm, GdlTerm>, Integer> m = propNet.getInputMap();
    	for (int i = 0; i < moves.size(); ++i) {
    		GdlConstant r = roles.get(i).getName();
    		GdlTerm t = moves.get(i).getContents();
    		Pair<GdlConstant, GdlTerm> pair = Pair.of(r, t);
    		int index = m.get(pair);
    		doeses.fastSet(index);
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
    public BitMachineState getStateFromBase()
    {
    	Proposition[] bases = propNet.getBasePropositions();
        int size = bases.length;
        OpenBitSet contents = new OpenBitSet(size);

        for (int i = 0; i < size; ++i)
        {
        	Proposition p = bases[i];
            p.setCurrentValue(p.getSingleInput_arr().getCurrentValue());
            if (p.getCurrentValue())
            {
                contents.fastSet(i);
            }

        }
        return new BitMachineState(contents);
    }

    @Override
	public MachineState toGdl(BitMachineState state) {
    	Set<GdlSentence> bases = new HashSet<GdlSentence>();
    	Proposition[] baseProps = propNet.getBasePropositions();
    	for (int i = 0; i < baseProps.length; ++i) {
    		Proposition p = baseProps[i];
    		if (p.getCurrentValue()) bases.add(p.getName());
    	}
    	return new MachineState(bases);
    }

    @Override
	public BitMachineState toBit(MachineState state) {
    	Set<GdlSentence> bases = state.getContents();
    	HashMap<GdlSentence, Integer> basesMap = propNet.getBasesMap();
    	OpenBitSet bitSet = new OpenBitSet(basesMap.values().size());
    	for (GdlSentence base : bases) {
    		bitSet.fastSet(basesMap.get(base));
    	}
    	return new BitMachineState(bitSet);
    }


}
