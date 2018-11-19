package scheduler.testing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Iterator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import scheduler.*;

class Test_ASAP_Fixed {
	
	private static Graph graph;
	private static String resourceFile = "resources/heterogenous_16res";
	private static String graphFile = "graphs/testCyclic.dot";
	private static Schedule reference;
	
	@BeforeAll
	static void setUpBeforeClass() throws Exception {
		RC rc = new RC();
		rc.parse(resourceFile);
		
		Dot_reader dr = new Dot_reader(false);
		graph = dr.parse(graphFile);
		System.out.printf("%s%n", graph.diagnose());
	}

	@AfterAll
	static void tearDownAfterClass() throws Exception {
	}

	@BeforeEach
	void setUp() throws Exception {
		reference = new ASAP().schedule(graph);
	}

	@AfterEach
	void tearDown() throws Exception {
	}

	@Test
	void testASAPNoFixed() {
		Schedule partialSchedule = new Schedule();
		Schedule asap_fixed = new ASAP_Fixed().schedule(graph, partialSchedule);
		
		assertEquals(asap_fixed.length(), reference.length(),
				"Schedule should be the same length as the reference ASAP schedule");
		for (Node node : asap_fixed.getNodes().keySet()) {
			int startTime = asap_fixed.getNodes().get(node).lbound;
			int endTime = asap_fixed.getNodes().get(node).ubound;
			assertEquals(endTime - startTime, node.getDelay() - 1, "Incorrect interval length");
			for (Node successor : node.allSuccessors().keySet()) {
				int succStartTime = asap_fixed.getNodes().get(successor).lbound;
				assertTrue(succStartTime > endTime, "Data dependency violated");
			}
		}
	}
	
	@Test
	void testASAPOneFixed() {
		Schedule partialSchedule = new Schedule();
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
		for (Node node : asap_fixed.getNodes().keySet()) {
			int startTime = asap_fixed.getNodes().get(node).lbound;
			int endTime = asap_fixed.getNodes().get(node).ubound;
			assertEquals(endTime - startTime, node.getDelay() - 1, "Incorrect interval length");
			for (Node successor : node.allSuccessors().keySet()) {
				int succStartTime = asap_fixed.getNodes().get(successor).lbound;
				assertTrue(succStartTime > endTime, "Data dependency violated");
			}
		}
	}
	
	@Test
	void testASAPTwoFixed() {
		Schedule partialSchedule = new Schedule();
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

}