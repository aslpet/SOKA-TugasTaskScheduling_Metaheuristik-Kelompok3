import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.vms.Vm;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class ScheduleEvaluatorTest {

    @Test
    void testEvaluatorMatchesCloudSimOnWorkload() throws Exception {
        var path = DatasetLoader.resolvePath("pilot/workload_500.csv");
        if (!java.nio.file.Files.exists(path)) {
            path = DatasetLoader.resolvePath("wave1/workload_100.csv");
        }
        List<TaskSpec> tasks = DatasetLoader.load(path);
        // Subset first 50 tasks for fast unit test
        if (tasks.size() > 50) tasks = tasks.subList(0, 50);

        List<Vm> vms = DatacenterFactory.buildVms();

        // Generate a deterministic pseudorandom mapping
        Random rng = new Random(42);
        int[] mapping = new int[tasks.size()];
        for (int i = 0; i < mapping.length; i++) {
            mapping[i] = rng.nextInt(vms.size());
        }

        ScheduleEvaluator evaluator = new ScheduleEvaluator();
        var evalResult = evaluator.evaluate(mapping, tasks, vms, ScheduleEvaluator.QueuePolicy.FCFS);

        // Replay in CloudSim Plus
        CloudSimPlus sim = new CloudSimPlus();
        var infra = DatacenterFactory.buildDatacenters(sim);
        var broker = new DatacenterBrokerSimple(sim);
        List<Datacenter> dcs = infra.datacenters();
        broker.setDatacenterMapper((lastDc, vm) -> dcs.get((int) (vm.getId() % dcs.size())));
        broker.submitVmList(vms);

        List<CloudletSimple> cloudlets = new ArrayList<>();
        Map<Long, Vm> targetVm = new HashMap<>();
        for (int i = 0; i < tasks.size(); i++) {
            TaskSpec t = tasks.get(i);
            Vm vm = vms.get(mapping[i]);
            CloudletSimple cl = DatasetLoader.toCloudlet(t);
            cl.setSubmissionDelay(evalResult.dispatchTimes()[i]);
            cl.setVm(vm);
            cloudlets.add(cl);
            targetVm.put(cl.getId(), vm);
        }
        broker.setVmMapper(cl -> targetVm.getOrDefault(cl.getId(), Vm.NULL));
        broker.submitCloudletList(cloudlets);

        sim.start();

        var finished = broker.getCloudletFinishedList();
        assertEquals(tasks.size(), finished.size(), "All cloudlets must finish");

        double cloudSimMakespan = finished.stream().mapToDouble(Cloudlet::getFinishTime).max().orElse(0);
        double diff = Math.abs(cloudSimMakespan - evalResult.makespan());
        double relDiff = diff / cloudSimMakespan;

        assertTrue(relDiff < 0.01, String.format("Relative difference in makespan must be < 1%%, was %.4f%%", relDiff * 100));
    }
}
