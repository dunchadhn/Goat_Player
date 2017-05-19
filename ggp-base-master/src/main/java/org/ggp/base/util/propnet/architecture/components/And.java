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
	public boolean getValue() {
		return false;
	}
	@Override
	public boolean getCurrentValue()
	{
		/*for (Component c : this.getInputs())
			if (!c.getCurrentValue()) return false;
		return true;*/
		if(N == 0) {
			N = getInputs().size();
		}
		if(T == N) {
			return true;
		}
		return false;
	}

	@Override
	public void setCurrentValue(boolean val) {
		setValFromSubClass(getCurrentValue());
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

	public void set(int val) {
		T = val;
	}

	/**
	 * @see org.ggp.base.util.propnet.architecture.Component#toString()
	 */
	@Override
	public String toString()
	{
		if(this.getValue()) {
			return toDot("invhouse", "red", "AND" + T + ", " + N);
		} else {
			return toDot("invhouse", "grey", "AND" + T + ", " + N);
		}
	}

}
