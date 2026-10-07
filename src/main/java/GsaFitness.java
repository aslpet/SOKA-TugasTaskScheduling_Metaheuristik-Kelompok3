/**
 * Implements GSA Fitness function from Paper 1 (Mamalis & Perlitis 2022, Eq. P1-10):
 *  f = (gamma * T_exec_max + eps1) / (delta * Rutil_sum + eps2)
 *
 * Defaults:
 *  - T_exec_max = makespan
 *  - gamma = 1.0 / T0 (where T0 = baseline OLB makespan)
 *  - delta = 1.0 / m  (m = number of VMs)
 *  - eps1 = eps2 = 1e-9
 */
public class GsaFitness implements FitnessFunction {

    private final double eps1;
    private final double eps2;

    public GsaFitness() {
        this(1e-9, 1e-9);
    }

    public GsaFitness(double eps1, double eps2) {
        this.eps1 = eps1;
        this.eps2 = eps2;
    }

    public static double computeRawFitness(double tExecMax, double rutilSum, double gamma, double delta, double eps1, double eps2) {
        double numerator = gamma * tExecMax + eps1;
        double denominator = delta * rutilSum + eps2;
        return denominator > 0 ? numerator / denominator : Double.MAX_VALUE;
    }

    @Override
    public double evaluate(ScheduleEvaluator.EvaluationResult res, ScheduleEvaluator.BaselineReference ref, int totalVms) {
        double t0 = (ref != null && ref.olbMakespan() > 0) ? ref.olbMakespan() : 1.0;
        double gamma = 1.0 / t0;
        double delta = totalVms > 0 ? 1.0 / totalVms : 1.0;
        return computeRawFitness(res.makespan(), res.rutilSum(), gamma, delta, eps1, eps2);
    }
}
