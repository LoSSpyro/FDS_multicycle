package scheduler;

import java.util.HashMap;
import java.util.Map;

public class FDS extends Scheduler {

	Map<Node, Interval> node_mobility;
	Map<Node, HashMap<Integer, Float>> probabilities;
	Map<String, HashMap<Integer, Float>> resource_usage;
	
	private final int lmax;
	
	private Scheduler asap, alap;
	private Schedule asap_sched, alap_sched;
	private final RC resource_graph;
	
	public FDS(final RC rc) {
		lmax = 0;
		resource_graph = rc;
	}

	@Override
	public Schedule schedule (final Graph graph) {
		node_mobility = mobility(graph);
		probabilities = calcProbabilites();

		resource_usage = calcResourceUsage(graph);
		
		for (Node n : node_mobility.keySet()) {
			System.out.print("Node " + n.id + ", Mobility: " + node_mobility.get(n) + "\n");
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
		return null;
	}

	private HashMap<Node, Interval> mobility(final Graph graph) {
		HashMap<Node, Interval> mob = new HashMap<Node, Interval>();
		
		asap = new ASAP();
		asap_sched = asap.schedule(graph);

		alap = new ALAP();
		alap_sched = alap.schedule(graph);
		
		for (Node n : graph.getNodes().keySet()) {
			System.out.println(n);
			System.out.println("ASAP: " + asap_sched.slot(n));
			System.out.println("ALAP: " + alap_sched.slot(n));
			System.out.println("Mobility: " + (alap_sched.slot(n).lbound - asap_sched.slot(n).lbound));
			mob.put(n,
					new Interval(
							asap_sched.slot(n).lbound,
							alap_sched.slot(n).ubound));
		}
		
		return mob;
	}
	
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
		for (Node n : node_mobility.keySet()) {
			timeProb = new HashMap<Integer, Float>();
			Interval range = node_mobility.get(n);
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

}