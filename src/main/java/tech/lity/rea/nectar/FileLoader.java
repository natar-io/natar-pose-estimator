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
import processing.core.*;
import processing.data.JSONArray;
import processing.data.JSONObject;
import redis.clients.jedis.BinaryJedisPubSub;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import tech.lity.rea.nectar.calibration.files.HomographyCalibration;
import tech.lity.rea.nectar.calibration.files.ProjectiveDeviceCalibration;

/**
 *
 * @author Jeremy Laviole, <laviole@rea.lity.tech>
 */
@SuppressWarnings("serial")
public class FileLoader {

    static Jedis redis;
 
    // TODO: add hostname ?
    public static final String OUTPUT_PREFIX = "nectar:";
    public static final String OUTPUT_PREFIX2 = ":camera-server:camera";
    public static final String REDIS_PORT = "6379";

    static String defaultHost = "jiii-mi";
    static String defaultName = OUTPUT_PREFIX + defaultHost + OUTPUT_PREFIX2 + "#0";

    static ProjectiveDeviceP cameraDevice; 
    
    static public void main(String[] passedArgs) {

        connectRedis();

        try {

            Path currentRelativePath = Paths.get("");
            String path = currentRelativePath.toAbsolutePath().toString();

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

            // TODO: load the configuration from SVG. 
        } catch (Exception ex) {
            ex.printStackTrace();
            Logger.getLogger(FileLoader.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    
    
    class RedisThread extends Thread {
        public void run() {
            byte[] id = defaultName.getBytes();
            // Subscribe tests
            MyListener l = new MyListener();
//        byte[] id = defaultName.getBytes();
            redis.subscribe(l, markersChannels);
        }
    }      
    
    String markersChannels = "custom:markers:detected-markers";

    class MyListener extends JedisPubSub {

        // Listen to "camera
        public void onMessage(String channel, String message) {
            
            // TODO: 
            // Read the message with the markers. 
        }

        public void onSubscribe(String channel, int subscribedChannels) {
        }

        public void onUnsubscribe(String channel, int subscribedChannels) {
        }

        public void onPSubscribe(String pattern, int subscribedChannels) {
        }

        public void onPUnsubscribe(String pattern, int subscribedChannels) {
        }

        public void onPMessage(String pattern, String channel, String message) {
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
