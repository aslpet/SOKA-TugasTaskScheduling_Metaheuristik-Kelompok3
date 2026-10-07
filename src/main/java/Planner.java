import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.cloudsimplus.vms.Vm;

/**
 * Decides, for every task, WHEN it is dispatched and to WHICH VM.
 *
 * Model: every vCPU of every VM is one non-preemptive "slot". A decision point happens whenever
 * a slot is free and at least one task is waiting.
 *   - Task choice : HRRN -> highest Response Ratio RR = (WT + BT) / BT   (FCFS -> earliest arrival)
 *   - VM choice   : among the currently free slots, the fastest VM (earliest finish time)
 * BT is estimated as length / mean VM MIPS, so the ordering does not depend on which VM is free.
 * The result is replayed in CloudSim via submission delays (see Main).
 */
public class Planner {

    public enum Policy { HRRN, FCFS }

    public record Assignment(TaskSpec task, Vm vm, double dispatchTime) {}

    private final Policy policy;

    public Planner(Policy policy) {
        this.policy = policy;
    }

    public List<Assignment> plan(List<TaskSpec> tasks, List<Vm> vms) {
        List<TaskSpec> byArrival = new ArrayList<>(tasks);
        byArrival.sort(Comparator.comparingDouble(TaskSpec::arrival).thenComparingLong(TaskSpec::id));

        // one slot per vCPU
        List<Integer> slotVm = new ArrayList<>();
        for (int v = 0; v < vms.size(); v++) {
            for (long c = 0; c < vms.get(v).getPesNumber(); c++) slotVm.add(v);
        }
        double[] freeAt = new double[slotVm.size()];

        double refMips = vms.stream().mapToDouble(Vm::getMips).average().orElse(1000);

        List<TaskSpec> waiting = new ArrayList<>(); // kept in arrival order
        List<Assignment> result = new ArrayList<>();
        int next = 0;
        double t = 0;

        while (result.size() < byArrival.size()) {
            // step 1: tasks that have arrived join the ready queue
            while (next < byArrival.size() && byArrival.get(next).arrival() <= t) {
                waiting.add(byArrival.get(next++));
            }
            if (waiting.isEmpty()) {            // idle: jump to the next arrival
                t = byArrival.get(next).arrival();
                continue;
            }

            // find the fastest free slot
            int bestSlot = -1;
            for (int s = 0; s < freeAt.length; s++) {
                if (freeAt[s] > t) continue;
                if (bestSlot < 0 || vms.get(slotVm.get(s)).getMips() > vms.get(slotVm.get(bestSlot)).getMips()) {
                    bestSlot = s;
                }
            }
            if (bestSlot < 0) {                 // all busy: jump to the earliest completion
                t = Arrays.stream(freeAt).min().getAsDouble();
                continue;
            }

            // steps 2-4: pick the task
            TaskSpec chosen = pick(waiting, t, refMips);
            waiting.remove(chosen);

            // step 5: dispatch (non-preemptive)
            Vm vm = vms.get(slotVm.get(bestSlot));
            freeAt[bestSlot] = t + chosen.length() / vm.getMips();
            result.add(new Assignment(chosen, vm, t));
        }
        return result;
    }

    private TaskSpec pick(List<TaskSpec> waiting, double t, double refMips) {
        if (policy == Policy.FCFS) return waiting.get(0);

        TaskSpec best = null;
        double bestRr = -1;
        for (TaskSpec w : waiting) {
            double wt = t - w.arrival();
            double bt = w.length() / refMips;
            double rr = (wt + bt) / bt;
            if (rr > bestRr) {                  // strict '>' : ties go to the earlier arrival
                bestRr = rr;
                best = w;
            }
        }
        return best;
    }
}
