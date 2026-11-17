import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.vms.Vm;

/**
 * Orchestrates multi-wave, multi-seed experiments for GSA, TABWO, PSO,
 * and Baselines (OLB, Min-Min, Max-Min).
 */
public class ExperimentRunner {

    public record ExperimentConfig(
            int popSize,
            int feMax,
            int seedsSmall,
            int seedsLarge,
            int thresholdSmall,
            double gsaG0,
            double gsaAlpha,
            double tabwoPp,
            double tabwoCr,
            double tabwoPm,
            double tabwoSbcEta,
            double tabwoWeightM,
            double tabwoWeightE,
            double tabwoWeightT,
            double psoWMax,
            double psoWMin,
            double psoC1,
            double psoC2,
            double psoVMax,
            double priceDc0,
            double priceDc1
    ) {
        public static ExperimentConfig load(Path file) {
            Properties p = new Properties();
            Path resolved = Files.exists(file) ? file : DatasetLoader.resolvePath(file.toString());
            if (Files.exists(resolved)) {
                try (var in = new FileInputStream(resolved.toFile())) {
                    p.load(in);
                } catch (IOException e) {
                    System.err.println("Failed to read " + file + ", using defaults: " + e.getMessage());
                }
            }
            return new ExperimentConfig(
                    Integer.parseInt(p.getProperty("pop.size", "30")),
                    Integer.parseInt(p.getProperty("fe.max", "3000")),
                    Integer.parseInt(p.getProperty("seeds.small", "5")),
                    Integer.parseInt(p.getProperty("seeds.large", "3")),
                    Integer.parseInt(p.getProperty("threshold.small.tasks", "1000")),
                    Double.parseDouble(p.getProperty("gsa.g0", "100.0")),
                    Double.parseDouble(p.getProperty("gsa.alpha", "20.0")),
                    Double.parseDouble(p.getProperty("tabwo.pp", "0.6")),
                    Double.parseDouble(p.getProperty("tabwo.cr", "0.44")),
                    Double.parseDouble(p.getProperty("tabwo.pm", "0.4")),
                    Double.parseDouble(p.getProperty("tabwo.sbc.eta", "20.0")),
                    Double.parseDouble(p.getProperty("tabwo.weight.makespan", "0.4")),
                    Double.parseDouble(p.getProperty("tabwo.weight.energy", "0.3")),
                    Double.parseDouble(p.getProperty("tabwo.weight.trust", "0.3")),
                    Double.parseDouble(p.getProperty("pso.w.max", "0.9")),
                    Double.parseDouble(p.getProperty("pso.w.min", "0.4")),
                    Double.parseDouble(p.getProperty("pso.c1", "1.496")),
                    Double.parseDouble(p.getProperty("pso.c2", "1.496")),
                    Double.parseDouble(p.getProperty("pso.vmax", "2.0")),
                    Double.parseDouble(p.getProperty("price.dc0", "1.0")),
                    Double.parseDouble(p.getProperty("price.dc1", "1.5"))
            );
        }
    }

    public record WorkloadEntry(int wave, int runOrder, int tasks, String file) {}

    public static List<WorkloadEntry> loadManifest(Path manifestPath) throws IOException {
        Path resolved = DatasetLoader.resolvePath(manifestPath.toString());
        List<WorkloadEntry> entries = new ArrayList<>();
        List<String> lines = Files.readAllLines(resolved);
        for (int i = 1; i < lines.size(); i++) {
            String l = lines.get(i).trim();
            if (l.isEmpty()) continue;
            String[] f = l.split(",");
            entries.add(new WorkloadEntry(
                    Integer.parseInt(f[0].trim()),
                    Integer.parseInt(f[1].trim()),
                    Integer.parseInt(f[2].trim()),
                    f[3].trim()
            ));
        }
        return entries;
    }

    public static MetricsCalculator.ExtendedMetrics replayInCloudSim(
            String schedulerName,
            String workloadLabel,
            int wave,
            long seed,
            long schedTimeMs,
            int feUsed,
            double finalFitness,
            int[] mapping,
            double[] dispatchTimes,
            List<TaskSpec> tasks,
            List<Vm> vmsTemplate,
            DatacenterFactory.Infrastructure infraTemplate,
            double dc0Price,
            double dc1Price
    ) {
        CloudSimPlus sim = new CloudSimPlus();
        var infra = DatacenterFactory.buildDatacenters(sim);
        var broker = new DatacenterBrokerSimple(sim);
        List<Vm> vms = DatacenterFactory.buildVms();
        List<Datacenter> dcs = infra.datacenters();
        broker.setDatacenterMapper((lastDc, vm) -> dcs.get((int) (vm.getId() % dcs.size())));
        broker.submitVmList(vms);

        List<CloudletSimple> cloudlets = new ArrayList<>();
        Map<Long, Vm> target = new HashMap<>();
        for (int i = 0; i < tasks.size(); i++) {
            TaskSpec t = tasks.get(i);
            Vm vm = vms.get(mapping[i]);
            CloudletSimple cl = DatasetLoader.toCloudlet(t);
            cl.setSubmissionDelay(dispatchTimes[i]);
            cl.setVm(vm);
            cloudlets.add(cl);
            target.put(cl.getId(), vm);
        }
        broker.setVmMapper(cl -> target.getOrDefault(cl.getId(), Vm.NULL));
        broker.submitCloudletList(cloudlets);

        sim.start();

        Map<Long, TaskSpec> specs = new HashMap<>();
        for (TaskSpec t : tasks) specs.put(t.id(), t);

        var finished = broker.getCloudletFinishedList();
        if (finished.size() != tasks.size()) {
            System.err.printf("WARNING: only %d of %d cloudlets finished in replay!%n", finished.size(), tasks.size());
        }

        return MetricsCalculator.compute(
                schedulerName, workloadLabel, wave, seed, schedTimeMs, feUsed, finalFitness,
                finished, specs, infra, vms, dc0Price, dc1Price
        );
    }

    public static void runWorkload(
            WorkloadEntry entry,
            ExperimentConfig cfg,
            List<String> algos,
            Path resultsCsv,
            Path convergenceDir
    ) throws Exception {
        Path workloadPath = DatasetLoader.resolvePath(entry.file());
        List<TaskSpec> tasks = DatasetLoader.load(workloadPath);
        List<Vm> vms = DatacenterFactory.buildVms();
        ScheduleEvaluator evaluator = new ScheduleEvaluator(cfg.priceDc0(), cfg.priceDc1());

        System.out.printf("%n=== RUNNING WORKLOAD: %s (Wave %d, N=%d) ===%n", entry.file(), entry.wave(), entry.tasks());

        // 1. Run Baselines (1x deterministic run)
        var olbRes = Baselines.runOlb(tasks, vms, evaluator);
        var olbMetrics = replayInCloudSim("OLB", entry.file(), entry.wave(), 42,
                olbRes.schedTimeMs(), 1, olbRes.fitness(), olbRes.mapping(), olbRes.dispatchTimes(),
                tasks, vms, null, cfg.priceDc0(), cfg.priceDc1());
        MetricsCalculator.appendToCsv(resultsCsv, olbMetrics);

        var minMinRes = Baselines.runMinMin(tasks, vms, evaluator);
        var minMinMetrics = replayInCloudSim("MINMIN", entry.file(), entry.wave(), 42,
                minMinRes.schedTimeMs(), 1, minMinRes.fitness(), minMinRes.mapping(), minMinRes.dispatchTimes(),
                tasks, vms, null, cfg.priceDc0(), cfg.priceDc1());
        MetricsCalculator.appendToCsv(resultsCsv, minMinMetrics);

        var maxMinRes = Baselines.runMaxMin(tasks, vms, evaluator);
        var maxMinMetrics = replayInCloudSim("MAXMIN", entry.file(), entry.wave(), 42,
                maxMinRes.schedTimeMs(), 1, maxMinRes.fitness(), maxMinRes.mapping(), maxMinRes.dispatchTimes(),
                tasks, vms, null, cfg.priceDc0(), cfg.priceDc1());
        MetricsCalculator.appendToCsv(resultsCsv, maxMinMetrics);

        ScheduleEvaluator.BaselineReference baseRef = new ScheduleEvaluator.BaselineReference(
                olbMetrics.makespan(), olbMetrics.energyWh(), minMinMetrics.makespan(), minMinMetrics.energyWh()
        );

        // 2. Determine seeds for stochastic metaheuristics
        int seedCount = (entry.tasks() <= cfg.thresholdSmall()) ? cfg.seedsSmall() : cfg.seedsLarge();
        long[] seeds = new long[seedCount];
        for (int k = 0; k < seedCount; k++) {
            seeds[k] = 1000L * entry.wave() + entry.tasks() + 17L * k;
        }

        // 3. Run metaheuristics
        for (String algo : algos) {
            algo = algo.toUpperCase();
            Scheduler scheduler = null;

            if ("GSA".equals(algo)) {
                scheduler = new Gsa(cfg.popSize(), cfg.feMax(), cfg.gsaG0(), cfg.gsaAlpha(),
                        new GsaFitness(), ScheduleEvaluator.QueuePolicy.FCFS);
            } else if ("TABWO".equals(algo)) {
                scheduler = new Tabwo(cfg.popSize(), cfg.feMax(), cfg.tabwoPp(), cfg.tabwoCr(),
                        cfg.tabwoPm(), cfg.tabwoSbcEta(),
                        new TabwoFitness(cfg.tabwoWeightM(), cfg.tabwoWeightE(), cfg.tabwoWeightT()),
                        ScheduleEvaluator.QueuePolicy.PRIORITY, true);
            } else if ("PSO".equals(algo)) {
                scheduler = new Pso(cfg.popSize(), cfg.feMax(), cfg.psoWMax(), cfg.psoWMin(),
                        cfg.psoC1(), cfg.psoC2(), cfg.psoVMax(),
                        new CommonFitness(), ScheduleEvaluator.QueuePolicy.FCFS);
            }

            if (scheduler == null) continue;

            for (long seed : seeds) {
                System.out.printf("Running %s on %s (seed=%d)...%n", algo, entry.file(), seed);
                var schedRes = scheduler.schedule(tasks, vms, null, baseRef, seed);

                var mRecord = replayInCloudSim(algo, entry.file(), entry.wave(), seed,
                        schedRes.schedTimeMs(), schedRes.feUsed(), schedRes.fitness(),
                        schedRes.mapping(), schedRes.dispatchTimes(),
                        tasks, vms, null, cfg.priceDc0(), cfg.priceDc1());
                MetricsCalculator.appendToCsv(resultsCsv, mRecord);

                // Save convergence
                if (convergenceDir != null) {
                    Files.createDirectories(convergenceDir);
                    String fname = String.format("%s_wave%d_N%d_seed%d.csv",
                            algo.toLowerCase(), entry.wave(), entry.tasks(), seed);
                    Path cFile = convergenceDir.resolve(fname);
                    StringBuilder sb = new StringBuilder("iteration,best_fitness\n");
                    for (int it = 0; it < schedRes.convergenceHistory().size(); it++) {
                        sb.append(it).append(",").append(schedRes.convergenceHistory().get(it)).append("\n");
                    }
                    Files.writeString(cFile, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                }
            }
        }
    }
}
