package scheduler;

import java.util.HashMap;
import java.util.Map;

public class FixedALAP extends Scheduler {
	
	protected int lmax;
	
	protected final Schedule partialSchedule;
	
	//There i a problem when lmax is < 0:
	// Because the whole schedule get's shifted at the end, when lmax == 0, so 
	/*public FixedALAP(Schedule partialSchedule) {
		lmax = 0;
		this.partialSchedule = partialSchedule;
	}*/
	
	public FixedALAP(int lmax, Schedule partialSchedule) {
		if (lmax == 0) {
			System.out.println("WARNING: If Lmax == 0, this FixedALAP calculates a wrong Result!");
		}
		this.lmax = lmax;
		this.partialSchedule = partialSchedule;
	}

	@Override
	public Schedule schedule(Graph sg) {

		Schedule schedule = new Schedule();
		boolean valid = false;
		while(!valid) {
			schedule = new Schedule();
			
			//Schedule already scheduled Nodes
			for (Node scheduledNode : this.partialSchedule.getNodes().keySet()) {
				schedule.add(scheduledNode, partialSchedule.slot(scheduledNode));
			}
			
			Map<Node, Interval> queue = new HashMap<Node, Interval>();
			Map<Node, Interval> qq;
			Map<Node, Interval> min_queue = new HashMap<Node, Interval>();
			Integer min = lmax;
			Graph g = sg;
			
			for (Node nd : g)
				if (nd.leaf())
					if (schedule.containsNode(nd))
						queue.put(nd, schedule.slot(nd));
					else
						queue.put(nd, new Interval(lmax + 1 - nd.getDelay(), lmax));
			if(queue.size() == 0)
				System.out.println("No leaf in Graph found. Empty or cyclic graph");
			
			while(queue.size() > 0) {
				qq = new HashMap<Node, Interval>();
				for (Node nd : queue.keySet()) {
					Interval slot = queue.get(nd);
					if (slot.lbound < min)
						min = slot.lbound;
					if (!schedule.containsNode(nd))
						schedule.add(nd, slot);
					for (Node pred : nd.predecessors()) {
						
						g.handle(pred, nd);
						
						Interval ii = min_queue.get(pred);
						if (ii == null || slot.lbound <= ii.ubound) {
							if (schedule.containsNode(pred))
								ii = schedule.slot(pred);
							else 
								ii = new Interval(slot.lbound-pred.getDelay(), slot.lbound-1);
							min_queue.put(pred, ii);
						}
						if (!pred.bottom()) continue;
						if (queue.get(pred) == null) {
							if (qq.get(pred) == null) {
								qq.put(pred, ii);
							} else if (qq.get(pred).ubound >= slot.lbound) {
								qq.put(pred, ii);
							}
						} else if (queue.get(pred).ubound >= slot.lbound) {
							qq.put(pred, ii);
						}	
					}
				}
				queue = qq;
			}
			g.reset();
			
			valid = checkSchedule(schedule);
			if (!valid) { 
				System.out.println("WARNING!: invalid schedule found try with lmax + 1");
				this.lmax = this.lmax+1;
			}	
		}
		return schedule;
	}
	
	public int getLmax() {
		return lmax;
	}

	public void setLmax(int lmax) {
		this.lmax = lmax;
	}

	private boolean checkSchedule(Schedule schedule) {
		for (Node n : schedule.getNodes().keySet())
			if (schedule.slot(n).lbound < 0 || schedule.slot(n).ubound > this.lmax) {
				System.out.println("Lmax = " + this.lmax);
				return false;
			}
		return true;
	}

}
