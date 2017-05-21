package org.ggp.base.util.propnet.architecture.components;

import org.ggp.base.util.propnet.architecture.Component;

/**
 * The Not class is designed to represent logical NOT gates.
 */
@SuppressWarnings("serial")
public final class Not extends Component
{
	/**
	 * Returns the inverse of the input to the not.
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
			return toDot("invtriangle", "red", "NOT");
		} else {
			return toDot("invtriangle", "grey", "NOT");
		}
	}
}