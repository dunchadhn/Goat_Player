package org.ggp.base.util.statemachine.verifier;

import java.util.HashSet;
import java.util.List;

import org.ggp.base.util.logging.GamerLogger;
import org.ggp.base.util.statemachine.BitMachineState;
import org.ggp.base.util.statemachine.BitStateMachine;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;


public class BitStateMachineVerifier {
    public static boolean checkMachineConsistency(StateMachine theReference, BitStateMachine theSubject, long timeToSpend) throws MoveDefinitionException {
        long startTime = System.currentTimeMillis();

        GamerLogger.log("StateMachine", "Performing automatic consistency testing on " + theSubject.getClass().getName() + " using " + theReference.getClass().getName() + " as a reference.");

        GamerLogger.emitToConsole("Consistency checking: [");
        MachineState state1 = null;
        MachineState state2 = null;
        BitMachineState state = null;
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
                assert state.equals(theSubject.toBit(state2));
                for(Role theRole : theReference.getRoles()) {
                    try {
                        if(!(theSubject.getLegalMoves(state, theRole).size() == theReference.getLegalMoves(state1, theRole).size())) {
                            GamerLogger.log("StateMachine", "Inconsistency between bitMachine and ProverStateMachine over state " + state2.toString() + " vs " + state1.getContents());
                            GamerLogger.log("StateMachine", "RefMachine has move count = " + theReference.getLegalMoves(state1, theRole).size() + " for player " + theRole);
                            GamerLogger.log("StateMachine", "BitMachine has move count = " + theSubject.getLegalMoves(state, theRole).size() + " for player " + theRole);
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
                    if(theReference.getGoal(state1, theRole) != theSubject.getGoal(state, theRole)) {
                        GamerLogger.log("StateMachine", "Inconsistency between bitMachine and ProverStateMachine over goal value for " + theRole + " of state " + state1.toString() + ": " + theSubject.getGoal(state, theRole) + " vs " + theReference.getGoal(state1, theRole));
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