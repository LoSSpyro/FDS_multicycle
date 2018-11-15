package scheduler;

import java.util.HashMap;
import java.util.Map;

public class FDS extends Scheduler {

	Map<Node, Interval> node_mobility;

	public Schedule schedule (final Graph graph) {
		node_mobility = new HashMap<Node, Interval>();
		mobility(graph);
		for (Node n : node_mobility.keySet()) {
			System.out.print("Node " + n.id + ", Mobility: (" + node_mobility.get(n).lbound + ", " + node_mobility.get(n).ubound + ")\n");

		}
		return null;
	}

	private void mobility(Graph graph) {
		Scheduler asap = new ASAP();
		Schedule asap_sched = asap.schedule(graph);

		Scheduler alap = new ALAP();
		Schedule alap_sched = alap.schedule(graph);
		
		for (Node n : graph.getNodes().keySet()) {
			node_mobility.put(n,
				 new Interval(
				 	asap_sched.slot(n).lbound,
				 	alap_sched.slot(n).ubound));
		}

	}

}