import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class LeastLoadedScheduler implements SchedulingStrategy {
    @Override
    public Optional<ComputeNode> chooseNode(Job job, List<ComputeNode> nodes) {
        return nodes.stream()
                .filter(node -> node.canAccept(job))
                .min(Comparator.comparingInt(ComputeNode::availableGpus).reversed()
                        .thenComparingInt(ComputeNode::id));
    }
}
