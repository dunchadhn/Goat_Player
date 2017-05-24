package org.ggp.base.util.propnet.architecture.components;

import org.ggp.base.util.propnet.architecture.Component;

/**
 * The Or class is designed to represent logical OR gates.
 */
@SuppressWarnings("serial")
public final class Or extends Component
{
	int T = 0;
	/**
	 * Returns true if and only if at least one of the inputs to the or is true.
	 *
	 * @see org.ggp.base.util.propnet.architecture.Component#getValue()
	 * */
	@Override
	public boolean getCurrentValue()
	{
		if(T != 0) {
			return true;
		}
		return false;
	}

	@Override
	public boolean edit_T(boolean val) {
		if(val) {
			T++;
		}
		else {
			T--;
		}
		return true;
	}

	@Override
	public boolean set(int val) {
		T = val;
		return true;
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
	 * @see org.ggp.base.util.propnet.architecture.Component#toString()
	 */
	@Override
	public String toString()
	{
		if(this.getCurrentValue()) {
			return toDot("ellipse", "red", "OR" + T);
		} else {
			return toDot("ellipse", "grey", "OR" + T);
		}
	}

	protected int num_set(int compValue, long compInfo) {
		return (int) (compValue - 0x7FFF);
	}

	@Override
	public String bitString(int compValue, long compInfo, int[] connecTable) {
		boolean currVal = get_current_value(compValue);
		return toDot("ellipse", (currVal ? "red" : "grey"), "OR" + num_set(compValue, compInfo) + ", " + numInputs(compInfo));
	}
}