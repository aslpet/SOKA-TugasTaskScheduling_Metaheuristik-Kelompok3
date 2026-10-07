import java.util.List;

import org.cloudsimplus.vms.Vm;

/**
 * Common interface for all task scheduling algorithms (metaheuristics and baselines).
 */
public interface Scheduler {

    record ScheduleResult(
            int[] mapping,
            double[] dispatchTimes,
            double fitness,
            long schedTimeMs,
            int feUsed,
            List<Double> convergenceHistory
    ) {}

    ScheduleResult schedule(
            List<TaskSpec> tasks,
            List<Vm> vms,
            DatacenterFactory.Infrastructure infra,
            ScheduleEvaluator.BaselineReference baselines,
            long seed
    );
}
