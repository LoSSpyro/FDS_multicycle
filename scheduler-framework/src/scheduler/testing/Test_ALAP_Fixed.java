package scheduler.testing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Iterator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
		reference = new ASAP().schedule(graph);
		partialSchedule = new Schedule();
	}

	@Test
	void testASAPNoFixed() {
		Schedule asap_fixed = new ASAP_Fixed().schedule(graph, partialSchedule);
		
		assertEquals(asap_fixed.length(), reference.length(),
				"Schedule should be the same length as the reference ASAP schedule");
		checkIntervalsAndDependencies(asap_fixed);
	}
	
	@Test
	void testASAPOneFixed() {
		Iterator<Node> iter = graph.iterator();
		Node n1 = null;
		while (iter.hasNext()) {
			n1 = iter.next();
			if (n1.id.equals("N5_ADD")) {
				partialSchedule.add(n1, new Interval(9, 9));
				break;
			}
		}
		Schedule asap_fixed = new ASAP_Fixed().schedule(graph, partialSchedule);
		
		assertTrue(asap_fixed.length() >= reference.length(),
				"Schedule cannot be shorter than the reference ASAP schedule");
		assertEquals(asap_fixed.length().intValue(), 12, "Optimal schedule under the given constraints wasn't found");
		checkIntervalsAndDependencies(asap_fixed);
	}
	
	@Test
	void testASAPTwoFixed() {
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
		Schedule asap_fixed = new ASAP_Fixed().schedule(graph, partialSchedule);
		
		assertTrue(asap_fixed == null,
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