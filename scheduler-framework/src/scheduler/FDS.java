package scheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
			resourceUsage = calcResourceUsage(probabilities);
			
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
			List<Integer> minForceTimes = new ArrayList<Integer>(lmax);
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
					System.out.println("Node " + node.id + " at time " + time + " has force " + forceSum);
					// keep track of lowest force node
					if (forceSum < minForce) {
						minForceNode = node;
						minForceTimes.clear();
						minForceTimes.add(time);
						minForce = forceSum;
						System.out.println("Found new lowest force: Node " + node.id + " at time " + time + " with force " + forceSum);
					} else if (node == minForceNode && forceSum == minForce) {
						minForceTimes.add(time);
						System.out.println("Found new timestep with equal lowest force for node " + node.id + " at time " + time);
					}
				}
			}
			
			// Plan node with smallest force, remove it from the queue
			if (minForceNode == null) {
				System.err.println("Impossible to schedule or invalid decision was made during scheduling. Check input and implementation");
				return null;
			}
			// Select time step in the middle
			int timeStep = minForceTimes.get(minForceTimes.size() / 2);
			System.out.println(minForceTimes);
			Interval slot = new Interval(timeStep, timeStep + minForceNode.getDelay() - 1);
			schedule.add(minForceNode, slot);
			System.out.println("\tSCHEDULING Node " + minForceNode.id + " at time " + timeStep);
			queue.remove(minForceNode);
			
			/*if (minForceNode.id.equals("N3_MUL")) {
				System.exit(0);
				schedule.remove(minForceNode);
				schedule.add(minForceNode, new Interval(8, 11));
			}*/
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
			Map<Node, Map<Integer, Float>> tempProbabilities = calcProbabilites(tempMobilities);
			Map<RT, Map<Integer, Float>> tempResourceUsage = calcResourceUsage(tempProbabilities);
			
			// Predecessor forces
			predForceSum = calcNeighbourForceSum(node.predecessors(), tempMobilities, tempResourceUsage);
			// Successor forces
			succForceSum = calcNeighbourForceSum(node.successors(), tempMobilities, tempResourceUsage);
		} finally {
			// undo planning node
			schedule.remove(node);
		}
			
		return selfForce + predForceSum + succForceSum;
	}
	
	float q_avgOverInterval(Node node, int startTime, Map<RT, Map<Integer, Float>> tempResourceUsage) {
		float q_avg = 0;
		for (int i = startTime; i <= startTime + node.getDelay() - 1; i++) {
			q_avg += tempResourceUsage.get(node.getRT()).get(i);
		}
		q_avg /= node.getDelay();
		return q_avg;
	}
	
	private float calcSelfForce(Node node, int time) {
		float q_node = q_avgOverInterval(node, time, resourceUsage);
		
		float q_avg = 0;
		Interval mobility = mobilityIntervals.get(node);
		for (int i = mobility.lbound; i <= mobility.ubound - node.getDelay() + 1; i++) {
			q_avg += q_avgOverInterval(node, i, resourceUsage);
		}
		q_avg /= mobility.ubound - mobility.lbound - node.getDelay() + 2;
		return q_node - q_avg;
	}
	
	private float calcNeighbourForceSum(Set<Node> neighbours, Map<Node, Interval> tempMobilities, Map<RT, Map<Integer, Float>> tempResourceUsage) {
		float forceSum = 0;
		for (Node node : neighbours) {
			float q_tilde = 0, q_avg = 0;
			
			// calculate neighbour's q~'_k,j
			Interval mobility = tempMobilities.get(node);
			for (int i = mobility.lbound; i <= mobility.ubound - node.getDelay() + 1; i++) {
				// for every possible starting time
				q_tilde += q_avgOverInterval(node, i, tempResourceUsage);
			}
			q_tilde /= mobility.ubound - mobility.lbound - node.getDelay() + 2;
			
			// calculate neighbour's q-'_k,j
			mobility = mobilityIntervals.get(node);
			for (int i = mobility.lbound; i <= mobility.ubound - node.getDelay() + 1; i++) {
				q_avg += q_avgOverInterval(node, i, resourceUsage);
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
	
	private Map<Node, Map<Integer, Float>> calcProbabilites(Map<Node, Interval> mobilities) {
		Map<Node, Map<Integer, Float>> probs = new HashMap<Node, Map<Integer, Float>>();
		
		for (Node node : mobilities.keySet()) {
			Map<Integer, Float> nodeProbs = new HashMap<Integer, Float>();
			Interval nodeInterval = mobilities.get(node);
			float nodeMobility = nodeInterval.length() - node.getDelay() + 1;
			
			for (int t = 0; t < lmax; t++) {
				// actual computation of p'_i,t with given i and t
				float prob = 0f;
				
				for (int n = t - node.getDelay() + 1; n <= t; n++) {
					// computation of p_i,n (start probability)
					if (nodeInterval.lbound <= n && n <= nodeInterval.ubound - node.getDelay() + 1) {
						prob += 1f;
					}
				}
				prob /= nodeMobility + 1f;
				
				nodeProbs.put(t, prob);
			}
			
			probs.put(node, nodeProbs);
		}
		
		return probs;
	}
	
	private Map<RT, Map<Integer, Float>> calcResourceUsage(Map<Node, Map<Integer, Float>> probabilities) {
		Map<RT, Map<Integer, Float>> usages = new HashMap<RT, Map<Integer, Float>>();
		
		for (Node node : probabilities.keySet()) {
			if (!usages.containsKey(node.getRT())) {
				usages.put(node.getRT(), new HashMap<Integer, Float>());
			}
			Map<Integer, Float> typeUsages = usages.get(node.getRT());
			
			for (Integer t : probabilities.get(node).keySet()) {
				if (!typeUsages.containsKey(t)) {
					typeUsages.put(t, probabilities.get(node).get(t));
				} else {
					Float newUsage = typeUsages.get(t);
					newUsage += probabilities.get(node).get(t);
					typeUsages.put(t, newUsage);
				}
			}
		}
		
		return usages;
	}

}