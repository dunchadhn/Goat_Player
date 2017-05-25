package org.ggp.base.util.propnet.architecture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
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
import org.ggp.base.util.logging.GamerLogger;
import org.ggp.base.util.propnet.architecture.components.And;
import org.ggp.base.util.propnet.architecture.components.Constant;
import org.ggp.base.util.propnet.architecture.components.Not;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.architecture.components.Transition;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;



public final class XPropNet
{

	private static final int NUM_TYPE_BITS = 8;
	private static final int NUM_INPUT_BITS = 16;
	private static final int NUM_OUTPUT_BITS = 16;
	private static final int NUM_OFFSET_BITS = 24;

	private static final int OUTPUT_SHIFT = NUM_OFFSET_BITS;
	private static final int INPUT_SHIFT = OUTPUT_SHIFT + NUM_OUTPUT_BITS;
	private static final int TYPE_SHIFT = INPUT_SHIFT + NUM_INPUT_BITS;


	private static final long NOT_TRIGGER = 0;
	private static final long TRIGGER_TRANSITION = 0xC0_0000_0000_000000L;
	private static final long TRIGGER_LEGAL = 0x80_0000_0000_000000L;
	private static final int INIT_TRUE = 0x8000_0000;
	private static final int INIT_FALSE = 0;
	private static final int INIT_NOT = 0xFFFF_FFFF;
	private static final int INIT_DEFAULT = 0x7FFF_FFFF;

	private int numBases, baseOffset, numLegals, numInputs, legalOffset, inputOffset;
	private final Role[] roles;
	private int initProposition, terminalProposition;
    private int[] basePropositions;
    private int[] inputPropositions;
    private int[] constants;
    private int[] components;
    private long[] compInfo;
    private int[] connecTable;
    private HashMap<Role, int[]> goalPropositions;
    private HashMap<Role, List<Move>> actionsMap;
    private HashMap<Integer, GdlSentence> gdlSentenceMap;
    private HashMap<GdlSentence, Integer> basesMap;
    private HashMap<Integer, Integer> rolesIndexMap;
    private Move[] legalArray;
    private List<HashMap<Move, Integer>> roleMoves;

    PropNet oldProp;
    HashMap<Component, Integer> compIndexMap;


	public XPropNet(PropNet prop)
	{
		System.out.println("XPropNet initializing...");
		oldProp = prop;
		Set<Component> pComponents = prop.getComponents();
	    roles = prop.getRoles().toArray(new Role[prop.getRoles().size()]);

		Map<Role, Set<Proposition>> moveMap = prop.getLegalPropositions();
		//Mapping from component to component ID
		HashMap<Component, Integer> compIndices = new HashMap<Component, Integer>();
		int compId = 0, total_outputs = 0;

		//Components that we have already processed
		HashSet<Component> props = new HashSet<Component>(prop.getBasePropositions().values());
		props.addAll(prop.getInputPropositions().values());
		rolesIndexMap = new HashMap<Integer, Integer>();



/*
 * Define Proposition ordering for Bases. Populate gdlSentenceMap (mapping from GdlSentence to component ID) and
 * basesMap (mapping from component ID to GdlSentence for bases). Set numBases and baseOffset into components
 * and compInfo array. Define component IDs for bases and increment total outputs.
 */
		List<Proposition> bases = new ArrayList<Proposition>();
		numBases = 0; baseOffset = 0;
		gdlSentenceMap = new HashMap<Integer, GdlSentence>();//Mapping from compId to GdlSentence for bases
		basesMap = new HashMap<GdlSentence, Integer>();//Mapping from GdlSentence to compId
		for (Entry<GdlSentence, Proposition> e : prop.getBasePropositions().entrySet()) {
			GdlSentence s = e.getKey();
			Proposition b = e.getValue();

			gdlSentenceMap.put(compId, s);
			basesMap.put(s, compId - baseOffset);
			compIndices.put(b, compId);
			++compId;
			bases.add(b);
			++numBases;
			total_outputs += b.getOutputs_set().size();
		}


/*
 * Define Proposition ordering for Legals. The ordering will be in 1-1 correspondence with the
 * ordering of inputs. Set numLegals and legalOffset.
 */

		List<Proposition> inputs = new ArrayList<Proposition>();
		Map<Proposition, Proposition> legalInputMap = prop.getLegalInputMap();
		List<List<Proposition>> legals  = new ArrayList<List<Proposition>>();
		List<Move> legalArr = new ArrayList<Move>();//List of all moves in the game, in order of role
		actionsMap = new HashMap<Role, List<Move>>();
		rolesIndexMap = new HashMap<Integer, Integer>();
		numLegals = 0; legalOffset = compId;
		for (int i = 0; i < roles.length; ++i) {
			List<Proposition> rLegals = new ArrayList<Proposition>(moveMap.get(roles[i]));
			rolesIndexMap.put(i, compId);
			List<Move> rMoves = new ArrayList<Move>();
			rolesIndexMap.put(i, compId);

			for (Proposition l : rLegals) {
				Move m = new Move(l.getName().getBody().get(1));
				legalArr.add(m);
				rMoves.add(m);
				Proposition in = legalInputMap.get(l);
				inputs.add(in); //Ensures that legal move list exactly corresponds to input list
				props.add(in);
				compIndices.put(l, compId);
				++compId;
				total_outputs += l.getOutputs_set().size();
				total_outputs += in.getOutputs_set().size();
			}

			actionsMap.put(roles[i], rMoves);
			numLegals += rLegals.size();
			numInputs += rLegals.size();
			props.addAll(rLegals);
			legals.add(rLegals);
		}


		inputOffset = compId;
		for (Proposition i : inputs) {
			compIndices.put(i, compId);
			++compId;
		}

		legalArray = legalArr.toArray(new Move[legalArr.size()]);
		if (numLegals != numInputs) {
			System.out.println("numLegals != numInputs");
			System.exit(0);
		}


///////////////////////////

		Proposition init = prop.getInitProposition();
		if (init == null) initProposition = -1;
		Proposition term = prop.getTerminalProposition();
		HashMap<Component, List<Component>> outputMap = new HashMap<Component, List<Component>>();
		for (Component c : prop.getComponents()) {
			outputMap.put(c, new ArrayList<Component>(c.getOutputs_set()));
			if (c.equals(init)) initProposition = compId;
			if (c.equals(term)) terminalProposition = compId;

			if (!props.contains(c)) {
				props.add(c);
				compIndices.put(c, compId);
				++compId;
				total_outputs += c.getOutputs_set().size();
			}
		}

		compIndexMap = compIndices;

//Add bases, inputs, legals to props
		props = new HashSet<Component>(prop.getBasePropositions().values());
		props.addAll(prop.getInputPropositions().values());
		for (int i = 0; i < roles.length; ++i) {
			props.addAll(legals.get(i));
		}

		if (compId != pComponents.size()) {
			System.out.println("compId != pComponents.size()");
			System.exit(0);
		}

		connecTable = new int[total_outputs];
		components = new int[pComponents.size()];
		compInfo = new long[pComponents.size()];
		int outputIndex = 0;


		basePropositions = new int[numBases];
		for (int i = 0; i < numBases; ++i) {
			basePropositions[i] = baseOffset + i;
			Proposition b = bases.get(i);
			if (compIndices.get(b) != (baseOffset + i)) {
				System.out.println("compIndices.get(b) != (baseOffset + i)");
				System.exit(0);
			}
		}

		for (Proposition b : bases) {
			long type = NOT_TRIGGER;
			long num_inputs = ((long)b.getInputs_set().size()) << INPUT_SHIFT;
			List<Component> outputs = outputMap.get(b);
			long num_outputs = ((long)outputs.size()) << OUTPUT_SHIFT;
			long outIndex = ((long)outputIndex);
			long info = type | num_inputs | num_outputs | outIndex;
			compInfo[compIndices.get(b)] = info;
			components[compIndices.get(b)] = INIT_DEFAULT;
			for (Component out : outputs) {
				connecTable[outputIndex] = compIndices.get(out);
				++outputIndex;
			}
		}

		inputPropositions = new int[numInputs];
		for (int i = 0; i < numInputs; ++i) {
			inputPropositions[i] = inputOffset + i;
			Proposition b = inputs.get(i);
			if (compIndices.get(b) != (inputOffset + i)) {
				System.out.println("compIndices.get(b) != (inputOffset + i)");
				System.exit(0);
			}
		}


		roleMoves = new ArrayList<HashMap<Move, Integer>>();
		for (int i = 0; i < roles.length; ++i) {
			List<Proposition> ls = legals.get(i);
			props.addAll(ls);
			HashMap<Move, Integer> mMap = new HashMap<Move, Integer>();

			for (Proposition l : ls) {
				mMap.put(new Move(l.getName().getBody().get(1)), compIndices.get(l) - legalOffset);
				int inIndex = compIndices.get(legalInputMap.get(l)) - inputOffset;
				if (inIndex != (compIndices.get(l) - legalOffset)) {
					System.out.println("inIndex != (compIndices.get(l) - legalOffset)");
					System.exit(0);
				}

				long type = TRIGGER_LEGAL;
				long num_inputs = ((long)l.getInputs_set().size()) << INPUT_SHIFT;
				List<Component> outputs = outputMap.get(l);
				long num_outputs = ((long)outputs.size()) << OUTPUT_SHIFT;
				long outIndex = ((long)outputIndex);
				long info = type | num_inputs | num_outputs | outIndex;
				compInfo[compIndices.get(l)] = info;
				components[compIndices.get(l)] = INIT_DEFAULT;
				for (Component out : outputs) {
					connecTable[outputIndex] = compIndices.get(out);
					++outputIndex;
				}
			}
			roleMoves.add(mMap);
		}

		for (Proposition i : inputs) {
			long type = NOT_TRIGGER;
			long num_inputs = ((long)i.getInputs_set().size()) << INPUT_SHIFT;
			List<Component> outputs = outputMap.get(i);
			long num_outputs = ((long)outputs.size()) << OUTPUT_SHIFT;
			long outIndex = ((long)outputIndex);
			long info = type | num_inputs | num_outputs | outIndex;
			compInfo[compIndices.get(i)] = info;
			components[compIndices.get(i)] = INIT_DEFAULT;
			for (Component out : outputs) {
				connecTable[outputIndex] = compIndices.get(out);
				++outputIndex;
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
				long info = type | num_inputs | num_outputs | outIndex;
				compInfo[compIndices.get(c)] = info;
				components[compIndices.get(c)] = INIT_DEFAULT;
				for (Component out : outputs) {
					connecTable[outputIndex] = compIndices.get(out);
					++outputIndex;
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
				long info = type | num_inputs | num_outputs | outIndex;
				compInfo[compIndices.get(g)] = info;
				components[compIndices.get(g)] = INIT_DEFAULT;
				rewards[i] = compIndices.get(g);
				++i;
				for (Component out : outputs) {
					connecTable[outputIndex] = compIndices.get(out);
					++outputIndex;
				}
			}
			goalPropositions.put(r, rewards);
		}

		List<Integer> consts = new ArrayList<Integer>();
		for (Component c : prop.getComponents()) {
			if (c instanceof Constant) consts.add(compIndices.get(c));
			if (!props.contains(c)) {
				if (c instanceof And) {
					components[compIndices.get(c)] = INIT_TRUE - c.getInputs_set().size();
				} else if (c instanceof Not) {
					components[compIndices.get(c)] = INIT_NOT;
				} else if (c instanceof Constant) {
					components[compIndices.get(c)] = c.getCurrentValue() ? INIT_TRUE : INIT_FALSE;
				} else if (c.equals(init)) {
					if (compIndices.get(init) != initProposition) {
						System.out.println("init compId incorrect");
						System.exit(0);
					}
					components[compIndices.get(c)] = INIT_TRUE;
				} else {
					components[compIndices.get(c)] = INIT_DEFAULT;
				}
				long type = NOT_TRIGGER;
				long num_inputs = ((long)c.getInputs_set().size()) << INPUT_SHIFT;
				List<Component> outputs = outputMap.get(c);
				long num_outputs = ((long)outputs.size()) << OUTPUT_SHIFT;
				long outIndex = ((long)outputIndex);
				long info = type | num_inputs | num_outputs | outIndex;
				compInfo[compIndices.get(c)] = info;
				for (Component out : outputs) {
					connecTable[outputIndex] = compIndices.get(out);
					++outputIndex;
				}

			}
		}
		constants = new int[consts.size()];
		for (int i = 0; i < constants.length; ++i) constants[i] = consts.get(i);

		checkPropNet();


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

	public HashMap<GdlSentence, Integer> getBasesMap() {
		return basesMap;
	}

	public HashMap<Integer, Integer> getRolesIndexMap() {
		return rolesIndexMap;
	}

	public List<HashMap<Move, Integer>> getRoleMoves() {
		return roleMoves;
	}


	public int numOutputs(long comp) {//inline these functions
    	return (int) ((comp & 0x00_0000_FFFF_000000L) >> 24);
    }

    public int numInputs(long comp) {
    	return (int) ((comp & 0x00_FFFF_0000_000000L) >> 40);
    }

    public void checkPropNet() {
    	for ( Component component : compIndexMap.keySet())
		{
			int index = compIndexMap.get(component);
			if (numInputs(compInfo[index]) != component.getInputs_set().size()) {
				System.out.println(component);
				System.out.println("NumInputs incorrect: " + "Correct: " + component.getInputs_set().size() + " Incorrect: " + numInputs(compInfo[index]));
				String hex = Long.toHexString(compInfo[index]);
				String pad = "";
				for (int i = 0; i < 16 - hex.length(); ++i) pad += "0";
				hex = "0x" + pad + hex;
				System.out.println("compInfo: " + hex);
				System.exit(0);
			}
			if (numOutputs(compInfo[index]) != component.getOutputs_set().size()) {
				System.out.println(component);
				System.out.println("NumOutputs incorrect: " + "Correct: " + component.getOutputs_set().size() + " Incorrect: " + component.getOutputs_set().size());
				String hex = Long.toHexString(compInfo[index]);
				String pad = "";
				for (int i = 0; i < 16 - hex.length(); ++i) pad += "0";
				hex = "0x" + pad + hex;
				System.out.println("compInfo: " + hex);
				System.exit(0);
			}
		}
		System.out.println("CORRECT!");
    }


	public String bitString(int[] comps)
	{
		StringBuilder sb = new StringBuilder();

		sb.append("digraph propNet\n{\n");
		for ( Component component : compIndexMap.keySet())
		{
			int index = compIndexMap.get(component);
			sb.append("\t" + component.bitString(comps[index], compInfo[index], connecTable) + "\n");

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
    public void renderToFile(String filename, int[] comps) {
        try {
            File f = new File(filename);
            FileOutputStream fos = new FileOutputStream(f);
            OutputStreamWriter fout = new OutputStreamWriter(fos, "UTF-8");
            fout.write(bitString(comps));
            fout.close();
            fos.close();
        } catch(Exception e) {
            GamerLogger.logStackTrace("StateMachine", e);
        }
        //oldProp.renderToFile("old" + filename);
    }

}





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

