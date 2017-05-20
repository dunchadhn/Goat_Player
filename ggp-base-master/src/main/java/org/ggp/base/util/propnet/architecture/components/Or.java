package org.ggp.base.util.propnet.architecture.components;

import org.ggp.base.util.propnet.architecture.Component;

/**
 * The Or class is designed to represent logical OR gates.
 */
@SuppressWarnings("serial")
public final class Or extends Component
{
	int T = 0;
	boolean value = false;
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


	public void edit_T(boolean val) {
		if(val) {
			T++;
		}
		else {
			T--;
		}
	}

	public void set(int val) {
		T = val;
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
}