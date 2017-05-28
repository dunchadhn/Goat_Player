package org.ggp.base.util.statemachine.verifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.apache.lucene.util.OpenBitSet;
import org.ggp.base.util.Pair;
import org.ggp.base.util.logging.GamerLogger;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.components.And;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.XStateMachine;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;


public class BitStateMachineVerifier {
	 private static final int CURR_VAL_MASK = 0x8000_0000;

	protected static boolean get_current_value(int value) {
    	return (value & CURR_VAL_MASK) != 0;
    }

    private static final long OFFSET_MASK = 0x00_0000_0000_FFFFFFL;

    protected static int outputsOffset(long comp) {
    	return (int) (comp & OFFSET_MASK);
    }

	protected static void printInputs(Component c, HashMap<Component, Integer> compIndexMap, int[] components, int depth, int depthMax,
			int[] connecTable, long[] compInfo, HashMap<Component, List<Component>> outputMap) {
		if (depth == depthMax) return;
		int index = compIndexMap.get(c);
		String spaces = "";
		for (int i = 0; i < depth; ++i) spaces += " ";
		System.out.println(spaces + c + " (" + index + ") " + get_current_value(components[index]) + " " + Integer.toBinaryString(components[index]));
		if (c instanceof And) {
			List<Component> outputs = outputMap.get(c);
			if (!((new HashSet<Component>(outputs)).equals(c.getOutputs()))) {
				System.out.println("!new HashSet<Component>(outputs)).equals(c.getOutputs_set())");
				System.exit(0);
			}
			System.out.println("Outputs: ");
			int fIndex = outputsOffset(compInfo[compIndexMap.get(c)]);
			System.out.println("First Output: " + connecTable[fIndex]);
			for (Component out : outputs) {
				System.out.println(out + " " + "(" + compIndexMap.get(out) + ")");
			}
		}
    	System.out.println(spaces + " Inputs" + depth + ":");
    	for (Component in : c.getInputs()) {
    		printInputs(in, compIndexMap, components, depth + 1, depthMax, connecTable, compInfo, outputMap);
    	}
	}

    public static boolean checkMachineConsistency(StateMachine theReference, XStateMachine theSubject, long timeToSpend) throws MoveDefinitionException {
        long startTime = System.currentTimeMillis();

        GamerLogger.log("StateMachine", "Performing automatic consistency testing on " + theSubject.getClass().getName() + " using " + theReference.getClass().getName() + " as a reference.");

        GamerLogger.emitToConsole("Consistency checking: [");
        MachineState state1 = null;
        MachineState state2 = null;
        OpenBitSet state = null;
        int nRound = 0;
        while(true) {
            nRound++;

            GamerLogger.emitToConsole(".");

            try {
                state1 = theReference.getInitialState();
                state2 = theSubject.toGdl(theSubject.getInitialState());
                if (!state1.equals(state2)) {
                	GamerLogger.log("StateMachine", "Inconsistency between bitMachine and ProverStateMachine over initial state " + state1.toString() + " vs \n" + state2.toString());
                	return false;
                }
            } catch(Exception e) {
                GamerLogger.log("StateMachine", "BitMachine failed to generate an initial state!");
                return false;
            }

            while(!theReference.isTerminal(state1)) {
            	//GamerLogger.emitToConsole("n: " + n++);
                if(System.currentTimeMillis() > startTime + timeToSpend)
                    break;

                // Do per-state consistency checks
                state = theSubject.toBit(state1);
                if (!state.equals(theSubject.toBit(state2))) {
                	GamerLogger.log("StateMachine", "Here Inconsistency between bitMachine and ProverStateMachine over state " + state1.toString() + " vs \n" + state2.toString() + " toBit");
                	return false;
                }
                for(Role theRole : theReference.getRoles()) {
                    try {
                    	int rIndex = theReference.getRoles().indexOf(theRole);
                    	List<Move> refMoves = theReference.getLegalMoves(state1, theRole);
                    	List<Move> subjMoves = theSubject.getLegalMoves(state, rIndex);
                        if(!(refMoves.size() == subjMoves.size())) {
                            GamerLogger.log("StateMachine", "Inconsistency between bitMachine and ProverStateMachine over state " + state2.toString() + " vs " + state1.getContents());
                            GamerLogger.log("StateMachine", "RefMachine has move count = " + refMoves.size() + " for player " + theRole + " and moves: " + refMoves.toString());
                            GamerLogger.log("StateMachine", "BitMachine has move count = " + subjMoves.size() + " for player " + theRole + " and moves: " + subjMoves.toString());
                            HashMap<Pair<Role, Move>, Integer> legalMoveMap = theSubject.getPropNet().getLegalMoveMap();
                            HashSet<Move> diff = (new HashSet<Move>(refMoves));
                            diff.removeAll(subjMoves);
                            HashMap<Integer, Component> indexCompMap = theSubject.getPropNet().indexCompMap();
                            HashMap<Component, Integer> compIndexMap = theSubject.getPropNet().compIndexMap();
                            int[] components = theSubject.components;
                            for (Move m : diff) {
                            	Pair<Role, Move> p = Pair.of(theRole, m);
                            	int index = legalMoveMap.get(p);
                            	Component c = indexCompMap.get(index);
                            	printInputs(c, compIndexMap, components, 1, 17, theSubject.connecTable, theSubject.compInfo, theSubject.getPropNet().outputMap);

                            }
                            return false;
                        }
                        if(!(new HashSet<Move>(theReference.getLegalMoves(state1, theRole)).equals(new HashSet<Move>(theSubject.getLegalMoves(state, rIndex))))) {
                        	GamerLogger.log("StateMachine", "Inconsistency between bitMachine and ProverStateMachine over state " + state1.toString());
                            GamerLogger.log("StateMachine", "refMachine has moves = " + theReference.getLegalMoves(state1, theRole).toString() + " for player " + theRole);
                            GamerLogger.log("StateMachine", "bitMachine has moves = " + theSubject.getLegalMoves(state, theRole).toString() + " for player " + theRole);
                            return false;
                        }
                    } catch(Exception e) {
                        GamerLogger.logStackTrace("StateMachine", e);
                    }
                }


              //Proceed on to the next state.
                List<Move> theJointMove = theReference.getRandomJointMove(state1);
                try {
                    state1 = theReference.getNextState(state1, theJointMove);
                    state2 = theSubject.toGdl(theSubject.getNextState(state, theJointMove));
                    state = theSubject.toBit(state2);
                    if (!state1.equals(state2)) {
                    	GamerLogger.log("StateMachine", "Inconsistency between bitMachine and ProverStateMachine over state " + state2.toString() + " vs \n" + state1.toString());
                    	return false;
                    }
                    if (theReference.isTerminal(state1) != theSubject.isTerminal(state)) {
                    	GamerLogger.log("StateMachine", "Inconsistency between bitMachine and ProverStateMachine over Terminality of state " + state1);
                    	return false;
                    }
                } catch(Exception e) {
                    GamerLogger.logStackTrace("StateMachine", e);
                }
            }

            if(System.currentTimeMillis() > startTime + timeToSpend)
                break;



            // Do final consistency checks
            if(!theSubject.isTerminal(state)) {
                GamerLogger.log("StateMachine", "Inconsistency between bitMachine and ProverStateMachine over terminal-ness of state " + state1.toString());
                return false;
            } else if(!state1.equals(state2)) {
            	GamerLogger.log("StateMachine", state1.toString() + " " + state2.toString());
            	return false;
            }
            for(Role theRole : theReference.getRoles()) {
                try {
                	int rIndex = theReference.getRoles().indexOf(theRole);
                	int refVal = theReference.getGoal(state1, theRole); int subjVal = theSubject.getGoal(state, rIndex);
                    if(refVal != subjVal) {
                        GamerLogger.log("StateMachine", "Inconsistency between bitMachine and ProverStateMachine over goal value for " + theRole + " of state " + state1.toString() + ": " + subjVal + " vs " + refVal);
                        return false;
                    }
                } catch(Exception e) {
                    GamerLogger.log("StateMachine", "Inconsistency between bitMachine and ProverStateMachine over goal-ness of state " + state1.toString());
                    return false;
                }
            }
        }
        GamerLogger.emitToConsole("]\n");

        GamerLogger.log("StateMachine", "Completed automatic consistency testing on " + theSubject.getClass().getName() + ", w/ " + nRound + " rounds: all tests pass!");
        return true;
    }
}