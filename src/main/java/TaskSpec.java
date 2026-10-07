/** One row of the workload CSV (units: MI, bytes, seconds). */
public record TaskSpec(long id,
                       long length,
                       long fileSizeBytes,
                       long outputSizeBytes,
                       double arrival,
                       double deadline,
                       int priority,
                       double ramUtil,
                       String sizeClass) {
}
