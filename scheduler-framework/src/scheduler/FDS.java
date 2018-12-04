package scheduler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FDS extends Scheduler {

	Map<Node, Interval> mobilityIntervals;
	Map<Node, Map<Integer, Float>> probabilities;
	Map<RT, Map<Integer, Float>> resourceUsage;
	
	private final int lmax; // number of cycles, not index of last cycle
	
	public FDS(int lmax) {
		this.lmax = lmax;
	}

	@Override
	public Schedule schedule(final Graph graph) {
		// Add all nodes to queue
		Set<Node> queue = new HashSet<Node>();
		for (Node node : graph.getNodes().keySet()) {
			queue.add(node);
		}
		
		Schedule schedule = new Schedule();
		
		while (queue.size() > 0) {
			// Determine node mobility with respect to fixed operations
			try {
				mobilityIntervals = calcMobilities(graph, schedule);
			} catch (IllegalConstraintsException e) {
				e.printStackTrace();
				System.err.println("Impossible to schedule or invalid decision was made during scheduling. Check input and implementation");
				return null;
			}
			// Compute all p_i,t and q_k,t
			probabilities = calcProbabilites(mobilityIntervals);
			resourceUsage = calcResourceUsage(graph, probabilities);
			
			// Evaluate Set K of all operations v_i with mobility_i > 0
			Set<Node> removeFromQueue = new HashSet<Node>();
			for (Node node : queue) {
				Interval mobility = mobilityIntervals.get(node);
				if (mobility.ubound - mobility.lbound - node.getDelay() + 1 <= 0) { // mobility > 0
					schedule.add(node, new Interval(mobility.lbound, mobility.lbound + node.getDelay() - 1));
					removeFromQueue.add(node);
				}
			}
			for (Node node : removeFromQueue) {
				queue.remove(node);
			}
			if (queue.isEmpty()) {
				return schedule;
			}
			
			// Compute sum of forces of v_i for all time steps t in [tau_asap(v_i), tau_alap(v_i)];
			Node minForceNode = null;
			int minForceTime = 0;
			float minForce = Float.MAX_VALUE;
			for (Node node : queue) {
				Interval mobility = mobilityIntervals.get(node);
				for (int time = mobility.lbound; time <= mobility.ubound - node.getDelay() + 1; time++) {
					// compute sum of forces (for node and time)
					float forceSum;
					try {
						forceSum = calcForces(graph, schedule, node, time);
					} catch (IllegalConstraintsException e) {
						continue;
					}
					//System.out.println("Node: " + node.id + ", time: " + time + ", force: " + forceSum);
					// keep track of lowest force node
					if (forceSum < minForce) {
						minForceNode = node;
						minForceTime = time;
						minForce = forceSum;
						System.out.println("New lowest force: Node " + node.id + " at time " + time + " with force " + forceSum);
					}
				}
			}
			
			// Plan node with smallest force, remove it from the queue
			if (minForceNode == null) {
				System.err.println("Impossible to schedule or invalid decision was made during scheduling. Check input and implementation");
				return null;
			}
			Interval slot = new Interval(minForceTime, minForceTime + minForceNode.getDelay() - 1);
			schedule.add(minForceNode, slot);
			System.out.println("\tSCHEDULING Node " + minForceNode.id + " at time " + minForceTime);
			queue.remove(minForceNode);
		}
		
		return schedule;
	}
	
	
	private float calcForces(Graph graph, Schedule schedule, Node node, int time) throws IllegalConstraintsException {
		float selfForce, predForceSum, succForceSum;

		//System.out.println("Resource usage: " + resourceUsage);
		//System.out.println("Calculating forces for node " + node + ", time step " + time);
		if (node.id.equals("N5_ADD") && time == 4) {
			//System.exit(0);
		}
		
		// Self force
		selfForce = calcSelfForce(node, time);
		
		// temporarily plan node
		schedule.add(node, new Interval(time, time + node.getDelay() - 1));
		// CAUTION: DO NOT CALL RETURN until node has been unplanned again
		try {
			Map<Node, Interval> tempMobilities = calcMobilities(graph, schedule);
			
			// Predecessor forces
			predForceSum = calcNeighbourForceSum(node.predecessors(), tempMobilities);
			// Successor forces
			succForceSum = calcNeighbourForceSum(node.successors(), tempMobilities);
		} finally {
			// undo planning node
			schedule.remove(node);
		}
			
		return selfForce + predForceSum + succForceSum;
	}
	
	float q_avgOverInterval(Node node, int lbound, int ubound) {
		return q_avgOverInterval(node, new Interval(lbound, ubound));
	}
	float q_avgOverInterval(Node node, int time) {
		return q_avgOverInterval(node, new Interval(time, time + node.getDelay() - 1));
	}
	float q_avgOverInterval(Node node, Interval interval) {
		float q_avg = 0;
		for (int i = interval.lbound; i <= interval.ubound; i++) {
			q_avg += resourceUsage.get(node.getRT()).get(i);
		}
		q_avg /= node.getDelay();
		return q_avg;
	}
	
	private float calcSelfForce(Node node, int time) {
		float q_node = q_avgOverInterval(node, time);
		
		float q_avg = 0;
		Interval mobility = mobilityIntervals.get(node);
		for (int i = mobility.lbound; i <= mobility.ubound - node.getDelay() + 1; i++) {
			q_avg += q_avgOverInterval(node, i);
		}
		q_avg /= mobility.ubound - mobility.lbound - node.getDelay() + 2;
		return q_node - q_avg;
	}
	
	private float calcNeighbourForceSum(Set<Node> neighbours, Map<Node, Interval> tempMobilities) {
		float forceSum = 0;
		for (Node node : neighbours) {
			float q_tilde = 0, q_avg = 0;
			
			// calculate neighbour's q^~_k,j
			Interval mobility = tempMobilities.get(node);
			for (int i = mobility.lbound; i <= mobility.ubound - node.getDelay() + 1; i++) {
				q_tilde += q_avgOverInterval(node, i);
			}
			q_tilde /= mobility.ubound - mobility.lbound - node.getDelay() + 2;
			
			// calculate neighbour's q^-_k,j
			mobility = mobilityIntervals.get(node);
			for (int i = mobility.lbound; i <= mobility.ubound - node.getDelay() + 1; i++) {
				q_avg += q_avgOverInterval(node, i);
			}
			q_avg /= mobility.ubound - mobility.lbound - node.getDelay() + 2;
			
			// update sum
			forceSum += q_tilde - q_avg;
		}
		return forceSum;
	}
	
	
	private Map<Node, Interval> calcMobilities(final Graph graph, final Schedule partialSchedule) throws IllegalConstraintsException {
		HashMap<Node, Interval> mob = new HashMap<Node, Interval>();
		
		Schedule asap = new ASAP_Fixed(lmax).schedule(graph, partialSchedule);
		Schedule alap = new ALAP_Fixed(lmax).schedule(graph, partialSchedule);
		
		
		for (Node n : graph.getNodes().keySet()) {
			/*System.out.print("" + n.id + " - ");
			System.out.print("ASAP: " + asap.slot(n) + ", ");
			System.out.print("ALAP: " + alap.slot(n) + ", ");
			System.out.println("Mobility: " + (alap.slot(n).lbound - asap.slot(n).lbound));*/
			mob.put(n, new Interval(asap.slot(n).lbound, alap.slot(n).ubound));
		}
		
		return mob;
	}
	

	/**
	 * TODO:check if ASAP and ALAP are correct if for ex.Mul->Res1,Res2
	 * @param graph
	 * @return
	 */
	private Map<RT, Map<Integer, Float>> calcResourceUsage(final Graph graph, Map<Node, Map<Integer, Float>> probabilities) {
		Map<RT, Map<Integer, Float>> resUsage = new HashMap<RT, Map<Integer, Float>>();
		/*for (Node n : graph.getNodes().keySet()) {
			//System.out.println("Resource Usage for Node " + n + ": on " + resource_graph.getRes(n.getRT()));
		}*/
		// System.out.println("Resource " + resource + " on RT: " + resource_graph.getAllRes().get(resource));
		for (Node n : graph.getNodes().keySet()) {
			// if Node n works on one of the resources
			Map<Integer, Float> timeUsage = probabilities.get(n);
			// Add the probability of timeUsage of Node n to the resource Usage
			if (!resUsage.containsKey(n.getRT())) {
				resUsage.put(n.getRT(), timeUsage);
			} else {
				Map<Integer, Float> currentTimeUsage = resUsage.get(n.getRT());
				// Do this for each time step available in the Nodes Execution Probabilities
				// TODO: Possibly can be simplified, when each Node has every time step in the Probabilities Map. Currently each Node just has the probability for its mobilityInterval
				timeUsage.forEach((t, prob) -> currentTimeUsage.merge(t, prob, (v1, v2) -> v1 + v2));
				resUsage.put(n.getRT(), currentTimeUsage);
			}
		}
		return resUsage;
	}
	
	/**
	 * Calculate the Probability for each timestep a given Operation is executed in those timesteps
	 * @return A Allocation of probability that a given Node is executed for each timestep the given Node can possibly been executed.
	 */
	private Map<Node, Map<Integer, Float>> calcProbabilites(Map<Node, Interval> mobilities) {
		/**
		 * Create Empty Maps, for Node <-> (time step <-> probability) and
		 * time step <-> probability
		 */
		Map<Node, Map<Integer, Float>> probs = new HashMap<Node, Map<Integer, Float>>();
		HashMap<Integer, Float> timeProb;
		/*
		 * iterate over every Node
		 */
		for (Node n : mobilities.keySet()) {
			timeProb = new HashMap<Integer, Float>();
			Interval range = mobilities.get(n);
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
			
			// fill 0 probability entries
			/*for (int i = 0; i < lmax; i++) {
				if (!timeProb.containsKey(i)) {
					timeProb.put(i, 0f);
				}
			}*/
			
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
		for (RT res : resourceUsage.keySet()) {
			System.out.println("Resourge usage prob. on res: " + res);
			for (Integer t : resourceUsage.get(res).keySet()) {
				System.out.println("In timestep " + t + ": " + resourceUsage.get(res).get(t));
			}
		}
	}

}