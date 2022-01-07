//package org.fog.test.perfeval;
//
//import javafx.util.Pair;
//import org.fog.entities.Actuator;
//import org.fog.entities.FogDevice;
//import org.fog.entities.Sensor;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//public class MobilityFogDevice {
//    static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
//    static List<Sensor> sensors = new ArrayList<Sensor>();
//    static List<Actuator> actuators = new ArrayList<Actuator>();
//    static Map<String, Integer> getIdByName = new HashMap<>();
//    static Map<Integer, Pair<Double, Integer>> mobilityMap = new HashMap<Integer, Pair<Double, Integer>>();
//    static String mobilityDestination = "FogDevice-0";
//
//    public static void main(String[] args){
//        Controller controller = new Controller("master-controller", fogDe-vices, sensors, actuators);
//        controller.setMobilityMap(mobilityMap);
//    }
//
//    private static FogDevice addLowLevelFogDevice(String id, int brokerId, String appId, int parentId){
//        FogDevice lowLevelFogDevice = createAFogDevice("LowLevelFog-Device-"+id, 1000, 1000, 10000, 270, 2, 0, 87.53, 82.44);
//        lowLevelFogDevice.setParentId(parentId);
//        getIdByName.put(lowLevelFogDevice.getName(), lowLevelFogDevice.getId());
//        if((int)(Math.random()*100)%2==0){
//            Pair<Double, Integer> pair = new Pair<Double, Integer>(100.00, getIdByName.get(mobilityDestination));
//            mobilityMap.put(lowLevelFogDevice.getId(), pair);}
//        Sensor sensor = new Sensor("s-"+id, "Sensor", brokerId, appId, new DeterministicDistribution(getValue(5.00)));
//        sensors.add(sensor);
//        Actuator actuator = new Actuator("a-"+id, brokerId, appId, "OutputData");
//        actuators.add(actuator);
//        sensor.setGatewayDeviceId(lowLevelFogDevice.getId());
//        sensor.setLatency(6.0);
//        actuator.setGatewayDeviceId(lowLevelFogDevice.getId());
//        actuator.setLatency(1.0);
//        return lowLevelFogDevice;
//    }
//}
