package org.ggp.base.util.propnet.architecture.components;

import org.ggp.base.util.propnet.architecture.Component;

/**
 * The Transition class is designed to represent pass-through gates.
 */
@SuppressWarnings("serial")
public final class Transition extends Component
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
		if(this.getCurrentValue()) {
			return toDot("box", "red", "TRANSITION");
		} else {
			return toDot("box", "grey", "TRANSITION");
		}

	}

	@Override
	public String bitString(int compValue, long compInfo, int[] connecTable) {
		boolean currVal = get_current_value(compValue);
		return toDot("box", (currVal ? "red" : "grey"), "TRANSITION");
	}
}