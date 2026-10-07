import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.cloudsimplus.vms.Vm;

/**
 * Trust-Aware Black Widow Optimization (TABWO) for task scheduling
 * based on Wu (J. Eng. Appl. Sci. 2024).
 */
public class Tabwo implements Scheduler {

    private final int popSize;
    private final int feMax;
    private final double pp;
    private final double cr;
    private final double pm;
    private final double sbcEta;
    private final FitnessFunction fitnessFunc;
    private final ScheduleEvaluator.QueuePolicy queuePolicy;
    private final boolean biasedInit;

    public Tabwo(int popSize, int feMax, double pp, double cr, double pm, double sbcEta,
                 FitnessFunction fitnessFunc, ScheduleEvaluator.QueuePolicy queuePolicy, boolean biasedInit) {
        this.popSize = popSize;
        this.feMax = feMax;
        this.pp = pp;
        this.cr = cr;
        this.pm = pm;
        this.sbcEta = sbcEta;
        this.fitnessFunc = fitnessFunc;
        this.queuePolicy = queuePolicy;
        this.biasedInit = biasedInit;
    }

    public Tabwo(FitnessFunction fitnessFunc, ScheduleEvaluator.QueuePolicy queuePolicy, boolean biasedInit) {
        this(30, 3000, 0.6, 0.44, 0.4, 20.0, fitnessFunc, queuePolicy, biasedInit);
    }

    public static int[] decode(double[] pos, int m) {
        int[] discrete = new int[pos.length];
        for (int k = 0; k < pos.length; k++) {
            int vm = (int) Math.floor(pos[k]);
            discrete[k] = Math.max(0, Math.min(m - 1, vm));
        }
        return discrete;
    }

    /**
     * Adaptive mutation rate:
     *  P = L / 50.0
     *  p_s(t) = P + (t - 1) * (1.0 - P) / (t_M - 1)
     *  mr(t) = p_s(t) / L
     */
    public static double computeAdaptiveMutationRate(int t, int tMax, int l) {
        if (tMax <= 1 || l <= 0) return 0.02;
        double p = l / 50.0;
        double ps = p + (double) (t - 1) * (1.0 - p) / (tMax - 1);
        return ps / (double) l;
    }

    /**
     * Procreation convex combination (Eq. P2-14):
     *  v1 = alpha .* u1 + (1 - alpha) .* u2
     *  v2 = alpha .* u2 + (1 - alpha) .* u1
     */
    public static double[][] procreate(double[] u1, double[] u2, double[] alpha) {
        int d = u1.length;
        double[] v1 = new double[d];
        double[] v2 = new double[d];
        for (int k = 0; k < d; k++) {
            v1[k] = alpha[k] * u1[k] + (1.0 - alpha[k]) * u2[k];
            v2[k] = alpha[k] * u2[k] + (1.0 - alpha[k]) * u1[k];
        }
        return new double[][]{v1, v2};
    }

    private static class Spider {
        double[] pos;
        double fitness;

        Spider(double[] pos, double fitness) {
            this.pos = pos;
            this.fitness = fitness;
        }
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
        List<Spider> population = new ArrayList<>(popSize);

        // Biased VM probabilities for Mode A
        double[] vmProbs = new double[m];
        if (biasedInit) {
            double sumP = 0.0;
            double[] dcPrices = new double[]{1.0, 1.5};
            double[] prioVm = PriorityModel.computeVmPriorities(dcPrices);
            for (int i = 0; i < m; i++) {
                int dcId = (i % 2);
                double p = prioVm[dcId] * (vms.get(i).getPesNumber() * vms.get(i).getMips());
                vmProbs[i] = p;
                sumP += p;
            }
            for (int i = 0; i < m; i++) vmProbs[i] /= sumP;
        }

        double globalBestFitness = Double.MAX_VALUE;
        int[] globalBestMapping = new int[n];
        double[] globalBestDispatchTimes = new double[n];
        int feCount = 0;
        List<Double> convergence = new ArrayList<>();

        // Initialize population
        for (int i = 0; i < popSize; i++) {
            double[] x = new double[n];
            for (int k = 0; k < n; k++) {
                if (biasedInit && rng.nextDouble() < 0.5) {
                    double r = rng.nextDouble();
                    double accum = 0.0;
                    int chosenVm = 0;
                    for (int v = 0; v < m; v++) {
                        accum += vmProbs[v];
                        if (r <= accum) { chosenVm = v; break; }
                    }
                    x[k] = chosenVm + rng.nextDouble() * 0.9999;
                } else {
                    x[k] = rng.nextDouble() * m;
                }
            }

            int[] mapping = decode(x, m);
            var evalRes = evaluator.evaluate(mapping, tasks, vms, queuePolicy);
            double fit = fitnessFunc.evaluate(evalRes, baselines, m);
            feCount++;
            population.add(new Spider(x, fit));

            if (fit < globalBestFitness) {
                globalBestFitness = fit;
                globalBestMapping = mapping.clone();
                globalBestDispatchTimes = evalRes.dispatchTimes().clone();
            }
        }
        convergence.add(globalBestFitness);

        // Main BWO generational loop
        int iter = 1;
        while (feCount < feMax && iter <= maxIter) {
            double mr = computeAdaptiveMutationRate(iter, maxIter, n);
            population.sort(Comparator.comparingDouble(s -> s.fitness));

            // Procreation & Cannibalism
            int numMating = Math.max(1, (int) Math.round(popSize * pp / 2.0));
            List<Spider> offspringList = new ArrayList<>();

            for (int p = 0; p < numMating && feCount < feMax; p++) {
                int idx1 = rng.nextInt(popSize);
                int idx2 = rng.nextInt(popSize);
                if (idx1 == idx2) idx2 = (idx1 + 1) % popSize;

                Spider parent1 = population.get(idx1);
                Spider parent2 = population.get(idx2);

                // Convex combination
                double[] alpha = new double[n];
                for (int k = 0; k < n; k++) alpha[k] = rng.nextDouble();

                double[][] childrenPos = procreate(parent1.pos, parent2.pos, alpha);
                List<Spider> pairChildren = new ArrayList<>(2);

                for (double[] childPos : childrenPos) {
                    // Boundary clip
                    for (int k = 0; k < n; k++) {
                        if (childPos[k] < 0) childPos[k] = 0;
                        else if (childPos[k] >= m) childPos[k] = m - 1e-6;
                    }
                    int[] mapping = decode(childPos, m);
                    var evalRes = evaluator.evaluate(mapping, tasks, vms, queuePolicy);
                    double fit = fitnessFunc.evaluate(evalRes, baselines, m);
                    feCount++;
                    Spider childSpider = new Spider(childPos, fit);
                    pairChildren.add(childSpider);

                    if (fit < globalBestFitness) {
                        globalBestFitness = fit;
                        globalBestMapping = mapping.clone();
                        globalBestDispatchTimes = evalRes.dispatchTimes().clone();
                    }
                    if (feCount >= feMax) break;
                }

                // Sibling Cannibalism: keep best (1 - CR)
                pairChildren.sort(Comparator.comparingDouble(s -> s.fitness));
                int survivors = Math.max(1, (int) Math.round(pairChildren.size() * (1.0 - cr)));
                for (int s = 0; s < survivors && s < pairChildren.size(); s++) {
                    offspringList.add(pairChildren.get(s));
                }

                // Sexual Cannibalism: weaker parent eaten
                Spider mother = parent1.fitness <= parent2.fitness ? parent1 : parent2;

                // Child-eats-mother: if best child is fitter than mother
                if (!pairChildren.isEmpty() && pairChildren.get(0).fitness < mother.fitness) {
                    mother.pos = pairChildren.get(0).pos.clone();
                    mother.fitness = pairChildren.get(0).fitness;
                }
            }

            // Adaptive Mutation on population
            for (Spider spider : population) {
                if (feCount >= feMax) break;
                if (rng.nextDouble() < pm) {
                    double[] mutatedPos = spider.pos.clone();
                    int operator = rng.nextInt(3); // 0: single point, 1: uniform, 2: SBC

                    if (operator == 0) {
                        // Single-point
                        int k = rng.nextInt(n);
                        mutatedPos[k] = rng.nextDouble() * m;
                    } else if (operator == 1) {
                        // Uniform with rate mr
                        for (int k = 0; k < n; k++) {
                            if (rng.nextDouble() < mr) {
                                mutatedPos[k] = rng.nextDouble() * m;
                            }
                        }
                    } else {
                        // Simulated Binary Crossover / polynomial perturbation
                        for (int k = 0; k < n; k++) {
                            if (rng.nextDouble() < mr) {
                                double u = rng.nextDouble();
                                double delta = (u <= 0.5)
                                        ? Math.pow(2.0 * u, 1.0 / (sbcEta + 1.0)) - 1.0
                                        : 1.0 - Math.pow(2.0 * (1.0 - u), 1.0 / (sbcEta + 1.0));
                                mutatedPos[k] += delta * (m / 4.0);
                                if (mutatedPos[k] < 0) mutatedPos[k] = 0;
                                else if (mutatedPos[k] >= m) mutatedPos[k] = m - 1e-6;
                            }
                        }
                    }

                    int[] mapping = decode(mutatedPos, m);
                    var evalRes = evaluator.evaluate(mapping, tasks, vms, queuePolicy);
                    double fit = fitnessFunc.evaluate(evalRes, baselines, m);
                    feCount++;

                    if (fit < spider.fitness) {
                        spider.pos = mutatedPos;
                        spider.fitness = fit;
                    }

                    if (fit < globalBestFitness) {
                        globalBestFitness = fit;
                        globalBestMapping = mapping.clone();
                        globalBestDispatchTimes = evalRes.dispatchTimes().clone();
                    }
                }
            }

            // Combine and retain top popSize spiders
            population.addAll(offspringList);
            population.sort(Comparator.comparingDouble(s -> s.fitness));
            while (population.size() > popSize) {
                population.remove(population.size() - 1);
            }

            convergence.add(globalBestFitness);
            iter++;
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
