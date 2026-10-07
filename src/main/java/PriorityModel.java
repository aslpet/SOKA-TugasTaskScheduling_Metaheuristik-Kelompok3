import java.util.Arrays;
import java.util.List;

import org.cloudsimplus.vms.Vm;

/**
 * Implements Priority equations from Paper 2 (Wu 2024, Eq. P2-3 to P2-7):
 *  - pr_v_i     = s_i * mu_i (cores * MIPS)
 *  - size_j     = L_j * w_j  (length * priority weight)
 *  - prio_task  = size_j / pr_v_i
 *  - prio_VM_i  = max(prices) / price_{DC(i)}
 */
public class PriorityModel {

    public static double computeVmCapacity(long cores, double mips) {
        return cores * mips;
    }

    public static double computeTaskSize(long length, int priorityWeight) {
        return length * (double) priorityWeight;
    }

    public static double computeTaskPriority(double taskSize, double vmCapacity) {
        return vmCapacity > 0 ? taskSize / vmCapacity : 0.0;
    }

    public static double[] computeVmPriorities(double[] dcPrices) {
        double maxPrice = Arrays.stream(dcPrices).max().orElse(1.0);
        double[] prio = new double[dcPrices.length];
        for (int i = 0; i < dcPrices.length; i++) {
            prio[i] = dcPrices[i] > 0 ? maxPrice / dcPrices[i] : 1.0;
        }
        return prio;
    }

    /** For dataset priority 1 (highest) to 3 (lowest), weight is 4 - priority -> 3, 2, 1. */
    public static int getDatasetPriorityWeight(int priority) {
        return Math.max(1, 4 - priority);
    }

    public static double getTaskPriorityForVm(TaskSpec task, Vm vm) {
        double taskSize = computeTaskSize(task.length(), getDatasetPriorityWeight(task.priority()));
        double vmCap = computeVmCapacity(vm.getPesNumber(), vm.getMips());
        return computeTaskPriority(taskSize, vmCap);
    }
}
