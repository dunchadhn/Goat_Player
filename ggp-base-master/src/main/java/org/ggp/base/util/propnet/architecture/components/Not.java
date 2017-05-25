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
		if(!getSingleInput_set().getCurrentValue()) {
			return toDot("invtriangle", "red", "NOT");
		} else {
			return toDot("invtriangle", "grey", "NOT");
		}
	}


	@Override
	public String bitString(int compValue, long compInfo, int[] connecTable) {
		assert numInputs(compInfo) == 1;
		boolean currVal = get_current_value(compValue);
		if (currVal) {
			if (compValue != 0xFFFF_FFFF) {
				System.out.println("NOT is true but not 0xFFFF");
				System.exit(0);
			}
		}
		else {
			if (compValue != 0) {
				System.out.println("NOT is false but not 0");
				System.exit(0);
			}
		}
		return toDot("invtriangle", (currVal ? "red" : "grey"), "NOT");
	}
}