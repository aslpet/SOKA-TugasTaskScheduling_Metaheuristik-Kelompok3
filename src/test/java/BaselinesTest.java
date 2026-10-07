import java.util.List;

import org.cloudsimplus.vms.Vm;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class BaselinesTest {

    @Test
    void testBaselinesExecution() throws Exception {
        var path = DatasetLoader.resolvePath("wave1/workload_100.csv");
        List<TaskSpec> tasks = DatasetLoader.load(path);
        List<Vm> vms = DatacenterFactory.buildVms();
        ScheduleEvaluator evaluator = new ScheduleEvaluator();

        var olbRes = Baselines.runOlb(tasks, vms, evaluator);
        assertEquals(tasks.size(), olbRes.mapping().length);
        assertTrue(olbRes.fitness() > 0);

        var minMinRes = Baselines.runMinMin(tasks, vms, evaluator);
        assertEquals(tasks.size(), minMinRes.mapping().length);
        assertTrue(minMinRes.fitness() > 0);

        var maxMinRes = Baselines.runMaxMin(tasks, vms, evaluator);
        assertEquals(tasks.size(), maxMinRes.mapping().length);
        assertTrue(maxMinRes.fitness() > 0);

        // Min-Min should typically have a better (lower) makespan than naive OLB
        assertTrue(minMinRes.fitness() <= olbRes.fitness() * 1.05,
                String.format("Min-Min makespan (%.2f) should be competitive with OLB (%.2f)",
                        minMinRes.fitness(), olbRes.fitness()));
    }
}
