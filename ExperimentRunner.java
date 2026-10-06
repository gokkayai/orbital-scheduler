import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/** Generates reproducible workloads and compares both schedulers fairly. */
public final class ExperimentRunner {
    private static final int ARRIVAL_WINDOW_MINUTES = 180;
    private static final int DEADLINE_WINDOW_MINUTES = 90;
    private static final int END_MINUTE = ARRIVAL_WINDOW_MINUTES + DEADLINE_WINDOW_MINUTES;
    private static final Path RESULTS_FILE = Path.of("results.csv");

    private enum Scenario {
        LIGHT("Low Workload + Healthy Batteries", 40, 80, 100, 0.1, 30, false),
        HIGH_LOAD("High Workload", 240, 80, 100, 0.1, 30, false),
        POWER_CONSTRAINED("Low Batteries + Long Eclipse", 120, 25, 45, 0.1, 35, false),
        TRANSFER_CONSTRAINED("Large Transfers + Slower Links", 120, 80, 100, 5.0, 30, true);

        final String label;
        final int jobsPerTenNodes;
        final double minimumBattery;
        final double maximumBattery;
        final double inputGb;
        final int eclipseMinutes;
        final boolean mixedBandwidth;

        Scenario(String label, int jobsPerTenNodes, double minimumBattery,
                 double maximumBattery, double inputGb, int eclipseMinutes,
                 boolean mixedBandwidth) {
            this.label = label;
            this.jobsPerTenNodes = jobsPerTenNodes;
            this.minimumBattery = minimumBattery;
            this.maximumBattery = maximumBattery;
            this.inputGb = inputGb;
            this.eclipseMinutes = eclipseMinutes;
            this.mixedBandwidth = mixedBandwidth;
        }
    }

    record ExperimentResult(String scenario, String strategy, long seed,
                            int nodes, Simulator.Result metrics) {}

    public static void main(String[] args) throws IOException {
        Options options = parseOptions(args);
        List<ExperimentResult> results = runExperiments(options.seed, options.nodes);
        printResults(results);
        writeCsv(results);
        System.out.println("Results written to " + RESULTS_FILE.toAbsolutePath());
    }

    static List<ExperimentResult> runExperiments(long seed, int nodeCount) {
        if (seed < 0 || seed > Long.MAX_VALUE - Scenario.values().length) {
            throw new IllegalArgumentException("Seed must be between 0 and "
                    + (Long.MAX_VALUE - Scenario.values().length));
        }
        if (nodeCount < 1 || nodeCount > 1000) {
            throw new IllegalArgumentException("Node count must be between 1 and 1000");
        }

        List<ExperimentResult> results = new ArrayList<>();
        for (Scenario scenario : Scenario.values()) {
            long scenarioSeed = seed + scenario.ordinal();
            results.add(runOnce(scenario, "Least Loaded", seed, scenarioSeed, nodeCount,
                    new LeastLoadedScheduler()));
            results.add(runOnce(scenario, "Energy Aware", seed, scenarioSeed, nodeCount,
                    new EnergyAwareScheduler()));
        }
        return List.copyOf(results);
    }

    private static ExperimentResult runOnce(Scenario scenario, String strategyName,
                                            long requestedSeed, long workloadSeed,
                                            int nodeCount, SchedulingStrategy strategy) {
        List<ComputeNode> nodes = generateNodes(scenario, workloadSeed, nodeCount);
        List<Job> jobs = generateJobs(scenario, workloadSeed, nodeCount);
        Simulator.Result metrics = new Simulator(nodes, jobs, strategy,
                scenario.eclipseMinutes).run(END_MINUTE);
        return new ExperimentResult(scenario.label, strategyName, requestedSeed, nodeCount, metrics);
    }

    private static List<ComputeNode> generateNodes(Scenario scenario, long seed, int count) {
        SplittableRandom random = new SplittableRandom(seed * 31 + 1);
        List<ComputeNode> nodes = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            double battery = random.nextDouble(scenario.minimumBattery, scenario.maximumBattery);
            double solarWatts = random.nextDouble(40, 80);
            double bandwidth = scenario.mixedBandwidth
                    ? switch (index % 3) { case 0 -> 10; case 1 -> 50; default -> 100; }
                    : 100;
            nodes.add(new ComputeNode(index + 1, 2, battery, solarWatts, bandwidth));
        }
        return nodes;
    }

    private static List<Job> generateJobs(Scenario scenario, long seed, int nodeCount) {
        SplittableRandom random = new SplittableRandom(seed * 31 + 2);
        int jobCount = Math.max(1, (scenario.jobsPerTenNodes * nodeCount + 5) / 10);
        List<Job> jobs = new ArrayList<>(jobCount);
        for (int index = 0; index < jobCount; index++) {
            int arrival = random.nextInt(ARRIVAL_WINDOW_MINUTES);
            int gpus = random.nextInt(1, 3);
            int computeMinutes = random.nextInt(5, 21);
            jobs.add(new Job(index + 1, arrival, gpus, computeMinutes,
                    scenario.inputGb, arrival + DEADLINE_WINDOW_MINUTES));
        }
        return jobs;
    }

    private static void printResults(List<ExperimentResult> results) {
        for (int index = 0; index < results.size(); index += 2) {
            ExperimentResult baseline = results.get(index);
            ExperimentResult aware = results.get(index + 1);
            System.out.println("Scenario: " + baseline.scenario());
            printStrategy(baseline);
            printStrategy(aware);
            System.out.println("Difference (Energy Aware - Least Loaded)");
            System.out.printf(Locale.ROOT,
                    "Completion: %+.1f percentage points%nDeadline misses: %+.1f percentage points%n"
                            + "Average wait: %+.1f min%nEnergy: %+.1f%%%n%n",
                    aware.metrics().completedPercent() - baseline.metrics().completedPercent(),
                    aware.metrics().missedPercent() - baseline.metrics().missedPercent(),
                    aware.metrics().averageWaitingMinutes()
                            - baseline.metrics().averageWaitingMinutes(),
                    percentChange(aware.metrics().energyKwh(), baseline.metrics().energyKwh()));
        }
        System.out.println("Waiting includes queue time and power pauses across all submitted jobs.");
    }

    private static void printStrategy(ExperimentResult result) {
        Simulator.Result metrics = result.metrics();
        System.out.println("\n" + result.strategy());
        System.out.printf(Locale.ROOT,
                "Completed: %.1f%%%nDeadline misses: %d (%.1f%%)%n"
                        + "Average wait: %.1f min%nEnergy consumed: %.3f kWh%n",
                metrics.completedPercent(), metrics.deadlineMisses(), metrics.missedPercent(),
                metrics.averageWaitingMinutes(), metrics.energyKwh());
    }

    private static double percentChange(double value, double baseline) {
        if (baseline == 0) return 0;
        double change = 100 * (value - baseline) / baseline;
        return Math.abs(change) < 0.05 ? 0 : change;
    }

    private static void writeCsv(List<ExperimentResult> results) throws IOException {
        StringBuilder csv = new StringBuilder("scenario,strategy,seed,nodes,jobs,completed_percent,"
                + "deadline_misses,deadline_miss_percent,average_wait_minutes,energy_kwh\n");
        for (ExperimentResult result : results) {
            Simulator.Result metrics = result.metrics();
            csv.append(String.format(Locale.ROOT,
                    "%s,%s,%d,%d,%d,%.3f,%d,%.3f,%.3f,%.6f%n",
                    result.scenario(), result.strategy(), result.seed(), result.nodes(),
                    metrics.submitted(), metrics.completedPercent(), metrics.deadlineMisses(),
                    metrics.missedPercent(), metrics.averageWaitingMinutes(), metrics.energyKwh()));
        }
        Files.writeString(RESULTS_FILE, csv);
    }

    private static Options parseOptions(String[] args) {
        long seed = 42;
        int nodes = 10;
        for (int index = 0; index < args.length; index += 2) {
            if (index + 1 >= args.length) {
                throw new IllegalArgumentException("Options require a value: --seed N --nodes N");
            }
            switch (args[index]) {
                case "--seed" -> seed = Long.parseLong(args[index + 1]);
                case "--nodes" -> nodes = Integer.parseInt(args[index + 1]);
                default -> throw new IllegalArgumentException("Unknown option: " + args[index]);
            }
        }
        return new Options(seed, nodes);
    }

    private record Options(long seed, int nodes) {}
}
