import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimulatorTest {
    @Test
    void leastLoadedChoosesMostFreeGpusThenLowestId() {
        ComputeNode busy = new ComputeNode(1, 4, 90, 60, 100);
        busy.assign(new Job(1, 0, 3, 10, 0, 30));
        ComputeNode free = new ComputeNode(2, 2, 90, 60, 100);
        ComputeNode tied = new ComputeNode(3, 2, 90, 60, 100);
        Job incoming = new Job(2, 0, 1, 5, 0, 30);

        assertSame(free, new LeastLoadedScheduler()
                .chooseNode(incoming, List.of(tied, busy, free)).orElseThrow());
        assertEquals(1, busy.availableGpus());
    }

    @Test
    void insufficientResourcesDoNotBlockSmallerJobsAndCountAsWaiting() {
        ComputeNode node = new ComputeNode(1, 1, 90, 60, 100);
        Job tooLarge = new Job(1, 0, 2, 1, 0, 3);
        Job fits = new Job(2, 0, 1, 1, 0, 3);

        assertTrue(new LeastLoadedScheduler().chooseNode(tooLarge, List.of(node)).isEmpty());
        Simulator.Result result = new Simulator(List.of(node), List.of(tooLarge, fits),
                new LeastLoadedScheduler(), 30).run(3);

        assertEquals(Job.Status.MISSED, tooLarge.status());
        assertEquals(Job.Status.COMPLETED, fits.status());
        assertEquals(1, result.completed());
        assertEquals(1, result.deadlineMisses());
        assertEquals(1.5, result.averageWaitingMinutes());
        assertEquals(0, node.usedGpus());
    }

    @Test
    void batteryPauseRetainsProgressAndSunlightResumesWork() {
        ComputeNode node = new ComputeNode(1, 1, 20.5, 60, 100);
        Job job = new Job(1, 0, 1, 2, 0, 10);
        node.assign(job);

        assertEquals(25.0 / 60, node.advanceMinute(), 1e-9);
        assertEquals(Job.Status.COMPUTING, job.status());
        assertEquals(5.0 / 60, node.advanceMinute(), 1e-9);
        assertEquals(1, job.waitingMinutes());
        assertEquals(1, node.usedGpus());
        assertEquals(20, node.batteryWh(), 1e-9);

        node.setSunlight(true);
        assertEquals(25.0 / 60, node.advanceMinute(), 1e-9);
        assertEquals(Job.Status.COMPLETED, job.status());
        assertEquals(0, node.usedGpus());
        assertEquals(20 + 35.0 / 60, node.batteryWh(), 1e-9);
    }

    @Test
    void transferAndDeadlineBoundariesReleaseResourcesAndAccountForEnergy() {
        ComputeNode node = new ComputeNode(1, 1, 90, 60, 100);
        Job onTime = new Job(1, 0, 1, 1, 0.1, 2);
        Job expires = new Job(2, 2, 1, 2, 0, 3);
        Job afterExpiry = new Job(3, 3, 1, 1, 0, 4);
        Simulator simulation = new Simulator(List.of(node), List.of(onTime, expires, afterExpiry),
                new LeastLoadedScheduler(), 30);

        Simulator.Result result = simulation.run(4);

        assertEquals(Job.Status.COMPLETED, onTime.status());
        assertEquals(Job.Status.MISSED, expires.status());
        assertEquals(Job.Status.COMPLETED, afterExpiry.status());
        assertEquals(0, node.usedGpus());
        assertEquals(0, result.averageWaitingMinutes());
        // One transfer minute (5 W baseline + 5 W transfer), then three compute minutes (25 W).
        assertEquals(85.0 / 60 / 1000, result.energyKwh(), 1e-9);
        assertThrows(IllegalStateException.class, () -> simulation.run(4));
    }
}
