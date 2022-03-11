package com.relaypro.app;

import com.relaypro.sdk.Relay;
import com.relaypro.sdk.Workflow;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet(loadOnStartup = 1, value="/", name="startup")
public class StartupServlet extends HttpServlet {
    
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        
        System.out.println("Startup servlet");

        Relay.addWorkflow("hello", new Workflow() {
            @Override
            protected void onStart(Object startEvent) {
                super.onStart(startEvent);

                System.out.println("started hello wf");
            }
        });
        
    }
    
}
