package org.ggp.base.util.propnet.architecture.components;

import org.ggp.base.util.propnet.architecture.Component;

/**
 * The Constant class is designed to represent nodes with fixed logical values.
 */
@SuppressWarnings("serial")
public final class Constant extends Component
{
	/** The value of the constant. */
	private final boolean value;

	/**
	 * Creates a new Constant with value <tt>value</tt>.
	 *
	 * @param value
	 *            The value of the Constant.
	 */
	public Constant(boolean value)
	{
		this.value = value;
	}

	@Override
	public Component getSingleInput_arr() {
        return this;
    }

	@Override
	public Component getSingleInput_set() {
        return this;
    }

	/**
	 * Returns the value that the constant was initialized to.
	 *
	 * @see org.ggp.base.util.propnet.architecture.Component#getValue()
	 */

	@Override
	public boolean getCurrentValue() {
		return value;
	}


	/**
	 * @see org.ggp.base.util.propnet.architecture.Component#toString()
	 */
	@Override
	public String toString()
	{
		return toDot("doublecircle", (this.getCurrentValue() ? "red" : "grey"), Boolean.toString(value).toUpperCase());
	}

	protected int num_set(int compValue, long compInfo) {
		return (int) (compValue - 0x8000_0000);
	}

	@Override
	public String bitString(int compValue, long compInfo, int[] connecTable) {
		boolean currVal = get_current_value(compValue);
		return toDot("doublecircle", (currVal ? "red" : "grey"), Boolean.toString(currVal).toUpperCase() + num_set(compValue, compInfo) + ", " + numInputs(compInfo));
	}
}