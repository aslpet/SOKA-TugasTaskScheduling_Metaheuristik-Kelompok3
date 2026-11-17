/**
 * Implements composite fitness for TABWO (Paper 2 reconstruction):
 *  F = w_M * (M / M0) + w_E * (E_cost / E0) + w_T * (1.0 - Trust_norm)
 *
 * Defaults:
 *  - w_M = 0.4
 *  - w_E = 0.3
 *  - w_T = 0.3
 *  - M0, E0 from baseline OLB
 */
public class TabwoFitness implements FitnessFunction {

    private final double wM;
    private final double wE;
    private final double wT;

    public TabwoFitness() {
        this(0.4, 0.3, 0.3);
    }

    public TabwoFitness(double wM, double wE, double wT) {
        this.wM = wM;
        this.wE = wE;
        this.wT = wT;
    }

    @Override
    public double evaluate(ScheduleEvaluator.EvaluationResult res, ScheduleEvaluator.BaselineReference ref, int totalVms) {
        double m0 = (ref != null && ref.olbMakespan() > 0) ? ref.olbMakespan() : res.makespan();
        double e0 = (ref != null && ref.olbEnergyWh() > 0) ? ref.olbEnergyWh() : res.energyWh();

        double normM = m0 > 0 ? res.makespan() / m0 : 1.0;
        double normE = e0 > 0 ? res.energyWh() / e0 : 1.0;
        double trustPenalty = Math.max(0.0, 1.0 - res.trustNorm());

        return wM * normM + wE * normE + wT * trustPenalty;
    }
}
