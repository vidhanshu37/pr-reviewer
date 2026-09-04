package com.pr_reviewer.prreviewer.ExceptionHandler;

import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class StartupFailureListener implements ApplicationListener<ApplicationFailedEvent> {
    @Override
    public void onApplicationEvent(ApplicationFailedEvent event) {

        Throwable ex = event.getException();

        if (ex.getMessage().contains("Connection refused")) {

            System.err.println();
            System.err.println("PostgreSQL is not running.");
            System.err.println("Start the Docker container:");
            System.err.println("docker start pr-reviewer-pg");
        }
    }
}
