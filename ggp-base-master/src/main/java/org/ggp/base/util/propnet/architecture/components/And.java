package org.ggp.base.util.propnet.architecture.components;

import org.ggp.base.util.propnet.architecture.Component;

/**
 * The And class is designed to represent logical AND gates.
 */
@SuppressWarnings("serial")
public final class And extends Component
{
	int T = 0;
	int N = getInputs().size();
	/**
	 * Returns true if and only if every input to the and is true.
	 *
	 * @see org.ggp.base.util.propnet.architecture.Component#getValue()
	 */
	@Override
	public boolean getValue()
	{
		for (Component c : this.getInputs())
			if (!c.getCurrentValue()) return false;
		return true;
		/*if(T == N) {
			return true;
		}
		return false;*/
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
			return toDot("invhouse", "red", "AND" + T);
		} else {
			return toDot("invhouse", "grey", "AND" + T);
		}
	}

}
