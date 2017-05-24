package org.ggp.base.util.propnet.architecture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.propnet.architecture.components.And;
import org.ggp.base.util.propnet.architecture.components.Constant;
import org.ggp.base.util.propnet.architecture.components.Not;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.architecture.components.Transition;
import org.ggp.base.util.statemachine.Move;
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

	private static final int NUM_TYPE_BITS = 8;
	private static final int NUM_INPUT_BITS = 16;
	private static final int NUM_OUTPUT_BITS = 16;
	private static final int NUM_OFFSET_BITS = 24;

	private static final int OUTPUT_SHIFT = NUM_OFFSET_BITS;
	private static final int INPUT_SHIFT = OUTPUT_SHIFT + NUM_OUTPUT_BITS;
	private static final int TYPE_SHIFT = INPUT_SHIFT + NUM_INPUT_BITS;

	private static final long TYPE_MASK = 0xF0000000;
	private static final long INPUTS_MASK = 0x0FF00000;
	private static final long OUTPUTS_MASK = 0x000FF000;
	private static final long OFFSET_MASK = 0x00000FFF;

	private static final long NOT_TRIGGER = 0;
	private static final long TRIGGER_TRANSITION = 0xC000;
	private static final long TRIGGER_LEGAL = 0x8000;
	private static final int INIT_TRUE = 0x8000;
	private static final int INIT_FALSE = 0;
	private static final int INIT_NOT = 0xFFFF;
	private static final int INIT_DEFAULT = 0x7FFF;

	private int numBases, baseOffset, numLegals, numInputs, legalOffset, inputOffset;
	private final Role[] roles;
	private int initProposition, terminalProposition;
    private int[] basePropositions;
    private int[] inputPropositions;
    private int[] legalPropositions;
    private int[] constants;
    private int[] components;
    private long[] compInfo;
    private int[] connecTable;
    private HashMap<Role, int[]> goalPropositions;
    private HashMap<Role, List<Move>> actionsMap;
    private HashMap<Integer, GdlSentence> gdlSentenceMap;
    private Move[] legalArray;


	public XPropNet(PropNet prop)
	{
		Set<Component> pComponents = prop.getComponents();
	    roles = (Role[]) prop.getRoles().toArray();

		Map<Role, Set<Proposition>> moveMap = prop.getLegalPropositions();
		HashMap<Component, Integer> compIndices = new HashMap<Component, Integer>();
		int compId = 0, total_outputs = 0;

		HashSet<Component> props = new HashSet<Component>(prop.getBasePropositions().values());
		props.addAll(prop.getInputPropositions().values());

		baseOffset = 0;
		List<Proposition> bases = new ArrayList<Proposition>(prop.getBasePropositions().values());
		numBases = bases.size();
		for (Entry<GdlSentence, Proposition> e : prop.getBasePropositions().entrySet()) {
			Proposition b = e.getValue();
			bases.add(b);
			gdlSentenceMap.put(compId, e.getKey());
			compIndices.put(b, compId++);
			total_outputs += b.getOutputs_set().size();
		}

		inputOffset = compId;
		List<Proposition> inputs = new ArrayList<Proposition>(prop.getInputPropositions().values());
		numInputs = inputs.size();
		for (Proposition i : inputs) {
			compIndices.put(i, compId++);
			total_outputs += i.getOutputs_set().size();
		}

		actionsMap = new HashMap<Role, List<Move>>();
		legalOffset = compId;
		List<List<Proposition>> legals  = new ArrayList<List<Proposition>>();
		numLegals = 0;
		for (int i = 0; i < roles.length; ++i) {
			legals.add(new ArrayList<Proposition>(moveMap.get(roles[i])));
			List<Move> moves = new ArrayList<Move>();
			for (Proposition l : legals.get(i)) {
				compIndices.put(l, compId++);
				total_outputs += l.getOutputs_set().size();
				System.out.println(l.getName().getBody().get(1));
				moves.add(new Move(l.getName().getBody().get(1)));
			}
			numLegals += legals.get(i).size();
			props.addAll(moveMap.get(roles[i]));
		}
		assert numLegals == numInputs;

		Proposition init = prop.getInitProposition();
		Proposition term = prop.getTerminalProposition();
		HashMap<Component, List<Component>> outputMap = new HashMap<Component, List<Component>>();
		for (Component c : prop.getComponents()) {
			outputMap.put(c, new ArrayList<Component>(c.getOutputs_set()));
			if (c == init) initProposition = compId;
			if (c == term) terminalProposition = compId;
			if (!props.contains(c)) {
				compIndices.put(c, compId++);
				total_outputs += c.getOutputs_set().size();
			}
		}

		props = new HashSet<Component>(prop.getBasePropositions().values());
		props.addAll(prop.getInputPropositions().values());
		for (int i = 0; i < roles.length; ++i) {
			props.addAll(moveMap.get(roles[i]));
		}

		connecTable = new int[total_outputs];
		components = new int[pComponents.size()];
		compInfo = new long[pComponents.size()];
		int outputIndex = 0;

		basePropositions = new int[numBases];
		for (int i = 0; i < numBases; ++i) basePropositions[i] = baseOffset + i;
		for (Proposition b : bases) {
			long type = NOT_TRIGGER;
			long num_inputs = ((long)b.getInputs_set().size()) << INPUT_SHIFT;
			List<Component> outputs = outputMap.get(b);
			long num_outputs = ((long)outputs.size()) << OUTPUT_SHIFT;
			long outIndex = ((long)outputIndex);
			long info = type ^ num_inputs ^ num_outputs ^ outIndex;
			compInfo[compIndices.get(b)] = info;
			components[compIndices.get(b)] = INIT_DEFAULT;
			for (Component out : outputs) {
				connecTable[outputIndex++] = compIndices.get(out);
			}
		}

		inputPropositions = new int[numInputs];
		for (int i = 0; i < numInputs; ++i) inputPropositions[i] = inputOffset + i;
		for (Proposition i : inputs) {
			long type = NOT_TRIGGER;
			long num_inputs = ((long)i.getInputs_set().size()) << INPUT_SHIFT;
			List<Component> outputs = outputMap.get(i);
			long num_outputs = ((long)outputs.size()) << OUTPUT_SHIFT;
			long outIndex = ((long)outputIndex);
			long info = type ^ num_inputs ^ num_outputs ^ outIndex;
			compInfo[compIndices.get(i)] = info;
			components[compIndices.get(i)] = INIT_DEFAULT;
			for (Component out : outputs) {
				connecTable[outputIndex++] = compIndices.get(out);
			}
		}
		for (int i = 0; i < roles.length; ++i) {
			List<Proposition> ls = legals.get(i);
			props.addAll(ls);
			for (Proposition l : ls) {
				long type = TRIGGER_LEGAL;
				long num_inputs = ((long)l.getInputs_set().size()) << INPUT_SHIFT;
				List<Component> outputs = outputMap.get(l);
				long num_outputs = ((long)outputs.size()) << OUTPUT_SHIFT;
				long outIndex = ((long)outputIndex);
				long info = type ^ num_inputs ^ num_outputs ^ outIndex;
				compInfo[compIndices.get(l)] = info;
				components[compIndices.get(i)] = INIT_DEFAULT;
				for (Component out : outputs) {
					connecTable[outputIndex++] = compIndices.get(out);
				}
			}
		}

		for (Component c : prop.getComponents()) {
			if (c instanceof Transition) {
				props.add(c);
				long type = TRIGGER_TRANSITION;
				long num_inputs = ((long)c.getInputs_set().size()) << INPUT_SHIFT;
				List<Component> outputs = outputMap.get(c);
				long num_outputs = ((long)outputs.size()) << OUTPUT_SHIFT;
				long outIndex = ((long)outputIndex);
				long info = type ^ num_inputs ^ num_outputs ^ outIndex;
				compInfo[compIndices.get(c)] = info;
				components[compIndices.get(c)] = INIT_DEFAULT;
				for (Component out : outputs) {
					connecTable[outputIndex++] = compIndices.get(out);
				}

			}
		}

		Map<Role, Set<Proposition>> goalProps = prop.getGoalPropositions();
		goalPropositions = new HashMap<Role, int[]>();
		for (Role r : goalProps.keySet()) {
			int[] rewards = new int[goalProps.get(r).size()];
			int i = 0;
			for (Proposition g : goalProps.get(r)) {
				props.add(g);
				GdlRelation relation = (GdlRelation) g.getName();
		        GdlConstant constant = (GdlConstant) relation.get(1);
		        int goalVal = Integer.parseInt(constant.toString());
				long type = ((long)goalVal) << TYPE_SHIFT;
				long num_inputs = ((long)g.getInputs_set().size()) << INPUT_SHIFT;
				List<Component> outputs = outputMap.get(g);
				long num_outputs = ((long)outputs.size()) << OUTPUT_SHIFT;
				long outIndex = ((long)outputIndex);
				long info = type ^ num_inputs ^ num_outputs ^ outIndex;
				compInfo[compIndices.get(g)] = info;
				components[compIndices.get(g)] = INIT_DEFAULT;
				rewards[i++] = compIndices.get(g);
				for (Component out : outputs) {
					connecTable[outputIndex++] = compIndices.get(out);
				}
			}
		}

		List<Integer> consts = new ArrayList<Integer>();
		for (Component c : prop.getComponents()) {
			if (c instanceof Constant) consts.add(compIndices.get(c));
			if (!props.contains(c)) {
				if (c instanceof And) {
					components[compIndices.get(c)] = INIT_TRUE - c.getInputs_set().size();
				} else if (c instanceof Not) {
					components[compIndices.get(c)] = INIT_NOT;
				} else {
					components[compIndices.get(c)] = INIT_DEFAULT;
				}
				long type = NOT_TRIGGER;
				long num_inputs = ((long)c.getInputs_set().size()) << INPUT_SHIFT;
				List<Component> outputs = outputMap.get(c);
				long num_outputs = ((long)outputs.size()) << OUTPUT_SHIFT;
				long outIndex = ((long)outputIndex);
				long info = type ^ num_inputs ^ num_outputs ^ outIndex;
				compInfo[compIndices.get(c)] = info;

			}
		}
		constants = consts.toArray();

		roleIndexMap = propNet.getRoleIndexMap();
        roleMoves = propNet.getRoleMoves();

	}

	public int[] getComponents() {
		return components.clone();
	}

	public int[] getBasePropositions() {
		return basePropositions;
	}

	public int[] getInputPropositions() {
		return inputPropositions;
	}

	public int getInitProposition() {
		return initProposition;
	}

	public int getTerminalProposition() {
		return terminalProposition;
	}

	public HashMap<Role, int[]> getGoalPropositions() {
		return goalPropositions;
	}

	public long[] getCompInfo() {
		return compInfo;
	}

	public int[] getConnecTable() {
		return connecTable;
	}

	public Role[] getRoles() {
		return roles;
	}

	public int numBases() {
		return numBases;
	}

	public int numInputs() {
		return numInputs;
	}

	public int numLegals() {
		return numLegals;
	}

	public int getBaseOffset() {
		return baseOffset;
	}

	public int getInputOffset() {
		return inputOffset;
	}

	public int getLegalOffset() {
		return legalOffset;
	}

	public HashMap<Role, List<Move>> getActionsMap() {
		return actionsMap;
	}

	public Move[] getLegalArray() {
		return legalArray;
	}

	public int[] getConstants() {
		return constants;
	}

	public HashMap<Integer, GdlSentence> getGdlSentenceMap() {
		return gdlSentenceMap;
	}


}








	/*private Map<BitProposition, BitProposition> makeLegalInputMap() {
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
	}*/

/*
	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();

		sb.append("digraph propNet\n{\n");
		for (int i = 0; i < components.length; ++i)
		{
			sb.append("\t" + i + "\n");
		}
		sb.append("}");

		return sb.toString();
	}
*/
	/**
     * Outputs the propnet in .dot format to a particular file.
     * This can be viewed with tools like Graphviz and ZGRViewer.
     *
     * @param filename the name of the file to output to
     */
   /* public void renderToFile(String filename) {
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
    }*/


	/*public int getSize() {
		return components.length;
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
	}*/

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

