import java.util.List;

import org.cloudsimplus.vms.Vm;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class GsaTest {

    /**
     * Section 10 test 1:
     * T_max = 11, Rutil_sum = 1.45, gamma = delta = 1, eps = 0 -> f = 7.586
     */
    @Test
    void testPaperExampleFitnessGsa1() {
        double f = GsaFitness.computeRawFitness(11.0, 1.45, 1.0, 1.0, 0.0, 0.0);
        assertEquals(7.586, f, 0.001);
    }

    /**
     * Section 10 test 2:
     * T_max = 7, Rutil_sum = 1.65 -> f = 4.242
     */
    @Test
    void testPaperExampleFitnessGsa2() {
        double f = GsaFitness.computeRawFitness(7.0, 1.65, 1.0, 1.0, 0.0, 0.0);
        assertEquals(4.242, f, 0.001);
    }

    /**
     * Section 10 test 3:
     * Masses: 2 agents, best = 4.242, worst = 7.586 -> M_B approx 1, M_A approx 0
     */
    @Test
    void testPaperExampleMassGsa() {
        double[] fitnesses = new double[]{7.586, 4.242}; // agent A (worst), agent B (best)
        double[] masses = Gsa.computeMasses(fitnesses);

        assertTrue(masses[0] < 0.05, "Worst agent mass M_A should be close to 0, was " + masses[0]);
        assertTrue(masses[1] > 0.95, "Best agent mass M_B should be close to 1, was " + masses[1]);
        assertEquals(1.0, masses[0] + masses[1], 1e-6);
    }

    @Test
    void testGsaExecutionOnWorkload() throws Exception {
        var path = DatasetLoader.resolvePath("wave1/workload_100.csv");
        List<TaskSpec> tasks = DatasetLoader.load(path);
        // Subset 30 tasks for rapid test
        if (tasks.size() > 30) tasks = tasks.subList(0, 30);
        List<Vm> vms = DatacenterFactory.buildVms();

        ScheduleEvaluator evaluator = new ScheduleEvaluator();
        var olbRes = Baselines.runOlb(tasks, vms, evaluator);
        ScheduleEvaluator.BaselineReference ref = new ScheduleEvaluator.BaselineReference(
                olbRes.fitness(), 100.0, olbRes.fitness(), 100.0
        );

        Gsa gsa = new Gsa(10, 100, 100.0, 20.0, new GsaFitness(), ScheduleEvaluator.QueuePolicy.FCFS);
        var res = gsa.schedule(tasks, vms, null, ref, 42);

        assertEquals(tasks.size(), res.mapping().length);
        assertTrue(res.fitness() > 0);
        assertTrue(res.convergenceHistory().size() > 1);
    }
}
