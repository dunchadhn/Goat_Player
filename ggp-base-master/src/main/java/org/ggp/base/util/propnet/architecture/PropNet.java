package org.ggp.base.util.propnet.architecture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ggp.base.util.Pair;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlPool;
import org.ggp.base.util.gdl.grammar.GdlProposition;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.gdl.grammar.GdlTerm;
import org.ggp.base.util.logging.GamerLogger;
import org.ggp.base.util.propnet.architecture.components.And;
import org.ggp.base.util.propnet.architecture.components.Constant;
import org.ggp.base.util.propnet.architecture.components.Not;
import org.ggp.base.util.propnet.architecture.components.Or;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.architecture.components.Transition;
import org.ggp.base.util.statemachine.Role;

import com.google.common.collect.ImmutableList;


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

public final class PropNet
{
	/** References to every component in the PropNet. */
	private final Set<Component> components;

	/** References to every Proposition in the PropNet. */
	private final Set<Proposition> propositions;

	/** References to every BaseProposition in the PropNet, indexed by name. */
	private final Map<GdlSentence, Proposition> basePropositions;

	/** References to every InputProposition in the PropNet, indexed by name. */
	private final Map<GdlSentence, Proposition> inputPropositions;

	/** References to every LegalProposition in the PropNet, indexed by role. */
	private final Map<Role, Set<Proposition>> legalPropositions;

	/** References to every GoalProposition in the PropNet, indexed by role. */
	private final Map<Role, Set<Proposition>> goalPropositions;

	/** A reference to the single, unique, InitProposition. */
	private final Proposition initProposition;

	/** A reference to the single, unique, TerminalProposition. */
	private final Proposition terminalProposition;

	/** A helper mapping between input/legal propositions. */
	private final Map<Proposition, Proposition> legalInputMap;

	/** A helper list of all of the roles. */
	private final List<Role> roles;

	//pairwise distances between components
	private int[][] dependencyMatrix;

	public void addComponent(Component c)
	{
		components.add(c);
		if (c instanceof Proposition) propositions.add((Proposition)c);
	}

	/**
	 * Creates a new PropNet from a list of Components, along with indices over
	 * those components.
	 *
	 * @param components
	 *            A list of Components.
	 */
	public PropNet(List<Role> roles, Set<Component> components)
	{

	    this.roles = roles;
		this.components = components;
		this.propositions = recordPropositions();
		this.basePropositions = recordBasePropositions();
		this.inputPropositions = recordInputPropositions();
		this.legalPropositions = recordLegalPropositions();
		this.goalPropositions = recordGoalPropositions();
		this.initProposition = recordInitProposition();
		this.terminalProposition = recordTerminalProposition();
		this.legalInputMap = makeLegalInputMap();
	}

	public List<Role> getRoles()
	{
	    return roles;
	}

	public Map<Proposition, Proposition> getLegalInputMap()
	{
		return legalInputMap;
	}

	private Map<Proposition, Proposition> makeLegalInputMap() {
		Map<Proposition, Proposition> legalInputMap = new HashMap<Proposition, Proposition>();
		// Create a mapping from Body->Input.
		Map<List<GdlTerm>, Proposition> inputPropsByBody = new HashMap<List<GdlTerm>, Proposition>();
		for(Proposition inputProp : inputPropositions.values()) {
			List<GdlTerm> inputPropBody = (inputProp.getName()).getBody();
			inputPropsByBody.put(inputPropBody, inputProp);
		}
		// Use that mapping to map Input->Legal and Legal->Input
		// based on having the same Body proposition.
		for(Set<Proposition> legalProps : legalPropositions.values()) {
			for(Proposition legalProp : legalProps) {
				List<GdlTerm> legalPropBody = (legalProp.getName()).getBody();
				if (inputPropsByBody.containsKey(legalPropBody)) {
    				Proposition inputProp = inputPropsByBody.get(legalPropBody);
    				legalInputMap.put(inputProp, legalProp);
    				legalInputMap.put(legalProp, inputProp);
				}
			}
		}
		return legalInputMap;
	}

	/**
	 * Getter method.
	 *
	 * @return References to every BaseProposition in the PropNet, indexed by
	 *         name.
	 */
	public Map<GdlSentence, Proposition> getBasePropositions()
	{
		return basePropositions;
	}

	/**
	 * Getter method.
	 *
	 * @return References to every Component in the PropNet.
	 */
	public Set<Component> getComponents()
	{
		return components;
	}

	/**
	 * Getter method.
	 *
	 * @return References to every GoalProposition in the PropNet, indexed by
	 *         player name.
	 */
	public Map<Role, Set<Proposition>> getGoalPropositions()
	{
		return goalPropositions;
	}

	/**
	 * Getter method. A reference to the single, unique, InitProposition.
	 *
	 * @return
	 */
	public Proposition getInitProposition()
	{
		return initProposition;
	}

	/**
	 * Getter method.
	 *
	 * @return References to every InputProposition in the PropNet, indexed by
	 *         name.
	 */
	public Map<GdlSentence, Proposition> getInputPropositions()
	{
		return inputPropositions;
	}

	/**
	 * Getter method.
	 *
	 * @return References to every LegalProposition in the PropNet, indexed by
	 *         player name.
	 */
	public Map<Role, Set<Proposition>> getLegalPropositions()
	{
		return legalPropositions;
	}

	/**
	 * Getter method.
	 *
	 * @return References to every Proposition in the PropNet.
	 */
	public Set<Proposition> getPropositions()
	{
		return propositions;
	}

	/**
	 * Getter method.
	 *
	 * @return A reference to the single, unique, TerminalProposition.
	 */
	public Proposition getTerminalProposition()
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
		for ( Component component : components )
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
	private Map<GdlSentence, Proposition> recordBasePropositions()
	{
		Map<GdlSentence, Proposition> basePropositions = new HashMap<GdlSentence, Proposition>();
		for (Proposition proposition : propositions) {
		    // Skip all propositions without exactly one input.
		    if (proposition.getInputs().size() != 1)
		        continue;

			Component component = proposition.getSingleInput();
			if (component instanceof Transition) {
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
	private Map<Role, Set<Proposition>> recordGoalPropositions()
	{
		Map<Role, Set<Proposition>> goalPropositions = new HashMap<Role, Set<Proposition>>();
		for (Proposition proposition : propositions)
		{
		    // Skip all propositions that aren't GdlRelations.
		    if (!(proposition.getName() instanceof GdlRelation))
		        continue;

			GdlRelation relation = (GdlRelation) proposition.getName();
			if (!relation.getName().getValue().equals("goal"))
			    continue;

			Role theRole = new Role((GdlConstant) relation.get(0));
			if (!goalPropositions.containsKey(theRole)) {
				goalPropositions.put(theRole, new HashSet<Proposition>());
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
	private Proposition recordInitProposition()
	{
		for (Proposition proposition : propositions)
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
	private Map<GdlSentence, Proposition> recordInputPropositions()
	{
		Map<GdlSentence, Proposition> inputPropositions = new HashMap<GdlSentence, Proposition>();
		for (Proposition proposition : propositions)
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
	private Map<Role, Set<Proposition>> recordLegalPropositions()
	{
		Map<Role, Set<Proposition>> legalPropositions = new HashMap<Role, Set<Proposition>>();
		for (Proposition proposition : propositions)
		{
		    // Skip all propositions that aren't GdlRelations.
			if (!(proposition.getName() instanceof GdlRelation))
			    continue;

			GdlRelation relation = (GdlRelation) proposition.getName();
			if (relation.getName().getValue().equals("legal")) {
				GdlConstant name = (GdlConstant) relation.get(0);
				Role r = new Role(name);
				if (!legalPropositions.containsKey(r)) {
					legalPropositions.put(r, new HashSet<Proposition>());
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
	private Set<Proposition> recordPropositions()
	{
		Set<Proposition> propositions = new HashSet<Proposition>();
		for (Component component : components)
		{
			if (component instanceof Proposition) {
				propositions.add((Proposition) component);
			}
		}
		return propositions;
	}

	/**
	 * Records a reference to the single, unique, TerminalProposition.
	 *
	 * @return A reference to the single, unqiue, TerminalProposition.
	 */
	private Proposition recordTerminalProposition()
	{
		for ( Proposition proposition : propositions )
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
		for(Component c : components) {
			if(c instanceof And)
				andCount++;
		}
		return andCount;
	}

	public int getNumOrs() {
		int orCount = 0;
		for(Component c : components) {
			if(c instanceof Or)
				orCount++;
		}
		return orCount;
	}

	public int getNumNots() {
		int notCount = 0;
		for(Component c : components) {
			if(c instanceof Not)
				notCount++;
		}
		return notCount;
	}

	public int getNumLinks() {
		int linkCount = 0;
		for(Component c : components) {
			linkCount += c.getOutputs().size();
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
	public void removeComponent(Component c) {


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
	}

	public static List<PropNet> factor_propnet(PropNet prop, Role r) {
		//prop.renderToFile("unfactoredprop.dot");

		int PROP_SIZE_LIMIT = 10000;
		if (prop.getComponents().size() > PROP_SIZE_LIMIT) {
			List<PropNet> factoredProps = new ArrayList<>();
			PropNet prop2 = new PropNet(prop.getRoles(), prop.getComponents());
			factoredProps.add(prop2);
			System.out.println("Propnet too large to factor, gtfo. " + prop.getComponents().size());
			return factoredProps;
		}

		int cId = 0;
		HashMap<Component, Integer> cMap = new HashMap<Component, Integer>();
		HashMap<Integer, Component> iMap = new HashMap<Integer, Component>();
		Set<Component> constans = new HashSet<Component>();
		for (Component c : prop.getComponents()) {
			iMap.put(cId, c);
			cMap.put(c, cId++);
			if (c instanceof Constant) constans.add(c);

		}




		//label direct connections of components
		prop.initDependencyMatrix(prop.getComponents().size());
		for (Component c : prop.getComponents()) {
			int cIndex = cMap.get(c);
			Set<Component> outputs = c.getOutputs();
			for (Component out : outputs) {
				int outIndex = cMap.get(out);
				if (cIndex < outIndex) {
					prop.dependencyMatrix[cIndex][outIndex] = 1;
				} else {
					prop.dependencyMatrix[outIndex][cIndex] = 1;
				}
			}
		}

		/*for (int i=0;i<dependencyMatrix.length;++i) {
			System.out.println("i: "+indexCompMap.get(i));
			for (int j=i+1;j<dependencyMatrix[0].length;++j) {
				if (dependencyMatrix[i][j] == 1) {
					System.out.println("  "+'\t'+indexCompMap.get(j));
				}
			}
		}*/

		//find the disjoint sets of connected components
		List<Set<Component>> disjointSets = new LinkedList<Set<Component>>();
		for(Component c : prop.getComponents()) {
			Set<Component> singleSet = new HashSet<Component>();
			singleSet.add(c);
			disjointSets.add(singleSet);
		}
		for(int i=0;i<prop.dependencyMatrix.length;++i) {
			Set<Component> iSet = findSet(disjointSets, iMap.get(i));
			for(int j=i+1;j<prop.dependencyMatrix[0].length;++j) {
				if (prop.dependencyMatrix[i][j] == 1) {
	    			Set<Component> jSet = findSet(disjointSets, iMap.get(j));
	    			if (iSet.equals(jSet)) {
	    				continue;
	    			}
	    			iSet.addAll(jSet);
	    			disjointSets.remove(jSet);
				}
			}
		}
		System.out.println("Number of disjoint sets: " + disjointSets.size());
		for (Set<Component> s : disjointSets) {
			System.out.println(s.size());
		}

		/*
		//get graph distance between all components
		for (int i=0;i<dependencyMatrix.length;++i) {
			for (int j=i+1;j<dependencyMatrix.length;++j) {
				for (int k=0;k<dependencyMatrix.length;++k) {
					int ik = Math.max(dependencyMatrix[i][k], dependencyMatrix[k][i]);
					int kj = Math.max(dependencyMatrix[k][j], dependencyMatrix[j][k]);
					int ij = dependencyMatrix[i][j];

					if (ik == -1 || kj == -1) {
						continue;
					}

					if (ij == -1) {
						dependencyMatrix[i][j] = ik + kj;
					} else {
						dependencyMatrix[i][j] = Math.min(ij, ik + kj);
					}
				}
			}
		}
		*/

		/*
		for (int i=0;i<dependencyMatrix.length;++i) {
			for (int j=i+1;j<dependencyMatrix.length;++j) {
				System.out.print(dependencyMatrix[i][j] + " ");
			}
			System.out.println();
		}
		*/

		//gather the noops
		Set<Component> noops = new HashSet<Component>();
		Iterator<Set<Component>> it = disjointSets.iterator();
		while (it.hasNext()) {
			Set<Component> disjointSet = it.next();
			if (disjointSet.size() == 1) { //houston, we have a noop?
				for (Component c : disjointSet) {
					//noops.add(prop.getLegalInputMap().get(c));
					noops.add(c);
					List<GdlTerm> gdlTerms = prop.getLegalInputMap().get(c).getName().getBody();
					for (GdlTerm term : gdlTerms) {
						System.out.println(term.toString());
						GdlTerm t = new GdlConstant(term.toString());
						System.out.println(t.toString());
					}
					System.out.println("Found noop: " + prop.getLegalInputMap().get(c));
				}
				it.remove();
			}
		}


		//create a map for the best goal containing a connected input with its graph distance
		Set<Proposition> goals = prop.getGoalPropositions().get(r);
		Pair<Proposition, Integer> bestGoal = Pair.of(null, -1);
		for (Proposition g : goals) {
			GdlRelation relation = (GdlRelation) g.getName();
	        GdlConstant constant = (GdlConstant) relation.get(1);
	        int goalVal = Integer.parseInt(constant.toString());
			if (goalVal > bestGoal.right) {
				bestGoal = Pair.of(g, goalVal);
			}
		}
		System.out.println("Goal: " + bestGoal.left.getName());
		Map<Component, Integer> goalDependency = new HashMap<>();
		Set<Component> seen = new HashSet<>();
		inputDFS(bestGoal.left, 0, goalDependency, seen, prop, bestGoal.left.toString());
		//do the same for terminal state, bc it seems like a good idea
		inputDFS(prop.getTerminalProposition(), 0, goalDependency, seen, prop, prop.getTerminalProposition().toString());
		for (Component noop : noops) {
			goalDependency.put(noop, -1);
		}


		//remove sets which have no goal value > 0
		it = disjointSets.iterator();
		while (it.hasNext()) {
			Set<Component> disjointSet = it.next();
			boolean containsGoal = false;
			for (Proposition g : goals) {
				GdlRelation relation = (GdlRelation) g.getName();
		        GdlConstant constant = (GdlConstant) relation.get(1);
		        int goalVal = Integer.parseInt(constant.toString());
				if (goalVal == 0) {
					continue;
				}
				if (disjointSet.contains(g)) {
					containsGoal = true;
					break;
				}
			}

			if (!containsGoal) {
				it.remove();
			}
		}
		System.out.println("Number of disjoint sets with goals: " + disjointSets.size());
		System.out.println("disjoint size: " + disjointSets.get(0).size() + " total size: " + prop.getComponents().size());



		//get input props from the disjoint sets
		List<Proposition> inputProps = new ArrayList<Proposition>(prop.getInputPropositions().values());
		List<Set<Component>> disjointInputs = new ArrayList<Set<Component>>();
		for (Set<Component> disjointSet : disjointSets) {
			Set<Component> disjointInput = new HashSet<Component>();
			for (Proposition p : inputProps) {
				if (disjointSet.contains(p)) {
					disjointInput.add(p);
				}
			}
			disjointInput.addAll(noops);
			disjointInputs.add(disjointInput);
		}

		//remove legals which are not part of our new propnet
		Iterator<Set<Component>> disjointIter = disjointSets.iterator();
		Iterator<Set<Component>> inputIter = disjointInputs.iterator();
		Set<Component> inputGoal = new HashSet<>(goalDependency.keySet());
		while (disjointIter.hasNext()) {
			Set<Component> disjointInput = inputIter.next();
			Set<Component> disjointSet = disjointIter.next();

			if (disjointInput.isEmpty()) {
				inputIter.remove();
				disjointIter.remove();
				continue;
			}

			//add legals that may be missing
			for (Component c : inputGoal) {
				Proposition l = prop.getLegalInputMap().get((Proposition) c);
				if (l == null) {
					continue;
				}
				disjointSet.add(l);
				disjointSet.add(c);
				System.out.println("Adding: " + l.getName());
				//don't forget to add daddy
				disjointSet.addAll(l.getInputs());
				for (Component d : l.getInputs()) {
					disjointSet.addAll(d.getOutputs());
				}
			}

			//remove all legals for inputs not connected to relevant goal
			Iterator<Component> iter = disjointInput.iterator();
			Set<Component> toRemove = new HashSet<>();
			while (iter.hasNext()) {
				Component c = iter.next();
				if (!(c instanceof Proposition)) {
					continue;
				}
				Proposition p = (Proposition) c;
				Proposition l = prop.getLegalInputMap().get(p);
				if (!inputGoal.contains(p)) {
					for (Component in : l.getInputs()) {
						in.removeOutput(l);
					}
					System.out.println("Removing: " + l.getName());
					System.out.println("Detaching: " + p.getName());

					toRemove.add(l);
					//i think we should be doing this
					for (Component in : p.getInputs()) {
						in.removeOutput(p);
					}
					p.removeAllInputs();
					for (Component out : p.getOutputs()) {
						out.removeInput(p);
					}
					p.removeAllOutputs();
				}
			}
			for (Component c : toRemove) {
				disjointSet.remove(c);
			}

			//remove all legals without a matching input in the disjoint set
			for (Component c : disjointSet) {
				if (!(c instanceof Proposition)) {
					continue;
				}
				Proposition p = (Proposition) c;
				if (prop.getInputPropositions().values().contains(p)) {
					continue;
				}
				Proposition li = prop.getLegalInputMap().get(p);
				if (li != null && !disjointSet.contains(li)) {
					for (Component in : p.getInputs()) {
						in.removeOutput(p);
					}
					toRemove.add(p);
					System.out.println("Removing2: " + p.getName() + "  " + li.getName());
				}
			}
			for (Component rem : toRemove) {
				disjointSet.remove(rem);
			}
		}


		Set<Component> trimmedComponents = new HashSet<Component>();
		trimmedComponents.addAll(disjointSets.get(0));

		PropNet prop2 = new PropNet(prop.getRoles(), trimmedComponents);

		//add pseudo NOOPS if needed
		//when is it need one may ask?
		//well, whenever we trim the move set of either role
		//but not our own role
		for (Role rol : prop2.getRoles()) {
			if ( !rol.equals(r) && (prop2.getLegalPropositions().get(rol) == null || prop2.getLegalPropositions().get(rol).size() < prop.getLegalPropositions().get(rol).size()) ) {
				//Set<Proposition> pseudoNoop = new HashSet<>();
				ImmutableList<GdlTerm> body = ImmutableList.of((GdlTerm) rol.getName(), new GdlConstant("noope"));
				Proposition noopLegal = new Proposition(new GdlRelation(GdlPool.getConstant("legal"), body));
				Constant c = new Constant(true);
				c.addOutput(noopLegal);
				noopLegal.addInput(c);
				Proposition noopInput = new Proposition(new GdlRelation(GdlPool.getConstant("does"), body));

				trimmedComponents.add(noopLegal);
				trimmedComponents.add(noopInput);
				trimmedComponents.add(c);
				System.out.println(noopLegal);
				System.out.println(noopInput);
			}
		}
		prop2 = new PropNet(prop.getRoles(), trimmedComponents);



		//prop2.renderToFile("factoredprop.dot");

		System.out.println("Original roles: " + prop.getRoles().size());
		System.out.println("Original components: " + prop.getComponents().size());
		System.out.println("Original inputs: " + prop.getInputPropositions().values().size());
		System.out.println("Original legal proposition map: " + prop.getLegalPropositions().size());
		System.out.println("Original legals role: " + prop.getLegalPropositions().get(r).size());


		System.out.println("Factored roles: " + prop2.getRoles().size());
		System.out.println("Factored components: " + prop2.getComponents().size());
		System.out.println("Factored inputs: " + prop2.getInputPropositions().values().size());
		System.out.println("Factored legal proposition map: " + prop2.getLegalPropositions().size());
		System.out.println("Factored legals role: " + prop2.getLegalPropositions().get(r).size());




		//try to factor split on OR terminal
		/*
		Proposition terminal = prop2.getTerminalProposition();
		Set<Component> terminalInputs = terminal.getInputs();
		if (terminalInputs.size() == 1 && terminal.getSingleInput() instanceof Or) { //terminal connected by ORs
			Component terminalOr = terminal.getSingleInput();
			Set<Component> stepCounter = new HashSet<Component>();
			List<Component> orInputs = new ArrayList<Component>();
			Iterator<Component> iter = terminalOr.getInputs().iterator();
			while (iter.hasNext()) {
				Set<Component> sc = new HashSet<Component>();
				Component orInput = iter.next();
				boolean isStepCounter = PropNet.stepCounterDetection(orInput, 0, sc, terminalOr, prop.getInitProposition());
				if (isStepCounter) {
					stepCounter = sc;
				} else {
					orInputs.add(orInput);
				}
			}


		}
		*/



		List<PropNet> factoredProps = new ArrayList<>();
		factoredProps.add(prop2);
		//factoredProps.add(prop3);

		return factoredProps;
	}

	private static void inputDFS(Component c, int dist, Map<Component, Integer> goalDependency, Set<Component> seen, PropNet prop, String path) {
		if (seen.contains(c)) {
			return;
		}
		seen.add(c);
		if ((c instanceof Proposition)){
			Proposition p = (Proposition) c;
			boolean isInput = prop.getInputPropositions().values().contains(p);
			if (isInput) {
				goalDependency.put(p, dist);
			}
		}
		for (Component in : c.getInputs()) {
			inputDFS(in, dist+1, goalDependency, seen, prop, path+" "+in);
		}
	}

    private void initDependencyMatrix(int length) {

    	dependencyMatrix = new int[length][length];

    	//let's initialize each entry to -1 for some reason
    	for(int i=0;i<dependencyMatrix.length;++i) {
    		for(int j=0;j<dependencyMatrix.length;++j) {
    			dependencyMatrix[i][j] = -1;
    		}
    	}
    }

    private static Set<Component> findSet(List<Set<Component>> sets, Component elem) {
    	for (Set<Component> set : sets) {
    		if (set.contains(elem)) {
    			return set;
    		}
    	}
		return null;
    }

	public static Pair<PropNet, Integer> removeStepCounter(PropNet prop) {
		Proposition terminal = prop.getTerminalProposition();
		Set<Component> terminalInputs = terminal.getInputs();
		if (terminalInputs.size() == 1 && terminal.getSingleInput() instanceof Or) { //terminal connected by ORs
			Component terminalOr = terminal.getSingleInput();
			Iterator<Component> iter = terminalOr.getInputs().iterator();
			while (iter.hasNext()) {
				Set<Component> stepCounter = new HashSet<Component>();
				Component orInput = iter.next();
				boolean isStepCounter = PropNet.stepCounterDetection(orInput, 0, stepCounter, terminalOr, prop.getInitProposition());
				if (isStepCounter) {
					Set<Component> counterlessComponents = new HashSet<>(prop.getComponents());
					counterlessComponents.removeAll(stepCounter);
					iter.remove();
					PropNet counterlessProp = new PropNet(prop.getRoles(), counterlessComponents);
					return Pair.of(counterlessProp, (stepCounter.size()/3+1));
				}
			}
		} else if (terminalInputs.size() == 1 && terminal.getSingleInput() instanceof Proposition) {//terminal potentially connected by just stepcounter
			System.out.println("Step counter is only input to terminal");
			Set<Component> stepCounter = new HashSet<Component>();
			boolean isStepCounter = PropNet.stepCounterDetection(terminal.getSingleInput(), 0, stepCounter, null, prop.getInitProposition());
			if (isStepCounter) {
				Set<Component> counterlessComponents = new HashSet<>(prop.getComponents());
				counterlessComponents.removeAll(stepCounter);
				terminal.removeAllInputs();
				PropNet counterlessProp = new PropNet(prop.getRoles(), counterlessComponents);
				return Pair.of(counterlessProp, (stepCounter.size()/3+1));
			}
		}
		return null;
	}

	private static boolean stepCounterDetection(Component c, int state, Set<Component> stepCounter, Component last, Proposition init) {
		state = state % 3;
		stepCounter.add(c);
		if (state == 0) {
			if (!(c instanceof Proposition) || c.getInputs().size() != 1) {
				return false;
			}
			boolean isStepCounter = PropNet.stepCounterDetection(c.getSingleInput(), state+1, stepCounter, c, init);
			if (isStepCounter) {
				Set<Component> toRemove = new HashSet<>();
				for (Component output : c.getOutputs()) {
					if (output.equals(last)) {
						continue;
					}
					toRemove.add(output);
					output.removeInput(c);
				}
				if (toRemove.size() > 0) {
					System.out.println("We have other outputs coming out of the step counter, this can be dangerous ;)");
				}
				for (Component r : toRemove) {
					c.removeOutput(r);
				}
			}
			return isStepCounter;
		}
		if (state == 1) {
			if (!(c instanceof Transition) || c.getInputs().size() != 1) {
				return false;
			}
			return PropNet.stepCounterDetection(c.getSingleInput(), state+1, stepCounter, c, init);
		}
		if (state == 2) {
			if (c.equals(init)) { //omg we found the step counter, probably
				System.out.println("FOUND STEP COUNTER " + stepCounter.size());
				stepCounter.remove(c);
				c.removeOutput(last);
				return true;
			}
			if (!(c instanceof Proposition) || c.getInputs().size() != 1) {
				return false;
			}
			return PropNet.stepCounterDetection(c.getSingleInput(), state+1, stepCounter, c, init);
		}
		return false;
	}
}