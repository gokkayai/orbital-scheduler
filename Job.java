/** Job requirements use GPU units, whole minutes, and decimal gigabytes. */
public final class Job {
    public enum Status { QUEUED, TRANSFERRING, COMPUTING, COMPLETED, MISSED }

    private final int id;
    private final int arrivalMinute;
    private final int gpuRequirement;
    private final int computeMinutes;
    private final double inputGb;
    private final int deadlineMinute;
    private Status status = Status.QUEUED;
    private int remainingCompute;
    private int remainingTransfer;
    private int waitingMinutes;

    public Job(int id, int arrivalMinute, int gpuRequirement, int computeMinutes,
               double inputGb, int deadlineMinute) {
        if (id < 0 || arrivalMinute < 0 || gpuRequirement < 1 || computeMinutes < 1
                || !Double.isFinite(inputGb) || inputGb < 0 || deadlineMinute <= arrivalMinute) {
            throw new IllegalArgumentException("Invalid job requirements or arrival/deadline");
        }
        this.id = id;
        this.arrivalMinute = arrivalMinute;
        this.gpuRequirement = gpuRequirement;
        this.computeMinutes = computeMinutes;
        this.remainingCompute = computeMinutes;
        this.inputGb = inputGb;
        this.deadlineMinute = deadlineMinute;
    }

    public int id() { return id; }
    public int arrivalMinute() { return arrivalMinute; }
    public int gpuRequirement() { return gpuRequirement; }
    public int computeMinutes() { return computeMinutes; }
    public double inputGb() { return inputGb; }
    public int deadlineMinute() { return deadlineMinute; }
    public Status status() { return status; }
    public int waitingMinutes() { return waitingMinutes; }

    public boolean isFinished() {
        return status == Status.COMPLETED || status == Status.MISSED;
    }

    void start(int transferMinutes) {
        remainingTransfer = transferMinutes;
        status = transferMinutes == 0 ? Status.COMPUTING : Status.TRANSFERRING;
    }

    void advanceMinute() {
        if (status == Status.TRANSFERRING) {
            if (--remainingTransfer == 0) status = Status.COMPUTING;
        } else if (status == Status.COMPUTING) {
            if (--remainingCompute == 0) status = Status.COMPLETED;
        }
    }

    void waitOneMinute() { waitingMinutes++; }
    void missDeadline() { status = Status.MISSED; }
}
