import java.nio.file.Path;
import java.util.List;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class PlannerTest {

    @Test
    void testSlideExampleHrrn() {
        Vm vm = new VmSimple(1, 1);
        List<Vm> vms = List.of(vm);

        List<TaskSpec> tasks = List.of(
                new TaskSpec(1, 8, 1000, 1000, 0, 50, 1, 0.05, "small"),
                new TaskSpec(2, 4, 1000, 1000, 1, 50, 1, 0.05, "small"),
                new TaskSpec(3, 2, 1000, 1000, 2, 50, 1, 0.05, "small"),
                new TaskSpec(4, 6, 1000, 1000, 3, 50, 1, 0.05, "small"),
                new TaskSpec(5, 3, 1000, 1000, 4, 50, 1, 0.05, "small")
        );

        var planner = new Planner(Planner.Policy.HRRN);
        var plan = planner.plan(tasks, vms);

        List<Long> order = plan.stream().map(a -> a.task().id()).toList();
        assertEquals(List.of(1L, 3L, 2L, 5L, 4L), order);

        double totalWait = 0;
        for (var a : plan) {
            double wait = a.dispatchTime() - a.task().arrival();
            totalWait += wait;
        }
        double avgWait = totalWait / tasks.size();
        assertEquals(7.8, avgWait, 1e-9);
    }

    @Test
    void testSlideExampleFcfs() {
        Vm vm = new VmSimple(1, 1);
        List<Vm> vms = List.of(vm);

        List<TaskSpec> tasks = List.of(
                new TaskSpec(1, 8, 1000, 1000, 0, 50, 1, 0.05, "small"),
                new TaskSpec(2, 4, 1000, 1000, 1, 50, 1, 0.05, "small"),
                new TaskSpec(3, 2, 1000, 1000, 2, 50, 1, 0.05, "small"),
                new TaskSpec(4, 6, 1000, 1000, 3, 50, 1, 0.05, "small"),
                new TaskSpec(5, 3, 1000, 1000, 4, 50, 1, 0.05, "small")
        );

        var planner = new Planner(Planner.Policy.FCFS);
        var plan = planner.plan(tasks, vms);

        List<Long> order = plan.stream().map(a -> a.task().id()).toList();
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), order);
    }

    @Test
    void testAllTwentyVmsAllocated() {
        var simulation = new CloudSimPlus();
        var infra = DatacenterFactory.buildDatacenters(simulation);
        var broker = new DatacenterBrokerSimple(simulation);

        List<Vm> vms = DatacenterFactory.buildVms();
        List<Datacenter> dcs = infra.datacenters();
        broker.setDatacenterMapper((lastDc, vm) -> dcs.get((int) (vm.getId() % dcs.size())));
        broker.submitVmList(vms);

        simulation.start();

        assertEquals(20, broker.getVmCreatedList().size(), "Semua 20 VM harus berhasil dibuat!");
        assertTrue(broker.getVmFailedList().isEmpty(), "Tidak boleh ada VM yang gagal dialokasikan!");
    }
}
