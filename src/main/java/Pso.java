import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.cloudsimplus.vms.Vm;

/**
 * Standard Particle Swarm Optimization (PSO) baseline solver
 * using continuous particle encoding, personal best (pbest), and global best (gbest).
 */
public class Pso implements Scheduler {

    private final int popSize;
    private final int feMax;
    private final double wMax;
    private final double wMin;
    private final double c1;
    private final double c2;
    private final double vMax;
    private final FitnessFunction fitnessFunc;
    private final ScheduleEvaluator.QueuePolicy queuePolicy;

    public Pso(int popSize, int feMax, double wMax, double wMin, double c1, double c2, double vMax,
               FitnessFunction fitnessFunc, ScheduleEvaluator.QueuePolicy queuePolicy) {
        this.popSize = popSize;
        this.feMax = feMax;
        this.wMax = wMax;
        this.wMin = wMin;
        this.c1 = c1;
        this.c2 = c2;
        this.vMax = vMax;
        this.fitnessFunc = fitnessFunc;
        this.queuePolicy = queuePolicy;
    }

    public Pso(FitnessFunction fitnessFunc, ScheduleEvaluator.QueuePolicy queuePolicy) {
        this(30, 3000, 0.9, 0.4, 1.496, 1.496, 2.0, fitnessFunc, queuePolicy);
    }

    public static int[] decode(double[] pos, int m) {
        int[] discrete = new int[pos.length];
        for (int k = 0; k < pos.length; k++) {
            int vm = (int) Math.floor(pos[k]);
            discrete[k] = Math.max(0, Math.min(m - 1, vm));
        }
        return discrete;
    }

    @Override
    public ScheduleResult schedule(
            List<TaskSpec> tasks,
            List<Vm> vms,
            DatacenterFactory.Infrastructure infra,
            ScheduleEvaluator.BaselineReference baselines,
            long seed
    ) {
        long startTime = System.currentTimeMillis();
        int n = tasks.size();
        int m = vms.size();
        Random rng = new Random(seed);
        ScheduleEvaluator evaluator = new ScheduleEvaluator();

        int maxIter = Math.max(1, feMax / popSize);

        double[][] x = new double[popSize][n];
        double[][] v = new double[popSize][n];
        double[][] pbest = new double[popSize][n];
        double[] pbestFitness = new double[popSize];

        double[] gbest = new double[n];
        double gbestFitness = Double.MAX_VALUE;
        int[] gbestMapping = new int[n];
        double[] gbestDispatchTimes = new double[n];

        int feCount = 0;
        List<Double> convergence = new ArrayList<>();

        // Initialize particles
        for (int i = 0; i < popSize; i++) {
            for (int k = 0; k < n; k++) {
                x[i][k] = rng.nextDouble() * m;
                v[i][k] = (rng.nextDouble() * 2.0 - 1.0) * vMax;
                pbest[i][k] = x[i][k];
            }
            int[] mapping = decode(x[i], m);
            var evalRes = evaluator.evaluate(mapping, tasks, vms, queuePolicy);
            double fit = fitnessFunc.evaluate(evalRes, baselines, m);
            feCount++;

            pbestFitness[i] = fit;
            if (fit < gbestFitness) {
                gbestFitness = fit;
                gbest = x[i].clone();
                gbestMapping = mapping.clone();
                gbestDispatchTimes = evalRes.dispatchTimes().clone();
            }
        }
        convergence.add(gbestFitness);

        // Main PSO loop
        for (int iter = 0; iter < maxIter && feCount < feMax; iter++) {
            double w = wMax - (wMax - wMin) * ((double) iter / maxIter);

            for (int i = 0; i < popSize; i++) {
                for (int k = 0; k < n; k++) {
                    double r1 = rng.nextDouble();
                    double r2 = rng.nextDouble();
                    v[i][k] = w * v[i][k] + c1 * r1 * (pbest[i][k] - x[i][k]) + c2 * r2 * (gbest[k] - x[i][k]);

                    if (v[i][k] > vMax) v[i][k] = vMax;
                    else if (v[i][k] < -vMax) v[i][k] = -vMax;

                    x[i][k] += v[i][k];
                    if (x[i][k] < 0) x[i][k] = 0.0;
                    else if (x[i][k] >= m) x[i][k] = m - 1e-6;
                }

                int[] mapping = decode(x[i], m);
                var evalRes = evaluator.evaluate(mapping, tasks, vms, queuePolicy);
                double fit = fitnessFunc.evaluate(evalRes, baselines, m);
                feCount++;

                if (fit < pbestFitness[i]) {
                    pbestFitness[i] = fit;
                    pbest[i] = x[i].clone();
                }

                if (fit < gbestFitness) {
                    gbestFitness = fit;
                    gbest = x[i].clone();
                    gbestMapping = mapping.clone();
                    gbestDispatchTimes = evalRes.dispatchTimes().clone();
                }

                if (feCount >= feMax) break;
            }
            convergence.add(gbestFitness);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        return new ScheduleResult(
                gbestMapping,
                gbestDispatchTimes,
                gbestFitness,
                elapsed,
                feCount,
                convergence
        );
    }
}
