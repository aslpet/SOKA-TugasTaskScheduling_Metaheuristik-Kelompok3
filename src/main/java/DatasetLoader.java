import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;

/** Reads workload CSV files and builds cloudlets. */
public class DatasetLoader {

    public static Path resolvePath(String pathStr) {
        Path p = Path.of(pathStr);
        if (Files.exists(p)) return p;
        List<Path> candidates = List.of(
                Path.of("Heuristik_meta_data").resolve(p),
                Path.of("Heuristik_meta_data", "data").resolve(p),
                Path.of("data").resolve(p),
                Path.of("Metaheuristik").resolve(p),
                Path.of("Metaheuristik", "data").resolve(p),
                Path.of("Metaheuristik", "Heuristik_meta_data").resolve(p),
                Path.of("Metaheuristik", "Heuristik_meta_data", "data").resolve(p)
        );
        for (Path cand : candidates) {
            if (Files.exists(cand)) return cand;
        }
        return p;
    }

    public static List<TaskSpec> load(Path csv) throws IOException {
        Path resolved = Files.exists(csv) ? csv : resolvePath(csv.toString());
        List<TaskSpec> tasks = new ArrayList<>();
        List<String> lines = Files.readAllLines(resolved);
        for (int i = 1; i < lines.size(); i++) { // line 0 = header
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            String[] f = line.split(",");
            tasks.add(new TaskSpec(
                    Long.parseLong(f[0]),                    // id
                    Long.parseLong(f[1]),                    // length (MI)
                    Long.parseLong(f[2]) * 1_000_000L,       // file size (MB -> bytes)
                    Long.parseLong(f[3]) * 1_000_000L,       // output size (MB -> bytes)
                    Double.parseDouble(f[4]),                // arrival
                    Double.parseDouble(f[5]),                // deadline
                    Integer.parseInt(f[6]),                  // priority
                    Double.parseDouble(f[7]),                // ram utilization fraction
                    f.length > 8 ? f[8] : ""));              // size class
        }
        return tasks;
    }

    /** CPU = 100% of one vCPU; RAM/BW are small fractions so several cloudlets can share a VM. */
    public static CloudletSimple toCloudlet(TaskSpec t) {
        var cl = new CloudletSimple(t.length(), 1, new UtilizationModelFull());
        cl.setId(t.id());
        cl.setFileSize(t.fileSizeBytes());
        cl.setOutputSize(t.outputSizeBytes());
        cl.setUtilizationModelRam(new UtilizationModelDynamic(t.ramUtil()));
        cl.setUtilizationModelBw(new UtilizationModelDynamic(0.05));
        return cl;
    }
}
