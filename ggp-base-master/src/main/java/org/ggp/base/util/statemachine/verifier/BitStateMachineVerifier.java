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
                    	List<Move> refMoves = theReference.getLegalMoves(state1, theRole);
                    	List<Move> subjMoves = theSubject.getLegalMoves(state, theRole);
                        if(!(refMoves.size() == subjMoves.size())) {
                            GamerLogger.log("StateMachine", "Inconsistency between bitMachine and ProverStateMachine over state " + state2.toString() + " vs " + state1.getContents());
                            GamerLogger.log("StateMachine", "RefMachine has move count = " + refMoves.size() + " for player " + theRole + " and moves: " + refMoves.toString());
                            GamerLogger.log("StateMachine", "BitMachine has move count = " + subjMoves.size() + " for player " + theRole + " and moves: " + subjMoves.toString());
                            HashMap<Pair<Role, Move>, Integer> legalMoveMap = theSubject.getPropNet().getLegalMoveMap();
                            HashSet<Move> diff = (new HashSet<Move>(refMoves));
                            diff.removeAll(subjMoves);
                            HashMap<Integer, Component> indexCompMap = theSubject.getPropNet().indexCompMap();
                            HashMap<Component, Integer> compIndexMap = theSubject.getPropNet().compIndexMap();
                            int[] components = theSubject.getComps();
                            for (Move m : diff) {
                            	Pair<Role, Move> p = Pair.of(theRole, m);
                            	int index = legalMoveMap.get(p);
                            	Component c = indexCompMap.get(index);
                            	printInputs(c, compIndexMap, components, 1, 17, theSubject.connecTable, theSubject.compInfo, theSubject.getPropNet().outputMap);
                            	/*System.out.println(c + " (" + index + ") " + get_current_value(components[index]) + " " + Integer.toBinaryString(components[index]));
                            	System.out.println("  Inputs:" + c.getInputs_set().toString());
                            	for (Component in : c.getInputs_set()) {
                            		int iIndex = compIndexMap.get(in);
                            		System.out.println("   " + in + " (" + iIndex + ") " + get_current_value(components[iIndex]) + " " + Integer.toBinaryString(components[iIndex]));
                            		System.out.println("    Inputs2:" + in.getInputs_set().toString());
                            		for (Component in2 : in.getInputs_set()) {
                                		int iIndex2 = compIndexMap.get(in2);
                                		System.out.println("     " + in2 + " (" + iIndex2 + ") " + get_current_value(components[iIndex2]) + " " + Integer.toBinaryString(components[iIndex2]));
                                		System.out.println("      Inputs3:" + in2.getInputs_set().toString());
                                		for (Component in3 : in2.getInputs_set()) {
                                    		int iIndex3 = compIndexMap.get(in3);
                                    		System.out.println("       " + in3 + " (" + iIndex3 + ") " + get_current_value(components[iIndex3]) + " " + Integer.toBinaryString(components[iIndex3]));
                                    		System.out.println("        Inputs4:" + in3.getInputs_set().toString());
                                    		for (Component in4 : in3.getInputs_set()) {
                                        		int iIndex4 = compIndexMap.get(in4);
                                        		System.out.println("         " + in4 + " (" + iIndex4 + ") " + get_current_value(components[iIndex4]) + " " + Integer.toBinaryString(components[iIndex4]));
                                        		System.out.println("          Inputs5:" + in4.getInputs_set().toString());
                                        		for (Component in5 : in4.getInputs_set()) {
                                            		int iIndex5 = compIndexMap.get(in5);
                                            		System.out.println("           " + in5 + " (" + iIndex5 + ") " + get_current_value(components[iIndex5]) + " " + Integer.toBinaryString(components[iIndex5]));
                                            		System.out.println("            Inputs6:" + in5.getInputs_set().toString());
                                            		for (Component in6 : in5.getInputs_set()) {
                                                		int iIndex6 = compIndexMap.get(in6);
                                                		System.out.println("             " + in6 + " (" + iIndex6 + ") " + get_current_value(components[iIndex6]) + " " + Integer.toBinaryString(components[iIndex6]));
                                                		System.out.println("              Inputs7:" + in6.getInputs_set().toString());
                                                		for (Component in7 : in6.getInputs_set()) {
                                                    		int iIndex7 = compIndexMap.get(in7);
                                                    		System.out.println("               " + in7 + " (" + iIndex7 + ") " + get_current_value(components[iIndex7]) + " " + Integer.toBinaryString(components[iIndex7]));
                                                    		System.out.println("                Inputs8:" + in7.getInputs_set().toString());
                                                    		for (Component in8 : in7.getInputs_set()) {
                                                        		int iIndex8 = compIndexMap.get(in8);
                                                        		System.out.println("               " + in8 + " (" + iIndex8 + ") " + get_current_value(components[iIndex8]) + " " + Integer.toBinaryString(components[iIndex8]));
                                                        		System.out.println("                 Inputs9:" + in8.getInputs_set().toString());
                                                        		for (Component in9 : in8.getInputs_set()) {
                                                            		int iIndex9 = compIndexMap.get(in9);
                                                            		System.out.println("                  " + in9 + " (" + iIndex9 + ") " + get_current_value(components[iIndex9]) + " " + Integer.toBinaryString(components[iIndex9]));
                                                            		System.out.println("                   Inputs10:" + in9.getInputs_set().toString());
                                                            		for (Component in10 : in9.getInputs_set()) {
                                                                		int iIndex10 = compIndexMap.get(in10);
                                                                		System.out.println("                    " + in10 + " (" + iIndex10 + ") " + get_current_value(components[iIndex10]) + " " + Integer.toBinaryString(components[iIndex10]));
                                                                		System.out.println("                     Inputs11:" + in10.getInputs_set().toString());
                                                                		for (Component in11 : in10.getInputs_set()) {
                                                                    		int iIndex11 = compIndexMap.get(in11);
                                                                    		System.out.println("                      " + in11 + " (" + iIndex11 + ") " + get_current_value(components[iIndex11]) + " " + Integer.toBinaryString(components[iIndex11]));
                                                                    		System.out.println("                       Inputs12:" + in11.getInputs_set().toString());
                                                                    		for (Component in12 : in11.getInputs_set()) {
                                                                        		int iIndex12 = compIndexMap.get(in12);
                                                                        		System.out.println("                        " + in12 + " (" + iIndex12 + ") " + get_current_value(components[iIndex12]) + " " + Integer.toBinaryString(components[iIndex12]));
                                                                        		System.out.println("                         Inputs13:" + in12.getInputs_set().toString());
                                                                        		for (Component in13 : in12.getInputs_set()) {
                                                                            		int iIndex13 = compIndexMap.get(in13);
                                                                            		System.out.println("                          " + in13 + " (" + iIndex13 + ") " + get_current_value(components[iIndex13]) + " " + Integer.toBinaryString(components[iIndex13]));
                                                                            		System.out.println("                           Inputs14:" + in13.getInputs_set().toString());
                                                                            		for (Component in14 : in13.getInputs_set()) {
                                                                                		int iIndex14 = compIndexMap.get(in14);
                                                                                		System.out.println("                            " + in14 + " (" + iIndex14 + ") " + get_current_value(components[iIndex14]) + " " + Integer.toBinaryString(components[iIndex14]));
                                                                                		System.out.println("                             Inputs15:" + in14.getInputs_set().toString());
                                                                                		for (Component in15 : in14.getInputs_set()) {
                                                                                    		int iIndex15 = compIndexMap.get(in15);
                                                                                    		System.out.println("                              " + in15 + " (" + iIndex15 + ") " + get_current_value(components[iIndex15]) + " " + Integer.toBinaryString(components[iIndex15]));
                                                                                    		System.out.println("                               Inputs16:" + in15.getInputs_set().toString());
                                                                                    		for (Component in16 : in15.getInputs_set()) {
                                                                                        		int iIndex16 = compIndexMap.get(in16);
                                                                                        		System.out.println("                                " + in16 + " (" + iIndex16 + ") " + get_current_value(components[iIndex16]) + " " + Integer.toBinaryString(components[iIndex16]));
                                                                                        	}
                                                                                    	}
                                                                                	}
                                                                            	}
                                                                        	}
                                                                    	}
                                                                	}
                                                            	}
                                                        	}
                                                    	}
                                                	}
                                            	}
                                        	}
                                    	}
                                	}
                            	}*/


                            }
                            return false;
                        }
                        if(!(new HashSet<Move>(theReference.getLegalMoves(state1, theRole)).equals(new HashSet<Move>(theSubject.getLegalMoves(state, theRole))))) {
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
                	int refVal = theReference.getGoal(state1, theRole); int subjVal = theSubject.getGoal(state, theRole);
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