package tech.lity.rea.nectar;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import org.bytedeco.javacv.Marker;
import org.xml.sax.SAXException;
import processing.core.*;
import processing.data.JSONArray;
import processing.data.JSONObject;
import processing.data.XML;
import redis.clients.jedis.BinaryJedisPubSub;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import tech.lity.rea.nectar.calibration.files.HomographyCalibration;
import tech.lity.rea.nectar.calibration.files.ProjectiveDeviceCalibration;
import tech.lity.rea.nectar.tracking.DetectedMarker;
import tech.lity.rea.nectar.tracking.MarkerList;
import tech.lity.rea.nectar.tracking.MarkerSVGReader;

/**
 *
 * @author Jeremy Laviole, <laviole@rea.lity.tech>
 */
@SuppressWarnings("serial")
public class FileLoader {

    static Jedis redis;
    static Jedis redisSend;

    // TODO: add hostname ?
    public static final String OUTPUT_PREFIX = "nectar:";
    public static final String OUTPUT_PREFIX2 = ":camera-server:camera";
    public static final String REDIS_PORT = "6379";

    static String defaultHost = "jiii-mi";
    static String defaultName = OUTPUT_PREFIX + defaultHost + OUTPUT_PREFIX2 + "#0";

    static ProjectiveDeviceP cameraDevice;
    static MarkerList markersFromSVG;

    static MarkerList loadMarkerList(String fileName) {
        try {
            XML xml;

            xml = new XML(new File(fileName));
            markersFromSVG = (new MarkerSVGReader(xml)).getList();
//            System.out.println("MARKER MODEL: " + markersFromSVG.toString());
            return markersFromSVG;
        } catch (IOException ex) {
            Logger.getLogger(FileLoader.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(FileLoader.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SAXException ex) {
            Logger.getLogger(FileLoader.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    static String testMessage = "{\"markers\":[{\"id\":101,\"dir\":0,\"confidence\":100,\"type\":\"ARTK\",\"center\":[495.66229248046877,137.36204528808595],\"corners\":[528.4631958007813,166.75375366210938,467.1305847167969,167.5231170654297,464.3076171875,109.0,524.7392578125,109.0]},{\"id\":104,\"dir\":0,\"confidence\":100,\"type\":\"ARTK\",\"center\":[177.1398162841797,140.3543701171875],\"corners\":[208.6136932373047,168.848876953125,144.86376953125,169.41928100585938,147.2744140625,110.0,209.93331909179688,110.0]},{\"id\":102,\"dir\":1,\"confidence\":100,\"type\":\"ARTK\",\"center\":[502.210693359375,250.17922973632813],\"corners\":[472.46484375,281.10406494140627,469.58148193359377,219.35861206054688,531.6472778320313,218.14463806152345,535.5087890625,279.6341552734375]},{\"id\":105,\"dir\":1,\"confidence\":100,\"type\":\"ARTK\",\"center\":[175.24224853515626,253.04522705078126],\"corners\":[141.70474243164063,284.3676452636719,143.3638458251953,221.02484130859376,208.11019897460938,220.9053192138672,206.58773803710938,282.7593994140625]},{\"id\":106,\"dir\":1,\"confidence\":100,\"type\":\"ARTK\",\"center\":[284.8018798828125,252.07203674316407],\"corners\":[258.0,278.6250915527344,258.0,224.0,314.0,224.0,314.0,277.7332763671875]},{\"id\":107,\"dir\":0,\"confidence\":100,\"type\":\"ARTK\",\"center\":[392.0487976074219,250.06759643554688],\"corners\":[420.7771911621094,276.8341979980469,365.35504150390627,277.3885803222656,364.02435302734377,224.1168212890625,418.80621337890627,222.5819091796875]},{\"id\":100,\"dir\":1,\"confidence\":100,\"type\":\"ARTK\",\"center\":[508.28436279296877,368.41619873046877],\"corners\":[477.65985107421877,400.4190368652344,474.90478515625,335.2553405761719,538.5796508789063,334.3932189941406,542.3674926757813,398.8800048828125]},{\"id\":103,\"dir\":0,\"confidence\":100,\"type\":\"ARTK\",\"center\":[173.85433959960938,371.16400146484377],\"corners\":[206.0,403.64190673828127,138.6188201904297,405.2336730957031,140.2191162109375,338.0,206.0,338.0]}],\"pose\":[0.0001670950441621244,12816.30078125,911981084672.0,13258579.0,12.264715194702149,6.93794675044046e-7,2.8004915714263918,0.011855416931211949,0.0007411280530504882,11.140938758850098,0.000011216202437935863,1.0724120258487347e-8,12335936.0,3395665.5,0.19462676346302033,0.0000026413042633066654]}";

    static public void main(String[] passedArgs) {

        connectRedis();

        try {

            Path currentRelativePath = Paths.get("");
            String path = currentRelativePath.toAbsolutePath().toString();

            System.out.println("uri: " + path + "/data/calib1.svg");
            loadMarkerList(path + "/data/calib1.svg");
//            loadMarkerList(path + "/data/A4-default.svg");
            DetectedMarker[] detectedMarkers = parseMarkerList(testMessage);

            PMatrix3D camProjExtrinsics = loadCalibration(path + "/data/camProjExtrinsics.xml");
            camProjExtrinsics.print();

            // CamProj Homoraphy
            PMatrix3D camProjHomography = loadCalibration(path + "/data/camProjHomography.xml");
            JSONObject cp = new JSONObject();
            cp.setJSONArray("matrix", ProjectiveDeviceP.PMatrixToJSON(camProjHomography));
            redis.set(defaultName + ":cam_proj_homograhy", cp.toString());

            // COLOR
            ProjectiveDeviceP pdp = ProjectiveDeviceP.loadCameraDevice(path + "/data/calibration-AstraS-rgb.yaml");
            redis.set(OUTPUT_PREFIX + defaultHost + ":calibration:astra-s-rgb", pdp.toJSON().toString());
            cameraDevice = pdp;

            // DEPTH
            pdp = ProjectiveDeviceP.loadCameraDevice(path + "/data/calibration-AstraS-depth.yaml");
            redis.set(OUTPUT_PREFIX + defaultHost + ":calibration:astra-s-depth", pdp.toJSON().toString());

            /// Extrinsics
            PMatrix3D extrinsics = loadCalibration(path + "/data/camProjExtrinsics.xml");
            JSONObject cp1 = new JSONObject();
            cp1.setJSONArray("matrix", ProjectiveDeviceP.PMatrixToJSON(extrinsics));
            redis.set(OUTPUT_PREFIX + defaultHost + ":calibration:astra-extrinsics", cp.toString());

            PMatrix3D pose = DetectedMarker.compute3DPos(detectedMarkers, markersFromSVG, cameraDevice);
            pose.print();

            RedisThread redisThread = new RedisThread();
            redisThread.start();

            System.out.println("Wating to finish....");
        } catch (Exception ex) {
            ex.printStackTrace();
            Logger.getLogger(FileLoader.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    static class RedisThread extends Thread {

        public void run() {
            byte[] id = defaultName.getBytes();
            // Subscribe tests
            MyListener l = new MyListener();
            System.out.println("In Redis Thread");
//        byte[] id = defaultName.getBytes();
            redis.subscribe(l, markersChannels);
        }
    }

    static void sendPose(String message) {

        DetectedMarker[] markers = parseMarkerList(message);
        if(markers.length < 1){
            System.out.println("No markers -> no pose");
            return;
        }
        PMatrix3D pose = DetectedMarker.compute3DPos(markers, markersFromSVG, cameraDevice);
        JSONArray poseJson = ProjectiveDeviceP.PMatrixToJSON(pose);

        System.out.println("Pose: " + poseJson.toString());
        redisSend.set("custom:image:detected-pose", poseJson.toString());
        redisSend.publish("custom:image:detected-pose", poseJson.toString());
    }

    static DetectedMarker[] parseMarkerList(String jsonMessage) {

        DetectedMarker detectedMarkers[] = new DetectedMarker[0];
//        Marker m = new Marker(0, corners);
        JSONObject msg = JSONObject.parse(jsonMessage);

        if (msg == null) {
            return detectedMarkers;
        }
//        System.out.println("json: " + msg.getJSONArray("markers").size());

        JSONArray markers = msg.getJSONArray("markers");

        if (markers != null && markers.size() > 0) {
            detectedMarkers = new DetectedMarker[markers.size()];
            for (int i = 0; i < markers.size(); i++) {
                JSONObject m = markers.getJSONObject(0);

                int id = m.getInt("id");
                JSONArray corners = m.getJSONArray("corners");

                assert (corners.size() == 8);
//                System.out.println("Corners size: " + corners.size());
                DetectedMarker dm = new DetectedMarker(id,
                        corners.getFloat(0),
                        corners.getFloat(1),
                        corners.getFloat(2),
                        corners.getFloat(3),
                        corners.getFloat(4),
                        corners.getFloat(5),
                        corners.getFloat(6),
                        corners.getFloat(7));

                detectedMarkers[i] = dm;
            }
        }

        return detectedMarkers;
    }

    static String markersChannels = "custom:image:detected-markers";

    static class MyListener extends JedisPubSub {

        // Listen to "camera
        @Override
        public void onMessage(String channel, String message) {

            System.out.println("CHANNEL: " + channel);
            System.out.println("Message: " + message);
            sendPose(message);
        }

        @Override
        public void onSubscribe(String channel, int subscribedChannels) {
            System.out.println("Subscribe to: " + channel);
        }

        @Override
        public void onUnsubscribe(String channel, int subscribedChannels) {
            System.out.println("CHANNEL: " + channel);
        }

        public void onPSubscribe(String pattern, int subscribedChannels) {
        }

        public void onPUnsubscribe(String pattern, int subscribedChannels) {

        }

        @Override
        public void onPMessage(String pattern, String channel, String message) {
            System.out.println("CHANNEL: " + channel);
        }
    }

    public static PMatrix3D loadCalibration(String fileName) {
//        File f = new File(sketchPath() + "/" + fileName);
        File f = new File(fileName);
        if (f.exists()) {
            try {
                //            return HomographyCalibration.getMatFrom(this, sketchPath() + "/" + fileName);
                return HomographyCalibration.getMatFrom(fileName);
            } catch (Exception ex) {
                Logger.getLogger(FileLoader.class.getName()).log(Level.SEVERE, null, ex);
                return null;
            }
        } else {
            return null;
        }
    }

    private static void connectRedis() {
        try {
            redis = new Jedis("127.0.0.1", 6379);
            redisSend = new Jedis("127.0.0.1", 6379);
            if (redis == null) {
                throw new Exception("Cannot connect to server. ");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
        // redis.auth("156;2Asatu:AUI?S2T51235AUEAIU");
    }

}
