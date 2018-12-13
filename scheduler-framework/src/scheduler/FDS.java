package scheduler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javafx.util.Pair;

public class FDS extends Scheduler {

	Map<Node, Interval> mobilityIntervals;
	Map<Node, HashMap<Integer, Float>> probabilities;
	Map<RT, HashMap<Integer, Float>> resource_usage;
	Schedule schedule;
	
	private int lmax; // 
	
	public FDS(int lmax) {
		this.lmax = lmax;
	}

	@Override
	public Schedule schedule(final Graph graph) {
		schedule = new Schedule();
		
		mobilityIntervals = mobility(graph, schedule);
		probabilities = calcProbabilites(graph, schedule);
		resource_usage = calcResourceUsage(graph, schedule);
		
		probDebug();

		Interval mobilityInterval;
		Integer mobility;
		Set<Node> candidates = new HashSet<Node>();
		for (Node n : graph) {
			mobilityInterval = mobilityIntervals.get(n);
			mobility = getMobility(n, mobilityInterval);
			if (mobility == 0 && !schedule.containsNode(n)) 
					schedule.add(n, mobilityInterval);
			else
				candidates.add(n);
		}
		
		while(!candidates.isEmpty()) {
			System.out.print("\nCurrent Candidates: ");
			for (Node n : candidates)
				System.out.print(n + ", ");
			System.out.println("");
			
			Pair <Node, Interval> bestNext = bestNodeToSchedule(graph, schedule, candidates);
			
			schedule.add(bestNext.getKey(), bestNext.getValue());
			System.out.println("Schedule Node: " + bestNext.getKey() + " -> " + bestNext.getValue());
			
			mobilityIntervals = mobility(graph, schedule);
			probabilities = calcProbabilites(graph, schedule);
			resource_usage = calcResourceUsage(graph, schedule);
			
			candidates = new HashSet<Node>();
			for (Node n : graph) {
				mobilityInterval = mobilityIntervals.get(n);
				mobility = getMobility(n, mobilityInterval);
				if (mobility == 0) {
					if (!schedule.containsNode(n)) {
						schedule.add(n, mobilityInterval);
						System.out.println("Node " + n + " is now unmoveable -> " + mobilityInterval);
					}
				} else
					candidates.add(n);
			}
		}
		
		//probDebug();
		
		
		return schedule;
	}

	private Pair<Node, Interval> bestNodeToSchedule(final Graph graph, Schedule schedule, Set<Node> candidates) {
		Map<Node, HashMap<Interval, Float>> forces 		= new HashMap<Node, HashMap<Interval, Float>>();
		Pair<Node, Interval> 				bestNext 	= new Pair<Node, Interval>(null, null);
		Float 								minForce 	= Float.MAX_VALUE;
		
		Interval mobilityInterval;
		Interval lowerBound;
		Interval upperBound;
		Integer mobility;
		HashMap<Interval, Float> timeForce;
		for (Node candidate : candidates) {
			timeForce = new HashMap<Interval, Float>();
			mobilityInterval = mobilityIntervals.get(candidate);
			mobility = getMobility(candidate, mobilityInterval);
			
			Float selfForce, neighborForce, force;
			lowerBound = mobilityInterval.lowerBound(candidate.getDelay());
			upperBound = mobilityInterval.upperBound(candidate.getDelay());
			for (Interval t = lowerBound; t.lbound <= upperBound.lbound; t=t.shift(1)) {
				selfForce = calculateSelfForce(candidate, t, mobilityInterval, mobility);
				neighborForce = calculateDependentForces(graph, candidate, t, schedule);
				force = selfForce + neighborForce;
				timeForce.put(t, force);
				
				if (force < minForce) {
					minForce = force;
					bestNext = new Pair<Node, Interval>(candidate, t);
				} else if (force == minForce) {
					if (t.lbound < bestNext.getValue().lbound) {
						bestNext = new Pair<Node, Interval>(candidate, t);
					}
				}
					
			}
			forces.put(candidate, timeForce);
		}
		
		return bestNext;
	}
	
	private Float calculateDependentForces(Graph graph, Node node, Interval interval, Schedule schedule) {
		Schedule hypSchedule = schedule.clone();
		hypSchedule.add(node, interval);
		HashMap<Node, Interval> hypMobilityIntervals = mobility(graph, hypSchedule);
		Map<RT, HashMap<Integer, Float>> res_usage = calcResourceUsage(graph, hypSchedule);
		
		Set<Node> dependents = new HashSet<Node>();
		for (Node n : node.allPredecessors().keySet())
			//if (!schedule.containsNode(n))
				dependents.add(n);
		for (Node n : node.allSuccessors().keySet()) {
			//System.out.println("Add Node: " + n);
			//if (!schedule.containsNode(n))
				dependents.add(n);
		}
		
		Interval lowerBound, upperBound;
		Interval mobilityInterval;
		Float neighborForce = 0f;
		for (Node n : dependents) {
			mobilityInterval = hypMobilityIntervals.get(n);
			
			lowerBound = mobilityInterval.lowerBound(n.getDelay());
			upperBound = mobilityInterval.upperBound(n.getDelay());
			
			Float qCurrent = 0f;
			for (Interval t = lowerBound; t.lbound <= upperBound.lbound; t=t.shift(1)) {
				//System.out.println("Current Interval: " + t);
				qCurrent += getResourceUsage(n, t, res_usage);
			}
			
			lowerBound = this.mobilityIntervals.get(n).lowerBound(n.getDelay());
			upperBound = this.mobilityIntervals.get(n).upperBound(n.getDelay());
			
			Float qMedian = 0f;
			for (Interval t = lowerBound; t.lbound <= upperBound.lbound; t=t.shift(1)) {
				qMedian += getResourceUsage(n, t);
			}
			
			//Median of the pretended Schedule
			qCurrent /= (getMobility(n, mobilityInterval) +1 );
			//Median of the original Schedule
			qMedian /= (getMobility(n, this.mobilityIntervals.get(n))+1);
			neighborForce += qCurrent - qMedian;
		}
		
		return neighborForce;
	}
	
	
	private Float calculateSelfForce(Node node, Interval currentInterval, Interval range, Integer mobility) {
		Float qCurrent = getResourceUsage(node, currentInterval);
		Float qMedian = 0f;
		Interval lBound = range.lowerBound(node.getDelay());
		Interval uBound = range.upperBound(node.getDelay());
		for (Interval t = lBound; t.lbound <= uBound.lbound; t=t.shift(1)) {
			qMedian += getResourceUsage(node, t);
		}
		qMedian /= mobility+1;
		Float selfForce = qCurrent - qMedian;
		return selfForce;
	}

	private HashMap<Node, Interval> mobility(final Graph graph, final Schedule partialSchedule) {
		HashMap<Node, Interval> mob = new HashMap<Node, Interval>();
		
		Schedule asap = new FixedASAP(partialSchedule).schedule(graph);
		FixedALAP alapScheduler = new FixedALAP(lmax, partialSchedule);
		Schedule alap = alapScheduler.schedule(graph);
		if (this.lmax != alapScheduler.getLmax()) {
			System.out.println("Changing lmax and Res_Usage");
			this.lmax = alapScheduler.getLmax();
			this.probabilities = calcProbabilites(graph, this.schedule);
			this.resource_usage = calcResourceUsage(graph, this.schedule);
		}
		
		
		for (Node n : graph) {
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
	@SuppressWarnings("unchecked")
	private Map<RT, HashMap<Integer, Float>> calcResourceUsage(final Graph graph, Schedule schedule) {
		HashMap<RT, HashMap<Integer, Float>> resUsage = new HashMap<RT, HashMap<Integer, Float>>();
		Map<Node, HashMap<Integer, Float>> probabilities = calcProbabilites(graph, schedule);
		// System.out.println("Resource " + resource + " on RT: " +
		// resource_graph.getAllRes().get(resource));
		for (Node n : graph) {
			/**
			 * 
			 * if Node n works on one of the resources
			 * 
			 */
			HashMap<Integer, Float> timeUsage = (HashMap<Integer, Float>) probabilities.get(n).clone();
			/**
			 * 
			 * Add the probabilty of timeUsage of Node n to the resource Usage
			 * 
			 *
			 */
			if (!resUsage.containsKey(n.getRT())) {
				resUsage.put(n.getRT(), timeUsage);
			} else {
				HashMap<Integer, Float> currentTimeUsage = resUsage.get(n.getRT());
				/**
				 * 
				 * Do this for each timestep available in the Nodes Execution Probabilities
				 * 
				 * TODO: Possibly can be simplified, when each Node has every timestep in
				 * 
				 * the Probabiliies Map. Currently each Node just has the prob. for his
				 * mobilityInterval
				 * 
				 */
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
	private Map<Node, HashMap<Integer, Float>> calcProbabilites(Graph graph, Schedule schedule) {
		/**
		 * Create Empty Maps, for Node <-> (timestep <-> probability) and
		 * timestep <-> probability
		 */
		Map<Node, HashMap<Integer, Float>> probs = new HashMap<Node, HashMap<Integer, Float>>();
		HashMap<Integer, Float> timeProb;
		Map<Node, Interval> mobilityIntervals = mobility(graph, schedule);
		/*
		 * iterate over every Node
		 */

		
		Interval rangeInterval;
		for (Node n : mobilityIntervals.keySet()) {
			Interval range = mobilityIntervals.get(n);
			Integer mobility = getMobility(n, range);
			timeProb = new HashMap<Integer, Float>();
			
			float probability;
			for (Integer t = 0; t <= lmax; t++) {
				probability = 0f;
				if (range.contains(t)) {
					rangeInterval  = new Interval(t, t+n.getDelay()-1);
					for (Integer i : rangeInterval) {
						probability = timeProb.containsKey(i) ? timeProb.get(t) : 0f;
						probability += 1f/(mobility+1f);
					}
				}
				timeProb.put(t, probability);	
			}
			probs.put(n, timeProb);
		}
		//TODO: When lmax is defined, the probs Map need to get converted into a map with timestep <-> (Node <-> prob)
		//to get the pobabilities (0) for timesteps, that are not contained in the possible scheduled Intervals of a
		//operation
		return probs;
	}

	private Float getResourceUsage(Node node, Interval duration, Map<RT, HashMap<Integer, Float>> resource_usage) {
		Float res = 0f;
		for (Integer i = duration.lbound; i  <= duration.ubound; i++) {
			res += resource_usage.get(node.getRT()).get(i);
		}
		return res/node.getDelay();
	}
	
	private Float getResourceUsage(Node node, Interval duration) {
		Float res = 0f;
		for (Integer i = duration.lbound; i  <= duration.ubound; i++) {
			res += resource_usage.get(node.getRT()).get(i);
		}
		return res/node.getDelay();
	}
	
	private Integer getMobility(Node n, Interval range) {
		return range.ubound - (range.lbound + n.getDelay()-1);
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
		
		
		for (RT res : resource_usage.keySet()) {
			System.out.println("Resourge usage prob. on res: " + res);
			for (Integer t : resource_usage.get(res).keySet()) {
				System.out.println("In timestep " + t + ": " + resource_usage.get(res).get(t));
			}
		}
	}


}