/*******************************************************************************
 * Copyright © 2016-2020 Torkild U. Resheim
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Torkild U. Resheim - initial API and implementation
 *******************************************************************************/
package net.resheim.eclipse.timekeeper.db;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.core.resources.ISaveContext;
import org.eclipse.core.resources.ISaveParticipant;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.osgi.service.datalocation.Location;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Core features for the time keeping application. Handles database and basic
 * time tracking.
 * 
 * @author Torkild U. Resheim
 */
public class TimekeeperPlugin extends Plugin {

	private static final Logger log = LoggerFactory.getLogger(TimekeeperPlugin.class);

	public static final String BUNDLE_ID = "net.resheim.eclipse.timekeeper.db"; //$NON-NLS-1$

	/* Preferences */
	public static final String PREF_DATABASE_URL = "database-url";
	public static final String PREF_DATABASE_LOCATION = "database-location";
	public static final String PREF_DATABASE_LOCATION_SHARED = "shared";
	public static final String PREF_DATABASE_LOCATION_WORKSPACE = "workspace";
	public static final String PREF_DATABASE_LOCATION_URL = "url";
	public static final String PREF_REPORT_TEMPLATES = "report-templates";
	public static final String PREF_DEFAULT_TEMPLATE = "default-template";

	private static TimekeeperPlugin instance;

	private TimekeeperService timekeeperService;

	private static final ListenerList<DatabaseChangeListener> listeners = new ListenerList<>();

	public void addListener(DatabaseChangeListener listener) {
		listeners.add(listener);
		log.info("Added new DatabaseChangeListener {}", listener);
	}

	public void removeListener(DatabaseChangeListener listener) {
		listeners.remove(listener);
	}

	private void notifyListeners() {
		log.info("Database state changed");
		for (DatabaseChangeListener databaseChangeListener : listeners) {
			SafeRunner.run(new ISafeRunnable() {
				@Override
				public void run() throws Exception {
					databaseChangeListener.databaseStateChanged();
				}

				@Override
				public void handleException(Throwable exception) {
					// ignore
				}
			});
		}
	}

	public boolean isReady() {
		return (timekeeperService != null && timekeeperService.initialized());
	}

	public class WorkspaceSaveParticipant implements ISaveParticipant {

		@Override
		public void doneSaving(ISaveContext context) {
			// nothing to do here
		}

		@Override
		public void prepareToSave(ISaveContext context) throws CoreException {
			// nothing to do here
		}

		@Override
		public void rollback(ISaveContext context) {
			// nothing to do here
		}

		@Override
		public void saving(ISaveContext context) throws CoreException {
		}
	}

	/**
	 * Returns the shared plug-in instance.
	 *
	 * @return the shared instance
	 */
	public static TimekeeperPlugin getDefault() {
		if (instance == null) {
			instance = new TimekeeperPlugin();
		}
		return instance;
	}

	public TimekeeperService getTimekeeperService() {
		return timekeeperService;
	}

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		log.info("Starting TimekeeperPlugin");
		instance = this;
		timekeeperService = new TimekeeperService(resolveJdbcUrl(), true);
		ISaveParticipant saveParticipant = new WorkspaceSaveParticipant();
		ResourcesPlugin.getWorkspace().addSaveParticipant(BUNDLE_ID, saveParticipant);
		notifyListeners();
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		timekeeperService.closePersistence();
		super.stop(context);
	}

	/**
	 * <p>
	 * If the lock file does not exist, it is created. Then a server socket is
	 * opened on a defined port, and kept open. The port and IP address of the
	 * process that opened the database is written into the lock file.
	 * </p>
	 * <p>
	 * If the lock file exists, and the lock method is 'file', then the software
	 * switches to the 'file' method.
	 * </p>
	 * <p>
	 * If the lock file exists, and the lock method is 'socket', then the process
	 * checks if the port is in use. If the original process is still running, the
	 * port is in use and this process throws an exception (database is in use). If
	 * the original process died (for example due to a power failure, or abnormal
	 * termination of the virtual machine), then the port was released. The new
	 * process deletes the lock file and starts again.
	 * </p>
	 * 
	 * @return a connection URL string for the shared location
	 */
	public String getSharedLocation() {
		return "jdbc:h2:~/.timekeeper/h2db;AUTO_SERVER=TRUE;FILE_LOCK=SOCKET;AUTO_RECONNECT=TRUE;AUTO_SERVER_PORT=9999";
	}

	private String getWorkspaceLocation() throws IOException {
		String jdbc_url;
		Location instanceLocation = Platform.getInstanceLocation();
		Path path = Path.of("~/.timekeeper");
		try {
			path = Paths.get(instanceLocation.getURL().toURI()).resolve(".timekeeper");
			if (!path.toFile().exists()) {
				Files.createDirectory(path);
			}
		} catch (URISyntaxException e) {
			log.error("Unable to resolve directory", e);
		}
		jdbc_url = "jdbc:h2:" + path + "/h2db";
		return jdbc_url;
	}

	public String getSpecifiedLocation() {
		String jdbc_url;
		jdbc_url = Platform.getPreferencesService().getString(BUNDLE_ID, PREF_DATABASE_URL,
				"jdbc:h2:tcp://localhost/~/.timekeeper/h2db", // note use server location per default
				new IScopeContext[] { InstanceScope.INSTANCE });
		return jdbc_url;
	}

	private String resolveJdbcUrl() throws IOException {
		String jdbc_url = "jdbc:h2:~/.timekeeper/h2db";
		String location = Platform.getPreferencesService().getString(BUNDLE_ID, PREF_DATABASE_LOCATION,
				PREF_DATABASE_LOCATION_SHARED, new IScopeContext[] { InstanceScope.INSTANCE });
		switch (location) {
		default:
		case PREF_DATABASE_LOCATION_SHARED:
			jdbc_url = getSharedLocation();
			// Fix https://github.com/turesheim/eclipse-timekeeper/issues/107
			System.setProperty("h2.bindAddress", "localhost");
			break;
		case PREF_DATABASE_LOCATION_WORKSPACE:
			jdbc_url = getWorkspaceLocation();
			break;
		case PREF_DATABASE_LOCATION_URL:
			jdbc_url = getSpecifiedLocation();
			break;
		}
		// if this property has been specified it will override all other settings
		if (System.getProperty("net.resheim.eclipse.timekeeper.db.url") != null) {
			log.info("Database URL was specified using property 'net.resheim.eclipse.timekeeper.db.url'");
			jdbc_url = System.getProperty("net.resheim.eclipse.timekeeper.db.url");
		}
		log.info("Using database at '{}'", jdbc_url);
		return jdbc_url;

	}

}
