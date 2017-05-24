package org.ggp.base.util.propnet.architecture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ggp.base.util.Pair;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlProposition;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.gdl.grammar.GdlTerm;
import org.ggp.base.util.logging.GamerLogger;
import org.ggp.base.util.propnet.architecture.components.BitAnd;
import org.ggp.base.util.propnet.architecture.components.BitConstant;
import org.ggp.base.util.propnet.architecture.components.BitNot;
import org.ggp.base.util.propnet.architecture.components.BitOr;
import org.ggp.base.util.propnet.architecture.components.BitProposition;
import org.ggp.base.util.propnet.architecture.components.BitTransition;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.statemachine.Role;


/**
 * The PropNet class is designed to represent Propositional Networks.
 *
 * A propositional network (also known as a "propnet") is a way of representing
 * a game as a logic circuit. States of the game are represented by assignments
 * of TRUE or FALSE to "base" propositions, each of which represents a single
 * fact that can be true about the state of the game. For example, in a game of
 * Tic-Tac-Toe, the fact (cell 1 1 x) indicates that the cell (1,1) has an 'x'
 * in it. That fact would correspond to a base proposition, which would be set
 * to TRUE to indicate that the fact is true in the current state of the game.
 * Likewise, the base corresponding to the fact (cell 1 1 o) would be false,
 * because in that state of the game there isn't an 'o' in the cell (1,1).
 *
 * A state of the game is uniquely determined by the assignment of truth values
 * to the base propositions in the propositional network. Every assignment of
 * truth values to base propositions corresponds to exactly one unique state of
 * the game.
 *
 * Given the values of the base propositions, you can use the connections in
 * the network (AND gates, OR gates, NOT gates) to determine the truth values
 * of other propositions. For example, you can determine whether the terminal
 * proposition is true: if that proposition is true, the game is over when it
 * reaches this state. Otherwise, if it is false, the game isn't over. You can
 * also determine the value of the goal propositions, which represent facts
 * like (goal xplayer 100). If that proposition is true, then that fact is true
 * in this state of the game, which means that xplayer has 100 points.
 *
 * You can also use a propositional network to determine the next state of the
 * game, given the current state and the moves for each player. First, you set
 * the input propositions which correspond to each move to TRUE. Once that has
 * been done, you can determine the truth value of the transitions. Each base
 * proposition has a "transition" component going into it. This transition has
 * the truth value that its base will take on in the next state of the game.
 *
 * For further information about propositional networks, see:
 *
 * "Decomposition of Games for Efficient Reasoning" by Eric Schkufza.
 * "Factoring General Games using Propositional Automata" by Evan Cox et al.
 *
 * @author Sam Schreiber
 */

public final class XPropNet
{
	/** References to every component in the PropNet. */
	private final Set<BitComponent> components;

	/** References to every Proposition in the PropNet. */
	private final Set<BitProposition> propositions;

	/** References to every BaseProposition in the PropNet, indexed by name. */

	private final BitProposition[] basePropositions;

	/** References to every InputProposition in the PropNet, indexed by name. */
	private final BitProposition[] inputPropositions;

	/** References to every LegalProposition in the PropNet, indexed by role. */
	private final HashMap<Role, BitProposition[]> legalPropositions;

	/** References to every GoalProposition in the PropNet, indexed by role. */
	private final Map<Role, BitProposition[]> goalPropositions;

	/** A reference to the single, unique, InitProposition. */
	private final BitProposition initProposition;

	/** A reference to the single, unique, TerminalProposition. */
	private final BitProposition terminalProposition;

	private final HashMap< Pair<GdlTerm, GdlTerm>, Integer> inputMap;

	/** A helper list of all of the roles. */
	private final Role[] roles;

	private final HashMap<GdlSentence, Integer> bases;

	private final Map<BitProposition, BitProposition> legalInputMap;

	private final List<BitProposition> bs;


	public void addComponent(BitComponent c)
	{
		components.add(c);
		if (c instanceof BitProposition) propositions.add((BitProposition)c);
	}

	/**
	 * Creates a new PropNet from a list of Components, along with indices over
	 * those components.
	 *
	 * @param components
	 *            A list of Components.
	 */
	public XPropNet(PropNet prop)
	{

		components = propNet.getComponents();
        compInfo = propNet.getCompInfo();
        connecTable = propNet.getConnecTable();
        roles = propNet.getRoles();
        numBases = propNet.getBasePropositions().length;
        numInputs = propNet.getInputPropositions().length;
        numLegals = numInputs;
        baseOffset = propNet.getBaseOffset();
        legalOffset = propNet.getLegalOffset();
        inputOffset = propNet.getInputOffset();
        actions = propNet.getLegalPropositions();
        roleIndexMap = propNet.getRoleIndexMap();
        legalArray = propNet.getLegalArray();
        roleMoves = propNet.getRoleMoves();

		//COMPONENT VALUES WILL BE SET BY PROPNET
	    roles = (Role[]) prop.getRoles().toArray();

	    int compId = 0, total_outputs = 0;
		HashMap<BitComponent, Integer> idMap = new HashMap<BitComponent, Integer>();
		HashMap<Integer, BitComponent> indexMap = new HashMap<Integer, BitComponent>();
		for (BitComponent b : components) {
			idMap.put(b, compId);
			indexMap.put(compId, b);
			++compId;
			total_outputs += b.getOutputs_set().size();
		}

		long[] compInfo = new long[components.size()];
		int[] connecTable = new int[total_outputs];

		int connecIndex = 0;
		int[] intComponents = new int[components.size()];
		for (int i = 0; i < compId; ++i) {
			BitComponent b = indexMap.get(i);

			if (b instanceof BitAnd) {
				intComponents[i] = 0x80000000 - b.getInputs_set().size();
			} else if (b instanceof BitNot) {
				intComponents[i] = 0xFFFFFFFF;
			} else if (b instanceof BitConstant){
				if (b.getCurrentValue(0)) intComponents[i] = 0x10000000;
				else intComponents[i] = 0;
			} else {
				intComponents[i] = 0x7FFFFFFF;
			}

			//type
			long type = (long)0;
			long num_inputs = ((long)b.getInputs_set().size()) << (NUM_INPUT_BITS + NUM_OUTPUT_BITS + NUM_OFFSET_BITS);
			long num_outputs = ((long)b.getOutputs_set().size()) << (NUM_OUTPUT_BITS + NUM_OFFSET_BITS);
			long outputs_offset = connecIndex;
			long info = type ^ num_inputs ^ num_outputs ^ outputs_offset;

			System.out.println(Long.toBinaryString(type));
			System.out.println(Long.toBinaryString(num_inputs));
			System.out.println(Long.toBinaryString(num_outputs));
			System.out.println(Long.toBinaryString(outputs_offset));
			System.out.println(Long.toBinaryString(info));
			System.out.println();

			compInfo[i] = info;
			for (BitComponent out : b.getOutputs_set()) {
				int outIndex = idMap.get(out);
				connecTable[connecIndex++] = outIndex;
			}
		}

		List<Proposition> bases = new ArrayList<Proposition>(prop.getBasePropositions().values());
		HashMap<Proposition, Set<Component>> baseOutputs = new HashMap<Proposition, Set<Component>>();
		int compId = 0, total_outputs = 0;
		HashMap<Component, Integer> idMap = new HashMap<Component, Integer>();
		HashMap<Integer, Component> indexMap = new HashMap<Integer, Component>();
		for (Proposition b : bases) {
			idMap.put(b, compId);
			indexMap.put(compId, b);
			++compId;
			total_outputs += b.getOutputs_set().size();
			baseOutputs.put(b, b.getOutputs_set());
		}

	    //bases are in order
	    //inputs are in order
	    //legals are in order
	    int index = 0, output_index = 0;
	    int baseOffset = 0;
	    for (Proposition b : bases) {
	    	long type = 0;
	    	long num_inputs = 0;
	    	long num_outputs = 0;
	    	long outputs_offset = 0;
	    	long value = 0;

	    	compInfo[index++] = value;
	    	for (Component c : baseOutputs.get(b)) {

	    	}
	    }
		this.components = components;
		this.propositions = recordPropositions();

		bs = new ArrayList<BitProposition>(recordBasePropositions().values());
		List<BitProposition> i = new ArrayList<BitProposition>(recordInputPropositions().values());
		Map<Role, Set<BitProposition>> l = recordLegalPropositions();
		Map<Role, Set<BitProposition>> g = recordGoalPropositions();


		this.initProposition = recordInitProposition();
		this.terminalProposition = recordTerminalProposition();

		this.basePropositions = (BitProposition[]) bs.toArray(new BitProposition[bs.size()]);
		this.inputPropositions = (BitProposition[]) i.toArray(new BitProposition[i.size()]);
		this.legalPropositions = new HashMap<Role, BitProposition[]>();
		this.goalPropositions = new HashMap<Role, BitProposition[]>();
		this.inputMap = new HashMap< Pair<GdlTerm, GdlTerm>, Integer>();
		this.legalInputMap = makeLegalInputMap();

		for (int index = 0; index < inputPropositions.length; ++index) {
			BitProposition p = inputPropositions[index];
			Pair<GdlTerm, GdlTerm> pair = Pair.of(p.getName().getBody().get(0), p.getName().getBody().get(1));
			inputMap.put(pair, index);
		}

		for (Role r : l.keySet()) {
			Set<BitProposition> lval = l.get(r);
			this.legalPropositions.put(r, lval.toArray(new BitProposition[lval.size()]));
			Set<BitProposition> gval = g.get(r);
			this.goalPropositions.put(r, gval.toArray(new BitProposition[gval.size()]));
		}

		bases = new HashMap<GdlSentence, Integer>();
		for (int index = 0; index < basePropositions.length; ++index) {
			BitProposition p = basePropositions[index];
			bases.put(p.getName(), index);
		}
	}

	private static final int NUM_TYPE_BITS = 8;
	private static final int NUM_INPUT_BITS = 16;
	private static final int NUM_OUTPUT_BITS = 16;
	private static final int NUM_OFFSET_BITS = 24;
	public void XPropNet() {
		//System.out.println(String.format("0x%08X", 1));
		assert ((NUM_INPUT_BITS + NUM_OUTPUT_BITS + NUM_TYPE_BITS + NUM_OFFSET_BITS) == Long.SIZE);

		int compId = 0, total_outputs = 0;
		HashMap<BitComponent, Integer> idMap = new HashMap<BitComponent, Integer>();
		HashMap<Integer, BitComponent> indexMap = new HashMap<Integer, BitComponent>();
		for (BitComponent b : components) {
			idMap.put(b, compId);
			indexMap.put(compId, b);
			++compId;
			total_outputs += b.getOutputs_set().size();
		}

		long[] compInfo = new long[components.size()];
		int[] connecTable = new int[total_outputs];

		int connecIndex = 0;
		int[] intComponents = new int[components.size()];
		for (int i = 0; i < compId; ++i) {
			BitComponent b = indexMap.get(i);

			if (b instanceof BitAnd) {
				intComponents[i] = 0x80000000 - b.getInputs_set().size();
			} else if (b instanceof BitNot) {
				intComponents[i] = 0xFFFFFFFF;
			} else if (b instanceof BitConstant){
				if (b.getCurrentValue(0)) intComponents[i] = 0x10000000;
				else intComponents[i] = 0;
			} else {
				intComponents[i] = 0x7FFFFFFF;
			}

			//type
			long type = (long)0;
			long num_inputs = ((long)b.getInputs_set().size()) << (NUM_INPUT_BITS + NUM_OUTPUT_BITS + NUM_OFFSET_BITS);
			long num_outputs = ((long)b.getOutputs_set().size()) << (NUM_OUTPUT_BITS + NUM_OFFSET_BITS);
			long outputs_offset = connecIndex;
			long info = type ^ num_inputs ^ num_outputs ^ outputs_offset;

			System.out.println(Long.toBinaryString(type));
			System.out.println(Long.toBinaryString(num_inputs));
			System.out.println(Long.toBinaryString(num_outputs));
			System.out.println(Long.toBinaryString(outputs_offset));
			System.out.println(Long.toBinaryString(info));
			System.out.println();

			compInfo[i] = info;
			for (BitComponent out : b.getOutputs_set()) {
				int outIndex = idMap.get(out);
				connecTable[connecIndex++] = outIndex;
			}
		}
		System.exit(0);
	}

	public Map<BitProposition, BitProposition> getLegalInputMap()
	{
		return legalInputMap;
	}

	private Map<BitProposition, BitProposition> makeLegalInputMap() {
		Map<BitProposition, BitProposition> legalInputMap = new HashMap<BitProposition, BitProposition>();
		// Create a mapping from Body->Input.
		Map<List<GdlTerm>, BitProposition> inputPropsByBody = new HashMap<List<GdlTerm>, BitProposition>();
		int size = inputPropositions.length;
		for(int i = 0; i < size; ++i) {
			BitProposition inputProp = inputPropositions[i];
			List<GdlTerm> inputPropBody = (inputProp.getName()).getBody();
			inputPropsByBody.put(inputPropBody, inputProp);
		}
		// Use that mapping to map Input->Legal and Legal->Input
		// based on having the same Body proposition.
		for(BitProposition[] legalProps : legalPropositions.values()) {
			size = legalProps.length;
			for(int i = 0; i < size; ++i) {
				BitProposition legalProp = legalProps[i];
				List<GdlTerm> legalPropBody = (legalProp.getName()).getBody();
				if (inputPropsByBody.containsKey(legalPropBody)) {
    				BitProposition inputProp = inputPropsByBody.get(legalPropBody);
    				legalInputMap.put(inputProp, legalProp);
    				legalInputMap.put(legalProp, inputProp);
				}
			}
		}
		return legalInputMap;
	}

	public HashMap<GdlSentence, Integer> getBasesMap() {
		return bases;
	}
	public List<Role> getRoles()
	{
	    return roles;
	}


	public HashMap< Pair<GdlTerm, GdlTerm>, Integer> getInputMap() {
		return inputMap;
	}


	/**
	 * Getter method.
	 *
	 * @return References to every BaseProposition in the PropNet, indexed by
	 *         name.
	 */
	public BitProposition[] getBasePropositions()
	{
		return basePropositions;
	}


	/**
	 * Getter method.
	 *
	 * @return References to every Component in the PropNet.
	 */
	public Set<BitComponent> getComponents()
	{
		return components;
	}

	/**
	 * Getter method.
	 *
	 * @return References to every GoalProposition in the PropNet, indexed by
	 *         player name.
	 */
	public Map<Role, BitProposition[]> getGoalPropositions()
	{
		return goalPropositions;
	}

	/**
	 * Getter method. A reference to the single, unique, InitProposition.
	 *
	 * @return
	 */
	public BitProposition getInitProposition()
	{
		return initProposition;
	}

	/**
	 * Getter method.
	 *
	 * @return References to every InputProposition in the PropNet, indexed by
	 *         name.
	 */
	public BitProposition[] getInputPropositions()
	{
		return inputPropositions;
	}

	/**
	 * Getter method.
	 *
	 * @return References to every LegalProposition in the PropNet, indexed by
	 *         player name.
	 */
	public Map<Role, BitProposition[]> getLegalPropositions()
	{
		return legalPropositions;
	}

	/**
	 * Getter method.
	 *
	 * @return References to every Proposition in the PropNet.
	 */
	public Set<BitProposition> getPropositions()
	{
		return propositions;
	}

	/**
	 * Getter method.
	 *
	 * @return A reference to the single, unique, TerminalProposition.
	 */
	public BitProposition getTerminalProposition()
	{
		return terminalProposition;
	}

	/**
	 * Returns a representation of the PropNet in .dot format.
	 *
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();

		sb.append("digraph propNet\n{\n");
		for ( BitComponent component : components )
		{
			sb.append("\t" + component.toString() + "\n");
		}
		sb.append("}");

		return sb.toString();
	}

	/**
     * Outputs the propnet in .dot format to a particular file.
     * This can be viewed with tools like Graphviz and ZGRViewer.
     *
     * @param filename the name of the file to output to
     */
    public void renderToFile(String filename) {
        try {
            File f = new File(filename);
            FileOutputStream fos = new FileOutputStream(f);
            OutputStreamWriter fout = new OutputStreamWriter(fos, "UTF-8");
            fout.write(toString());
            fout.close();
            fos.close();
        } catch(Exception e) {
            GamerLogger.logStackTrace("StateMachine", e);
        }
    }

	/**
	 * Builds an index over the BasePropositions in the PropNet.
	 *
	 * This is done by going over every single-input proposition in the network,
	 * and seeing whether or not its input is a transition, which would mean that
	 * by definition the proposition is a base proposition.
	 *
	 * @return An index over the BasePropositions in the PropNet.
	 */
	private Map<GdlSentence, BitProposition> recordBasePropositions()
	{
		Map<GdlSentence, BitProposition> basePropositions = new HashMap<GdlSentence, BitProposition>();
		for (BitProposition proposition : propositions) {
		    // Skip all propositions without exactly one input.
		    if (proposition.getInputs_set().size() != 1)
		        continue;

		    BitComponent component = proposition.getSingleInput_set();
			if (component instanceof BitTransition) {
				basePropositions.put(proposition.getName(), proposition);
			}
		}

		return basePropositions;
	}

	/**
	 * Builds an index over the GoalPropositions in the PropNet.
	 *
	 * This is done by going over every function proposition in the network
     * where the name of the function is "goal", and extracting the name of the
     * role associated with that goal proposition, and then using those role
     * names as keys that map to the goal propositions in the index.
	 *
	 * @return An index over the GoalPropositions in the PropNet.
	 */
	private Map<Role, Set<BitProposition>> recordGoalPropositions()
	{
		Map<Role, Set<BitProposition>> goalPropositions = new HashMap<Role, Set<BitProposition>>();
		for (BitProposition proposition : propositions)
		{
		    // Skip all propositions that aren't GdlRelations.
		    if (!(proposition.getName() instanceof GdlRelation))
		        continue;

			GdlRelation relation = (GdlRelation) proposition.getName();
			if (!relation.getName().getValue().equals("goal"))
			    continue;

			Role theRole = new Role((GdlConstant) relation.get(0));
			if (!goalPropositions.containsKey(theRole)) {
				goalPropositions.put(theRole, new HashSet<BitProposition>());
			}
			goalPropositions.get(theRole).add(proposition);
		}

		return goalPropositions;
	}

	/**
	 * Returns a reference to the single, unique, InitProposition.
	 *
	 * @return A reference to the single, unique, InitProposition.
	 */
	private BitProposition recordInitProposition()
	{
		for (BitProposition proposition : propositions)
		{
		    // Skip all propositions that aren't GdlPropositions.
			if (!(proposition.getName() instanceof GdlProposition))
			    continue;

			GdlConstant constant = ((GdlProposition) proposition.getName()).getName();
			if (constant.getValue().toUpperCase().equals("INIT")) {
				return proposition;
			}
		}
		return null;
	}

	/**
	 * Builds an index over the InputPropositions in the PropNet.
	 *
	 * @return An index over the InputPropositions in the PropNet.
	 */
	private Map<GdlSentence, BitProposition> recordInputPropositions()
	{
		Map<GdlSentence, BitProposition> inputPropositions = new HashMap<GdlSentence, BitProposition>();
		for (BitProposition proposition : propositions)
		{
		    // Skip all propositions that aren't GdlFunctions.
			if (!(proposition.getName() instanceof GdlRelation))
			    continue;

			GdlRelation relation = (GdlRelation) proposition.getName();
			if (relation.getName().getValue().equals("does")) {
				inputPropositions.put(proposition.getName(), proposition);
			}
		}

		return inputPropositions;
	}

	/**
	 * Builds an index over the LegalPropositions in the PropNet.
	 *
	 * @return An index over the LegalPropositions in the PropNet.
	 */
	private Map<Role, Set<BitProposition>> recordLegalPropositions()
	{
		Map<Role, Set<BitProposition>> legalPropositions = new HashMap<Role, Set<BitProposition>>();
		for (BitProposition proposition : propositions)
		{
		    // Skip all propositions that aren't GdlRelations.
			if (!(proposition.getName() instanceof GdlRelation))
			    continue;

			GdlRelation relation = (GdlRelation) proposition.getName();
			if (relation.getName().getValue().equals("legal")) {
				GdlConstant name = (GdlConstant) relation.get(0);
				Role r = new Role(name);
				if (!legalPropositions.containsKey(r)) {
					legalPropositions.put(r, new HashSet<BitProposition>());
				}
				legalPropositions.get(r).add(proposition);
			}
		}

		return legalPropositions;
	}

	/**
	 * Builds an index over the Propositions in the PropNet.
	 *
	 * @return An index over Propositions in the PropNet.
	 */
	private Set<BitProposition> recordPropositions()
	{
		Set<BitProposition> propositions = new HashSet<BitProposition>();
		for (BitComponent component : components)
		{
			if (component instanceof BitProposition) {
				propositions.add((BitProposition) component);
			}
		}
		return propositions;
	}

	/**
	 * Records a reference to the single, unique, TerminalProposition.
	 *
	 * @return A reference to the single, unqiue, TerminalProposition.
	 */
	private BitProposition recordTerminalProposition()
	{
		for ( BitProposition proposition : propositions )
		{
			if ( proposition.getName() instanceof GdlProposition )
			{
				GdlConstant constant = ((GdlProposition) proposition.getName()).getName();
				if ( constant.getValue().equals("terminal") )
				{
					return proposition;
				}
			}
		}

		return null;
	}

	public int getSize() {
		return components.size();
	}

	public int getNumAnds() {
		int andCount = 0;
		for(BitComponent c : components) {
			if(c instanceof BitAnd)
				andCount++;
		}
		return andCount;
	}

	public int getNumOrs() {
		int orCount = 0;
		for(BitComponent c : components) {
			if(c instanceof BitOr)
				orCount++;
		}
		return orCount;
	}

	public int getNumNots() {
		int notCount = 0;
		for(BitComponent c : components) {
			if(c instanceof BitNot)
				notCount++;
		}
		return notCount;
	}

	public int getNumLinks() {
		int linkCount = 0;
		for(BitComponent c : components) {
			linkCount += c.getOutputs_set().size();
		}
		return linkCount;
	}

	/**
	 * Removes a component from the propnet. Be very careful when using
	 * this method, as it is not thread-safe. It is highly recommended
	 * that this method only be used in an optimization period between
	 * the propnet's creation and its initial use, during which it
	 * should only be accessed by a single thread.
	 *
	 * The INIT and terminal components cannot be removed.
	 */
	/*public void removeComponent(Component c) {


		//Go through all the collections it could appear in
		if(c instanceof Proposition) {
			Proposition p = (Proposition) c;
			GdlSentence name = p.getName();
			if(basePropositions.containsKey(name)) {
				basePropositions.remove(name);
			} else if(inputPropositions.containsKey(name)) {
				inputPropositions.remove(name);
				//The map goes both ways...
				Proposition partner = legalInputMap.get(p);
				if(partner != null) {
					legalInputMap.remove(partner);
					legalInputMap.remove(p);
				}
			} else if(name == GdlPool.getProposition(GdlPool.getConstant("INIT"))) {
				throw new RuntimeException("The INIT component cannot be removed. Consider leaving it and ignoring it.");
			} else if(name == GdlPool.getProposition(GdlPool.getConstant("terminal"))) {
				throw new RuntimeException("The terminal component cannot be removed.");
			} else {
				for(Set<Proposition> propositions : legalPropositions.values()) {
					if(propositions.contains(p)) {
						propositions.remove(p);
						Proposition partner = legalInputMap.get(p);
						if(partner != null) {
							legalInputMap.remove(partner);
							legalInputMap.remove(p);
						}
					}
				}
				for(Set<Proposition> propositions : goalPropositions.values()) {
					propositions.remove(p);
				}
			}
			propositions.remove(p);
		}
		components.remove(c);

		//Remove all the local links to the component
		for(Component parent : c.getInputs())
			parent.removeOutput(c);
		for(Component child : c.getOutputs())
			child.removeInput(c);
		//These are actually unnecessary...
		//c.removeAllInputs();
		//c.removeAllOutputs();
	}*/
}
