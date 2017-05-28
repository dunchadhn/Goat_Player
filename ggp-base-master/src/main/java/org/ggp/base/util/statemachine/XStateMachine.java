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
    public long[] compInfo;
    public int[] connecTable;
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
            propNet = new XPropNet(OptimizingPropNetFactory.create(description));
            components = null;
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

    private List<Long> curr = new ArrayList<Long>();
    private List<Long> next = new ArrayList<Long>();

    protected void setLegals() {
    	for (int i = 0; i < numLegals; ++i) {
    		if (get_current_value(components[legalOffset + i])) {
    			currLegals.fastSet(i);

    		}
    		else currLegals.fastClear(i);
    	}
    }

    public int[] getComps() {
    	return components;
    }


    @Override
    public OpenBitSet getInitialState() {//Do initialization in initialize
    	int[] oldComponents = components;
    	clearPropNet();
    	if (components == oldComponents) {
    		System.out.println("PropNet not cleared properly");
    		System.exit(0);
    	}

    	//System.out.println("getInitial");
    	currInputs = new OpenBitSet(numInputs);
    	setInit(true);
    	//propNet.renderToFile("init.dot", components);
    	initBases();
    	OpenBitSet state = (OpenBitSet) currentState.clone();

    	setConstants();
    	//propNet.renderToFile("postInitConstants.dot", components);

    	rawPropagate();
    	//propNet.renderToFile("postRaw.dot", components);

    	//System.out.println("NextState: " + Long.toBinaryString(nextState.getBits()[0]));
    	//System.out.println(toGdl(nextState).toString());

    	OpenBitSet legals = (OpenBitSet) currLegals.clone();
    	//System.out.println("CurrLegals: ");
    	for (int i = 0; i < currLegals.getBits().length; ++i) {
    		//System.out.println(Long.toBinaryString(currLegals.getBits()[i]));
    		curr.add(currLegals.getBits()[i]);
    	}

    	Queue<Pair<Integer, Boolean>> q = setInit(false);
    	propagate(q);
    	//propNet.renderToFile("postInit.dot", components);
    	//currLegals = legals;

    	//System.out.println("nextState: " + Long.toBinaryString(nextState.getBits()[0]));
    	//System.out.println(toGdl(nextState).toString());
    	/*System.out.println("newLegals: ");
    	for (int i = 0; i < currLegals.getBits().length; ++i) {
    		//System.out.println(Long.toBinaryString(currLegals.getBits()[i]));
    		next.add(currLegals.getBits()[i]);
    	}
    	if (curr.size() != next.size()) {
    		System.out.println("size diff");
    		System.exit(0);
    	}
    	for (int i = 0; i < curr.size(); ++i) {
    		if ((curr.get(i) == 0) && (next.get(i) == 0)) {
    			curr.remove(i); next.remove(i);
    		}
    	}

    	System.out.println();
    	for (int i = 0; i < curr.size(); ++i) {
    		if (curr.get(i) != 0)
    		System.out.println("curr: " + Long.toBinaryString(curr.get(i)) + " next: " + Long.toBinaryString(next.get(i)));
    	}*/
    	currentState = state;

        return (OpenBitSet) state.clone();//necessary to clone?
    }

    protected Queue<Pair<Integer, Boolean>> setInit(boolean val) {
    	Queue<Pair<Integer, Boolean>> q = new LinkedList<Pair<Integer, Boolean>>();
    	int initId = propNet.getInitProposition();
    	if (initId == -1) return q;
    	if (!val) components[initId] -= 1;
    	long comp = compInfo[initId];
    	int num_outputs = numOutputs(comp);
    	int outputsIndex = outputsOffset(comp);

    	for (int j = 0; j < num_outputs; ++j) {
    		int outIndex = connecTable[outputsIndex + j];
    		boolean lastPropagatedValue = get_current_value(components[outIndex]);
    		if (val) {
    			components[outIndex] += 1; //+= 1 corresponds to edit_T(true)
    			//q.add(Pair.of(outIndex, get_current_value(components[outIndex])));//Not used
    		} else {
    			components[outIndex] -= 1;
    			boolean newVal = get_current_value(components[outIndex]);
    			if (newVal != lastPropagatedValue) {
    				q.add(Pair.of(outIndex, newVal));
    			}
    		}

    	}

    	return q;
    }


    protected void initBases() {
    	currentState = new OpenBitSet(numBases);
    	int[] initBases = propNet.initBases();
    	if (initBases != null) {
    		for (int i = 0; i < initBases.length; ++i) {
        		int bIndex = initBases[i];
        		components[bIndex] += 1;
        		currentState.fastSet(bIndex - baseOffset);

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
    protected void rawPropagate() {//compute ordering
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
    				if (val) {
    					nextState.fastSet(baseIndex);
    					//components[baseIndex] += 1;
    				}
    				else {
    					nextState.clear(baseIndex);
    				}
    				continue;

    			} else {
    				int legalIndex = compId - legalOffset;
    				//System.out.println("legalTrigger");
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

    private int kIter = 0;
    protected void propagate(Queue<Pair<Integer, Boolean>> q) {
    	//kIter = 0;
    	while(!q.isEmpty()) {
    		Pair<Integer, Boolean> p = q.remove();
    		int compId = p.left;
    		boolean val = p.right;

    		//System.out.println(indexCompMap.get(compId) + " (" + compId + ") " + val + Long.toHexString(compInfo[compId]));
    		//System.out.print("     Outputs:");
    		long comp = compInfo[compId];
    		if (isTrigger(comp)) {
    			//System.out.println("Trigger");
    			if (isTransition(comp)) {
    				//System.out.println("Transition");
    				int outputIndex = outputsOffset(comp);
    				int baseIndex = connecTable[outputIndex] - baseOffset;
    				if (val) nextState.fastSet(baseIndex);
    				else nextState.clear(baseIndex);
    				//System.out.println(indexCompMap.get(baseIndex + baseOffset) + " (" + (baseIndex + baseOffset) + ") " + " " + val);
    				continue;
    			} else {
    				//System.out.println("Trigger");
    				int legalIndex = compId - legalOffset;
    				if (val) currLegals.fastSet(legalIndex);
    				else currLegals.clear(legalIndex);
    			}
    		}

        	int num_outputs = numOutputs(comp);
        	int outputsIndex = outputsOffset(comp);

        	//System.out.print("NumOutputs: " + num_outputs + " OutputsIndex: " + outputsIndex + " FirstOutput: " + connecTable[outputsIndex]);

        	for (int i = 0; i < num_outputs; ++i) {
        		int outIndex = connecTable[outputsIndex + i];
        		int outValue = components[outIndex];
        		boolean lastPropagatedValue = get_current_value(outValue);
        		int lastVal = outValue;
        		if (val) components[outIndex] += 1; //+= 1 corresponds to edit_T(true)
        		else components[outIndex] -= 1;
        		int newValue = components[outIndex];
        		boolean newVal = get_current_value(components[outIndex]);
        		//System.out.print(indexCompMap.get(outIndex) + " (" + outIndex + ") " + lastPropagatedValue + " " + Integer.toBinaryString(lastVal) + " " + newVal + " " + Integer.toBinaryString(newValue));
        		//printOutputs(outIndex);
        		if (newVal != lastPropagatedValue) {
        			q.add(Pair.of(outIndex, newVal));
        		}
        	}
        	//System.out.println();
        	//propNet.renderToFile("kIter" + ++kIter + ".dot", components);
    	}

    }

    protected HashMap<Role, List<Move>> getLegals(OpenBitSet s) {
    	HashMap<Role, List<Move>> legalMap = new HashMap<Role, List<Move>>();
    	int size = roles.length - 1;
    	for (int i = 0; i < size; ++i) {
    		List<Move> moves = new ArrayList<Move>();
    		int roleIndex = rolesIndexMap.get(i);
    		int nextRoleIndex = rolesIndexMap.get(i + 1);
    		/*System.out.println(roleIndex + ", " + nextRoleIndex);
    		System.out.println(roles[i]);
    		System.out.print("   ");*/
    		for (int j = roleIndex; j < nextRoleIndex; ++j) {
    			if (s.fastGet(j)) {
    				//System.out.println(roles[i] + " " + legalArray[j]);
    				moves.add(legalArray[j]);
    				//System.out.print(" " + legalArray[j] + " (" + (legalOffset + j) + ")");
    			}
    		}
    		//System.out.println();
    		legalMap.put(roles[i], moves);
    	}
    	int start = rolesIndexMap.get(size);
    	int end = legalArray.length;
    	//System.out.println(start + ", " + end);
    	List<Move> moves = new ArrayList<Move>();
    	//System.out.println(roles[size]);
    	//System.out.print("   ");
    	for(int i = start; i < end; ++i) {
    		if (s.fastGet(i)) {
    			//System.out.println(roles[size] + " " + legalArray[i]);
    			moves.add(legalArray[i]);
    			//System.out.print(" " + legalArray[i] + " (" + (legalOffset + i) + ")");
    		}
    	}
    	//System.out.println();
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

    int kLegal = 0;
    @Override
    public List<Move> getLegalMoves(OpenBitSet state, Role role)//Change such that we don't have to keep updating legal moves
            throws MoveDefinitionException {
    	//System.out.println("getLegalMoves" + ++kLegal);

    	//propNet.renderToFile("preLegal" + kLegal + ".dot", components);


    	setState(state, null);


    	//propNet.renderToFile("postLegal" + kLegal + ".dot", components);

    	//System.out.println("New Legals: " + Long.toBinaryString(currLegals.getBits()[0]));
    	//System.out.println("nextState: " + Long.toBinaryString(nextState.getBits()[0]));
    	//System.out.println(toGdl(nextState).toString());

    	//setLegals();
    	currentLegalMoves = getLegals(currLegals);
    	//System.out.println();

    	//if (kLegal == 2) System.exit(0);
    	return currentLegalMoves.get(role);
    }



	protected void setBases(OpenBitSet state, Queue<Pair<Integer, Boolean>> q) {
    	if (state == null) return;
    	int[] bases = propNet.getBasePropositions();
    	int size = bases.length;

    	//System.out.println("CurrentState: " + Long.toBinaryString(currentState.getBits()[0]));
    	//System.out.println(toGdl(currentState).toString());
    	//System.out.println("State: " + Long.toBinaryString(state.getBits()[0]));
    	//System.out.println(toGdl(state).toString());

    	OpenBitSet temp = (OpenBitSet) state.clone();
    	//nextState = (OpenBitSet) currentState.clone();
    	state.xor(currentState);
    	currentState = temp;
    	//System.out.println("xor: " + Long.toBinaryString(state.getBits()[0]));

    	for (int i = state.nextSetBit(0); i != -1; i = state.nextSetBit(i + 1)) {
    		boolean val = temp.fastGet(i);
    		if (val) components[baseOffset + i] += 1;
    		else components[baseOffset + i] -= 1;
    		//System.out.println(gdlSentenceMap.get(baseOffset + i) + " " + val);

    		long comp = compInfo[baseOffset + i];
    		int num_outputs = numOutputs(comp);
        	int outputsIndex = outputsOffset(comp);

        	//System.out.println("    Outputs:");
        	for (int j = 0; j < num_outputs; ++j) {
        		int outIndex = connecTable[outputsIndex + j];
        		int outValue = components[outIndex];
        		boolean lastPropagatedValue = get_current_value(outValue);
        		if (val) components[outIndex] += 1;
        		else components[outIndex] -= 1;
        		//System.out.print(indexCompMap.get(outIndex) + " (" + outIndex + ") " + lastPropagatedValue + " " + get_current_value(components[outIndex]) + " ");
        		//printOutputs(outIndex);
        		boolean newVal = get_current_value(components[outIndex]);
        		if (newVal != lastPropagatedValue) {
        			q.add(Pair.of(outIndex, newVal));
        		}
        	}
        	//System.out.println();
    	}

    }


	protected void printOutputs(int index) {
		long comp = compInfo[index];
		int num_outputs = numOutputs(comp);
    	int outputsIndex = outputsOffset(comp);

    	System.out.print("\n                   Outputs2: ");
    	for (int j = 0; j < num_outputs; ++j) {
    		int outIndex = connecTable[outputsIndex + j];

    		int outValue = components[outIndex];
    		boolean lastPropagatedValue = get_current_value(outValue);

    		System.out.print(indexCompMap.get(outIndex) + " (" + outIndex + ") " + " " + lastPropagatedValue + " " + get_current_value(components[outIndex]) + " ");
    	}
    	System.out.println();
	}

	protected void setActions(OpenBitSet moves, Queue<Pair<Integer, Boolean>> q) {
    	if(moves == null) return;

    	int[] inputs = propNet.getInputPropositions();
    	int size = inputs.length;

    	/*System.out.println("currInputs: ");
    	for (int i = 0; i < currInputs.getBits().length; ++i) System.out.println(Long.toBinaryString(currInputs.getBits()[i]));
    	System.out.println("nextInputs: ");
    	for (int i = 0; i < moves.getBits().length; ++i) System.out.println(Long.toBinaryString(moves.getBits()[i]));
*/
    	OpenBitSet tempInputs = (OpenBitSet) moves.clone();
    	moves.xor(currInputs);
    	currInputs = tempInputs;
    	//System.out.println("xor: ");
    	//for (int i = 0; i < moves.getBits().length; ++i) System.out.println(Long.toBinaryString(moves.getBits()[i]));

    	for (int i = moves.nextSetBit(0); i != -1; i = moves.nextSetBit(i + 1)) {
    		boolean val = currInputs.fastGet(i);
    		int inputIndex = inputOffset + i;
    		if (val) components[inputIndex] += 1;
    		else components[inputIndex] -= 1;
    		//System.out.println(indexCompMap.get(inputIndex) + " " + val);

    		long comp = compInfo[inputIndex];
    		int num_outputs = numOutputs(comp);
        	int outputsIndex = outputsOffset(comp);

        	//System.out.println("    Outputs:");
        	for (int j = 0; j < num_outputs; ++j) {
        		int outIndex = connecTable[outputsIndex + j];

        		int outValue = components[outIndex];
        		boolean lastPropagatedValue = get_current_value(outValue);
        		if (val) components[outIndex] += 1;
        		else components[outIndex] -= 1;
        		//System.out.print(indexCompMap.get(outIndex) + " (" + outIndex + ") " + " " + lastPropagatedValue + " " + get_current_value(components[outIndex]) + " ");
        		//printOutputs(outIndex);
        		boolean newVal = get_current_value(components[outIndex]);
        		if (newVal != lastPropagatedValue) {
        			q.add(Pair.of(outIndex, newVal));
        		}
        		//System.out.println();
        	}
    	}

    }

	protected OpenBitSet movesToBit(List<Move> moves) {
		//System.out.println("movesToBit");
		if (moves == null || moves.isEmpty()) return null;

		OpenBitSet movesSet = new OpenBitSet(numInputs);
		for (int i = 0; i < roles.length; ++i) {
			Pair<Role, Move> p = Pair.of(roles[i], moves.get(i));
			int index = roleMoves.get(p);
			movesSet.fastSet(index);
		}

		return movesSet;
	}

    protected void setState(OpenBitSet state, List<Move> moves) {
    	Queue<Pair<Integer, Boolean>> q = new LinkedList<Pair<Integer, Boolean>>();
    	//System.out.println("SetState");
    	setBases((OpenBitSet)state.clone(), q);
    	//propNet.renderToFile("postBases.dot", components);
    	setActions(movesToBit(moves), q);
    	//propNet.renderToFile("postActions.dot", components);
    	propagate(q);
    	//propNet.renderToFile("postProp.dot", components);
    }

   /* protected void setState(OpenBitSet state, List<Move> moves, Queue<Integer> q, int thread_id) {
    	System.out.println("SetState");
    	setBases((OpenBitSet)state.clone(), q, thread_id);
    	propNet.renderToFile("postBases.dot", components);
    	setActions(movesToBit(moves), q, thread_id);
    	propNet.renderToFile("postActions.dot", components);
    	propagate(q, thread_id);
    	propNet.renderToFile("postProp.dot", components);
    }*/

    int kNext = 0;
    @Override
	public OpenBitSet getNextState(OpenBitSet state, List<Move> moves) {
    	long td = Thread.currentThread().getId();

    	/*System.out.println("getNextState" + ++kNext);
    	for (int i = 0; i < roles.length; ++i) System.out.print(roles[i] + " " + moves.get(i) + " ");
    	System.out.println();*/
    	//propNet.renderToFile("preNext" + kNext + ".dot", components);

    	setState(state, moves);

    	//propNet.renderToFile("postNextState" + kNext + ".dot", components);

    	/*System.out.println("nextState: " + Long.toBinaryString(nextState.getBits()[0]));
    	System.out.println(toGdl(nextState).toString());
    	System.out.println();*/

    	//if (kNext == 3) System.exit(0);
    	return (OpenBitSet) nextState.clone();
    }

    protected void clearPropNet() {
    	components = propNet.getComponents();
    }

    @Override
    public boolean isTerminal(OpenBitSet state) {
    	//System.out.println("isTerminal");
    	setState(state, null);
    	int term = propNet.getTerminalProposition();
    	return get_current_value(components[term]);
    }

    //goal Propositions will never be Trigger components, so we
    //can use its 2nd bit. Goal value is stored in bits 2-8, reading
    //from the left
    private static final long GOAL_MASK = 0x7F_0000_0000_000000L;
    private static final int GOAL_SHIFT = TYPE_SHIFT;
    protected int getGoalValue(long value) {//inline
    	//System.out.println("Value: " + (int)((value & GOAL_MASK) >> TYPE_SHIFT));
    	return (int) ((value & GOAL_MASK) >> TYPE_SHIFT);
    }

    @Override
    public int getGoal(OpenBitSet state, Role role)
            throws GoalDefinitionException {
    	//System.out.println("getGoal");
    	setState(state, null);
        int[] rewards = propNet.getGoalPropositions().get(role);
        int size = rewards.length;
        for(int i = 0; i < size; ++i) {
        	int rewardIndex = rewards[i];
        	int value = components[rewardIndex];
        	if (get_current_value(value)) {
        		int goalVal = getGoalValue(compInfo[rewardIndex]);
        		//System.out.println("GoalVal: " + goalVal);
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


}
