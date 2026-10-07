import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Main CLI entry point for running Metaheuristic Task Scheduling experiments.
 *
 * Usage:
 *   mvn compile exec:java "-Dexec.args=--mode A --algos gsa,tabwo,pso --waves 1,2,3"
 *   mvn compile exec:java "-Dexec.args=--pilot --algos gsa,tabwo"
 *   mvn compile exec:java "-Dexec.args=--workload wave1/workload_100.csv --algos gsa,tabwo"
 */
public class Main {

    public static void main(String[] args) throws Exception {
        Path configPath = Path.of("config/params.properties");
        var cfg = ExperimentRunner.ExperimentConfig.load(configPath);

        List<String> modes = new ArrayList<>(List.of("A"));
        List<String> algos = new ArrayList<>(List.of("GSA", "TABWO", "PSO"));
        List<Integer> waves = new ArrayList<>(List.of(1, 2, 3));
        boolean isPilot = false;
        String singleWorkload = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equalsIgnoreCase("--mode") && i + 1 < args.length) {
                modes = Arrays.asList(args[++i].split(","));
            } else if (arg.equalsIgnoreCase("--algos") && i + 1 < args.length) {
                algos = Arrays.asList(args[++i].split(","));
            } else if (arg.equalsIgnoreCase("--waves") && i + 1 < args.length) {
                waves = Arrays.stream(args[++i].split(","))
                        .map(String::trim)
                        .map(Integer::parseInt)
                        .toList();
            } else if (arg.equalsIgnoreCase("--pilot")) {
                isPilot = true;
            } else if (arg.equalsIgnoreCase("--workload") && i + 1 < args.length) {
                singleWorkload = args[++i];
            }
        }

        Path resultsCsv = Path.of("results/results_meta.csv");
        Path convergenceDir = Path.of("results/convergence");

        if (isPilot) {
            System.out.println(">>> RUNNING PILOT TUNING STUDY (pilot/workload_500.csv, pilot/workload_2000.csv) <<<");
            List<ExperimentRunner.WorkloadEntry> pilotEntries = List.of(
                    new ExperimentRunner.WorkloadEntry(0, 1, 500, "pilot/workload_500.csv"),
                    new ExperimentRunner.WorkloadEntry(0, 2, 2000, "pilot/workload_2000.csv")
            );
            for (var entry : pilotEntries) {
                ExperimentRunner.runWorkload(entry, cfg, modes, algos, resultsCsv, convergenceDir);
            }
            System.out.println("\nPilot run complete! Results written to: " + resultsCsv);
            return;
        }

        if (singleWorkload != null) {
            System.out.println(">>> RUNNING SINGLE WORKLOAD: " + singleWorkload + " <<<");
            var entry = new ExperimentRunner.WorkloadEntry(1, 1, 100, singleWorkload);
            ExperimentRunner.runWorkload(entry, cfg, modes, algos, resultsCsv, convergenceDir);
            System.out.println("\nSingle workload run complete! Results written to: " + resultsCsv);
            return;
        }

        // Standard multi-wave execution
        System.out.printf(">>> STARTING MULTI-WAVE EXPERIMENT (Waves: %s, Algos: %s, Modes: %s) <<<%n",
                waves, algos, modes);
        Path manifestPath = Path.of("data/wave_manifest.csv");
        var manifest = ExperimentRunner.loadManifest(manifestPath);

        for (var entry : manifest) {
            if (!waves.contains(entry.wave())) continue;
            ExperimentRunner.runWorkload(entry, cfg, modes, algos, resultsCsv, convergenceDir);
        }

        System.out.println("\nAll requested experiment runs complete!");
        System.out.println("Results saved to: " + resultsCsv);
        System.out.println("Convergence logs in: " + convergenceDir);
    }
}
