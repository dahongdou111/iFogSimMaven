package org.fog.test.perfeval.ceph;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppModule;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Tuple;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.test.perfeval.ceph.osd.Message;
import org.fog.test.perfeval.ceph.osd.OpRequest;
import org.fog.utils.FogEvents;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.NetworkUsageMonitor;

import java.util.*;

public class OSDEntity extends FogDevice {

    // 工作队列
    Queue<OpRequest> op_wq;
    // 从节点都完成操作，主节点才往client发送ack。
    Map<Integer, Pair<Integer,Integer> > backend_ack;

    /**
     * Creates a new entity.
     *
     * @param name the name to be associated with this entity
     */
//    public OSDEntity(String name) {
//
//    }

    public OSDEntity(String name,
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
                Log.printLine(getName() + ":" + ev.getData().toString());
                processTupleArrival(ev);
                break;
            case FogEvents.UPDATE_SOUTH_TUPLE_QUEUE:
                updateSouthTupleQueue();
                break;
            case FogEvents.REPLICATED_BACKEND:

                break;
            case FogEvents.REPLICATED_BACKEND_ACK:

                break;
            default:
                break;
        }
    }

    @Override
    public void shutdownEntity() {

    }

    protected void processTupleArrival(SimEvent ev){
        Tuple preTuple = (Tuple) ev.getData();

        AppModule module = getApplicationMap().get(preTuple.getAppId()).getModuleByName(preTuple.getDestModuleName());

        List<Tuple> tuples = getApplicationMap().get(preTuple.getAppId()).getResultantTuples(module.getName(), preTuple, getId(), module.getId());
        Log.printLine(preTuple.getActualTupleId() + ":" + preTuple.getSourceDeviceId());

        Tuple tuple = tuples.get(0);
        tuple.setSourceDeviceId(getId());
        updateTimingsOnSending(tuple);

        // 发送给其他osd节点

        // 发送给client
        if (tuple.getDirection() == Tuple.DOWN) {
            sendDown(tuple, preTuple.getSourceDeviceId());
        }
    }

    protected void sendDown(Tuple tuple, int childId) {
        if (!isSouthLinkBusy()) {
            sendDownFreeLink(tuple, childId);
        } else {
            southTupleQueue.add(new Pair<>(tuple, childId));
        }
    }

    protected void sendDownFreeLink(Tuple tuple, int childId) {
        double networkDelay = tuple.getCloudletFileSize() / getDownlinkBandwidth();
        setSouthLinkBusy(true);
        double latency = getUplinkLatency(); // 应该是downLatency
        send(getId(), networkDelay, FogEvents.UPDATE_SOUTH_TUPLE_QUEUE);
        send(childId, networkDelay + latency, FogEvents.TUPLE_ARRIVAL, tuple);
        NetworkUsageMonitor.sendingTuple(latency, tuple.getCloudletFileSize());
    }
}
