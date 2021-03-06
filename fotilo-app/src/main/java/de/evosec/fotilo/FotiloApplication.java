package de.evosec.fotilo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Application;

public class FotiloApplication extends Application {

	private static final Logger LOG =
	        LoggerFactory.getLogger(FotiloApplication.class);

	private Thread.UncaughtExceptionHandler defaultHandler;

	private final Thread.UncaughtExceptionHandler handler =
	        new Thread.UncaughtExceptionHandler() {

		        @Override
		        public void uncaughtException(Thread thread, Throwable ex) {
			        LOG.error("Uncaught exception", ex);
			        defaultHandler.uncaughtException(thread, ex);
		        }
	        };

	@Override
	public void onCreate() {
		super.onCreate();
		defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler(handler);
	}

}
