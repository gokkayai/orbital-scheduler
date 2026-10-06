import java.util.ArrayList;
import java.util.List;

/** A synthetic small compute payload; energy is stored in Wh, power in W. */
public final class ComputeNode {
    public static final double BATTERY_CAPACITY_WH = 100;
    public static final double RESERVE_WH = 20;
    public static final double BASELINE_WATTS = 5;
    public static final double GPU_WATTS = 20;
    public static final double TRANSFER_WATTS = 5;
    private static final double EPSILON = 1e-9;

    private final int id;
    private final int gpuCapacity;
    private final double solarWatts;
    private final double bandwidthMbps;
    private final List<Job> activeJobs = new ArrayList<>();
    private double batteryWh;
    private boolean sunlight;

    public ComputeNode(int id, int gpuCapacity, double batteryWh,
                       double solarWatts, double bandwidthMbps) {
        if (id < 0 || gpuCapacity < 1 || !Double.isFinite(batteryWh)
                || batteryWh < 0 || batteryWh > BATTERY_CAPACITY_WH
                || !Double.isFinite(solarWatts) || solarWatts < 0
                || !Double.isFinite(bandwidthMbps) || bandwidthMbps <= 0) {
            throw new IllegalArgumentException("Invalid node capacity, power, or bandwidth");
        }
        this.id = id;
        this.gpuCapacity = gpuCapacity;
        this.batteryWh = batteryWh;
        this.solarWatts = solarWatts;
        this.bandwidthMbps = bandwidthMbps;
    }

    public int id() { return id; }
    public int gpuCapacity() { return gpuCapacity; }
    public double batteryWh() { return batteryWh; }
    public boolean isSunlit() { return sunlight; }
    public double solarWatts() { return solarWatts; }
    public double bandwidthMbps() { return bandwidthMbps; }

    public int usedGpus() {
        return activeJobs.stream().mapToInt(Job::gpuRequirement).sum();
    }

    public int availableGpus() { return gpuCapacity - usedGpus(); }

    public int transferMinutes(Job job) {
        return (int) Math.ceil(job.inputGb() * 8000 / bandwidthMbps / 60);
    }

    public boolean canAccept(Job job) {
        double startingWatts = job.inputGb() > 0
                ? TRANSFER_WATTS : GPU_WATTS * job.gpuRequirement();
        return job.status() == Job.Status.QUEUED
                && availableGpus() >= job.gpuRequirement()
                && batteryWh > RESERVE_WH
                && canPower(activeWatts() + startingWatts);
    }

    private double activeWatts() {
        double watts = BASELINE_WATTS;
        for (Job job : activeJobs) {
            watts += job.status() == Job.Status.TRANSFERRING
                    ? TRANSFER_WATTS : GPU_WATTS * job.gpuRequirement();
        }
        return watts;
    }

    private boolean canPower(double watts) {
        return batteryWh + (generationWatts() - watts) / 60 >= RESERVE_WH - EPSILON;
    }

    private double generationWatts() { return sunlight ? solarWatts : 0; }

    void setSunlight(boolean sunlight) { this.sunlight = sunlight; }

    void assign(Job job) {
        if (!canAccept(job)) throw new IllegalArgumentException("Node cannot accept job");
        job.start(transferMinutes(job));
        activeJobs.add(job);
    }

    void releaseFinishedJobs() { activeJobs.removeIf(Job::isFinished); }

    /** Advance one minute and return consumed energy in Wh, not net battery drain. */
    double advanceMinute() {
        double watts = activeWatts();
        if (canPower(watts)) {
            activeJobs.forEach(Job::advanceMinute);
        } else {
            activeJobs.forEach(Job::waitOneMinute);
            watts = BASELINE_WATTS;
        }
        double generatedWh = generationWatts() / 60;
        double consumedWh = Math.min(watts / 60, batteryWh + generatedWh);
        batteryWh = Math.max(0, Math.min(BATTERY_CAPACITY_WH,
                batteryWh + generatedWh - consumedWh));
        releaseFinishedJobs();
        return consumedWh;
    }
}
