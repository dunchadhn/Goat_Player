package org.ggp.base.util.propnet.architecture.components;

import java.util.Arrays;

import org.ggp.base.util.propnet.architecture.BitComponent;

/**
 * The Or class is designed to represent logical OR gates.
 */
@SuppressWarnings("serial")
public final class BitOr extends BitComponent
{
	private int num_threads = 48;
	private int T[] = new int[num_threads + 1];
	/**
	 * Returns true if and only if at least one of the inputs to the or is true.
	 *
	 * @see org.ggp.base.util.propnet.architecture.Component#getValue()
	 * */
	@Override
	public boolean getCurrentValue(int i)
	{
		if(T[i] != 0) {
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
	public BitComponent getSingleInput_arr() {
        return this;
    }

	@Override
	public BitComponent getSingleInput_set() {
        return this;
    }

	/**
	 * @see org.ggp.base.util.propnet.architecture.Component#toString()
	 */
	@Override
	public String toString()
	{
		if(this.getCurrentValue(0)) {
			return toDot("ellipse", "red", "OR" + T);
		} else {
			return toDot("ellipse", "grey", "OR" + T);
		}
	}
}