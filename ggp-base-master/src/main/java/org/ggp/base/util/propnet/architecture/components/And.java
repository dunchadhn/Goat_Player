package org.ggp.base.util.propnet.architecture.components;

import java.util.Arrays;

import org.ggp.base.util.propnet.architecture.Component;

/**
 * The And class is designed to represent logical AND gates.
 */
@SuppressWarnings("serial")
public final class And extends Component
{
	private int num_threads = 48;
	private int T[] = new int[num_threads + 1];
	private int N = 0;
	/**
	 * Returns true if and only if every input to the and is true.
	 *
	 * @see org.ggp.base.util.propnet.architecture.Component#getValue()
	 */

	@Override
	public boolean getCurrentValue(int i)
	{
		if(N == 0) {
			N = getInputsSize();
		}
		if(T[i] == N) {
			return true;
		}
		return false;
	}

	@Override
	public boolean edit_T(boolean val, int i) {
		if(val) {
			T[i]++;
		}
		else {
			T[i]--;
		}
		return true;
	}

	@Override
	public boolean edit_TAll(boolean val, int i) {
		if(val) {
			for(int j = 0; j < num_threads + 1; ++j) {
				T[j]++;
			}
		}
		else {
			for(int j = 0; j < num_threads + 1; ++j) {
				T[j]--;
			}
		}
		return true;
	}

	@Override
	public boolean set(int val, int i) {
		T[i] = val;
		return true;
	}

	@Override
	public boolean setAll(int val, int i) {
		Arrays.fill(T, val);
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
		if(this.getCurrentValue(0)) {
			return toDot("invhouse", "red", "AND" + T + ", " + N);
		} else {
			return toDot("invhouse", "grey", "AND" + T + ", " + N);
		}
	}

}
