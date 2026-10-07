import java.util.List;

import org.cloudsimplus.vms.Vm;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class TabwoTest {

    /**
     * Section 10:
     * u1 = [2,5,1,4], u2 = [3,1,6,2], alpha = [0.5, 0.2, 0.8, 0.5]
     * -> v1 = [2.5, 1.8, 2.0, 3.0], v2 = [2.5, 4.2, 5.0, 3.0]
     * and v1 + v2 == u1 + u2
     */
    @Test
    void testPaperExampleProcreation() {
        double[] u1 = {2.0, 5.0, 1.0, 4.0};
        double[] u2 = {3.0, 1.0, 6.0, 2.0};
        double[] alpha = {0.5, 0.2, 0.8, 0.5};

        double[][] children = Tabwo.procreate(u1, u2, alpha);
        double[] v1 = children[0];
        double[] v2 = children[1];

        assertEquals(2.5, v1[0], 1e-6);
        assertEquals(1.8, v1[1], 1e-6);
        assertEquals(2.0, v1[2], 1e-6);
        assertEquals(3.0, v1[3], 1e-6);

        assertEquals(2.5, v2[0], 1e-6);
        assertEquals(4.2, v2[1], 1e-6);
        assertEquals(5.0, v2[2], 1e-6);
        assertEquals(3.0, v2[3], 1e-6);

        for (int k = 0; k < 4; k++) {
            assertEquals(u1[k] + u2[k], v1[k] + v2[k], 1e-6);
        }
    }

    /**
     * Section 10:
     * L = 100, t_M = 50 -> t=1: 0.020, t=25: 0.0151, t=50: 0.010
     */
    @Test
    void testPaperExampleAdaptiveMutationRate() {
        int l = 100;
        int tMax = 50;

        double mr1 = Tabwo.computeAdaptiveMutationRate(1, tMax, l);
        assertEquals(0.020, mr1, 0.0001);

        double mr25 = Tabwo.computeAdaptiveMutationRate(25, tMax, l);
        assertEquals(0.0151, mr25, 0.0005);

        double mr50 = Tabwo.computeAdaptiveMutationRate(50, tMax, l);
        assertEquals(0.010, mr50, 0.0001);
    }

    /**
     * Section 10:
     * Electricity prices: 0.10, 0.15, 0.20 -> prio_VM = [2.00, 1.33, 1.00]
     */
    @Test
    void testPaperExampleVmPriority() {
        double[] prices = {0.10, 0.15, 0.20};
        double[] prio = PriorityModel.computeVmPriorities(prices);

        assertEquals(2.00, prio[0], 0.01);
        assertEquals(1.33, prio[1], 0.01);
        assertEquals(1.00, prio[2], 0.01);
    }

    /**
     * Section 10:
     * pr_no = 2, prMIPS = 1050, mMIPS = 500, mpr = 2 -> size = 1000, prio = 0.476
     */
    @Test
    void testPaperExampleTaskPriority() {
        double vmCap = PriorityModel.computeVmCapacity(2, 1050); // 2100
        double taskSize = PriorityModel.computeTaskSize(500, 2);  // 1000
        double prio = PriorityModel.computeTaskPriority(taskSize, vmCap);

        assertEquals(1000.0, taskSize, 1e-6);
        assertEquals(0.476, prio, 0.001);
    }

    /**
     * Section 10:
     * AV = 0.9, SR = 0.9, TE = 0.8 -> 0.71 (addition form)
     */
    @Test
    void testPaperExampleTrust() {
        double trust = TrustModel.computeRawTrust(0.9, 0.9, 0.8, 0.5, 0.2, 0.1);
        assertEquals(0.71, trust, 1e-6);
    }

    @Test
    void testTabwoExecutionOnWorkload() throws Exception {
        var path = DatasetLoader.resolvePath("wave1/workload_100.csv");
        List<TaskSpec> tasks = DatasetLoader.load(path);
        if (tasks.size() > 30) tasks = tasks.subList(0, 30);
        List<Vm> vms = DatacenterFactory.buildVms();

        ScheduleEvaluator evaluator = new ScheduleEvaluator();
        var olbRes = Baselines.runOlb(tasks, vms, evaluator);
        ScheduleEvaluator.BaselineReference ref = new ScheduleEvaluator.BaselineReference(
                olbRes.fitness(), 100.0, olbRes.fitness(), 100.0
        );

        Tabwo tabwo = new Tabwo(10, 100, 0.6, 0.44, 0.4, 20.0, new TabwoFitness(),
                ScheduleEvaluator.QueuePolicy.PRIORITY, true);
        var res = tabwo.schedule(tasks, vms, null, ref, 42);

        assertEquals(tasks.size(), res.mapping().length);
        assertTrue(res.fitness() > 0);
        assertTrue(res.convergenceHistory().size() > 1);
    }
}
