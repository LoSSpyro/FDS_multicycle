package scheduler;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class FDS extends Scheduler {

	Map<Node, Interval> mobilityIntervals;
	Map<Node, HashMap<Integer, Float>> probabilities;
	Map<String, HashMap<Integer, Float>> resource_usage;
	
	private final int lmax; // 
	
	private final RC resource_graph;
	
	public FDS(final RC rc, int lmax) {
		this.lmax = lmax;
		resource_graph = rc;
	}

	@Override
	public Schedule schedule(final Graph graph) {
		// Add all nodes to queue
		Set<Node> queue = new TreeSet<Node>();
		for (Node node : graph.getNodes().keySet()) {
			queue.add(node);
		}
		
		Schedule schedule = new Schedule();
		
		while (queue.size() > 0) {
			// Determine node mobility with respect to fixed operations
			mobilityIntervals = mobility(graph, schedule);
			// Compute all p_i,t and q_k,t
			probabilities = calcProbabilites();
			resource_usage = calcResourceUsage(graph);
			
			// Evaluate Set K of all operations v_i with mobility_i > 0
			Set<Node> candidates = new TreeSet<Node>();
			for (Node node : queue) {
				Interval slot = mobilityIntervals.get(node);
				if (slot.ubound - slot.lbound > 0) { // mobility > 0
					candidates.add(node);
				} else {
					// TODO schedule directly
				}
			}
			
			// Compute sum of forces of v_i for all time steps t in [tau_asap(v_i), tau_alap(v_i)];
			Node minForceNode = null;
			int minForceTime = -1;
			float minForce = Float.MAX_VALUE;
			for (Node node : candidates) {
				for (int time = 0; time < lmax; time++) {
					// compute sum of forces (for node and time)
					float forceSum = computeForces(schedule, node, time);
					// keep track of lowest force node
					if (forceSum < minForce) {
						minForceNode = node;
						minForceTime = time;
						minForce = forceSum;
					}
				}
			}
			
			// Plan operation with smallest force
			Interval slot = new Interval(minForceTime, minForceTime + minForceNode.getDelay());
			schedule.add(minForceNode, slot);
			queue.remove(minForceNode);
		}
		
		return schedule;
	}
	
	
	private float computeForces(Schedule schedule, Node node, int time) {
		// TODO implement
		
		
		// Self force
		float q_node = resource_usage.get(node.getRT()).get(time);
		float q_avg = 0;
		Interval mobility = mobilityIntervals.get(node);
		for (int i = mobility.lbound; i <= mobility.ubound; i++) {
			q_avg += resource_usage.get(node.getRT()).get(i);
		}
		q_avg /= mobility.ubound - mobility.lbound - node.getDelay() + 1;
		float selfForce = q_node - q_avg;
		
		// pseudo-plan node
		
		// Predecessor forces
		float predForceSum = 0;
		for (Node predecessor : node.allPredecessors().keySet()) {
			float predForce = 0;
			mobility = mobilityIntervals.get(node);
			for (int i = mobility.lbound; i <= mobility.ubound; i++) {
				predForce += resource_usage.get(node.getRT()).get(i);
			}
			predForce /= mobility.ubound - mobility.lbound - node.getDelay() + 1;
		}
		// Successor forces
		float succForceSum = 0;
		for (Node successor : node.allSuccessors().keySet()) {
			
		}
		
		return selfForce + predForceSum + succForceSum;
	}
	
	
	private HashMap<Node, Interval> mobility(final Graph graph, final Schedule partialSchedule) {
		HashMap<Node, Interval> mob = new HashMap<Node, Interval>();
		
		Schedule asap = new ASAP_Fixed().schedule(graph, partialSchedule);
		Schedule alap = new ALAP_Fixed(lmax).schedule(graph, partialSchedule);
		
		for (Node n : graph.getNodes().keySet()) {
			System.out.println(n);
			System.out.println("ASAP: " + asap.slot(n));
			System.out.println("ALAP: " + alap.slot(n));
			System.out.println("Mobility: " + (alap.slot(n).lbound - asap.slot(n).lbound));
			mob.put(n,
					new Interval(
							asap.slot(n).lbound,
							alap.slot(n).ubound));
		}
		
		return mob;
	}
	

	/**
	 * 
	 * TODO:It's wrong if
	 * one operation has multiple Resources-> Every resource has the same prob.
	 * TODO:check if ASAP and ALAP are correkt if for ex.Mul->Res1,Res2
	 * 
	 * @param graph
	 * @return
	 */
	/*@SuppressWarnings("unchecked")
	private Map<RT, HashMap<Integer, Float>> calcResourceUsage(final Graph graph) {
		HashMap<RT, HashMap<Integer, Float>> resUsage = new HashMap<RT, HashMap<Integer, Float>>();
		for (Node n : graph.getNodes().keySet()) {
			System.out.println("\nRESOURCEUSAGE for Node: " + n + " on " + resource_graph.getRes(n.getRT()));
		}
		// System.out.println("Resource " + resource + " on RT: " +
		// resource_graph.getAllRes().get(resource));
		for (Node n : graph.getNodes().keySet()) {
			/**
			 * 
			 * if Node n works on one of the resources
			 * 
			 *
			HashMap<Integer, Float> timeUsage = (HashMap<Integer, Float>) probabilities.get(n).clone();
			/**
			 * 
			 * Add the probabilty of timeUsage of Node n to the resource Usage
			 * 
			 *
			if (!resUsage.containsKey(n.getRT())) {
				resUsage.put(n.getRT(), timeUsage);
			} else {
				HashMap<Integer, Float> currentTimeUsage = resUsage.get(n.getRT());
				/**
				 * 
				 * Do this for each timestep available in the Nodes Execution Probabilities
				 * 
				 * TODO: Possibly can be simplified, when each Node hast every timestep in
				 * 
				 * the Probabiliies Map. Currently each Node just has the prob. for his
				 * mobilityInterval
				 * 
				 *
				timeUsage.forEach((t, prob) -> currentTimeUsage.merge(t, prob, (v1, v2) -> v1 + v2));
				resUsage.put(n.getRT(), currentTimeUsage);
			}
		}
		return resUsage;
	}*/

	
	/**
	 * TODO: It's wrong if one operation has multiple Resources -> Every resource has the same prob.
	 * TODO: check if ASAP and ALAP are correkt if for ex. Mul -> Res1, Res2
	 * @param graph
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private Map<String, HashMap<Integer, Float>> calcResourceUsage(final Graph graph) {
		HashMap<String, HashMap<Integer, Float>> resUsage = new HashMap<String, HashMap<Integer, Float>>();
		for (Node n : graph.getNodes().keySet()) {
			System.out.println("\nRESOURCEUSAGE for Node: " + n + " on " + resource_graph.getRes(n.getRT()));
		}
		
		for (String resource : resource_graph.getAllRes().keySet()) {
			System.out.println("Resource " + resource + " on RT: " + resource_graph.getAllRes().get(resource));
			for (Node n : graph.getNodes().keySet()) {
				/**
				 * if Node n works on one of the resources
				 */
				if (resource_graph.getRes(n.getRT()).contains(resource)) {
					HashMap<Integer, Float> timeUsage = (HashMap<Integer, Float>) probabilities.get(n).clone();
					/**
					 * Add the probabilty of timeUsage of Node n to the resource Usage
					 */
					if (!resUsage.containsKey(resource)) {
						resUsage.put(resource, timeUsage);
					} else {
						HashMap<Integer, Float> currentTimeUsage = resUsage.get(resource);
						/**
						 * Do this for each timestep available in the Nodes Execution Probabilities
						 * TODO: Possibly can be simplified, when each Node hast every timestep in
						 * the Probabiliies Map. Currently each Node just has the prob. for his mobilityInterval
						 */
						timeUsage.forEach((t, prob) -> currentTimeUsage.merge(t, prob, (v1, v2) -> v1 + v2));
						
						resUsage.put(resource, currentTimeUsage);
					}
				}
			}
		}
		return resUsage;
	}
	
	/**
	 * Calculate the Probability for each timestep a given Operation is executed in those timesteps
	 * @return A Allocation of probability that a given Node is executed for each timestep the given Node can possibly been executed.
	 */
	private Map<Node, HashMap<Integer, Float>> calcProbabilites() {
		/**
		 * Create Empty Maps, for Node <-> (timestep <-> probability) and
		 * timestep <-> probability
		 */
		Map<Node, HashMap<Integer, Float>> probs = new HashMap<Node, HashMap<Integer, Float>>();
		HashMap<Integer, Float> timeProb;
		/*
		 * iterate over every Node
		 */
		for (Node n : mobilityIntervals.keySet()) {
			timeProb = new HashMap<Integer, Float>();
			Interval range = mobilityIntervals.get(n);
			/**
			 * Calculate the mobility (on how many different timesteps can an operation be executed)
			 */
			int mobility = range.ubound - (range.lbound + n.getDelay()-1);
			
			float probability;
			/**
			 * For each Mobility the probability is calculated over the sum of 1/(mobility+1)
			 */
			for (Interval delay = new Interval(range.lbound, range.lbound + n.getDelay()-1); delay.ubound <= range.ubound; delay = delay.align_ubound(delay.ubound+1)) {
				for (int i = delay.lbound; i <= delay.ubound; i++) {
					probability = timeProb.containsKey(i) ? timeProb.get(i) : 0f;
					/**
					 * When the next possible Interval (in the mobility interval)
					 * contains the operation, the Probability gets increased
					 */
					probability += (float) (1./(mobility+1));
					timeProb.put(i, probability);
				}
			}
			probs.put(n, timeProb);
		}
		//TODO: When lmax is defined, the probs Map need to get converted into a map with timestep <-> (Node <-> prob)
		//to get the pobabilities (0) for timesteps, that are not contained in the possible scheduled Intervals of a
		//operation
		return probs;
	}
	
	public void probDebug() {
		for (Node n : mobilityIntervals.keySet()) {
			System.out.print("Node " + n.id + ", Mobility: " + mobilityIntervals.get(n) + "\n");
		}
		
		for (Node n : probabilities.keySet()) {
			System.out.println("\nNode " + n);
			for (Integer i : probabilities.get(n).keySet()) {
				System.out.println("Timestep: " + i + " Prob: " + probabilities.get(n).get(i));
			}
		}
		
		
		for (String res : resource_usage.keySet()) {
			System.out.println("Resourge usage prob. on res: " + res);
			for (Integer t : resource_usage.get(res).keySet()) {
				System.out.println("In timestep " + t + ": " + resource_usage.get(res).get(t));
			}
		}
	}

}