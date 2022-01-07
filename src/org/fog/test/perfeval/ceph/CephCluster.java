package org.fog.test.perfeval.ceph;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.*;
import org.fog.placement.ModuleMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

public class CephCluster {
    static List<ClientEntity> clientEntities = new ArrayList<>();
    static List<OSDEntity> osdEntities = new ArrayList<>();
    static List<Sensor> sensors = new ArrayList<>();
    static List<Actuator> actuators = new ArrayList<>();

    public static void main(String[] args){
        Log.printLine("Starting Ceph Service...");

        try {
//            Log.disable();
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;

            CloudSim.init(num_user, calendar, trace_flag);

            String appId = "Ceph_Service";

            // 这个代理有什么用？
            FogBroker broker = new FogBroker("broker");

            Application application = createApplication(appId, broker.getId());
            createCephDevice(broker.getId(), appId);

            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
            moduleMapping.addModuleToDevice("clientModule", "client");
            moduleMapping.addModuleToDevice("OSDModule", "osd1");

            CephController controller = new CephController("cephController", clientEntities, osdEntities, sensors, actuators);
            controller.submitApplication(application, 0, null);

            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

            CloudSim.startSimulation();

            CloudSim.stopSimulation();

            Log.printLine("Ceph Service finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happen");
        }
    }

    private static void createCephDevice(int userId, String appId) {
        ClientEntity client = createClient("client");
        clientEntities.add(client);

        OSDEntity osd1 = createOSD("osd1");
        OSDEntity osd2 = createOSD("osd2");
        OSDEntity osd3 = createOSD("osd3");
        osdEntities.add(osd1);
        osdEntities.add(osd2);
        osdEntities.add(osd3);

        Sensor sensor = new Sensor("sensor", "M-SENSOR", userId, appId, new DeterministicDistribution(10));
        Actuator display = new Actuator("actuator", userId, appId, "M-DISPLAY");
        sensors.add(sensor);
        actuators.add(display);

        sensor.setGatewayDeviceId(client.getId());
        sensor.setLatency(6.0);
        display.setGatewayDeviceId(client.getId());
        display.setLatency(1.0);
    }

    private static ClientEntity createClient(String name) {
        int mips = 1000;
        int ram = 1000;
        double busyPower = 1;
        double idlePower = 1;
        long upBw = 10000;
        long downBw = 1000;
        double ratePerMips = 1000;


        List<Pe> peList = new ArrayList<>();
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));

        int hostId = FogUtils.generateEntityId();
        long storage = 1000000;
        int bw = 10000;

        PowerHost host = new PowerHost(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(bw),
                storage,
                peList,
                new StreamOperatorScheduler(peList),
                new FogLinearPowerModel(busyPower, idlePower)
        );

        List<Host> hostList = new ArrayList<>();
        hostList.add(host);

        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double time_zone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        // resource
        double costPerBw = 0.0;
        LinkedList<Storage> storageList = new LinkedList<>();
        // devices by now

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                arch, os, vmm, host, time_zone, cost, costPerMem,
                costPerStorage, costPerBw);

        ClientEntity cli = null;
        try {
            cli = new ClientEntity(name, characteristics, new AppModuleAllocationPolicy(hostList), storageList,
                    0.1, upBw, downBw, 0.001, ratePerMips);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return cli;
    }

    private static OSDEntity createOSD(String name) {
        int mips = 1000;
        int ram = 1000;
        double busyPower = 1;
        double idlePower = 1;
        long upBw = 1000;
        long downBw = 50;
        double ratePerMips = 1000;


        List<Pe> peList = new ArrayList<>();
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));

        int hostId = FogUtils.generateEntityId();
        long storage = 1000000;
        int bw = 10000;

        PowerHost host = new PowerHost(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(bw),
                storage,
                peList,
                new StreamOperatorScheduler(peList),
                new FogLinearPowerModel(busyPower, idlePower)
        );

        List<Host> hostList = new ArrayList<>();
        hostList.add(host);

        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double time_zone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        // resource
        double costPerBw = 0.0;
        LinkedList<Storage> storageList = new LinkedList<>();
        // devices by now

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                arch, os, vmm, host, time_zone, cost, costPerMem,
                costPerStorage, costPerBw);

        OSDEntity osd = null;
        try {
            osd = new OSDEntity(name, characteristics, new AppModuleAllocationPolicy(hostList), storageList,
                    10, upBw, downBw, 0, ratePerMips);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return osd;
    }

    private static Application createApplication(String appId, int userId) {
        Application application = Application.createApplication(appId, userId);

        application.addAppModule("clientModule", 10);
        application.addAppModule("OSDModule", 10);

        application.addAppEdge("M-SENSOR", "clientModule", 2000, 500, "M-SENSOR", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("clientModule", "OSDModule", 3500, 500, "RAW_DATA", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("OSDModule", "clientModule", 3500, 500, "ACTION_COMMAND", Tuple.DOWN, AppEdge.MODULE);
        application.addAppEdge("clientModule", "M-DISPLAY", 1000, 500, "ACTUATION_SIGNAL", Tuple.DOWN, AppEdge.ACTUATOR);

        application.addTupleMapping("clientModule", "M-SENSOR", "RAW_DATA", new FractionalSelectivity(1.0));
        application.addTupleMapping("OSDModule", "RAW_DATA", "ACTION_COMMAND", new FractionalSelectivity(1.0));
        application.addTupleMapping("clientModule", "ACTION_COMMAND", "ACTUATION_SIGNAL", new FractionalSelectivity(1.0));

        final AppLoop loop1 = new AppLoop(new ArrayList<String>() {{
            add("M-SENSOR");
            add("clientModule");
            add("OSDModule");
            add("clientModule");
            add("M-DISPLAY");
        }});
        List<AppLoop> loops = new ArrayList<AppLoop>() {{
            add(loop1);
        }};
        application.setLoops(loops);

        return application;
    }
}
