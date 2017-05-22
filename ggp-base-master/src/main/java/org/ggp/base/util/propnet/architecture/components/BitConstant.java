package org.ggp.base.util.propnet.architecture.components;

import org.ggp.base.util.propnet.architecture.BitComponent;

/**
 * The Constant class is designed to represent nodes with fixed logical values.
 */
@SuppressWarnings("serial")
public final class BitConstant extends BitComponent
{
	/** The value of the constant. */
	private final boolean value;

	/**
	 * Creates a new Constant with value <tt>value</tt>.
	 *
	 * @param value
	 *            The value of the Constant.
	 */
	public BitConstant(boolean value)
	{
		this.value = value;
	}

	@Override
	public BitComponent getSingleInput_arr() {
        return this;
    }

	@Override
	public BitComponent getSingleInput_set() {
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