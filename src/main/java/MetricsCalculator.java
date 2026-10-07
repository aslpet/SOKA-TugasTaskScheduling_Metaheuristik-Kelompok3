import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;

/**
 * Computes official evaluation metrics from CloudSim Plus replay
 * and optimization run parameters.
 */
public class MetricsCalculator {

    public record ExtendedMetrics(
            String scheduler,
            String mode,
            String workload,
            int wave,
            int tasks,
            long seed,
            double makespan,
            double energyWh,
            double energyCost,
            double avgExecTime,
            double avgResponseTime,
            double avgTurnaround,
            double cpuUtilPct,
            double throughput,
            double loadImbalance,
            int deadlineMissed,
            double successRatePct,
            double trustNorm,
            double rutilSum,
            long schedTimeMs,
            int feUsed,
            double finalFitness
    ) {

        public String toCsvRow() {
            return String.format(Locale.ROOT,
                    "%s,%s,%s,%d,%d,%d,%.2f,%.3f,%.4f,%.2f,%.2f,%.2f,%.2f,%.4f,%.4f,%d,%.2f,%.4f,%.4f,%d,%d,%.6f",
                    scheduler, mode, workload, wave, tasks, seed, makespan, energyWh, energyCost,
                    avgExecTime, avgResponseTime, avgTurnaround, cpuUtilPct, throughput, loadImbalance,
                    deadlineMissed, successRatePct, trustNorm, rutilSum, schedTimeMs, feUsed, finalFitness);
        }

        public static String csvHeader() {
            return "scheduler,mode,workload,wave,tasks,seed,makespan_s,energy_Wh,energy_cost,avg_exec_s,"
                    + "avg_response_s,avg_turnaround_s,cpu_util_pct,throughput_task_per_s,load_imbalance,"
                    + "deadline_missed,success_rate_pct,trust_norm,rutil_sum,sched_time_ms,fe_used,final_fitness";
        }

        public void print() {
            System.out.println("==================== METRICS [" + scheduler + " | " + mode + " | " + workload + " (seed=" + seed + ")] ====================");
            System.out.printf(Locale.ROOT, "tasks finished     : %d%n", tasks);
            System.out.printf(Locale.ROOT, "makespan           : %.2f s%n", makespan);
            System.out.printf(Locale.ROOT, "energy             : %.3f Wh%n", energyWh);
            System.out.printf(Locale.ROOT, "energy cost        : $%.4f%n", energyCost);
            System.out.printf(Locale.ROOT, "avg execution time : %.2f s%n", avgExecTime);
            System.out.printf(Locale.ROOT, "avg response time  : %.2f s%n", avgResponseTime);
            System.out.printf(Locale.ROOT, "avg turnaround     : %.2f s%n", avgTurnaround);
            System.out.printf(Locale.ROOT, "cpu utilization    : %.2f %%%n", cpuUtilPct);
            System.out.printf(Locale.ROOT, "throughput         : %.4f tasks/s%n", throughput);
            System.out.printf(Locale.ROOT, "load imbalance     : %.4f%n", loadImbalance);
            System.out.printf(Locale.ROOT, "deadline missed    : %d (%.2f%% success)%n", deadlineMissed, successRatePct);
            System.out.printf(Locale.ROOT, "trust SLA          : %.4f%n", trustNorm);
            System.out.printf(Locale.ROOT, "rutil sum          : %.4f%n", rutilSum);
            System.out.printf(Locale.ROOT, "sched time         : %d ms%n", schedTimeMs);
            System.out.printf(Locale.ROOT, "fe used / fitness  : %d / %.6f%n", feUsed, finalFitness);
        }
    }

    public static ExtendedMetrics compute(
            String scheduler,
            String mode,
            String workload,
            int wave,
            long seed,
            long schedTimeMs,
            int feUsed,
            double finalFitness,
            List<? extends Cloudlet> finished,
            Map<Long, TaskSpec> specs,
            DatacenterFactory.Infrastructure infra,
            List<Vm> vms,
            double dc0Price,
            double dc1Price
    ) {
        double makespan = finished.stream().mapToDouble(Cloudlet::getFinishTime).max().orElse(0);
        double sumExec = 0, sumResp = 0, sumTat = 0, totalWork = 0;
        int missed = 0;
        Map<Vm, Double> workPerVm = new IdentityHashMap<>();

        double weightedTrustSum = 0.0;
        int n = finished.size();

        // Calculate individual cloudlet metrics
        for (Cloudlet c : finished) {
            TaskSpec s = specs.get(c.getId());
            double finish = c.getFinishTime();
            sumExec += c.getTotalExecutionTime();
            sumResp += c.getStartTime() - s.arrival();
            sumTat += finish - s.arrival();
            if (finish > s.deadline()) missed++;
            totalWork += c.getLength();
            workPerVm.merge(c.getVm(), (double) c.getLength(), Double::sum);
        }

        // Trust calculation per VM
        for (Vm vm : vms) {
            long vmTasks = finished.stream().filter(c -> c.getVm() == vm).count();
            if (vmTasks == 0) continue;

            long onTime = finished.stream().filter(c -> c.getVm() == vm && c.getFinishTime() <= specs.get(c.getId()).deadline()).count();
            double sr = (double) onTime / vmTasks;
            double sumTe = 0.0;
            for (Cloudlet c : finished) {
                if (c.getVm() == vm) {
                    TaskSpec s = specs.get(c.getId());
                    double estt = TrustModel.computeEstt(s.length(), vm.getMips());
                    double actt = TrustModel.computeActt(c.getFinishTime(), s.arrival());
                    sumTe += TrustModel.computeTeRatio(estt, actt);
                }
            }
            double te = sumTe / vmTasks;
            double rawTrust = TrustModel.computeRawTrust(1.0, sr, te);
            weightedTrustSum += vmTasks * rawTrust;
        }

        double trustNorm = TrustModel.normalizeSystemTrust(weightedTrustSum, n,
                TrustModel.DEFAULT_X1, TrustModel.DEFAULT_X2, TrustModel.DEFAULT_X3);
        double successRatePct = n > 0 ? 100.0 * (n - missed) / n : 100.0;

        // CPU utilization and load imbalance
        double capacity = 0, minBusy = Double.MAX_VALUE, maxBusy = 0, sumBusy = 0;
        double rutilSum = 0.0;
        for (Vm vm : vms) {
            double vmCapacity = vm.getMips() * vm.getPesNumber();
            capacity += vmCapacity;
            double work = workPerVm.getOrDefault(vm, 0.0);
            double busy = work / vmCapacity;
            minBusy = Math.min(minBusy, busy);
            maxBusy = Math.max(maxBusy, busy);
            sumBusy += busy;

            if (makespan > 0) {
                rutilSum += work / (vmCapacity * makespan);
            }
        }
        double meanBusy = sumBusy / vms.size();
        double imbalance = meanBusy > 0 ? (maxBusy - minBusy) / meanBusy : 0;
        double cpuUtil = makespan > 0 ? 100.0 * totalWork / (capacity * makespan) : 0;

        // Energy (Wh) and Cost ($) across hosts
        double energyWh = 0;
        double energyCost = 0;
        for (int h = 0; h < infra.hosts().size(); h++) {
            Host host = infra.hosts().get(h);
            DatacenterFactory.HostType type = infra.hostTypes().get(h);
            double work = 0;
            for (Vm vm : host.getVmCreatedList()) work += workPerVm.getOrDefault(vm, 0.0);
            double hostMips = type.cores() * (double) DatacenterFactory.PE_MIPS;
            double util = makespan > 0 ? Math.min(1.0, work / (hostMips * makespan)) : 0;
            double powerW = type.staticPowerW() + (type.maxPowerW() - type.staticPowerW()) * util;
            double hostWh = makespan > 0 ? powerW * makespan / 3600.0 : 0.0;
            energyWh += hostWh;

            double price = (h < 5) ? dc0Price : dc1Price;
            energyCost += price * (hostWh / 1000.0);
        }

        return new ExtendedMetrics(
                scheduler, mode, workload, wave, n, seed, makespan, energyWh, energyCost,
                n > 0 ? sumExec / n : 0, n > 0 ? sumResp / n : 0, n > 0 ? sumTat / n : 0,
                cpuUtil, makespan > 0 ? n / makespan : 0, imbalance, missed, successRatePct,
                trustNorm, rutilSum, schedTimeMs, feUsed, finalFitness
        );
    }

    public static synchronized void appendToCsv(Path file, ExtendedMetrics m) throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        boolean isNew = !Files.exists(file);
        StringBuilder sb = new StringBuilder();
        if (isNew) sb.append(ExtendedMetrics.csvHeader()).append(System.lineSeparator());
        sb.append(m.toCsvRow()).append(System.lineSeparator());
        Files.writeString(file, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
