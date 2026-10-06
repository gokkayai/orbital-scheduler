import java.util.List;
import java.util.Optional;

public interface SchedulingStrategy {
    /** Select an eligible node without changing job or node state. */
    Optional<ComputeNode> chooseNode(Job job, List<ComputeNode> nodes);
}
