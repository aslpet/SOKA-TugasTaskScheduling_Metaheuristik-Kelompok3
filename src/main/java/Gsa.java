import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.cloudsimplus.vms.Vm;

/**
 * Gravitational Search Algorithm (GSA) for task scheduling
 * based on Mamalis & Perlitis (IJCA 2022).
 */
public class Gsa implements Scheduler {

    private final int popSize;
    private final int feMax;
    private final double g0;
    private final double alpha;
    private final FitnessFunction fitnessFunc;
    private final ScheduleEvaluator.QueuePolicy queuePolicy;

    public Gsa(int popSize, int feMax, double g0, double alpha, FitnessFunction fitnessFunc, ScheduleEvaluator.QueuePolicy queuePolicy) {
        this.popSize = popSize;
        this.feMax = feMax;
        this.g0 = g0;
        this.alpha = alpha;
        this.fitnessFunc = fitnessFunc;
        this.queuePolicy = queuePolicy;
    }

    public Gsa(FitnessFunction fitnessFunc, ScheduleEvaluator.QueuePolicy queuePolicy) {
        this(30, 3000, 100.0, 20.0, fitnessFunc, queuePolicy);
    }

    public static int[] decode(double[] continuousPosition, int m) {
        int[] discrete = new int[continuousPosition.length];
        for (int k = 0; k < continuousPosition.length; k++) {
            int vm = (int) Math.floor(continuousPosition[k]);
            discrete[k] = Math.max(0, Math.min(m - 1, vm));
        }
        return discrete;
    }

    public static double[] computeMasses(double[] fitnesses) {
        int sa = fitnesses.length;
        double best = Arrays.stream(fitnesses).min().orElse(0.0);
        double worst = Arrays.stream(fitnesses).max().orElse(0.0);

        double[] masses = new double[sa];
        if (Math.abs(best - worst) < 1e-12) {
            Arrays.fill(masses, 1.0 / sa);
            return masses;
        }

        double sumM = 0.0;
        for (int i = 0; i < sa; i++) {
            // Minimization: best has highest mass (Eq. P1-3)
            double m = (fitnesses[i] - worst) / (best - worst + 1e-12) + 1e-12;
            masses[i] = m;
            sumM += m;
        }

        for (int i = 0; i < sa; i++) {
            masses[i] = masses[i] / sumM;
        }
        return masses;
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
        double vMax = m / 2.0;

        // Initialize agents
        double[][] x = new double[popSize][n];
        double[][] v = new double[popSize][n];
        double[] fitness = new double[popSize];

        double globalBestFitness = Double.MAX_VALUE;
        int[] globalBestMapping = new int[n];
        double[] globalBestDispatchTimes = new double[n];

        int feCount = 0;
        List<Double> convergence = new ArrayList<>();

        for (int i = 0; i < popSize; i++) {
            for (int k = 0; k < n; k++) {
                x[i][k] = rng.nextDouble() * m;
                v[i][k] = 0.0;
            }
            int[] mapping = decode(x[i], m);
            var evalRes = evaluator.evaluate(mapping, tasks, vms, queuePolicy);
            fitness[i] = fitnessFunc.evaluate(evalRes, baselines, m);
            feCount++;

            if (fitness[i] < globalBestFitness) {
                globalBestFitness = fitness[i];
                globalBestMapping = mapping.clone();
                globalBestDispatchTimes = evalRes.dispatchTimes().clone();
            }
        }
        convergence.add(globalBestFitness);

        // Iteration loop
        for (int iter = 0; iter < maxIter && feCount < feMax; iter++) {
            double g = g0 * Math.exp(-alpha * (double) iter / maxIter);
            double[] masses = computeMasses(fitness);

            // Compute distances between agents: R_ij
            double[][] dist = new double[popSize][popSize];
            for (int i = 0; i < popSize; i++) {
                for (int j = i + 1; j < popSize; j++) {
                    double sumSq = 0.0;
                    for (int k = 0; k < n; k++) {
                        double d = x[i][k] - x[j][k];
                        sumSq += d * d;
                    }
                    double d = Math.sqrt(sumSq);
                    dist[i][j] = d;
                    dist[j][i] = d;
                }
            }

            // Update force, acceleration, velocity, position
            for (int i = 0; i < popSize; i++) {
                double[] force = new double[n];
                for (int j = 0; j < popSize; j++) {
                    if (j == i) continue;
                    double randJ = rng.nextDouble();
                    double r = dist[i][j] + 1e-12;
                    double fCoeff = randJ * g * (masses[i] * masses[j]) / r;
                    for (int k = 0; k < n; k++) {
                        force[k] += fCoeff * (x[j][k] - x[i][k]);
                    }
                }

                double mI = Math.max(1e-12, masses[i]);
                for (int k = 0; k < n; k++) {
                    double a = force[k] / mI;
                    v[i][k] = rng.nextDouble() * v[i][k] + a;
                    // Velocity clamping
                    if (v[i][k] > vMax) v[i][k] = vMax;
                    else if (v[i][k] < -vMax) v[i][k] = -vMax;

                    x[i][k] += v[i][k];
                    // Boundary handling
                    if (x[i][k] < 0) x[i][k] = 0.0;
                    else if (x[i][k] >= m) x[i][k] = m - 1e-6;
                }
            }

            // Evaluate population
            for (int i = 0; i < popSize && feCount < feMax; i++) {
                int[] mapping = decode(x[i], m);
                var evalRes = evaluator.evaluate(mapping, tasks, vms, queuePolicy);
                fitness[i] = fitnessFunc.evaluate(evalRes, baselines, m);
                feCount++;

                if (fitness[i] < globalBestFitness) {
                    globalBestFitness = fitness[i];
                    globalBestMapping = mapping.clone();
                    globalBestDispatchTimes = evalRes.dispatchTimes().clone();
                }
            }
            convergence.add(globalBestFitness);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        return new ScheduleResult(
                globalBestMapping,
                globalBestDispatchTimes,
                globalBestFitness,
                elapsed,
                feCount,
                convergence
        );
    }
}
