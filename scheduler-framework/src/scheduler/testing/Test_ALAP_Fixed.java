package scheduler.testing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Iterator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import scheduler.*;

class Test_ALAP_Fixed {
	
	private static Graph graph;
	private static String resourceFile = "resources/heterogenous_16res";
	private static String graphFile = "graphs/testCyclic.dot";
	private static Schedule reference;
	private static Schedule partialSchedule;
	
	@BeforeAll
	static void setUpBeforeClass() throws Exception {
		RC rc = new RC();
		rc.parse(resourceFile);
		
		Dot_reader dr = new Dot_reader(false);
		graph = dr.parse(graphFile);
		System.out.printf("%s%n", graph.diagnose());
	}

	@BeforeEach
	void setUp() throws Exception {
		reference = new ALAP().schedule(graph);
		partialSchedule = new Schedule();
	}

	@Test
	void testALAPNoFixed() {
		Schedule alap_fixed = new ALAP_Fixed(10).schedule(graph, partialSchedule);
		
		assertEquals(alap_fixed.length(), reference.length(),
				"Schedule should be the same length as the reference ALAP schedule");
		checkIntervalsAndDependencies(alap_fixed);
	}
	
	@Test
	void testALAPOneFixed() {
		Iterator<Node> iter = graph.iterator();
		Node n1 = null;
		while (iter.hasNext()) {
			n1 = iter.next();
			if (n1.id.equals("N5_ADD")) {
				partialSchedule.add(n1, new Interval(7, 7));
				break;
			}
		}
		Schedule alap_fixed = new ALAP_Fixed(10).schedule(graph, partialSchedule);
		
		assertTrue(alap_fixed.length() >= reference.length(),
				"Schedule cannot be shorter than the reference ALAP schedule");
		assertEquals(alap_fixed.length().intValue(), 10, "Optimal schedule under the given constraints wasn't found");
		checkIntervalsAndDependencies(alap_fixed);
	}
	
	@Test
	void testALAPTwoFixed() {
		Iterator<Node> iter = graph.iterator();
		Node n1 = null, n2 = null;
		while ((n1 == null || n2 == null) && iter.hasNext()) {
			Node candidate = iter.next();
			if (candidate.id.equals("N2_MUL")) {
				partialSchedule.add(candidate, new Interval(2, 5));
			}
			if (candidate.id.equals("N7_ADD")) {
				partialSchedule.add(candidate, new Interval(9, 9));
			}
		}
		Schedule alap_fixed = new ALAP_Fixed(10).schedule(graph, partialSchedule);
		
		assertTrue(alap_fixed == null,
				"Schedule was delivered where no legal schedule is possible");
	}
	
	void checkIntervalsAndDependencies(Schedule schedule) {
		for (Node node : schedule.getNodes().keySet()) {
			int startTime = schedule.getNodes().get(node).lbound;
			int endTime = schedule.getNodes().get(node).ubound;
			assertEquals(endTime - startTime, node.getDelay() - 1, "Incorrect interval length");
			for (Node successor : node.allSuccessors().keySet()) {
				int succStartTime = schedule.getNodes().get(successor).lbound;
				assertTrue(succStartTime > endTime, "Data dependency violated");
			}
		}
	}

}