import java.util.List;

/**
 * Implements Trust SLA model from Paper 2 (Wu 2024, Eq. P2-8 to P2-11):
 *  - AV_i : Availability = accepted / assigned
 *  - SR_i : Success Rate = finished_before_deadline / assigned
 *  - TE_i : Time Efficiency = mean(min(1.0, ESTT / ACTT))
 *  - trust_i = X1*AV_i + X2*SR_i + X3*TE_i  (X = 0.5, 0.2, 0.1)
 *  - Trust_norm = sum_i(|Tasks_i| * trust_i) / (N * (X1 + X2 + X3))
 */
public class TrustModel {

    public static final double DEFAULT_X1 = 0.5;
    public static final double DEFAULT_X2 = 0.2;
    public static final double DEFAULT_X3 = 0.1;

    public record VmTrust(double av, double sr, double te, double rawTrust) {}

    public static double computeRawTrust(double av, double sr, double te, double x1, double x2, double x3) {
        return x1 * av + x2 * sr + x3 * te;
    }

    public static double computeRawTrust(double av, double sr, double te) {
        return computeRawTrust(av, sr, te, DEFAULT_X1, DEFAULT_X2, DEFAULT_X3);
    }

    public static double computeEstt(long length, double vmCoreMips) {
        return vmCoreMips > 0 ? length / vmCoreMips : 0.0;
    }

    public static double computeActt(double finishTime, double arrivalTime) {
        return Math.max(1e-9, finishTime - arrivalTime);
    }

    public static double computeTeRatio(double estt, double actt) {
        if (actt <= 0) return 1.0;
        return Math.min(1.0, estt / actt);
    }

    public static double normalizeSystemTrust(double weightedTrustSum, int totalTasks, double x1, double x2, double x3) {
        double maxScale = totalTasks * (x1 + x2 + x3);
        return maxScale > 0 ? Math.min(1.0, Math.max(0.0, weightedTrustSum / maxScale)) : 1.0;
    }
}
