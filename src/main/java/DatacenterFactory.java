import java.util.ArrayList;
import java.util.List;

import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

/**
 * Builds the infrastructure from the draft design:
 * 2 datacenters x 5 heterogeneous hosts (2 small, 2 medium, 1 large) and 20 VMs.
 * Power numbers are ASSUMPTIONS (for the energy metric) - documented in the report.
 */
public class DatacenterFactory {

    public record HostType(String name, int cores, long ramMb, double maxPowerW, double staticPowerW) {}
    public record VmType(String name, int vcpu, long mips, long ramMb) {}
    public record Infrastructure(List<Datacenter> datacenters, List<Host> hosts, List<HostType> hostTypes) {}

    public static final int PE_MIPS = 2000; // every host core

    public static final HostType HOST_SMALL  = new HostType("small", 4, 16_384, 150, 60);
    public static final HostType HOST_MEDIUM = new HostType("medium", 8, 32_768, 250, 100);
    public static final HostType HOST_LARGE  = new HostType("large", 16, 65_536, 400, 160);
    private static final List<HostType> DC_LAYOUT =
            List.of(HOST_SMALL, HOST_SMALL, HOST_MEDIUM, HOST_MEDIUM, HOST_LARGE);

    public static final VmType VM_SMALL  = new VmType("small", 2, 1000, 4_096);
    public static final VmType VM_MEDIUM = new VmType("medium", 4, 1500, 8_192);
    public static final VmType VM_LARGE  = new VmType("large", 8, 2000, 16_384);

    public static Infrastructure buildDatacenters(CloudSimPlus simulation) {
        List<Datacenter> datacenters = new ArrayList<>();
        List<Host> allHosts = new ArrayList<>();
        List<HostType> allTypes = new ArrayList<>();

        for (int d = 0; d < 2; d++) {
            List<Host> dcHosts = new ArrayList<>();
            for (HostType type : DC_LAYOUT) {
                List<Pe> pes = new ArrayList<>();
                for (int i = 0; i < type.cores(); i++) pes.add(new PeSimple(PE_MIPS));
                Host host = new HostSimple(type.ramMb(), 100_000, 1_000_000, pes);
                dcHosts.add(host);
                allHosts.add(host);
                allTypes.add(type);
            }
            datacenters.add(new DatacenterSimple(simulation, dcHosts));
        }
        return new Infrastructure(datacenters, allHosts, allTypes);
    }

    /** 2 large + 6 medium + 12 small = 20 VMs (64 vCPU of the 80 host cores).
     * Allocated large-first to avoid host fragmentation in CloudSim Plus worst-fit allocation. */
    public static List<Vm> buildVms() {
        List<Vm> vms = new ArrayList<>();
        addVms(vms, VM_LARGE, 2);
        addVms(vms, VM_MEDIUM, 6);
        addVms(vms, VM_SMALL, 12);
        for (int i = 0; i < vms.size(); i++) {
            vms.get(i).setId(i);
        }
        return vms;
    }

    private static void addVms(List<Vm> vms, VmType type, int count) {
        for (int i = 0; i < count; i++) {
            Vm vm = new VmSimple(type.mips(), type.vcpu());
            vm.setRam(type.ramMb());
            vm.setBw(1000);
            vm.setSize(10_000);
            // Space-shared: a VM runs up to `vcpu` cloudlets at once, one per vCPU, non-preemptive.
            vm.setCloudletScheduler(new CloudletSchedulerSpaceShared());
            vms.add(vm);
        }
    }
}
