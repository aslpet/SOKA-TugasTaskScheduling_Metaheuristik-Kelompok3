/**
 * Implements Mode B common fitness function:
 *  F = 0.5 * (M / M0) + 0.5 * (E / E0)
 *
 * Used as a fair control to compare the pure search mechanics of GSA, TABWO, and PSO.
 */
public class CommonFitness implements FitnessFunction {

    @Override
    public double evaluate(ScheduleEvaluator.EvaluationResult res, ScheduleEvaluator.BaselineReference ref, int totalVms) {
        double m0 = (ref != null && ref.olbMakespan() > 0) ? ref.olbMakespan() : res.makespan();
        double e0 = (ref != null && ref.olbEnergyWh() > 0) ? ref.olbEnergyWh() : res.energyWh();

        double normM = m0 > 0 ? res.makespan() / m0 : 1.0;
        double normE = e0 > 0 ? res.energyWh() / e0 : 1.0;

        return 0.5 * normM + 0.5 * normE;
    }
}
