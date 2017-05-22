package org.ggp.base.util.propnet.architecture.components;

import org.ggp.base.util.propnet.architecture.BitComponent;

/**
 * The Transition class is designed to represent pass-through gates.
 */
@SuppressWarnings("serial")
public final class BitTransition extends BitComponent
{
	/**
	 * Returns the value of the input to the transition.
	 *
	 * @see org.ggp.base.util.propnet.architecture.Component#getValue()
	 */

	/**
	 * @see org.ggp.base.util.propnet.architecture.Component#toString()
	 */
	@Override
	public String toString()
	{
		if(this.getCurrentValue(0)) {
			return toDot("box", "red", "TRANSITION");
		} else {
			return toDot("box", "grey", "TRANSITION");
		}

	}
}