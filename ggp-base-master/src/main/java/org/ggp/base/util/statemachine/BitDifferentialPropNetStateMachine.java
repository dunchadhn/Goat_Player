package org.ggp.base.util.statemachine;

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
import org.ggp.base.util.propnet.architecture.BitComponent;
import org.ggp.base.util.propnet.architecture.BitPropNet;
import org.ggp.base.util.propnet.architecture.components.BitConstant;
import org.ggp.base.util.propnet.architecture.components.BitNot;
import org.ggp.base.util.propnet.architecture.components.BitProposition;
import org.ggp.base.util.propnet.architecture.components.BitTransition;
import org.ggp.base.util.propnet.factory.BitOptimizingPropNetFactory;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;



@SuppressWarnings("unused")
public class BitDifferentialPropNetStateMachine extends BitStateMachine {
    /** The underlying proposition network  */
    private BitPropNet propNet;
    /** The player roles */
    private List<Role> roles;
    private long main_thread;
    private int main_ind = 48;
    private int num_threads = 48;
    private boolean initialized = false;

    /**
     * Initializes the PropNetStateMachine. You should compute the topological
     * ordering here. Additionally you may compute the initial state here, at
     * your discretion.
     */
    @Override
    public void initialize(List<Gdl> description) {
        try {
        	System.out.println("Initialized");
        	main_thread = Thread.currentThread().getId();
            propNet = BitOptimizingPropNetFactory.create(description);
            roles = propNet.getRoles();
            for(BitComponent c: propNet.getComponents()) {
            	c.crystallize();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public boolean isTerminal(BitMachineState state) {
    	int thread_id = main_ind;
    	long td = Thread.currentThread().getId();
    	if(td != main_thread) {
    		thread_id = (int) td % num_threads;
    	}
    	setState(state, null, thread_id);
    	return propNet.getTerminalProposition().getCurrentValue(thread_id);
    }


    @Override
    public int getGoal(BitMachineState state, Role role)
            throws GoalDefinitionException {
    	int thread_id = main_ind;
    	long td = Thread.currentThread().getId();
    	if(td != main_thread) {
    		thread_id = (int) td % num_threads;
    	}
    	setState(state, null, thread_id);
        List<Role> roles = propNet.getRoles();
        BitProposition[] rewards = propNet.getGoalPropositions().get(role);
        int size = rewards.length;
        for(int i = 0; i < size; ++i) {
        	BitProposition reward = rewards[i];
        	if (reward.getCurrentValue(thread_id))
        		return getGoalValue(reward);
        }
        System.out.println("ERROR! Reward not defined in state " + state.toString());
        System.exit(0);
        return 0;
    }


    protected void setInit(boolean val, Queue<BitComponent> queue, int thread_id) {
    	BitProposition init = propNet.getInitProposition();
    	if (init.getLastPropagatedOutputValue(thread_id) == val) return;
    	init.setCurrentValue(val,thread_id);
    	init.setLastPropagatedOutputValue(val,thread_id);
    	BitComponent[] outputs = init.getOutputs_arr();
    	int size = init.getOutputsSize();
    	for(int i = 0; i < size; ++i) {
    		outputs[i].edit_T(val,thread_id);
			queue.add(outputs[i]);
        }
    }

    protected void setInitAll(boolean val, Queue<BitComponent> queue, int thread_id) {
    	BitProposition init = propNet.getInitProposition();
    	if (init.getLastPropagatedOutputValue(thread_id) == val) return;
    	init.setCurrentValueAll(val,thread_id);
    	init.setLastPropagatedOutputValueAll(val,thread_id);
    	BitComponent[] outputs = init.getOutputs_arr();
    	int size = init.getOutputsSize();
    	for(int i = 0; i < size; ++i) {
    		outputs[i].edit_TAll(val,thread_id);
			queue.add(outputs[i]);
        }
    }

    protected void setConstants(Queue<BitComponent> q, int thread_id) {
    	for (BitComponent c: propNet.getComponents()) {
    		if (c instanceof BitConstant) {
    			boolean val = c.getCurrentValue(thread_id);
    			c.setLastPropagatedOutputValue(val,thread_id);
    			BitComponent[] outputs = c.getOutputs_arr();
		    	int size = c.getOutputsSize();
    			if(val) {
    		    	for(int i = 0; i < size; ++i) {
    		    		outputs[i].edit_T(val,thread_id);
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

    protected void setConstantsAll(Queue<BitComponent> q, int thread_id) {
    	for (BitComponent c: propNet.getComponents()) {
    		if (c instanceof BitConstant) {
    			boolean val = c.getCurrentValue(thread_id);
    			c.setLastPropagatedOutputValueAll(val,thread_id);
    			BitComponent[] outputs = c.getOutputs_arr();
		    	int size = c.getOutputsSize();
    			if(val) {
    		    	for(int i = 0; i < size; ++i) {
    		    		outputs[i].edit_TAll(val,thread_id);
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

    @Override
    public BitMachineState getInitialState() {
    	int thread_id = main_ind;
    	long td = Thread.currentThread().getId();
    	if(td != main_thread) {
    		thread_id = (int) td % num_threads;
		}
    	if(initialized) {
    		clearPropNet(thread_id);
    	} else {
    		clearPropNetAll(thread_id);
    	}
    	BitProposition init = propNet.getInitProposition();
    	if(initialized) {
    		init.setCurrentValue(true,thread_id);
    		init.setLastPropagatedOutputValue(true,thread_id);
    	} else {
    		init.setCurrentValueAll(true,thread_id);
    		init.setLastPropagatedOutputValueAll(true,thread_id);
    	}

        Queue<BitComponent> queue = new LinkedList<BitComponent>();
        if(initialized) {
        	setConstants(queue, thread_id);//Constants don't change throughout the game, so we set them once here
        } else {
        	setConstantsAll(queue, thread_id);
        }

        BitComponent[] outputs = init.getOutputs_arr();
    	int size = init.getOutputsSize();
    	for(int i = 0; i < size; ++i) {
    		if(initialized) {
    			outputs[i].edit_T(true,thread_id);
    		} else {
    			outputs[i].edit_TAll(true,thread_id);
    		}
			queue.add(outputs[i]);
        }

    	BitProposition[] bases = propNet.getBasePropositions();
    	size = bases.length;
        for(int i = 0; i < size; ++i) {//Don't add redundant states
        	BitProposition p = bases[i];
        	outputs = p.getOutputs_arr();
        	int size_2 = p.getOutputsSize();
        	for(int j = 0; j < size_2; ++j) {
    			queue.add(outputs[j]);
            }
        }

        BitProposition[] inputs = propNet.getInputPropositions();
    	size = inputs.length;
        for(int i = 0; i < size; ++i) {
        	BitProposition p = inputs[i];
        	outputs = p.getOutputs_arr();
        	int size_2 = p.getOutputsSize();
        	for(int j = 0; j < size_2; ++j) {
    			queue.add(outputs[j]);
            }
        }
        BitMachineState state = null;
        if(initialized) {
        	rawPropagate(queue, thread_id);
        	state = getStateFromBase(thread_id);
        } else {
        	rawPropagateAll(queue, thread_id);
        	state = getStateFromBaseAll(thread_id);
        }
        queue = new LinkedList<BitComponent>();
        if(initialized) {
        	setInit(false, queue, thread_id);
        	propagate(queue, thread_id);
        } else {
        	setInitAll(false, queue, thread_id);
        	propagateAll(queue, thread_id);
        }
        initialized = true;
        return state;
    }


    /**
     * Computes all possible actions for role.
     */
    @Override
    public List<Move> findActions(Role role)
            throws MoveDefinitionException {
    	BitProposition[] actions = propNet.getLegalPropositions().get(role);
        List<Move> moves = new ArrayList<>();

    	int size = actions.length;
        for(int i = 0; i < size; ++i) {
        	BitProposition action = actions[i];
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
    	int thread_id = main_ind;
    	long td = Thread.currentThread().getId();
    	if(td != main_thread) {
    		thread_id = (int) td % num_threads;
    	}
    	setState(state, null, thread_id);
    	BitProposition[] actions = propNet.getLegalPropositions().get(role);
        List<Move> moves = new ArrayList<>();

        int size = actions.length;
        for(int i = 0; i < size; ++i) {
        	BitProposition action = actions[i];
    		if (action.getCurrentValue(thread_id)) {
    			moves.add(getMoveFromProposition(action));
    		}
    	}
        return moves;
    }



	protected void setBases(BitMachineState state, Queue<BitComponent> q, int thread_id) {
    	if (state == null) return;
    	BitProposition[] bases = propNet.getBasePropositions();
    	int size = bases.length;
    	OpenBitSet bitSet = state.getContents();

    	for (int i = 0; i < size; ++i) {
    		BitProposition p = bases[i];

    		boolean val = bitSet.fastGet(i);
    		if (val == p.getLastPropagatedOutputValue(thread_id)) continue;

    		p.setLastPropagatedOutputValue(val,thread_id);
    		p.setCurrentValue(val,thread_id);

    		BitComponent[] outputs = p.getOutputs_arr();
        	int size_2 = p.getOutputsSize();
        	for(int j = 0; j < size_2; ++j) {
        		outputs[j].edit_T(val,thread_id);
    			q.add(outputs[j]);
            }
    	}
    }


	protected void setActions(List<Move> moves, Queue<BitComponent> q, int thread_id) {
    	if(moves == null || moves.isEmpty()) return;
    	BitProposition[] inputs = propNet.getInputPropositions();
    	int size = inputs.length;
    	OpenBitSet actions = toDoes(moves, size, inputs);
    	for (int i = 0; i < size; ++i) {
    		BitProposition p = inputs[i];

    		boolean val = actions.fastGet(i);
    		if (val == p.getLastPropagatedOutputValue(thread_id)) continue;

    		p.setLastPropagatedOutputValue(val,thread_id);
    		p.setCurrentValue(val,thread_id);

    		BitComponent[] outputs = p.getOutputs_arr();
        	int size_2 = p.getOutputsSize();
        	for(int j = 0; j < size_2; ++j) {
        		outputs[j].edit_T(val,thread_id);
    			q.add(outputs[j]);
            }
    	}
    }

    protected void setState(BitMachineState state, List<Move> moves, int thread_id) {
    	Queue<BitComponent> q = new LinkedList<BitComponent>();
    	setInit(false, q, thread_id);
    	setBases(state, q, thread_id);
    	setActions(moves, q, thread_id);
    	propagate(q, thread_id);
    }

    @Override
	public BitMachineState getNextState(BitMachineState state, List<Move> moves) {
    	int thread_id = main_ind;
    	long td = Thread.currentThread().getId();
    	if(td != main_thread) {
    		thread_id = (int) td % num_threads;
    	}
    	setState(state, moves, thread_id);
    	BitMachineState currentState = getStateFromBase(thread_id);
    	return currentState;
    }


    //Propagates normally (ignoring lastPropagatedOutputValue). This version of propagate
    //is only called during getInitialState()
    protected void rawPropagate(Queue<BitComponent> queue, int thread_id) {
    	while(!queue.isEmpty()) {
    		BitComponent c = queue.remove();
    		boolean val = false;
    		if (c instanceof BitNot) {
    			c.setCurrentValue(!c.getSingleInput_arr().getCurrentValue(thread_id),thread_id);
    			val = c.getCurrentValue(thread_id);
    		}
    		else {
    			c.setCurrentValue(c.getSingleInput_arr().getCurrentValue(thread_id),thread_id);
    			val = c.getCurrentValue(thread_id);
    		}
    		if (c instanceof BitTransition) {
    			c.setLastPropagatedOutputValue(val,thread_id);
    			continue;
    		}

    		boolean last_val = c.getLastPropagatedOutputValue(thread_id);
    		c.setLastPropagatedOutputValue(val,thread_id);

    		BitComponent[] outputs = c.getOutputs_arr();
        	int size = c.getOutputsSize();

    		if(val != last_val) {
            	for(int i = 0; i < size; i++) {
            		outputs[i].edit_T(val,thread_id);
        			queue.add(outputs[i]);
                }
			} else {
				for(int i = 0; i < size; i++) {
        			queue.add(outputs[i]);
                }
			}
    	}
    }

    protected void rawPropagateAll(Queue<BitComponent> queue, int thread_id) {
    	while(!queue.isEmpty()) {
    		BitComponent c = queue.remove();
    		boolean val = false;
    		if (c instanceof BitNot) {
    			c.setCurrentValueAll(!c.getSingleInput_arr().getCurrentValue(thread_id),thread_id);
    			val = c.getCurrentValue(thread_id);
    		}
    		else {
    			c.setCurrentValueAll(c.getSingleInput_arr().getCurrentValue(thread_id),thread_id);
    			val = c.getCurrentValue(thread_id);
    		}
    		if (c instanceof BitTransition) {
    			c.setLastPropagatedOutputValueAll(val,thread_id);
    			continue;
    		}

    		boolean last_val = c.getLastPropagatedOutputValue(thread_id);
    		c.setLastPropagatedOutputValueAll(val,thread_id);

    		BitComponent[] outputs = c.getOutputs_arr();
        	int size = c.getOutputsSize();

    		if(val != last_val) {
            	for(int i = 0; i < size; i++) {
            		outputs[i].edit_TAll(val,thread_id);
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
    public void propagate(Queue<BitComponent> queue, int thread_id) {
    	while(!queue.isEmpty()) {
    		BitComponent c = queue.remove();
    		boolean val = false;
    		if (c instanceof BitNot) {
    			c.setCurrentValue(!c.getSingleInput_arr().getCurrentValue(thread_id),thread_id);
    			val = c.getCurrentValue(thread_id);
    		}
    		else {
    			c.setCurrentValue(c.getSingleInput_arr().getCurrentValue(thread_id),thread_id);
    			val = c.getCurrentValue(thread_id);
    		}
    		if (c instanceof BitTransition) {
    			c.setLastPropagatedOutputValue(val,thread_id);
    			continue;
    		}

    		boolean lastVal = c.getLastPropagatedOutputValue(thread_id);
    		if (lastVal == val) continue;

    		c.setLastPropagatedOutputValue(val,thread_id);

    		BitComponent[] outputs = c.getOutputs_arr();
        	int size = c.getOutputsSize();

            for(int i = 0; i < size; i++) {
            	outputs[i].edit_T(val,thread_id);
        		queue.add(outputs[i]);
            }
    	}
    }

    public void propagateAll(Queue<BitComponent> queue, int thread_id) {
    	while(!queue.isEmpty()) {
    		BitComponent c = queue.remove();
    		boolean val = false;
    		if (c instanceof BitNot) {
    			c.setCurrentValueAll(!c.getSingleInput_arr().getCurrentValue(thread_id),thread_id);
    			val = c.getCurrentValue(thread_id);
    		}
    		else {
    			c.setCurrentValueAll(c.getSingleInput_arr().getCurrentValue(thread_id),thread_id);
    			val = c.getCurrentValue(thread_id);
    		}
    		if (c instanceof BitTransition) {
    			c.setLastPropagatedOutputValueAll(val,thread_id);
    			continue;
    		}

    		boolean lastVal = c.getLastPropagatedOutputValue(thread_id);
    		if (lastVal == val) continue;

    		c.setLastPropagatedOutputValueAll(val,thread_id);

    		BitComponent[] outputs = c.getOutputs_arr();
        	int size = c.getOutputsSize();

            for(int i = 0; i < size; i++) {
            	outputs[i].edit_TAll(val,thread_id);
        		queue.add(outputs[i]);
            }
    	}
    }

    private boolean clearPropNet(int thread_id) {
    	for (BitComponent c: propNet.getComponents()) {
    		c.set(0,thread_id);
    		c.setCurrentValue(false,thread_id);
    		c.setLastPropagatedOutputValue(false,thread_id);
    	}
    	return true;
    }



    private boolean clearPropNetAll(int thread_id) {
    	for (BitComponent c: propNet.getComponents()) {
    		c.setAll(0,thread_id);
    		c.setCurrentValueAll(false,thread_id);
    		c.setLastPropagatedOutputValueAll(false,thread_id);
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
    private OpenBitSet toDoes(List<Move> moves, int size, BitProposition[] inputs)
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
    public static Move getMoveFromProposition(BitProposition p)
    {
        return new Move(p.getName().get(1));
    }

    /**
     * Helper method for parsing the value of a goal proposition
     * @param goalProposition
     * @return the integer value of the goal proposition
     */
    private int getGoalValue(BitProposition goalProposition)
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
    public BitMachineState getStateFromBase(int thread_id)
    {
    	BitProposition[] bases = propNet.getBasePropositions();
        int size = bases.length;
        OpenBitSet contents = new OpenBitSet(size);

        for (int i = 0; i < size; ++i)
        {
        	BitProposition p = bases[i];
            p.setCurrentValue(p.getSingleInput_arr().getCurrentValue(thread_id),thread_id);
            if (p.getCurrentValue(thread_id))
            {
                contents.fastSet(i);
            }

        }
        return new BitMachineState(contents);
    }

    public BitMachineState getStateFromBaseAll(int thread_id)
    {
    	BitProposition[] bases = propNet.getBasePropositions();
        int size = bases.length;
        OpenBitSet contents = new OpenBitSet(size);

        for (int i = 0; i < size; ++i)
        {
        	BitProposition p = bases[i];
            p.setCurrentValueAll(p.getSingleInput_arr().getCurrentValue(thread_id),thread_id);
            if (p.getCurrentValue(thread_id))
            {
                contents.fastSet(i);
            }

        }
        return new BitMachineState(contents);
    }

    @Override
	public MachineState toGdl(BitMachineState state) {
    	int thread_id = main_ind;
    	long td = Thread.currentThread().getId();
    	if(td != main_thread) {
    		thread_id = (int) td % num_threads;
    	}
    	Set<GdlSentence> bases = new HashSet<GdlSentence>();
    	BitProposition[] baseProps = propNet.getBasePropositions();
    	for (int i = 0; i < baseProps.length; ++i) {
    		BitProposition p = baseProps[i];
    		if (p.getCurrentValue(thread_id)) bases.add(p.getName());
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
