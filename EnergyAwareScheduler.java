import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Balances transfer delay against a small penalty for using scarce battery energy.
 * The score is a scheduling preference, not a prediction of future power.
 */
public final class EnergyAwareScheduler implements SchedulingStrategy {
    static final double BATTERY_PENALTY_MINUTES = 30;
    private static final double SUNLIGHT_MULTIPLIER = 0.5;
    private static final double ECLIPSE_MULTIPLIER = 1.0;

    @Override
    public Optional<ComputeNode> chooseNode(Job job, List<ComputeNode> nodes) {
        return nodes.stream()
                .filter(node -> node.canAccept(job))
                .min(Comparator.comparingDouble((ComputeNode node) -> score(job, node))
                        .thenComparing(Comparator.comparingInt(ComputeNode::availableGpus).reversed())
                        .thenComparingInt(ComputeNode::id));
    }

    double score(Job job, ComputeNode node) {
        double batteryFraction = node.batteryWh() / ComputeNode.BATTERY_CAPACITY_WH;
        double sunlightMultiplier = node.isSunlit()
                ? SUNLIGHT_MULTIPLIER : ECLIPSE_MULTIPLIER;
        double batteryPenalty = BATTERY_PENALTY_MINUTES
                * (1 - batteryFraction) * sunlightMultiplier;
        return node.transferMinutes(job) + batteryPenalty;
    }
}
