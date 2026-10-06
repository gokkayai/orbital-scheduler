import java.util.List;
import java.util.Locale;

/** Milestone 1: a small baseline example. Paired experiments follow in a later milestone. */
public final class ExperimentRunner {
    public static void main(String[] args) {
        if (args.length != 0) {
            throw new IllegalArgumentException("This milestone runs a fixed example without arguments");
        }
        List<ComputeNode> nodes = List.of(
                new ComputeNode(1, 2, 90, 60, 100),
                new ComputeNode(2, 2, 90, 60, 100));
        List<Job> jobs = List.of(
                new Job(1, 0, 2, 10, 0, 30),
                new Job(2, 0, 2, 10, 0, 30),
                new Job(3, 0, 1, 5, 0, 30),
                new Job(4, 5, 1, 5, 0.1, 30));
        Simulator.Result result = new Simulator(nodes, jobs, new LeastLoadedScheduler(), 30).run(30);
        System.out.println("Scenario: Baseline example (milestone 1)\n\nLeast Loaded");
        System.out.printf(Locale.ROOT,
                "Completed: %.1f%%%nDeadline misses: %d (%.1f%%)%n"
                        + "Average wait: %.1f min%nEnergy consumed: %.4f kWh%n",
                result.completedPercent(), result.deadlineMisses(), result.missedPercent(),
                result.averageWaitingMinutes(), result.energyKwh());
        System.out.println("Waiting includes queue time and power pauses across all jobs.");
    }
}
