package org.ggp.base.util.propnet.architecture;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * The root class of the Component hierarchy, which is designed to represent
 * nodes in a PropNet. The general contract of derived classes is to override
 * all methods.
 */

public abstract class Component implements Serializable
{

	private static final long serialVersionUID = 352524175700224447L;
    /** The inputs to the component. */
    private final Set<Component> inputs_set;
    /** The outputs of the component. */
    private final Set<Component> outputs_set;

    private Component[] inputs_arr;
    private Component[] outputs_arr;

    private int inputs_size;
    private int outputs_size;

    private boolean currentValue;
    private boolean lastPropagatedOutputValue;

    /**
     * Creates a new Component with no inputs or outputs.
     */
    public Component()
    {
        this.inputs_set = new HashSet<Component>();
        this.outputs_set = new HashSet<Component>();
        this.currentValue = false;
        this.lastPropagatedOutputValue = false;
        inputs_size = 0;
        outputs_size = 0;
    }

    public void crystallize() {
    	inputs_size = inputs_set.size();
    	outputs_size = outputs_set.size();
    	this.inputs_arr = inputs_set.toArray(new Component[inputs_size]);
    	this.outputs_arr = outputs_set.toArray(new Component[outputs_size]);

    }

    public boolean edit_T(boolean val) {
    	return false;
    }

    public boolean set(int val) {
    	return false;
    }

    /**
     * Adds a new input.
     *
     * @param input
     *            A new input.
     */
    public void addInput(Component input)
    {
        inputs_set.add(input);
    }

    public void removeInput(Component input)
    {
    	inputs_set.remove(input);
    }

    public void removeOutput(Component output)
    {
    	outputs_set.remove(output);
    }

    public void removeAllInputs()
    {
		inputs_set.clear();
	}

	public void removeAllOutputs()
	{
		outputs_set.clear();
	}

    /**
     * Adds a new output.
     *
     * @param output
     *            A new output.
     */
    public void addOutput(Component output)
    {
        outputs_set.add(output);
    }

    /**
     * Getter method.
     *
     * @return The inputs to the component.
     */
    public Set<Component> getInputs_set()
    {
        return inputs_set;
    }

    public Component[] getInputs_arr() {
    	return inputs_arr;
    }

    public Component[] getOutputs_arr() {
    	return outputs_arr;
    }

    public int getInputsSize() {
    	return inputs_size;
    }

    public int getOutputsSize() {
    	return outputs_size;
    }

    /**
     * A convenience method, to get a single input.
     * To be used only when the component is known to have
     * exactly one input.
     *
     * @return The single input to the component.
     */

    public Component getSingleInput_set() {
        assert inputs_set.size() == 1;
        return inputs_set.iterator().next();
    }

    public Component getSingleInput_arr() {
        assert inputs_size == 1;
        return inputs_arr[0];
    }

    /**
     * Getter method.
     *
     * @return The outputs of the component.
     */
    public Set<Component> getOutputs_set()
    {
        return outputs_set;
    }

    /**
     * A convenience method, to get a single output.
     * To be used only when the component is known to have
     * exactly one output.
     *
     * @return The single output to the component.
     */
    public Component getSingleOutput_set() {
        assert outputs_set.size() == 1;
        return outputs_set.iterator().next();
    }

    public Component getSingleOutput_arr() {
        assert outputs_size == 1;
        return outputs_arr[0];
    }

    public boolean getCurrentValue() {
    	return currentValue;
    }

    public boolean getLastPropagatedOutputValue() {
    	return lastPropagatedOutputValue;
    }

    public void setCurrentValue(boolean value) {
    	this.currentValue = value;
    }

    public void setLastPropagatedOutputValue(boolean value) {
    	this.lastPropagatedOutputValue = value;
    }

    /**
     * Returns a representation of the Component in .dot format.
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public abstract String toString();

    public abstract String bitString(int compValue, long compInfo, int[] connecTable);

    /**
     * Returns a configurable representation of the Component in .dot format.
     *
     * @param shape
     *            The value to use as the <tt>shape</tt> attribute.
     * @param fillcolor
     *            The value to use as the <tt>fillcolor</tt> attribute.
     * @param label
     *            The value to use as the <tt>label</tt> attribute.
     * @return A representation of the Component in .dot format.
     */
    protected String toDot(String shape, String fillcolor, String label)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("\"@" + Integer.toHexString(hashCode()) + "\"[shape=" + shape + ", style= filled, fillcolor=" + fillcolor + ", label=\"" + label + "\"]; ");
        for ( Component component : getOutputs_set() )
        {
            sb.append("\"@" + Integer.toHexString(hashCode()) + "\"->" + "\"@" + Integer.toHexString(component.hashCode()) + "\"; ");
        }

        return sb.toString();
    }

    public boolean get_current_value(int value) {
    	return (value & 0x8000) != 0;
    }

    public int numOutputs(long comp) {//inline these functions
    	return (int) ((comp & 0x00_0000_FFFF_000000L) >> 24);
    }

    public int numInputs(long comp) {
    	return (int) ((comp & 0x00_FFFF_0000_000000L) >> 40);
    }

}