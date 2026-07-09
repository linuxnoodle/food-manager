package com.foodmanager.foodmanager.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

import java.net.ServerSocket;

/**
 * Stops a second boot from crashing when FoodManager is already up. a leftover or
 * intentional instance holds the server port (and the h2 file lock with it), so
 * starting again trips a scary "Database may be already in use" stack trace. this
 * checks the server port very early -- before tomcat/h2 try to bind -- and if it's
 * taken, logs that an instance is already running and exits cleanly (0), leaving
 * the live one to keep serving.
 */
@Slf4j
public class AlreadyRunningGuard implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        Environment env = event.getEnvironment();
        // server.port unset -> 8080. 0/-1 mean "random" so there's nothing to guard.
        Integer port = env.getProperty("server.port", Integer.class);
        int p = port == null ? 8080 : port;
        if (p <= 0) return;
        try (ServerSocket probe = new ServerSocket(p)) {
            // port was free -- we'll take it when tomcat starts. nothing to do.
        } catch (java.net.BindException alreadyTaken) {
            log.info("FoodManager already running on port {} -- keeping it up, this process exits.", p);
            System.exit(0);
        } catch (Exception e) {
            // unknown issue probing -- don't block startup over it
            log.warn("could not probe port {} for a prior instance: {}", p, e.getMessage());
        }
    }
}