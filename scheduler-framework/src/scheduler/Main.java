package scheduler;

public class Main {
	
	private static Graph g;
	private static String arg0;

	public static void main(String[] args) {
		//parse(args);
		
		defaultMain(args);
	}

	public static void testASAPFixed() {
		ASAP asap = new ASAP();
		Schedule sched = asap.schedule(g);
		System.out.printf("%nASAP%n%s%n", sched.diagnose());
		System.out.printf("cost = %s%n", sched.cost());
		
		sched.draw("schedules/ASAP_" + arg0.substring(arg0.lastIndexOf("/")+1));
		
		ASAP_Fixed asap_fixed = new ASAP_Fixed();
		sched = asap_fixed.schedule(g);
		System.out.printf("%nASAP_Fixed%n%s%n", sched.diagnose());
		System.out.printf("cost = %s%n", sched.cost());
		
		sched.draw("schedules/ASAP_Fixed_" + arg0.substring(arg0.lastIndexOf("/")+1));
	}
	public static void testALAPFixed() {
		ALAP alap = new ALAP();
		Schedule sched = alap.schedule(g);
		System.out.printf("%nALAP%n%s%n", sched.diagnose());
		System.out.printf("cost = %s%n", sched.cost());
		
		sched.draw("schedules/ALAP_" + arg0.substring(arg0.lastIndexOf("/")+1));
		
		ALAP_Fixed alap_fixed = new ALAP_Fixed();
		sched = alap_fixed.schedule(g);
		System.out.printf("%nALAP_Fixed%n%s%n", sched.diagnose());
		System.out.printf("cost = %s%n", sched.cost());
		
		sched.draw("schedules/ALAP_Fixed_" + arg0.substring(arg0.lastIndexOf("/")+1));
	}
	
	public static void parse(String[] args) {
		RC rc = null;
		if (args.length>1){
			System.out.println("Reading resource constraints from "+args[1]+"\n");
			rc = new RC();
			rc.parse(args[1]);
		}
		
		Dot_reader dr = new Dot_reader(false);
		if (args.length < 1) {
			System.err.printf("Usage: scheduler dotfile%n");
			System.exit(-1);
		}else {
			System.out.println("Scheduling "+args[0]);
			System.out.println();
		}
		
		g = dr.parse(args[0]);
		System.out.printf("%s%n", g.diagnose());
		
		arg0 = args[0];
	}
	
	public static void defaultMain(String[] args) {
		RC rc = null;
		if (args.length>1){
			System.out.println("Reading resource constraints from "+args[1]+"\n");
			rc = new RC();
			rc.parse(args[1]);
		}
		
		Dot_reader dr = new Dot_reader(false);
		if (args.length < 1) {
			System.err.printf("Usage: scheduler dotfile%n");
			System.exit(-1);
		}else {
			System.out.println("Scheduling "+args[0]);
			System.out.println();
		}
		
		Graph g = dr.parse(args[0]);
		System.out.printf("%s%n", g.diagnose());
		
		Scheduler s = new ASAP();
		Schedule sched = s.schedule(g);
		System.out.printf("%nASAP%n%s%n", sched.diagnose());
		System.out.printf("cost = %s%n", sched.cost());
		
		sched.draw("schedules/ASAP_" + args[0].substring(args[0].lastIndexOf("/")+1));
		
		s = new ALAP();
		sched = s.schedule(g);
		System.out.printf("%nALAP%n%s%n", sched.diagnose());
		System.out.printf("cost = %s%n", sched.cost());
		
		sched.draw("schedules/ALAP_" + args[0].substring(args[0].lastIndexOf("/")+1));

		s = new FDS(25);
		System.out.println("\n\n\nStarting FD scheduling\n");
		Schedule fdsSched = s.schedule(g);
		if (fdsSched == null) {
			System.err.println("FD Scheduling failed");
			return;
		}
		//fdsSched.draw("schedules/FDS_" + args[0].substring(args[0].lastIndexOf("/")+1));
		System.out.println("\n\n\nCost: " + fdsSched.cost());
		System.out.println(fdsSched.diagnose());
	}
}
