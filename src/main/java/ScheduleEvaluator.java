import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;

/**
 * High-speed discrete-event slot simulator (O(N log N)) that mimics CloudSim Plus
 * without simulation engine overhead during metaheuristic search.
 */
public class ScheduleEvaluator {

    public enum QueuePolicy { FCFS, PRIORITY }

    public record EvaluationResult(
            double makespan,
            double energyWh,
            double energyCost,
            double rutilSum,
            double trustNorm,
            double successRatePct,
            int deadlineMissed,
            double[] dispatchTimes,
            double[] finishTimes,
            double[] workPerVm
    ) {}

    public record BaselineReference(
            double olbMakespan,
            double olbEnergyWh,
            double minMinMakespan,
            double minMinEnergyWh
    ) {}

    // Cached VM to Host mapping and DC mapping for the 20 VMs
    private static final int[] VM_TO_HOST = new int[20];
    private static final int[] VM_TO_DC = new int[20];
    private static final int[] HOST_TO_DC = new int[10];
    private static final DatacenterFactory.HostType[] HOST_TYPES = new DatacenterFactory.HostType[10];
    private static final boolean INITIALIZED;

    static {
        // Initialize once to extract CloudSim Plus host placement
        CloudSimPlus sim = new CloudSimPlus();
        var infra = DatacenterFactory.buildDatacenters(sim);
        var broker = new DatacenterBrokerSimple(sim);
        List<Vm> vms = DatacenterFactory.buildVms();
        List<Datacenter> dcs = infra.datacenters();
        broker.setDatacenterMapper((lastDc, vm) -> dcs.get((int) (vm.getId() % dcs.size())));
        broker.submitVmList(vms);
        sim.start();

        for (int h = 0; h < infra.hosts().size(); h++) {
            Host host = infra.hosts().get(h);
            HOST_TYPES[h] = infra.hostTypes().get(h);
            HOST_TO_DC[h] = h < 5 ? 0 : 1;
            for (Vm vm : host.getVmCreatedList()) {
                int vid = (int) vm.getId();
                VM_TO_HOST[vid] = h;
                VM_TO_DC[vid] = HOST_TO_DC[h];
            }
        }
        INITIALIZED = true;
    }

    private final double dc0Price;
    private final double dc1Price;

    public ScheduleEvaluator() {
        this(1.0, 1.5);
    }

    public ScheduleEvaluator(double dc0Price, double dc1Price) {
        this.dc0Price = dc0Price;
        this.dc1Price = dc1Price;
    }

    public EvaluationResult evaluate(int[] mapping, List<TaskSpec> tasks, List<Vm> vms, QueuePolicy policy) {
        int n = tasks.size();
        int m = vms.size();

        double[] dispatchTimes = new double[n];
        double[] finishTimes = new double[n];
        double[] workPerVm = new double[m];
        double[] vmCoreMips = new double[m];
        int[] vmPes = new int[m];

        for (int i = 0; i < m; i++) {
            Vm vm = vms.get(i);
            vmCoreMips[i] = vm.getMips();
            vmPes[i] = (int) vm.getPesNumber();
        }

        // Partition tasks by VM
        @SuppressWarnings("unchecked")
        List<Integer>[] tasksOnVm = new List[m];
        for (int i = 0; i < m; i++) tasksOnVm[i] = new ArrayList<>();
        for (int j = 0; j < n; j++) {
            int vmId = Math.min(m - 1, Math.max(0, mapping[j]));
            tasksOnVm[vmId].add(j);
        }

        double makespan = 0.0;
        int deadlineMissed = 0;
        double weightedTrustSum = 0.0;

        // Process each VM independently
        for (int i = 0; i < m; i++) {
            List<Integer> assigned = tasksOnVm[i];
            if (assigned.isEmpty()) continue;

            int cores = vmPes[i];
            double mips = vmCoreMips[i];
            Vm currentVm = vms.get(i);

            // Sort according to policy
            if (policy == QueuePolicy.FCFS) {
                assigned.sort(Comparator.comparingDouble((Integer idx) -> tasks.get(idx).arrival())
                        .thenComparingLong(idx -> tasks.get(idx).id()));
            } else {
                // Priority queue: highest prio_task first, tie-break arrival, then id
                assigned.sort((a, b) -> {
                    double prioA = PriorityModel.getTaskPriorityForVm(tasks.get(a), currentVm);
                    double prioB = PriorityModel.getTaskPriorityForVm(tasks.get(b), currentVm);
                    int cmp = Double.compare(prioB, prioA); // descending
                    if (cmp != 0) return cmp;
                    int cmpArr = Double.compare(tasks.get(a).arrival(), tasks.get(b).arrival());
                    if (cmpArr != 0) return cmpArr;
                    return Long.compare(tasks.get(a).id(), tasks.get(b).id());
                });
            }

            // Simulate slots for this VM
            double[] slotFree = new double[cores];
            double sumTe = 0.0;
            int onTimeCount = 0;

            for (int taskIdx : assigned) {
                TaskSpec task = tasks.get(taskIdx);
                workPerVm[i] += task.length();

                // Find slot that frees earliest
                int bestSlot = 0;
                double earliestFree = slotFree[0];
                for (int s = 1; s < cores; s++) {
                    if (slotFree[s] < earliestFree) {
                        earliestFree = slotFree[s];
                        bestSlot = s;
                    }
                }

                double start = Math.max(task.arrival(), earliestFree);
                double duration = task.length() / mips;
                double finish = start + duration;

                slotFree[bestSlot] = finish;
                dispatchTimes[taskIdx] = start;
                finishTimes[taskIdx] = finish;

                if (finish > makespan) makespan = finish;
                if (finish > task.deadline()) {
                    deadlineMissed++;
                } else {
                    onTimeCount++;
                }

                // Trust metrics
                double estt = TrustModel.computeEstt(task.length(), mips);
                double actt = TrustModel.computeActt(finish, task.arrival());
                sumTe += TrustModel.computeTeRatio(estt, actt);
            }

            int count = assigned.size();
            double av = 1.0;
            double sr = (double) onTimeCount / count;
            double te = sumTe / count;
            double rawTrust = TrustModel.computeRawTrust(av, sr, te);
            weightedTrustSum += count * rawTrust;
        }

        // Trust normalization
        double trustNorm = TrustModel.normalizeSystemTrust(weightedTrustSum, n,
                TrustModel.DEFAULT_X1, TrustModel.DEFAULT_X2, TrustModel.DEFAULT_X3);
        double successRatePct = n > 0 ? 100.0 * (n - deadlineMissed) / n : 100.0;

        // Resource utilization sum (Paper 1, Eq. P1-9)
        double rutilSum = 0.0;
        for (int i = 0; i < m; i++) {
            double capacity = vmPes[i] * vmCoreMips[i] * makespan;
            if (capacity > 0) {
                rutilSum += workPerVm[i] / capacity;
            }
        }

        // Energy consumption across hosts (Wh) and cost ($)
        double energyWh = 0.0;
        double energyCost = 0.0;

        for (int h = 0; h < 10; h++) {
            DatacenterFactory.HostType type = HOST_TYPES[h];
            double hostWork = 0.0;
            for (int vid = 0; vid < m; vid++) {
                if (VM_TO_HOST[vid] == h) hostWork += workPerVm[vid];
            }
            double hostCapacity = type.cores() * DatacenterFactory.PE_MIPS * makespan;
            double util = hostCapacity > 0 ? Math.min(1.0, hostWork / hostCapacity) : 0.0;
            double powerW = type.staticPowerW() + (type.maxPowerW() - type.staticPowerW()) * util;
            double hostEnergyWh = makespan > 0 ? powerW * makespan / 3600.0 : 0.0;
            energyWh += hostEnergyWh;

            double dcPrice = HOST_TO_DC[h] == 0 ? dc0Price : dc1Price;
            energyCost += dcPrice * (hostEnergyWh / 1000.0); // $/kWh
        }

        return new EvaluationResult(
                makespan,
                energyWh,
                energyCost,
                rutilSum,
                trustNorm,
                successRatePct,
                deadlineMissed,
                dispatchTimes,
                finishTimes,
                workPerVm
        );
    }
}
