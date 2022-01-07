package org.fog.test.perfeval.ceph;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.entities.Actuator;
import org.fog.entities.Sensor;
import org.fog.placement.ModulePlacement;
import org.fog.test.perfeval.ceph.crush.Crush;
import org.fog.utils.FogEvents;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CephController extends SimEntity {

    private Crush.Crush_map crushMap;

    private Map<String, Application> applications;
    private List<ClientEntity> clientEntities;
    private List<OSDEntity> osdEntities;
    private List<Sensor> sensors;
    private List<Actuator> actuators;
    /**
     * Creates a new entity.
     *
     * @param name the name to be associated with this entity
     */
    public CephController(String name, List<ClientEntity> clientEntities, List<OSDEntity> osdEntities, List<Sensor> sensors, List<Actuator> actuators) {
        super(name);
        this.applications = new HashMap<>();

        //client和osd需要设置controller节点

        Map<Integer, OSDEntity> osdEntityMap = new HashMap<>();
        for (int i=0; i< osdEntities.size(); i++){
            osdEntityMap.put(i, osdEntities.get(i));
        }
        for (ClientEntity clientEntity: clientEntities){
            clientEntity.setOsdEntityMap(osdEntityMap);
            clientEntity.setControllerId(getId());
        }

        setClientEntities(clientEntities);
        setOsdEntities(osdEntities);
        setSensors(sensors);
        setActuators(actuators);
    }

    @Override
    public void startEntity() {

    }

    @Override
    public void processEvent(SimEvent ev) {
        switch (ev.getTag()) {
            case FogEvents.STOP_SIMULATION:
                CloudSim.stopSimulation();
                printTimeDetails();
                System.exit(0);
                break;
            default:
                break;
        }
    }

    @Override
    public void shutdownEntity() {

    }

//    private void processAppSubmit(Application application) {
//        System.out.println(CloudSim.clock() + " Submitted application " + application.getAppId());
//        FogUtils.appIdToGeoCoverageMap.put(application.getAppId(), application.getGeoCoverage());
//        getApplications().put(application.getAppId(), application);
//
//        for (ClientEntity cli : clientEntities) {
//            sendNow(cli.getId(), FogEvents.APP_SUBMIT, application);
//        }
//        for (OSDEntity osd : osdEntities) {
//            sendNow(osd.getId(), FogEvents.APP_SUBMIT, application);
//        }
//    }

    private void printTimeDetails() {
        System.out.println("=========================================");
        System.out.println("============== RESULTS ==================");
        System.out.println("=========================================");
        System.out.println("EXECUTION TIME : " + (Calendar.getInstance().getTimeInMillis() - TimeKeeper.getInstance().getSimulationStartTime()));
        System.out.println("=========================================");
        //System.out.println("APPLICATION LOOP DELAYS");
        //System.out.println("=========================================");
        for(Integer loopId : TimeKeeper.getInstance().getLoopIdToTupleIds().keySet()){
			/*double average = 0, count = 0;
			for(int tupleId : TimeKeeper.getInstance().getLoopIdToTupleIds().get(loopId)){
				Double startTime = 	TimeKeeper.getInstance().getEmitTimes().get(tupleId);
				Double endTime = 	TimeKeeper.getInstance().getEndTimes().get(tupleId);
				if(startTime == null || endTime == null)
					break;
				average += endTime-startTime;
				count += 1;
			}
			System.out.println(getStringForLoopId(loopId) + " ---> "+(average/count));*/
            System.out.println( " ---> "+TimeKeeper.getInstance().getLoopIdToCurrentAverage().get(loopId));
            System.out.println(TimeKeeper.getInstance().getLoopIdToCurrentNum().get(loopId));
        }
        System.out.println("=========================================");
        System.out.println("TUPLE CPU EXECUTION DELAY");
        System.out.println("=========================================");

        for (String tupleType : TimeKeeper.getInstance().getTupleTypeToAverageCpuTime().keySet()) {
            System.out.println(tupleType + " ---> " + TimeKeeper.getInstance().getTupleTypeToAverageCpuTime().get(tupleType));
        }

//        Application app = (Application) getApplications().values().toArray()[0];
//        for(AppLoop loop : app.getLoops()){
//            System.out.println(TimeKeeper.getInstance().getLoopIdToCurrentAverage().get(loop.getLoopId()));
//            System.out.println(TimeKeeper.getInstance().getLoopIdToCurrentNum().get(loop.getLoopId()));
//        }

//        for (double latency : TimeKeeper.getInstance().getLatencys()){
//            System.out.print(latency + " ");
//
//        }

        System.out.println("=========================================");
    }

    public void submitApplication(Application application, int delay, ModulePlacement modulePlacement) {
        FogUtils.appIdToGeoCoverageMap.put(application.getAppId(), application.getGeoCoverage());
        getApplications().put(application.getAppId(), application);

        for (Sensor sensor : sensors) {
            sensor.setApp(getApplications().get(sensor.getAppId()));
        }
        for (Actuator ac : actuators) {
            ac.setApp(getApplications().get(ac.getAppId()));
        }
        for (ClientEntity cli : clientEntities) {
            cli.setApplicationMap(applications);
            cli.getAssociatedActuatorIds().add(new Pair<>(actuators.get(0).getId(), 0.02));
        }
        for (OSDEntity osd : osdEntities) {
            osd.setApplicationMap(applications);
        }

        for (AppEdge edge : application.getEdges()) {
            if (edge.getEdgeType() == AppEdge.ACTUATOR) {
                String moduleName = edge.getSource();
                for (Actuator actuator : getActuators()) {
                    if (actuator.getActuatorType().equalsIgnoreCase(edge.getDestination())) {
                        application.getModuleByName(moduleName).subscribeActuator(actuator.getId(), edge.getTupleType());
                    }
                }
            }
        }
    }

    public Map<String, Application> getApplications() {
        return applications;
    }

    public void setApplications(Map<String, Application> applications) { this.applications = applications;}

    public List<ClientEntity> getClientEntities() {
        return clientEntities;
    }

    public void setClientEntities(List<ClientEntity> clientEntities) { this.clientEntities = clientEntities;}

    public List<OSDEntity> getOsdEntities() {
        return osdEntities;
    }

    public void setOsdEntities(List<OSDEntity> osdEntities) { this.osdEntities = osdEntities;}

    public List<Sensor> getSensors() {
        return sensors;
    }

    public void setSensors(List<Sensor> sensors) {this.sensors = sensors;}

    public List<Actuator> getActuators(){
        return actuators;
    }

    public void setActuators(List<Actuator> actuators) { this.actuators = actuators;}
}
