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
	public boolean getCurrentValue(int i) {
		return value;
	}


	/**
	 * @see org.ggp.base.util.propnet.architecture.Component#toString()
	 */
	@Override
	public String toString()
	{
		return toDot("doublecircle", (this.getCurrentValue(0) ? "red" : "grey"), Boolean.toString(value).toUpperCase());
	}
}