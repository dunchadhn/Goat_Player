package org.ggp.base.util.propnet.architecture.components;

import org.ggp.base.util.propnet.architecture.Component;

/**
 * The And class is designed to represent logical AND gates.
 */
@SuppressWarnings("serial")
public final class And extends Component
{
	int T = 0;
	int N = 0;
	/**
	 * Returns true if and only if every input to the and is true.
	 *
	 * @see org.ggp.base.util.propnet.architecture.Component#getValue()
	 */

	@Override
	public boolean getCurrentValue()
	{
		if(N == 0) {
			N = getInputs_set().size();
		}
		if(T == N) {
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
			return toDot("invhouse", "red", "AND" + T + ", " + N);
		} else {
			return toDot("invhouse", "grey", "AND" + T + ", " + N);
		}
	}

	protected int num_set(int compValue, long compInfo) {
		return (int) (compValue - (0x8000_0000 - numInputs(compInfo)));
	}

	@Override
	public String bitString(int compValue, long compInfo, int[] connecTable) {
		boolean currVal = get_current_value(compValue);
		return toDot("invhouse", (currVal ? "red" : "grey"), "AND" + num_set(compValue, compInfo) + ", " + numInputs(compInfo));
	}



}
