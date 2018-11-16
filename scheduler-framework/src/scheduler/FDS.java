package scheduler;

import java.util.HashMap;
import java.util.Map;

public class FDS extends Scheduler {

	Map<Node, Interval> node_mobility;
	Map<Node, HashMap<Integer, Float>> probabilities;
	private final int lmax;
	
	private Scheduler asap, alap;
	private Schedule asap_sched, alap_sched;
	
	public FDS() {
		lmax = 0;
	}

	public Schedule schedule (final Graph graph) {
		node_mobility = mobility(graph);
		calcProbabilites();

		for (Node n : probabilities.keySet()) {
			System.out.println("\nNode " + n);
			for (Integer i : probabilities.get(n).keySet()) {
				System.out.println("Timestep: " + i + " Prob: " + probabilities.get(n).get(i));
			}
		}
		
		for (Node n : node_mobility.keySet()) {
			System.out.print("Node " + n.id + ", Mobility: " + node_mobility.get(n) + "\n");
		}
		return null;
	}

	private HashMap<Node, Interval> mobility(Graph graph) {
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
	
	private void calcProbabilites() {
		HashMap<Integer, Float> timeProb;
		probabilities = new HashMap<Node, HashMap<Integer, Float>>();
		for (Node n : node_mobility.keySet()) {
			timeProb = new HashMap<Integer, Float>();
			Interval range = node_mobility.get(n);
			int mobility = range.ubound - (range.lbound + n.getDelay()-1);
			
			float probability = 0;
			for (Interval delay = new Interval(range.lbound, range.lbound + n.getDelay()-1); delay.ubound <= range.ubound; delay = delay.align_ubound(delay.ubound+1)) {
				for (int i = delay.lbound; i <= delay.ubound; i++) {
					if (timeProb.get(i) == null)
						probability = 0;
					else
						probability = timeProb.get(i);
					probability += (float) (1./(mobility+1));
					timeProb.put(i, probability);
				}
			}
			probabilities.put(n, timeProb);
		}
		
	}

}