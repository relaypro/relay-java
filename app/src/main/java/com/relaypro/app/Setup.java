package com.relaypro.app;

import com.relaypro.sdk.Relay;
import com.relaypro.sdk.Workflow;
//import org.apache.catalina.LifecycleException;
//import org.apache.catalina.startup.Tomcat;
//
//import java.io.File;
//import java.io.IOException;

public class Setup {
    
    private static final int PORT = 3000;
    
    public static void main(String... args) {
        System.out.println("started");
        
        Relay.addWorkflow("hello", new Workflow() {
            @Override
            protected void onStart(Object startEvent) {
                super.onStart(startEvent);

                System.out.println("started hello wf");
            }
        });
        
        // TODO bootstrap tomcat here
//        System.out.println("Starting tomcat server");
//        String appBase = ".";
//        Tomcat tomcat = new Tomcat();
////        tomcat.addContext("", "");
//        tomcat.setBaseDir(createTempDir());
//        tomcat.setPort(PORT);
//        tomcat.getHost().setAppBase(appBase);
//        tomcat.addWebapp("", appBase);
//        try {
//            tomcat.start();
//        } catch (LifecycleException e) {
//            e.printStackTrace();
//        }
//        tomcat.getServer().await();

    }

//    private static String createTempDir() {
//        try {
//            File tempDir = File.createTempFile("tomcat.", "." + PORT);
//            tempDir.delete();
//            tempDir.mkdir();
//            tempDir.deleteOnExit();
//            return tempDir.getAbsolutePath();
//        } catch (IOException ex) {
//            throw new RuntimeException("Unable to create tempDir. java.io.tmpdir is set to " + System.getProperty("java.io.tmpdir"), ex);
//        }
//    }
    
}
