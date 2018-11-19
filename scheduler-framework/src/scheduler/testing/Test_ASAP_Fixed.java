package scheduler.testing;

import static org.junit.jupiter.api.Assertions.*;

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
		//System.out.printf("%s%n", g.diagnose());
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
		asap_fixed.draw("schedules/ASAP_Fixed_" + graphFile.substring(graphFile.lastIndexOf("/")+1));
		
		assertEquals(reference.length(), asap_fixed.length(), "Schedule should be the same length as the reference ASAP schedule");
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

}