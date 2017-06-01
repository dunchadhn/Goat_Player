package org.ggp.base.util.statemachine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Stack;

import org.apache.lucene.util.OpenBitSet;
import org.ggp.base.util.Pair;
import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.XPropNet;
import org.ggp.base.util.propnet.factory.OptimizingPropNetFactory;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;



@SuppressWarnings("unused")
public class XStateMachine extends XMachine {
    public XPropNet propNet;
    public Role[] roles;

    private OpenBitSet currentState, nextState, currInputs, currLegals;
    public int numBases, baseOffset, numLegals, numInputs, legalOffset, inputOffset;

    public HashMap<Role, List<Move>> actions;
    public HashMap<Role, List<Move>> currentLegalMoves;
    public HashMap<Integer, Integer> rolesIndexMap;
    public Move[] legalArray;
    public HashMap< Pair<Role, Move>, Integer> roleMoves;

    public int[] components;
    public long[] compInfo;
    public int[] connecTable;

    public int[][] goalPropositions;

    public HashMap<Integer, GdlSentence> gdlSentenceMap;
    public HashMap<Integer, Component> indexCompMap;

    private Random rand;

    public OpenBitSet getCurrentState() {
    	return currentState;
    }

    public OpenBitSet getNextState() {
    	return nextState;
    }

    public OpenBitSet getCurrInputs() {
    	return currInputs;
    }

    public OpenBitSet getCurrLegals() {
    	return currLegals;
    }

    public long[] getCompInfo() {
    	return compInfo;
    }

    public int[] getComponents() {
    	return components;
    }

    public int[] getConnecTable() {
    	return connecTable;
    }

    @Override
    public void initialize(List<Gdl> description) {
        try {
        	System.out.println("Initialized");
            propNet = new XPropNet(OptimizingPropNetFactory.create(description));

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
            rand = new Random();

            goalPropositions = propNet.getGoalPropositions();

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
    public OpenBitSet getInitialState() {//Do initialization in initialize
    	ArrayDeque<Integer> q = new ArrayDeque<Integer>(compInfo.length);
    	resetPropNet();

    	setInit(true,q);
    	initBases();
    	OpenBitSet state = (OpenBitSet) currentState.clone();
    	setConstants();

    	rawPropagate();

    	setInit(false,q);
    	propagate(q);

    	currentState = state;
        return (OpenBitSet) state.clone();//necessary to clone?
    }

    protected void setInit(boolean val, ArrayDeque<Integer> q) {
    	int initId = propNet.getInitProposition();
    	if (initId == -1) return;

    	if (!val) components[initId] -= 1;
    	long comp = compInfo[initId];
    	int num_outputs = (int) ((comp & OUTPUTS_MASK) >> OUTPUT_SHIFT);
    	int outputsIndex = (int) (comp & OFFSET_MASK);

    	for (int j = 0; j < num_outputs; ++j) {
    		int outIndex = connecTable[outputsIndex + j];
    		if (val)  {
    			components[outIndex] += 1;
    		} else {
    			int lastPropagatedValue = (components[outIndex] & CURR_VAL_MASK);
    			components[outIndex] -= 1;
    			int newVal = (components[outIndex] & CURR_VAL_MASK);
    			if (newVal != lastPropagatedValue) {
    				q.add(newVal | outIndex);
    			}
    		}
    	}

    }


    protected void initBases() {

    	int[] initBases = propNet.initBases();
    	if (initBases != null) {
    		for (int i = 0; i < initBases.length; ++i) {
        		int bIndex = initBases[i];
        		components[bIndex] += 1;
        		currentState.fastSet(bIndex - baseOffset);

        		long comp = compInfo[bIndex];
            	int num_outputs = (int) ((comp & OUTPUTS_MASK) >> OUTPUT_SHIFT);
            	int outputsIndex = (int) (comp & OFFSET_MASK);

            	for (int j = 0; j < num_outputs; ++j) {
            		int outIndex = connecTable[outputsIndex + j];
            		components[outIndex] += 1;
            	}
        	}
    	}
    }


    protected void setConstants() {
    	int[] constants = propNet.getConstants();
    	if (constants == null) return;

    	for (int c = 0; c < constants.length; ++c) {
    		long comp = compInfo[constants[c]];
        	int num_outputs = (int) ((comp & OUTPUTS_MASK) >> OUTPUT_SHIFT);
        	int outputsIndex = (int) (comp & OFFSET_MASK);

        	boolean val = (components[constants[c]] & CURR_VAL_MASK) != 0;
        	for (int i = 0; i < num_outputs; ++i) {
        		int outIndex = connecTable[outputsIndex + i];
        		if (val) components[outIndex] += 1;
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
    protected void rawPropagate() {//compute ordering
    	Stack<Integer> ordering = propNet.getOrdering();

    	while (!ordering.isEmpty()) {
    		int compId = ordering.pop();
    		int value = components[compId];
    		boolean val = (value & CURR_VAL_MASK) != 0;
    		long comp = compInfo[compId];

    		if ((comp & TRIGGER_MASK) != 0) {
    			if ((comp & TRANSITION_MASK) != 0) {
    				int outputIndex = (int) (comp & OFFSET_MASK);
    				int baseIndex = connecTable[outputIndex] - baseOffset;
    				if (val) {
    					nextState.fastSet(baseIndex);
    				} else {
    					nextState.clear(baseIndex);
    				}
    				continue;

    			} else {
    				int legalIndex = compId - legalOffset;
    				if (val) {
    					currLegals.fastSet(legalIndex);
    				} else {
    					currLegals.clear(legalIndex);
    				}
    			}
    		}

        	int num_outputs = (int) ((comp & OUTPUTS_MASK) >> OUTPUT_SHIFT);
        	int outputsIndex = (int) (comp & OFFSET_MASK);

        	for (int i = 0; i < num_outputs; ++i) {
        		int outIndex = connecTable[outputsIndex + i];
        		if (val) components[outIndex] += 1;
        	}
    	}
    }

    private static final int getId(int value) {
    	return (NOT_CURR_VAL_MASK & value);
    }

    protected void propagate(ArrayDeque<Integer> q) {

    	while(!q.isEmpty()) {
    		int value = q.remove();
    		int compId = getId(value);
    		boolean val = get_current_value(value);


    		long comp = compInfo[compId];
    		if ((comp & TRIGGER_MASK) != 0) {
    			if ((comp & TRANSITION_MASK) != 0) {
    				int outputIndex = (int) (comp & OFFSET_MASK);
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

        	int num_outputs = (int) ((comp & OUTPUTS_MASK) >> OUTPUT_SHIFT);
        	int outputsIndex = (int) (comp & OFFSET_MASK);

        	for (int i = 0; i < num_outputs; ++i) {
        		int outIndex = connecTable[outputsIndex + i];
        		int outValue = components[outIndex];
        		int lastPropagatedValue = (outValue & CURR_VAL_MASK);

        		if (val) components[outIndex] += 1;
        		else components[outIndex] -= 1;

        		int newVal = (components[outIndex] & CURR_VAL_MASK);

        		if (newVal != lastPropagatedValue) {
        			q.add(newVal | outIndex);
        		}
        	}
    	}
    }


    @Override
	public List<List<Move>> getLegalJointMoves(OpenBitSet state) throws MoveDefinitionException {
    	setState(state, null);

        List<List<Move>> jointMoves = new ArrayList<List<Move>>();

        int size = roles.length - 1;
    	for (int i = 0; i < size; ++i) {
    		List<Move> moves = new ArrayList<Move>();
    		int roleIndex = rolesIndexMap.get(i);
    		int nextRoleIndex = rolesIndexMap.get(i + 1);

    		for (int j = roleIndex; j < nextRoleIndex; ++j) {
    			if (currLegals.fastGet(j)) {
    				moves.add(legalArray[j]);
    			}
    		}
    		jointMoves.add(moves);
    	}

    	int start = rolesIndexMap.get(size);
    	int end = legalArray.length;
    	List<Move> moves = new ArrayList<Move>();
    	for(int i = start; i < end; ++i) {
    		if (currLegals.fastGet(i)) {
    			moves.add(legalArray[i]);
    		}
    	}
    	jointMoves.add(moves);

        List<List<Move>> crossProduct = new ArrayList<List<Move>>();
        crossProductLegalMoves(jointMoves, crossProduct, new ArrayDeque<Move>());//

        return crossProduct;
    }

    @Override
	public List<Move> getRandomJointMove(OpenBitSet state) throws MoveDefinitionException
    {
        List<List<Move>> jointMoves = getLegalJointMoves(state);
        return jointMoves.get(rand.nextInt(jointMoves.size()));

    }

    public Move getRandomMove(OpenBitSet state, int rIndex) throws MoveDefinitionException
    {
        List<Move> legals = getLegalMoves(state, rIndex);
        return legals.get(rand.nextInt(legals.size()));
    }

    /**
     * Returns a random joint move from among all the possible joint moves in
     * the given state in which the given role makes the given move. Assumes move is a valid move
     */
    public List<Move> getRandomJointMove(OpenBitSet state, int rIndex, Move move) throws MoveDefinitionException
    {
    	List<List<Move>> randJointMove = new ArrayList<List<Move>>();
    	for (List<Move> jointMove : getLegalJointMoves(state)) {
    		if (jointMove.get(rIndex).equals(move)) randJointMove.add(jointMove);
    	}
    	return randJointMove.get(rand.nextInt(randJointMove.size()));//ASSUMES move is a valid move
    }

    @Override
	public OpenBitSet getRandomNextState(OpenBitSet state) throws MoveDefinitionException, TransitionDefinitionException
    {
        List<Move> random = getRandomJointMove(state);
        return getNextState(state, random);
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


    public List<Move> getLegalMoves(OpenBitSet state, int rIndex)//Change such that we don't have to keep updating legal moves
            throws MoveDefinitionException {

    	setState(state, null);

    	List<Move> moves = new ArrayList<Move>();
    	int roleIndex = rolesIndexMap.get(rIndex);
    	int nextRoleIndex = (rIndex == (roles.length - 1) ? legalArray.length : rolesIndexMap.get(rIndex + 1));
    	for (int i = roleIndex; i < nextRoleIndex; ++i) {
			if (currLegals.fastGet(i)) {
				moves.add(legalArray[i]);
			}
		}

    	return moves;
    }



	protected void setBases(OpenBitSet state, ArrayDeque<Integer> q) {
    	if (state == null) return;
    	int[] bases = propNet.getBasePropositions();
    	int size = bases.length;

    	OpenBitSet temp = (OpenBitSet) state.clone();
    	state.xor(currentState);
    	currentState = temp;

    	for (int i = state.nextSetBit(0); i != -1; i = state.nextSetBit(i + 1)) {
    		boolean val = temp.fastGet(i);
    		if (val) components[baseOffset + i] += 1;
    		else components[baseOffset + i] -= 1;

    		long comp = compInfo[baseOffset + i];
    		int num_outputs = (int) ((comp & OUTPUTS_MASK) >> OUTPUT_SHIFT);
        	int outputsIndex = (int) (comp & OFFSET_MASK);

        	for (int j = 0; j < num_outputs; ++j) {
        		int outIndex = connecTable[outputsIndex + j];
        		int outValue = components[outIndex];
        		int lastPropagatedValue = (outValue & CURR_VAL_MASK);
        		if (val) components[outIndex] += 1;
        		else components[outIndex] -= 1;

        		int newVal = (components[outIndex] & CURR_VAL_MASK);
        		if (newVal != lastPropagatedValue) {
        			q.add(newVal | outIndex);
        		}
        	}
    	}

    }


	protected void setActions(OpenBitSet moves, ArrayDeque<Integer> q) {
    	if(moves == null) return;

    	int[] inputs = propNet.getInputPropositions();
    	int size = inputs.length;

    	OpenBitSet tempInputs = (OpenBitSet) moves.clone();
    	moves.xor(currInputs);
    	currInputs = tempInputs;

    	for (int i = moves.nextSetBit(0); i != -1; i = moves.nextSetBit(i + 1)) {
    		boolean val = currInputs.fastGet(i);
    		int inputIndex = inputOffset + i;
    		if (val) components[inputIndex] += 1;
    		else components[inputIndex] -= 1;

    		long comp = compInfo[inputIndex];
    		int num_outputs = (int) ((comp & OUTPUTS_MASK) >> OUTPUT_SHIFT);
        	int outputsIndex = (int) (comp & OFFSET_MASK);

        	for (int j = 0; j < num_outputs; ++j) {
        		int outIndex = connecTable[outputsIndex + j];

        		int lastPropagatedValue = (components[outIndex] & CURR_VAL_MASK);
        		if (val) components[outIndex] += 1;
        		else components[outIndex] -= 1;

        		int newVal = (components[outIndex] & CURR_VAL_MASK);
        		if (newVal != lastPropagatedValue) {
        			q.add(newVal | outIndex);
        		}
        	}
    	}

    }

	protected OpenBitSet movesToBit(List<Move> moves) {
		if (moves == null || moves.isEmpty()) return null;

		OpenBitSet movesSet = new OpenBitSet(numInputs);
		for (int i = 0; i < roles.length; ++i) {
			int index = roleMoves.get(Pair.of(roles[i], moves.get(i)));
			movesSet.fastSet(index);
		}

		return movesSet;
	}

    protected void setState(OpenBitSet state, List<Move> moves) {
    	ArrayDeque<Integer> q = new ArrayDeque<Integer>(compInfo.length);
    	setBases((OpenBitSet)state.clone(), q);
    	setActions(movesToBit(moves), q);
    	propagate(q);
    }


    @Override
	public OpenBitSet getNextState(OpenBitSet state, List<Move> moves) {

    	setState(state, moves);

    	return (OpenBitSet) nextState.clone();
    }

    protected void resetPropNet() {
    	currInputs = new OpenBitSet(numInputs);
    	currentState = new OpenBitSet(numBases);
   		currLegals = new OpenBitSet(numLegals);
   		nextState = new OpenBitSet(numBases);
   		components = propNet.getComponents();
    }

    @Override
    public boolean isTerminal(OpenBitSet state) {
    	setState(state, null);
    	return (components[propNet.getTerminalProposition()] & CURR_VAL_MASK) != 0;
    }

    //goal Propositions will never be Trigger components, so we
    //can use its 2nd bit. Goal value is stored in bits 2-8, reading
    //from the left
    private static final long GOAL_MASK = 0x7F_0000_0000_000000L;
    private static final int GOAL_SHIFT = TYPE_SHIFT;
    protected int getGoalValue(long value) {//inline
    	return (int) ((value & GOAL_MASK) >> TYPE_SHIFT);
    }

    @Override
	public List<Integer> getGoals(OpenBitSet state) throws GoalDefinitionException {
        List<Integer> theGoals = new ArrayList<Integer>();
        for (int i = 0; i < roles.length; ++i) {
            theGoals.add(getGoal(state, i));
        }
        return theGoals;
    }

    public int getGoal(OpenBitSet state, int rIndex)
            throws GoalDefinitionException {

    	setState(state, null);
        int[] rewards = goalPropositions[rIndex];
        int size = rewards.length;

        for(int i = 0; i < size; ++i) {
        	int rewardIndex = rewards[i];
        	int value = components[rewardIndex];
        	if ((value & CURR_VAL_MASK) != 0) {
        		int goalVal = (int) ((compInfo[rewardIndex] & GOAL_MASK) >> TYPE_SHIFT);
        		return goalVal;
        	}

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

	@Override
	public MachineState getMachineStateFromBitSet(OpenBitSet contents) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Move> getLegalMoves(OpenBitSet state, Role role)
			throws MoveDefinitionException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getGoal(OpenBitSet state, Role role)
			throws GoalDefinitionException {
		// TODO Auto-generated method stub
		System.out.println("Shouldn't call this method");
		System.exit(0);
		return 0;
	}


}
