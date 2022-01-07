package org.fog.test.perfeval.ceph;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Tuple;
import org.fog.utils.FogEvents;
import org.fog.utils.NetworkUsageMonitor;
import org.fog.utils.TimeKeeper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ClientEntity extends FogDevice {

    private Map<Integer, OSDEntity> osdEntityMap;
    protected int controllerId;
    private int tmp = 0;
    String appId;
    /**
     * Creates a new entity.
     *
     * @param name the name to be associated with this entity
     */
    public ClientEntity(String name,
                        FogDeviceCharacteristics characteristics,
                        VmAllocationPolicy vmAllocationPolicy,
                        List<Storage> storageList,
                        double schedulingInterval,
                        double uplinkBandwidth, double downlinkBandwidth, double uplinkLatency, double ratePerMips) throws Exception {
        super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval,
                uplinkBandwidth, downlinkBandwidth, uplinkLatency, ratePerMips);
    }

    @Override
    public void startEntity() {

    }

    @Override
    public void processEvent(SimEvent ev) {
        switch (ev.getTag()) {
            case FogEvents.TUPLE_ARRIVAL:
//                Log.printLine(ev.toString());
                processTupleArrival(ev);
                break;
            case FogEvents.UPDATE_NORTH_TUPLE_QUEUE:
                updateNorthTupleQueue();
            default:
                break;
        }
    }

    protected void processTupleArrival(SimEvent ev) {
        Tuple preTuple = (Tuple) ev.getData();
        tmp++;
        if (tmp > 1000){
            send(getControllerId(), 0, FogEvents.STOP_SIMULATION);
            return;
        }

        AppModule module = getApplicationMap().get(preTuple.getAppId()).getModuleByName("clientModule");

        List<Tuple> tuples = getApplicationMap().get(preTuple.getAppId()).getResultantTuples(module.getName(), preTuple, getId(), module.getId());
        // 获得到OSDModule的边

        Tuple tuple = tuples.get(0);
        tuple.setSourceDeviceId(getId());
        updateTimingsOnSending(tuple);

        // 发送给actuator
        if (tuple.getDirection() == Tuple.ACTUATOR) {
            sendTupleToActuator(tuple);
            return;
        }

        if (tuple.getDirection() == Tuple.UP) {
            sendUp(tuple);
        }
    }

    protected void sendUp(Tuple tuple) {
        if (!isNorthLinkBusy()) {
            sendUpFreeLink(tuple);
        } else {
            northTupleQueue.add(tuple);
        }
    }

    protected void sendUpFreeLink(Tuple tuple) {
        double networkDelay = tuple.getCloudletFileSize() / getUplinkBandwidth();
        System.out.println("CloudletFileSize:" + tuple.getCloudletFileSize() + "getUplinkBandwidth: " + getUplinkBandwidth());
        setNorthLinkBusy(true);
        send(getId(), networkDelay, FogEvents.UPDATE_NORTH_TUPLE_QUEUE);
        Random random = new Random();
        System.out.println("networkDelay:" + networkDelay + "UplinkLatency" + getUplinkLatency());
        send(getOsdEntityMap().get(random.nextInt(3)).getId(), networkDelay + getUplinkLatency(), FogEvents.TUPLE_ARRIVAL, tuple);
        NetworkUsageMonitor.sendingTuple(getUplinkLatency(), tuple.getCloudletFileSize());
    }

    @Override
    public void shutdownEntity() {

    }

    public Map<Integer, OSDEntity> getOsdEntityMap() {
        return osdEntityMap;
    }

    public void setOsdEntityMap(Map<Integer, OSDEntity> osdEntityMap) {
        this.osdEntityMap = osdEntityMap;
    }

    public int getControllerId() {
        return controllerId;
    }

    public void setControllerId(int controllerId) {
        this.controllerId = controllerId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }
}
