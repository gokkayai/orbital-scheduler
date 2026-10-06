import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Single-use, deterministic simulation. Supplied nodes and jobs are mutated. */
public final class Simulator {
    public static final int ORBIT_MINUTES = 90;

    private final List<ComputeNode> nodes;
    private final List<Job> jobs;
    private final SchedulingStrategy strategy;
    private final int eclipseMinutes;
    private boolean hasRun;

    public Simulator(List<ComputeNode> nodes, List<Job> jobs,
                     SchedulingStrategy strategy, int eclipseMinutes) {
        if (nodes.isEmpty() || eclipseMinutes < 0 || eclipseMinutes >= ORBIT_MINUTES
                || strategy == null) {
            throw new IllegalArgumentException("Supply nodes, a strategy, and a valid eclipse duration");
        }
        Set<Integer> nodeIds = new HashSet<>();
        Set<Integer> jobIds = new HashSet<>();
        for (ComputeNode node : nodes) {
            if (!nodeIds.add(node.id()) || node.usedGpus() != 0) {
                throw new IllegalArgumentException("Nodes must have unique IDs and no active jobs");
            }
        }
        for (Job job : jobs) {
            if (!jobIds.add(job.id()) || job.status() != Job.Status.QUEUED
                    || job.waitingMinutes() != 0) {
                throw new IllegalArgumentException("Jobs must have unique IDs and be unused");
            }
        }
        this.nodes = nodes.stream().sorted(Comparator.comparingInt(ComputeNode::id)).toList();
        this.jobs = jobs.stream().sorted(Comparator.comparingInt(Job::arrivalMinute)
                .thenComparingInt(Job::id)).toList();
        this.strategy = strategy;
        this.eclipseMinutes = eclipseMinutes;
    }

    public Result run(int endMinute) {
        if (hasRun) throw new IllegalStateException("Create fresh jobs and nodes for each run");
        if (endMinute < 1 || jobs.stream().anyMatch(job -> job.deadlineMinute() > endMinute)) {
            throw new IllegalArgumentException("Run must include every job's deadline");
        }
        hasRun = true;
        double energyWh = 0;
        for (int minute = 0; minute < endMinute; minute++) {
            expireDeadlines(minute);
            for (int index = 0; index < nodes.size(); index++) {
                int phase = index * ORBIT_MINUTES / nodes.size();
                nodes.get(index).setSunlight((minute + phase) % ORBIT_MINUTES
                        < ORBIT_MINUTES - eclipseMinutes);
            }
            for (Job job : jobs) {
                if (job.arrivalMinute() <= minute && job.status() == Job.Status.QUEUED) {
                    strategy.chooseNode(job, nodes).ifPresent(node -> node.assign(job));
                    if (job.status() == Job.Status.QUEUED) job.waitOneMinute();
                }
            }
            for (ComputeNode node : nodes) energyWh += node.advanceMinute();
        }
        expireDeadlines(endMinute);
        int completed = (int) jobs.stream().filter(job -> job.status() == Job.Status.COMPLETED).count();
        double averageWait = jobs.stream().mapToInt(Job::waitingMinutes).average().orElse(0);
        return new Result(jobs.size(), completed, jobs.size() - completed, averageWait, energyWh / 1000);
    }

    private void expireDeadlines(int minute) {
        for (Job job : jobs) {
            if (!job.isFinished() && job.deadlineMinute() <= minute) job.missDeadline();
        }
        nodes.forEach(ComputeNode::releaseFinishedJobs);
    }

    public record Result(int submitted, int completed, int deadlineMisses,
                         double averageWaitingMinutes, double energyKwh) {
        public double completedPercent() { return submitted == 0 ? 0 : 100.0 * completed / submitted; }
        public double missedPercent() { return submitted == 0 ? 0 : 100.0 * deadlineMisses / submitted; }
    }
}
