package scheduler;

import java.util.HashMap;
import java.util.Map;

public class FixedASAP extends Scheduler {
	
	protected final Schedule partialSchedule;
	
	public FixedASAP(Schedule partialSchedule) {
		this.partialSchedule = partialSchedule;
	}
	

	@Override
	public Schedule schedule(Graph sg) {
		Map<Node, Interval> queue = new HashMap<Node, Interval>();
		Map<Node, Interval> qq;
		Map<Node, Interval> minSlot = new HashMap<Node, Interval>();
		
		Schedule schedule = new Schedule();
		
		//Schedule already scheduled Nodes
		for (Node scheduledNode : this.partialSchedule.getNodes().keySet()) {
			//System.out.println("Already Scheduled: " + scheduledNode + " : " + partialSchedule.slot(scheduledNode));
			schedule.add(scheduledNode, partialSchedule.slot(scheduledNode));
		}
		Graph g = sg;
		for (Node nd : g) {
			if (nd.root()) {
				if (schedule.containsNode(nd))
					queue.put(nd, schedule.slot(nd));
				else
					queue.put(nd, new Interval(0, nd.getDelay() - 1));
			}
		}
		
		if (queue.size() == 0) System.out.println("No root in Graph found!");
		
		while (queue.size() > 0) {
			qq = new HashMap<Node, Interval>();
			
			for (Node nd : queue.keySet()) {
				Interval slot = queue.get(nd);
				if (!schedule.containsNode(nd)) {
					schedule.add(nd, slot);
				}
				
				for (Node succ : nd.successors()) {
					g.handle(nd, succ);
					Interval ii = minSlot.get(succ);
					if (schedule.containsNode(succ)) {
						if (schedule.slot(succ).lbound.compareTo(slot.ubound) <= 0)
							throw new java.lang.Error("Invalid scheduled Nodes");
						minSlot.put(succ, schedule.slot(succ));
					}
					else if (ii == null) 
						minSlot.put(succ, new Interval(slot.ubound+1, slot.ubound + succ.getDelay()));
					else if (ii.lbound.compareTo(slot.ubound) <= 0) {
						minSlot.put(succ, new Interval(slot.ubound+1, slot.ubound + succ.getDelay()));
					}
					
					if (!succ.top()) continue;
					
					ii = minSlot.get(succ);
					if (queue.get(succ) == null) {
						if (qq.get(succ) == null)
							qq.put(succ, ii);
						else if (qq.get(succ).lbound <= slot.ubound)
							qq.put(succ, ii);
					} else if (queue.get(succ).lbound <= slot.ubound)
						qq.put(succ, ii);
				}
			}
			queue = qq;
		}
		g.reset();
		return schedule;
	}
	

}
