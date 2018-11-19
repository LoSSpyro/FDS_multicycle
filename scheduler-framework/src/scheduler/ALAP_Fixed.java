package scheduler;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class ALAP_Fixed extends ALAP {
	
	public ALAP_Fixed() {
		super();
	}
	public ALAP_Fixed(int lmax) {
		super(lmax);
	}
	
	public Schedule schedule(final Graph graph, final Schedule partialSchedule) {
		Set<Node> queue = new HashSet<Node>();
		Schedule schedule = partialSchedule.clone();
		Integer min = lmax;

		// schedule leaf nodes and add unscheduled nodes to queue
		for (Node node : graph) {
			if (!schedule.getNodes().keySet().contains(node)) {
				if (node.leaf()) {
					// add unscheduled leaf nodes to schedule
					schedule.add(node, new Interval(lmax - node.getDelay() + 1, lmax));
					min = Math.min(min, lmax - node.getDelay() + 1);
				} else {
					// add all non-leaf unscheduled nodes to queue
					queue.add(node);
				}
			}
		}
		
		while (queue.size() > 0) {
			
			// Choose node with all successors scheduled
			Node candidate = null;
			Iterator<Node> iter = queue.iterator();
			while (candidate == null && iter.hasNext()) {
				Node node = iter.next();
				boolean succUnplanned = false;
				for (Node successor : node.allSuccessors().keySet()) {
					if (!schedule.containsNode(successor)) {
						succUnplanned = true;
						break;
					}
				}
				candidate = succUnplanned ? null : node;
			}
			if (candidate == null) {
				System.err.println("Found no node with all successors planned. Cyclic dependencies?");
				return null;
			}
			
			// Determine latest possible ending time
			int minSuccBegin = Integer.MAX_VALUE;
			for (Node successor : candidate.allSuccessors().keySet()) {
				int succBegin = schedule.getNodes().get(successor).lbound;
				if (succBegin < minSuccBegin) {
					minSuccBegin = succBegin;
				}
			}
			Interval slot = new Interval(minSuccBegin - candidate.getDelay(), minSuccBegin - 1);
			
			// Check legality of found slot with predecessors (data dependencies)
			for (Node predecessor : candidate.allPredecessors().keySet()) {
				if (schedule.containsNode(predecessor)) {
					int predEnd = schedule.getNodes().get(predecessor).ubound;
					if (predEnd >= slot.lbound) {
						System.out.println(slot.toString());
						System.out.println(predEnd);
						System.err.println("Found critical timing problem. No legal schedule possible with given partial schedule.");
						return null;
					}
				}
			}
			
			// Schedule node and remove it from the queue
			schedule.add(candidate, slot);
			min = Math.min(min, slot.lbound);
			queue.remove(candidate);
			
		}
		
		graph.reset();
		if (lmax == 0) {
			return schedule.shift(-min);
		}
		return schedule;
	}
}
