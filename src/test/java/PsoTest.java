import java.util.List;

import org.cloudsimplus.vms.Vm;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class PsoTest {

    @Test
    void testPsoDecoding() {
        double[] pos = {-1.5, 0.2, 5.8, 19.9, 25.0};
        int[] vm = Pso.decode(pos, 20);
        assertEquals(0, vm[0]);
        assertEquals(0, vm[1]);
        assertEquals(5, vm[2]);
        assertEquals(19, vm[3]);
        assertEquals(19, vm[4]);
    }

    @Test
    void testPsoExecutionOnWorkload() throws Exception {
        var path = DatasetLoader.resolvePath("wave1/workload_100.csv");
        List<TaskSpec> tasks = DatasetLoader.load(path);
        if (tasks.size() > 30) tasks = tasks.subList(0, 30);
        List<Vm> vms = DatacenterFactory.buildVms();

        ScheduleEvaluator evaluator = new ScheduleEvaluator();
        var olbRes = Baselines.runOlb(tasks, vms, evaluator);
        ScheduleEvaluator.BaselineReference ref = new ScheduleEvaluator.BaselineReference(
                olbRes.fitness(), 100.0, olbRes.fitness(), 100.0
        );

        Pso pso = new Pso(10, 100, 0.9, 0.4, 1.496, 1.496, 2.0, new CommonFitness(), ScheduleEvaluator.QueuePolicy.FCFS);
        var res = pso.schedule(tasks, vms, null, ref, 42);

        assertEquals(tasks.size(), res.mapping().length);
        assertTrue(res.fitness() > 0);
        assertTrue(res.convergenceHistory().size() > 1);
    }
}
