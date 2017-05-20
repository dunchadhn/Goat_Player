package org.ggp.base.player.gamer.statemachine;

import org.apache.lucene.util.OpenBitSet;

public class BitMachineState {
    public BitMachineState() {
        this.contents = null;
    }

    /**
     * Starts with a simple implementation of a MachineState. StateMachines that
     * want to do more advanced things can subclass this implementation, but for
     * many cases this will do exactly what we want.
     */
    private final OpenBitSet contents;
    public BitMachineState(OpenBitSet contents)
    {
        this.contents = contents;
    }

    /**
     * getContents returns the GDL sentences which determine the current state
     * of the game being played. Two given states with identical GDL sentences
     * should be identical states of the game.
     */
    public OpenBitSet getContents()
    {
        return contents;
    }

    @Override
    public BitMachineState clone() {
        return new BitMachineState((OpenBitSet)contents.clone());
    }

    /* Utility methods */
    @Override
    public int hashCode()
    {
        return getContents().hashCode();
    }

    //To-DO: Update toString
    @Override
    public String toString()
    {
        OpenBitSet contents = getContents();
        if(contents == null)
            return "(MachineState with null contents)";
        else
            return contents.toString();
    }

    @Override
    public boolean equals(Object o)
    {
        if ((o != null) && (o instanceof BitMachineState))
        {
            BitMachineState state = (BitMachineState) o;
            return state.getContents().equals(getContents());
        }

        return false;
    }
}
