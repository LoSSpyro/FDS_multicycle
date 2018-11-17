package scheduler;

import java.util.HashMap;
import java.util.Map;

public class ASAP_Fixed extends ASAP {
	
	public Schedule scheduleWithFixedNodes(final Graph graph, final Schedule partialSchedule) {
		Map<Node, Interval> queue = new HashMap<Node, Interval>();
		Map<Node, Interval> newQueue;
		Map<Node, Interval> minSlot = new HashMap<Node, Interval>();
		Schedule schedule = partialSchedule.clone();
		
		// add all root nodes that are not yet scheduled to queue
		for (Node graphNode : graph) {
			if (graphNode.root() && !schedule.getNodes().keySet().contains(graphNode)) {
				queue.put(graphNode, new Interval(0, graphNode.getDelay() - 1));
			}
		}
		if (queue.size() == 0) {
			System.out.println("No root in Graph found. Empty or cyclic graph");
		}

		while (queue.size() > 0) {
			System.out.println("Nodes in Queue: ");
			for (Node queuedNode : queue.keySet()) {
				System.out.print(queuedNode.id + " ");
				System.out.print("[" + queue.get(queuedNode).lbound + ", " + queue.get(queuedNode).ubound + "); ");
			}
			System.out.println();
			
			newQueue = new HashMap<Node, Interval>();
			for (Node queuedNode : queue.keySet()) {
				Interval slot = queue.get(queuedNode);
				schedule.add(queuedNode, slot);
	
				for (Node successorNode : queuedNode.successors()) {
					graph.handle(queuedNode, successorNode);
	
					Interval successorSlot = minSlot.get(successorNode);
					if (successorSlot == null || successorSlot.lbound.compareTo(slot.ubound) <= 0) {
						// found an earlier slot
						minSlot.put(successorNode,
								new Interval(slot.ubound + 1, slot.ubound + successorNode.getDelay()));
					}

					if (!successorNode.top()) {
						continue;
					}
					successorSlot = minSlot.get(successorNode);
					if (queue.get(successorNode) == null) {
						if (newQueue.get(successorNode) == null) {
							newQueue.put(successorNode, successorSlot);
						}
						else if (newQueue.get(successorNode).lbound <= slot.ubound) {
							newQueue.put(successorNode, successorSlot);
						}
					} else if (queue.get(successorNode).lbound <= slot.ubound) {
						newQueue.put(successorNode, successorSlot);
					}
				}
			}
			queue = newQueue;
		}
		graph.reset();
	
		return schedule;
	}
}
