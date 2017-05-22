package org.ggp.base.util.propnet.architecture.components;

import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.propnet.architecture.BitComponent;

/**
 * The Proposition class is designed to represent named latches.
 */
@SuppressWarnings("serial")
public class BitProposition extends BitComponent
{
	/** The name of the Proposition. */
	private GdlSentence name;

	/**
	 * Creates a new Proposition with name <tt>name</tt>.
	 *
	 * @param name
	 * The name of the Proposition.
	 */
	public BitProposition(GdlSentence name)
	{
		this.name = name;
	}

	/**
	 * Getter method.
	 *
	 * @return The name of the Proposition.
	 */
	public GdlSentence getName()
	{
		return name;
	}

    /**
     * Setter method.
     *
     * This should only be rarely used; the name of a proposition
     * is usually constant over its entire lifetime.
     *
     * @return The name of the Proposition.
     */
    public void setName(GdlSentence newName)
    {
        name = newName;
    }

	/**
	 * Returns the current value of the Proposition.
	 *
	 * @see org.ggp.base.util.propnet.architecture.Component#getValue()
	 */
	/**
	 * Setter method.
	 *
	 * @param value
	 *            The new value of the Proposition.
	 */


	/**
	 * @see org.ggp.base.util.propnet.architecture.Component#toString()
	 */
	@Override
	public String toString()
	{
		return toDot("circle", getCurrentValue(0) ? "red" : "white", name.toString());
	}
}