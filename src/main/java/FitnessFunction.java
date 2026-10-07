/** Common interface for fitness function evaluation in metaheuristic optimization. */
public interface FitnessFunction {
    double evaluate(ScheduleEvaluator.EvaluationResult res, ScheduleEvaluator.BaselineReference ref, int totalVms);
}
