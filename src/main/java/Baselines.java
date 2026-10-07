import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.cloudsimplus.vms.Vm;

/**
 * Three layers of baseline schedulers:
 *  1. Naive floor: OLB (Opportunistic Load Balancing)
 *  2. Strong classic heuristics: Min-Min and Max-Min
 *  3. Historical references: FCFS and HRRN
 */
public class Baselines {

    public static Scheduler.ScheduleResult runOlb(List<TaskSpec> tasks, List<Vm> vms, ScheduleEvaluator evaluator) {
        long start = System.currentTimeMillis();
        int n = tasks.size();
        int m = vms.size();

        int[] mapping = new int[n];
        double[][] slotFree = new double[m][];
        for (int i = 0; i < m; i++) {
            slotFree[i] = new double[(int) vms.get(i).getPesNumber()];
        }

        // Sort task indices by arrival time
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingDouble((Integer idx) -> tasks.get(idx).arrival())
                .thenComparingLong(idx -> tasks.get(idx).id()));

        for (int taskIdx : order) {
            TaskSpec t = tasks.get(taskIdx);
            int bestVm = 0;
            int bestSlot = 0;
            double earliestStart = Double.MAX_VALUE;

            for (int i = 0; i < m; i++) {
                int cores = (int) vms.get(i).getPesNumber();
                for (int s = 0; s < cores; s++) {
                    double avail = Math.max(t.arrival(), slotFree[i][s]);
                    if (avail < earliestStart) {
                        earliestStart = avail;
                        bestVm = i;
                        bestSlot = s;
                    }
                }
            }

            mapping[taskIdx] = bestVm;
            double duration = t.length() / vms.get(bestVm).getMips();
            slotFree[bestVm][bestSlot] = earliestStart + duration;
        }

        var res = evaluator.evaluate(mapping, tasks, vms, ScheduleEvaluator.QueuePolicy.FCFS);
        long elapsed = System.currentTimeMillis() - start;
        return new Scheduler.ScheduleResult(mapping, res.dispatchTimes(), res.makespan(), elapsed, 1, Collections.singletonList(res.makespan()));
    }

    public static Scheduler.ScheduleResult runMinMin(List<TaskSpec> tasks, List<Vm> vms, ScheduleEvaluator evaluator) {
        return runMinOrMaxMin(tasks, vms, evaluator, false);
    }

    public static Scheduler.ScheduleResult runMaxMin(List<TaskSpec> tasks, List<Vm> vms, ScheduleEvaluator evaluator) {
        return runMinOrMaxMin(tasks, vms, evaluator, true);
    }

    private static Scheduler.ScheduleResult runMinOrMaxMin(List<TaskSpec> tasks, List<Vm> vms, ScheduleEvaluator evaluator, boolean isMaxMin) {
        long start = System.currentTimeMillis();
        int n = tasks.size();
        int m = vms.size();

        int[] mapping = new int[n];
        double[][] slotFree = new double[m][];
        for (int i = 0; i < m; i++) {
            slotFree[i] = new double[(int) vms.get(i).getPesNumber()];
        }

        boolean[] scheduled = new boolean[n];
        int remaining = n;

        while (remaining > 0) {
            int selectedTask = -1;
            int selectedVm = -1;
            int selectedSlot = -1;
            double globalExtremeMetric = isMaxMin ? -1.0 : Double.MAX_VALUE;

            for (int j = 0; j < n; j++) {
                if (scheduled[j]) continue;
                TaskSpec t = tasks.get(j);

                // Find earliest completion time for task j across all VMs
                double bestCompletionForTask = Double.MAX_VALUE;
                int bestVmForTask = -1;
                int bestSlotForTask = -1;

                for (int i = 0; i < m; i++) {
                    int cores = (int) vms.get(i).getPesNumber();
                    double mips = vms.get(i).getMips();
                    double duration = t.length() / mips;

                    for (int s = 0; s < cores; s++) {
                        double st = Math.max(t.arrival(), slotFree[i][s]);
                        double ct = st + duration;
                        if (ct < bestCompletionForTask) {
                            bestCompletionForTask = ct;
                            bestVmForTask = i;
                            bestSlotForTask = s;
                        }
                    }
                }

                if (isMaxMin) {
                    if (bestCompletionForTask > globalExtremeMetric) {
                        globalExtremeMetric = bestCompletionForTask;
                        selectedTask = j;
                        selectedVm = bestVmForTask;
                        selectedSlot = bestSlotForTask;
                    }
                } else {
                    if (bestCompletionForTask < globalExtremeMetric) {
                        globalExtremeMetric = bestCompletionForTask;
                        selectedTask = j;
                        selectedVm = bestVmForTask;
                        selectedSlot = bestSlotForTask;
                    }
                }
            }

            if (selectedTask >= 0) {
                mapping[selectedTask] = selectedVm;
                TaskSpec t = tasks.get(selectedTask);
                double st = Math.max(t.arrival(), slotFree[selectedVm][selectedSlot]);
                double duration = t.length() / vms.get(selectedVm).getMips();
                slotFree[selectedVm][selectedSlot] = st + duration;
                scheduled[selectedTask] = true;
                remaining--;
            }
        }

        var res = evaluator.evaluate(mapping, tasks, vms, ScheduleEvaluator.QueuePolicy.FCFS);
        long elapsed = System.currentTimeMillis() - start;
        return new Scheduler.ScheduleResult(mapping, res.dispatchTimes(), res.makespan(), elapsed, 1, Collections.singletonList(res.makespan()));
    }

    public static Scheduler.ScheduleResult runFcfsOrHrrn(List<TaskSpec> tasks, List<Vm> vms, ScheduleEvaluator evaluator, Planner.Policy policy) {
        long start = System.currentTimeMillis();
        Planner planner = new Planner(policy);
        var plan = planner.plan(tasks, vms);

        int n = tasks.size();
        int[] mapping = new int[n];
        for (var a : plan) {
            int taskIdx = (int) a.task().id() - 1; // 1-based to 0-based
            if (taskIdx >= 0 && taskIdx < n) {
                mapping[taskIdx] = (int) a.vm().getId();
            }
        }

        var res = evaluator.evaluate(mapping, tasks, vms, ScheduleEvaluator.QueuePolicy.FCFS);
        long elapsed = System.currentTimeMillis() - start;
        return new Scheduler.ScheduleResult(mapping, res.dispatchTimes(), res.makespan(), elapsed, 1, Collections.singletonList(res.makespan()));
    }
}
