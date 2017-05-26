package org.ggp.base.util.statemachine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;

import org.apache.lucene.util.OpenBitSet;
import org.ggp.base.util.Pair;
import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.XPropNet;
import org.ggp.base.util.propnet.architecture.components.Not;
import org.ggp.base.util.propnet.architecture.components.Or;
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
    private OpenBitSet currentState, nextState, currInputs, currLegals;
    private int numBases, baseOffset, numLegals, numInputs, legalOffset, inputOffset;
    private HashMap<Role, List<Move>> actions;
    private HashMap<Role, List<Move>> currentLegalMoves;
    private HashMap<Integer, Integer> rolesIndexMap;
    private Move[] legalArray;
    private HashMap< Pair<Role, Move>, Integer> roleMoves;
    private int[] components;
    private long[] compInfo;
    private int[] connecTable;
    private HashMap<Integer, GdlSentence> gdlSentenceMap;
    private HashMap<Integer, Component> indexCompMap;

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
            numBases = propNet.numBases();
            numInputs = propNet.numInputs();
            numLegals = propNet.numLegals();
            baseOffset = propNet.getBaseOffset();
            legalOffset = propNet.getLegalOffset();
            inputOffset = propNet.getInputOffset();
            actions = propNet.getActionsMap();
            rolesIndexMap = propNet.getRolesIndexMap();
            legalArray = propNet.getLegalArray();
            roleMoves = propNet.getRoleMoves();
            gdlSentenceMap = propNet.getGdlSentenceMap();
            indexCompMap = propNet.indexCompMap();
            currInputs = new OpenBitSet(numInputs);
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

	private static final long TYPE_MASK = 0xFF_0000_0000_000000L;
	private static final long INPUTS_MASK = 0x00_FFFF_0000_000000L;
    private static final long OUTPUTS_MASK = 0x00_0000_FFFF_000000L;
    private static final long OFFSET_MASK = 0x00_0000_0000_FFFFFFL;

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

    private static final int CURR_VAL_MASK = 0x8000_0000;
    private static final int NOT_CURR_VAL_MASK = 0x7FFF_FFFF;

    protected boolean get_current_value(int value) {
    	return (value & CURR_VAL_MASK) != 0;
    }

    @Override
    public OpenBitSet getInitialState() {
    	int thread_id = main_ind;
    	int[] oldComponents = components;
    	clearPropNet(thread_id);
    	if (components == oldComponents) {
    		System.out.println("PropNet not cleared properly");
    		System.exit(0);
    	}

    	initBases();
    	propNet.renderToFile("init.dot", components);

    	setConstants();
    	propNet.renderToFile("postInitConstants.dot", components);

    	rawPropagate(thread_id);
    	propNet.renderToFile("postRaw.dot", components);

    	System.out.println("CurrentState: " + Long.toBinaryString(currentState.getBits()[0]));
    	System.out.println("CurrLegals: " + Long.toBinaryString(currLegals.getBits()[0]));

        return (OpenBitSet) currentState.clone();//necessary to clone?
    }

    protected void initBases() {
    	currentState = new OpenBitSet(numBases);
    	int[] initBases = propNet.initBases();
    	if (initBases != null) {
    		for (int i = 0; i < initBases.length; ++i) {
        		int bIndex = initBases[i];
        		components[bIndex] += 1;

        		long comp = compInfo[bIndex];
            	int num_outputs = numOutputs(comp);
            	int outputsIndex = outputsOffset(comp);

            	for (int j = 0; j < num_outputs; ++j) {
            		int outIndex = connecTable[outputsIndex + j];
            		components[outIndex] += 1; //+= 1 corresponds to edit_T(true)
            	}
        	}
    	}
    }


    protected void setConstants() {//values will be set by propNet
    	int[] constants = propNet.getConstants();
    	if (constants == null) return;

    	for (int c = 0; c < constants.length; ++c) {
    		long comp = compInfo[constants[c]];
        	int num_outputs = numOutputs(comp);
        	int outputsIndex = outputsOffset(comp);

        	boolean val = get_current_value(components[constants[c]]);
        	for (int i = 0; i < num_outputs; ++i) {
        		int outIndex = connecTable[outputsIndex + i];
        		if (val) components[outIndex] += 1; //+= 1 corresponds to edit_T(true)
        	}
    	}
    }

    private static final long TRIGGER_MASK = 0x80_0000_0000_000000L;
    private static final long TRANSITION_MASK = 0x40_0000_0000_000000L;

    protected boolean isTrigger(long comp) {
    	return (comp & TRIGGER_MASK) != 0;
    }

    //Must be called after isTrigger
    protected boolean isTransition(long comp) {
    	return (comp & TRANSITION_MASK) != 0;
    }

  //Propagates normally (ignoring lastPropagatedOutputValue). This version of propagate
    //is only called during getInitialState()
    protected void rawPropagate(int thread_id) {//compute ordering
    	Stack<Integer> ordering = propNet.getOrdering();
    	currLegals = new OpenBitSet(numLegals);
    	nextState = new OpenBitSet(numBases);

    	while (!ordering.isEmpty()) {
    		int compId = ordering.pop();
    		//System.out.println(compId + " " + indexCompMap.get(compId));
    		int value = components[compId];
    		boolean val = get_current_value(value);
    		long comp = compInfo[compId];
    		if (isTrigger(comp)) {
    			if (isTransition(comp)) {
    				int outputIndex = outputsOffset(comp);
    				int baseIndex = connecTable[outputIndex] - baseOffset;
    				if (val) nextState.fastSet(baseIndex);
    				else nextState.clear(baseIndex);
    				continue;

    			} else {
    				int legalIndex = compId - legalOffset;
    				if (val) currLegals.fastSet(legalIndex);
    				else currLegals.clear(legalIndex);
    			}
    		}

        	int num_outputs = numOutputs(comp);
        	int outputsIndex = outputsOffset(comp);
        	//System.out.print("    Outputs:");
        	for (int i = 0; i < num_outputs; ++i) {
        		int outIndex = connecTable[outputsIndex + i];
        		//System.out.print(outIndex + ":" + indexCompMap.get(outIndex));
        		if (val) components[outIndex] += 1; //+= 1 corresponds to edit_T(true)
        		if (get_current_value(components[outIndex]) && components[outIndex] != 0x8000_0000 && (!(indexCompMap.get(outIndex) instanceof Or) && !(indexCompMap.get(outIndex) instanceof Not))) {
        			System.out.println("\ncomponents[outIndex] != 0x8000_0000");
        			System.out.println(Integer.toBinaryString(components[outIndex]));
        			System.exit(0);
        		}
        	}
    	}
    }

  //Propagates normally (ignoring lastPropagatedOutputValue). This version of propagate
    //is only called during getInitialState()
    protected void propagate(Queue<Integer> q, int thread_id) {

    	//currentState = (OpenBitSet) currentState.clone();
    	//currLegals = new OpenBitSet(numLegals);

    	while(!q.isEmpty()) {
    		int compId = q.remove();
    		int value = components[compId];
    		boolean val = get_current_value(value);
    		long comp = compInfo[compId];
    		if (isTrigger(comp)) {
    			if (isTransition(comp)) {
    				int outputIndex = outputsOffset(comp);
    				int baseIndex = connecTable[outputIndex] - baseOffset;
    				if (val) currentState.fastSet(baseIndex);
    				else currentState.clear(baseIndex);
    				continue;
    			} else {
    				int legalIndex = compId - legalOffset;
    				if (val) currLegals.fastSet(legalIndex);
    				else currLegals.clear(legalIndex);
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

    }

    protected HashMap<Role, List<Move>> getLegals(OpenBitSet s) {
    	HashMap<Role, List<Move>> legalMap = new HashMap<Role, List<Move>>();
    	int size = roles.length - 1;
    	for (int i = 0; i < size; ++i) {
    		List<Move> moves = new ArrayList<Move>();
    		int roleIndex = rolesIndexMap.get(i);
    		int nextRoleIndex = rolesIndexMap.get(i + 1);
    		//System.out.println(roleIndex + ", " + nextRoleIndex);
    		for (int j = roleIndex; j < nextRoleIndex; ++j) {
    			if (s.fastGet(j)) {
    				//System.out.println(roles[i] + " " + legalArray[j]);
    				moves.add(legalArray[j]);
    			}
    		}
    		legalMap.put(roles[i], moves);
    	}
    	int start = rolesIndexMap.get(size);
    	int end = legalArray.length;
    	//System.out.println(start + ", " + end);
    	List<Move> moves = new ArrayList<Move>();
    	for(int i = start; i < end; ++i) {
    		if (s.fastGet(i)) {
    			//System.out.println(roles[size] + " " + legalArray[i]);
    			moves.add(legalArray[i]);
    		}
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
    	System.out.println("getLegalMoves");
    	System.out.println("State: " + Long.toBinaryString(state.getBits()[0]));
    	propNet.renderToFile("preLegal.dot", components);
    	int thread_id = main_ind;

    	setState(state, null, thread_id);
    	propNet.renderToFile("postLegal.dot", components);

    	int size = currLegals.getBits().length;
    	for(int i = 0; i < size; ++i) {
    		System.out.println(Long.toBinaryString(currLegals.getBits()[i]));
    	}
    	currentLegalMoves = getLegals(currLegals);

    	return currentLegalMoves.get(role);
    }


	protected void setBases(OpenBitSet state, Queue<Integer> q, int thread_id) {
    	if (state == null) return;
    	int[] bases = propNet.getBasePropositions();
    	int size = bases.length;

    	OpenBitSet diff = (OpenBitSet) state.clone();//create a new instance so we don't modify caller's OpenBitSet
    	diff.xor(currentState);

    	for (int i = diff.nextSetBit(0); i != -1; i = diff.nextSetBit(i + 1)) {
    		boolean val = diff.fastGet(i);
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
    	System.out.println("currInputs: " + Long.toBinaryString(currInputs.getBits()[0]));
    	OpenBitSet nextInputs = moves;
    	System.out.println("nextInputs: " + Long.toBinaryString(nextInputs.getBits()[0]));
    	currInputs.xor(nextInputs);
    	System.out.println("xor: " + Long.toBinaryString(currInputs.getBits()[0]));

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
    	currInputs = nextInputs;

    }

	protected OpenBitSet movesToBit(List<Move> moves) {
		System.out.println("movesToBit");
		if (moves == null || moves.isEmpty()) return null;

		OpenBitSet movesSet = new OpenBitSet(numInputs);
		for (int i = 0; i < roles.length; ++i) {
			Pair<Role, Move> p = Pair.of(roles[i], moves.get(i));
			int index = roleMoves.get(p);
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

    	System.out.println("getNextState");
    	for (int i = 0; i < roles.length; ++i) System.out.println(moves.get(i));
    	propNet.renderToFile("preNext.dot", components);

    	setState(state, moves, thread_id);
    	propNet.renderToFile("postNextState.dot", components);

    	System.out.println(Long.toBinaryString(currentState.getBits()[0]));
    	//System.exit(0);
    	return (OpenBitSet) currentState.clone();
    }



    protected void clearPropNet(int thread_id) {
    	components = propNet.getComponents();
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
    private static final long GOAL_MASK = 0x7F_0000_0000_000000L;
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
    	List<Role> rs = new ArrayList<Role>();
        for (int i = 0; i < roles.length; ++i) rs.add(roles[i]);
        return rs;
    }



    @Override
	public MachineState toGdl(OpenBitSet state) {
    	Set<GdlSentence> bases = new HashSet<GdlSentence>();
    	int[] baseProps = propNet.getBasePropositions();
    	for (int i = state.nextSetBit(0); i != -1; i = state.nextSetBit(i + 1)) {
    		bases.add(gdlSentenceMap.get(baseOffset + i));
    	}
    	return new MachineState(bases);
    }

    @Override
	public OpenBitSet toBit(MachineState state) {
    	Set<GdlSentence> bases = state.getContents();
    	HashMap<GdlSentence, Integer> basesMap = propNet.getBasesMap();
    	OpenBitSet bitSet = new OpenBitSet(numBases);
    	for (GdlSentence base : bases) {
    		bitSet.fastSet(basesMap.get(base));
    	}
    	return bitSet;
    }


}
