package org.ggp.base.util.statemachine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.apache.lucene.util.OpenBitSet;
import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.propnet.architecture.XPropNet;
import org.ggp.base.util.propnet.architecture.components.BitProposition;
import org.ggp.base.util.propnet.factory.OptimizingPropNetFactory;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;



@SuppressWarnings("unused")
public class XStateMachine extends XMachine {
    private long main_thread;
    private int main_ind = 48;
    private int num_threads = 48;
    private boolean initialized = false;

    private XPropNet propNet;
    private Role[] roles;
    private OpenBitSet currentState;
    private OpenBitSet currInputs;
    private int numBases, baseOffset, numLegals, numInputs, legalOffset, inputOffset;
    private HashMap<Role, List<Move>> actions;
    private HashMap<Role, List<Move>> currentLegalMoves;
    private HashMap<Integer, Integer> roleIndexMap;
    private Move[] legalArray;
    private HashMap<Move, Integer>[] roleMoves;
    private int[] components;
    private long[] compInfo;
    private int[] connecTable;

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
            propNet = new XPropNet(OptimizingPropNetFactory.create(description));
            components = propNet.getComponents();
            compInfo = propNet.getCompInfo();
            connecTable = propNet.getConnecTable();
            roles = propNet.getRoles();
            numBases = propNet.getBasePropositions().length;
            numInputs = propNet.getInputPropositions().length;
            numLegals = numInputs;
            baseOffset = propNet.getBaseOffset();
            legalOffset = propNet.getLegalOffset();
            inputOffset = propNet.getInputOffset();
            actions = propNet.getLegalPropositions();
            roleIndexMap = propNet.getRoleIndexMap();
            legalArray = propNet.getLegalArray();
            roleMoves = propNet.getRoleMoves();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static final int NUM_TYPE_BITS = 8;
	private static final int NUM_INPUT_BITS = 16;
	private static final int NUM_OUTPUT_BITS = 16;
	private static final int NUM_OFFSET_BITS = 24;

	private static final int OUTPUT_SHIFT = NUM_OFFSET_BITS;
	private static final int INPUT_SHIFT = OUTPUT_SHIFT + NUM_OUTPUT_BITS;
	private static final int TYPE_SHIFT = INPUT_SHIFT + NUM_INPUT_BITS;

	private static final long TYPE_MASK = 0xF0000000;
	private static final long INPUTS_MASK = 0x0FF00000;
    private static final long OUTPUTS_MASK = 0x000FF000;
    private static final long OFFSET_MASK = 0x00000FFF;

    protected int type(long comp) {
    	return (int) ((comp & TYPE_MASK) >> TYPE_SHIFT);
    }

    protected int numInputs(long comp) {
    	return (int) ((comp & INPUTS_MASK) >> INPUT_SHIFT);
    }
    protected int numOutputs(long comp) {//inline these functions
    	return (int) ((comp & OUTPUTS_MASK) >> OUTPUT_SHIFT);
    }

    protected int outputsOffset(long comp) {
    	return (int) (comp & OFFSET_MASK);
    }

    private static final int CURR_VAL_MASK = 0x8000;
    private static final int NOT_CURR_VAL_MASK = 0x7FFF;

    protected boolean get_current_value(int value) {
    	return (value & CURR_VAL_MASK) != 0;
    }


    protected Queue<Integer> setInit(int initId, boolean val) {
    	Queue<Integer> q = new LinkedList<Integer>();
    	if (initId == -1) return q; //check if initProposition exists
    	components[initId] ^= CURR_VAL_MASK; //Set init current value to be true //THIS SHOULD BE DONE BY PROPNET
    	if (!val) components[initId] &= NOT_CURR_VAL_MASK;
    	long comp = compInfo[initId];
    	int num_outputs = numOutputs(comp);
    	int outputsIndex = outputsOffset(comp);

    	for (int i = 0; i < num_outputs; ++i) {
    		int outIndex = connecTable[outputsIndex + i];
    		if (val) components[outIndex] += 1; //+= 1 corresponds to edit_T(true)
    		else components[outIndex] -= 1;
    		q.add(outIndex); //add init outputs to the queue
    	}

    	return q;
    }

    protected void setConstants(Queue<Integer> q) {//values will be set by propNet
    	int[] constants = propNet.getConstants();
    	if (constants == null) return;

    	for (int c = 0; c < constants.length; ++c) {
    		long comp = compInfo[constants[c]];
        	int num_outputs = numOutputs(comp);
        	int outputsIndex = outputsOffset(comp);

        	boolean val = (components[c] & CURR_VAL_MASK) != 0;
        	for (int i = 0; i < num_outputs; ++i) {
        		int outIndex = connecTable[outputsIndex + i];
        		if (val) components[outIndex] += 1; //+= 1 corresponds to edit_T(true)
        		q.add(outIndex); //add output to the queue
        	}
    	}
    }

    public void addBases(Queue<Integer> q) {
    	int[] bases = propNet.getBasePropositions();
    	int size = bases.length;
        for(int i = 0; i < size; ++i) {
        	q.add(bases[i]);
        }
    }

    public void addInputs(Queue<Integer> q) {
    	int[] inputs = propNet.getInputPropositions();
    	int size = inputs.length;
        for(int i = 0; i < size; ++i) {
        	long comp = compInfo[inputs[i]];
        	int num_outputs = numOutputs(comp);
        	int outputsIndex = outputsOffset(comp);

        	for (int j = 0; j < num_outputs; ++j) {
        		int outIndex = connecTable[outputsIndex + j];
        		q.add(outIndex); //add output to the queue
        	}
        }
    }

    @Override
    public OpenBitSet getInitialState() {
    	int thread_id = main_ind;
    	clearPropNet(thread_id);
    	int init = propNet.getInitProposition();
    	Queue<Integer> q = setInit(init, true);
    	setConstants(q);
    	addBases(q);
    	addInputs(q);
    	rawPropagate(q, thread_id);
    	q = setInit(init, false);
        propagate(q, thread_id);
        return currentState;
    }

    private static final long TRIGGER_MASK = 0x80000000;
    private static final long TRANSITION_MASK = 0x40000000;

    protected boolean isTrigger(long comp) {
    	return (comp & TRIGGER_MASK) != 0;
    }

    //Must be called after isTrigger
    protected boolean isTransition(long comp) {
    	return (comp & TRANSITION_MASK) != 0;
    }

  //Propagates normally (ignoring lastPropagatedOutputValue). This version of propagate
    //is only called during getInitialState()
    protected void rawPropagate(Queue<Integer> q, int thread_id) {
    	OpenBitSet baseSet = new OpenBitSet(numBases);
    	OpenBitSet legalSet = new OpenBitSet(numLegals);
    	while(!q.isEmpty()) {
    		int compId = q.remove();
    		int value = components[compId];
    		boolean val = get_current_value(value);
    		long comp = compInfo[compId];
    		if (isTrigger(comp)) {
    			if (isTransition(comp)) {
    				if (val) {
    					int outputsIndex = outputsOffset(comp);
        				int baseIndex = outputsIndex - baseOffset;
        				baseSet.fastSet(baseIndex);
    				}
    				continue;
    			} else {
    				if (val) {
    					int legalIndex = compId - legalOffset;
    					legalSet.fastSet(legalIndex);
    				}
    			}
    		}

        	int num_outputs = numOutputs(comp);
        	int outputsIndex = outputsOffset(comp);

        	for (int i = 0; i < num_outputs; ++i) {
        		int outIndex = connecTable[outputsIndex + i];
        		if (val) components[outIndex] += 1; //+= 1 corresponds to edit_T(true)
        		else components[outIndex] -= 1;
        		q.add(outIndex); //add init outputs to the queue
        	}
    	}
    }

  //Propagates normally (ignoring lastPropagatedOutputValue). This version of propagate
    //is only called during getInitialState()
    protected void propagate(Queue<Integer> q, int thread_id) {
    	OpenBitSet baseSet = new OpenBitSet(numBases);
    	OpenBitSet legalSet = new OpenBitSet(numLegals);

    	while(!q.isEmpty()) {
    		int compId = q.remove();
    		int value = components[compId];
    		boolean val = get_current_value(value);
    		long comp = compInfo[compId];
    		if (isTrigger(comp)) {
    			if (isTransition(comp)) {
    				if (val) {
    					int outputsIndex = outputsOffset(comp);
        				int baseIndex = outputsIndex - baseOffset;
        				baseSet.fastSet(baseIndex);
    				}
    				continue;
    			} else {
    				if (val) {
    					int legalIndex = compId - legalOffset;
    					legalSet.fastSet(legalIndex);
    				}
    			}
    		}

        	int num_outputs = numOutputs(comp);
        	int outputsIndex = outputsOffset(comp);

        	for (int i = 0; i < num_outputs; ++i) {
        		int outIndex = connecTable[outputsIndex + i];
        		int outValue = components[outIndex];
        		boolean lastPropagatedValue = get_current_value(outValue);
        		if (val) components[outIndex] += 1; //+= 1 corresponds to edit_T(true)
        		else components[outIndex] -= 1;
        		if (get_current_value(components[outIndex]) != lastPropagatedValue) {
        			q.add(outIndex);
        		}
        	}
    	}

    	currentState = baseSet;
    	currentLegalMoves = getLegals(legalSet);
    }

    protected HashMap<Role, List<Move>> getLegals(OpenBitSet s) {
    	HashMap<Role, List<Move>> legalMap = new HashMap<Role, List<Move>>();
    	int size = roles.length - 1;
    	for (int i = 0; i < size; ++i) {
    		List<Move> moves = new ArrayList<Move>();
    		int roleIndex = roleIndexMap.get(i);
    		int nextRoleIndex = roleIndexMap.get(i + 1);
    		for (int j = roleIndex; j < nextRoleIndex; ++j) {
    			if (s.fastGet(j)) moves.add(legalArray[j]);
    		}
    		legalMap.put(roles[i], moves);
    	}
    	int start = roleIndexMap.get(size);
    	int end = legalArray.length;
    	List<Move> moves = new ArrayList<Move>();
    	for(int i = start; i < end; ++i) {
    		if (s.fastGet(i)) moves.add(legalArray[i]);
    	}
    	legalMap.put(roles[size], moves);
    	return legalMap;
    }


    /**
     * Computes all possible actions for role.
     */
    @Override
    public List<Move> findActions(Role role)
            throws MoveDefinitionException {
    	return actions.get(role);
    }

    @Override
    public XPropNet getPropNet() {
    	return propNet;
    }

    @Override
    public List<Move> getLegalMoves(OpenBitSet state, Role role)//Change such that we don't have to keep updating legal moves
            throws MoveDefinitionException {
    	int thread_id = main_ind;
    	setState(state, null, thread_id);
    	return currentLegalMoves.get(role);

    }


	protected void setBases(OpenBitSet nextSet, Queue<Integer> q, int thread_id) {
    	if (nextSet == null) return;
    	int[] bases = propNet.getBasePropositions();
    	int size = bases.length;
    	OpenBitSet currSet = currentState;
    	currSet.xor(nextSet);

    	for (int i = currSet.nextSetBit(0); i != -1; i = currSet.nextSetBit(i + 1)) {
    		boolean val = nextSet.fastGet(i);
    		if (val) components[baseOffset + i] += 1;
    		else components[baseOffset + i] -= 1;

    		long comp = compInfo[baseOffset + i];
    		int num_outputs = numOutputs(comp);
        	int outputsIndex = outputsOffset(comp);

        	for (int j = 0; j < num_outputs; ++j) {
        		int outIndex = connecTable[outputsIndex + j];
        		int outValue = components[outIndex];
        		boolean lastPropagatedValue = get_current_value(outValue);
        		if (val) components[outIndex] += 1;
        		else components[outIndex] -= 1;
        		if (get_current_value(components[outIndex]) != lastPropagatedValue) {
        			q.add(outIndex);
        		}
        	}
    	}
    }


	protected void setActions(OpenBitSet moves, Queue<Integer> q, int thread_id) {
    	if(moves == null) return;

    	int[] inputs = propNet.getInputPropositions();
    	int size = inputs.length;
    	OpenBitSet nextInputs = moves;
    	currInputs.xor(nextInputs);

    	for (int i = currInputs.nextSetBit(0); i != -1; i = currInputs.nextSetBit(i + 1)) {
    		boolean val = nextInputs.fastGet(i);
    		int inputIndex = inputOffset + i;
    		if (val) components[inputIndex] += 1;
    		else components[inputIndex] -= 1;

    		long comp = compInfo[inputIndex];
    		int num_outputs = numOutputs(comp);
        	int outputsIndex = outputsOffset(comp);

        	for (int j = 0; j < num_outputs; ++j) {
        		int outIndex = connecTable[outputsIndex + j];
        		int outValue = components[outIndex];
        		boolean lastPropagatedValue = get_current_value(outValue);
        		if (val) components[outIndex] += 1;
        		else components[outIndex] -= 1;
        		if (get_current_value(components[outIndex]) != lastPropagatedValue) {
        			q.add(outIndex);
        		}
        	}
    	}

    }

	protected OpenBitSet movesToBit(List<Move> moves) {
		if (moves == null || moves.isEmpty()) return null;
		OpenBitSet movesSet = new OpenBitSet(numLegals);
		for (int i = 0; i < moves.size(); ++i) {
			int index = roleMoves[i].get(moves.get(i));
			movesSet.fastSet(index);
		}
		return movesSet;
	}

    protected void setState(OpenBitSet state, List<Move> moves, int thread_id) {
    	Queue<Integer> q = new LinkedList<Integer>();
    	setBases(state, q, thread_id);
    	setActions(movesToBit(moves), q, thread_id);
    	propagate(q, thread_id);
    }

    @Override
	public OpenBitSet getNextState(OpenBitSet state, List<Move> moves) {
    	int thread_id = main_ind;
    	long td = Thread.currentThread().getId();
    	setState(state, moves, thread_id);
    	return currentState;
    }



    protected void clearPropNet(int thread_id) {
    	components = propNet.getDefaultComponents();
    }

    @Override
    public boolean isTerminal(OpenBitSet state) {
    	int thread_id = main_ind;
    	setState(state, null, thread_id);
    	int term = propNet.getTerminalProposition();
    	return get_current_value(components[term]);
    }

    //goal Propositions will never be Trigger components, so we
    //can use its 2nd bit. Goal value is stored in bits 2-8, reading
    //from the left
    private static final long GOAL_MASK = 0x7F000000;
    private static final int GOAL_SHIFT = TYPE_SHIFT;
    protected int getGoalValue(long value) {//inline
    	return (int) (value & GOAL_MASK) >> TYPE_SHIFT;
    }

    @Override
    public int getGoal(OpenBitSet state, Role role)
            throws GoalDefinitionException {
    	int thread_id = main_ind;
    	setState(state, null, thread_id);
        int[] rewards = propNet.getGoalPropositions().get(role);
        int size = rewards.length;
        for(int i = 0; i < size; ++i) {
        	int rewardIndex = rewards[i];
        	int value = components[rewardIndex];
        	if (get_current_value(value))
        		return getGoalValue(compInfo[rewardIndex]);
        }
        System.out.println("ERROR! Reward not defined in state " + state.toString());
        System.exit(0);
        return 0;
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
   /* private OpenBitSet toDoes(List<Move> moves, int size, BitProposition[] inputs)
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
    }*/



    @Override
	public MachineState toGdl(OpenBitSet state) {
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
	public OpenBitSet toBit(MachineState state) {
    	Set<GdlSentence> bases = state.getContents();
    	HashMap<GdlSentence, Integer> basesMap = propNet.getBasesMap();
    	OpenBitSet bitSet = new OpenBitSet(basesMap.values().size());
    	for (GdlSentence base : bases) {
    		bitSet.fastSet(basesMap.get(base));
    	}
    	return bitSet;
    }


}
