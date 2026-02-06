/*
 *   Copyright 2006 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package com.prtech.perun.services.ws;

import java.util.ArrayList;

import org.apache.logging.log4j.Logger;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;

import com.prtech.menu.manager.WsMenu;
import com.prtech.models.ModelFactory;
import com.prtech.models.ModelFactoryRegistry;
import com.prtech.models.WsModel;
import com.prtech.svarog.SvConf;
import com.prtech.svarog_interfaces.ISvExecutorGroup;

/**
 * This class implements a simple bundle that uses the bundle context to
 * register a list of services with the OSGi framework. There two types of
 * services which you can register. A set of JAX.RS anotated services, which
 * shall be automatically picked up by the JAX RS publisher if available. A
 * Another type of services would be services implementing ISvExecutor interface
 * which provide internal communication means for svarog executor services.
 * 
 */
public class Activator implements BundleActivator {
	/**
	 * THIS PROJECT HAS ONLY JAVA SERVICES. No frontend html/js to serve
	 */
	/**
	 * Logger instance from the Svarog classloader so we log our Svarog specific
	 * info outside of the OSGI container
	 */
	static final Logger log4j = SvConf.getLogger(Activator.class);

	/**
	 * List of service registrations which we use for unregistering when cleaning up
	 */
	private ArrayList<ServiceRegistration> registration = new ArrayList<ServiceRegistration>();

	/**
	 * ServiceTracker that monitors all registered ModelFactory services across all
	 * bundles in the OSGi container
	 */
	private ServiceTracker<ModelFactory, ModelFactory> tracker;

	/**
	 * List of JAXRS Service classes which we will later use for registration in the
	 * bundle startup
	 */
	private ArrayList<Class<?>> jaxServiceClasses = initClasses();

	/**
	 * List of classes implementing ISvExecutor which we will later use for
	 * registration in the bundle startup
	 */
	private ArrayList<ISvExecutorGroup> executorServiceClasses = initExecutors();

	/**
	 * Init method adding all classes to the list
	 * 
	 * @return list with class objects
	 */
	private ArrayList<Class<?>> initClasses() {
		ArrayList<Class<?>> list = new ArrayList<Class<?>>();
		list.add(WsSecurityActions.class);
		list.add(WsReactElements.class);
		list.add(WsConf.class);
		list.add(ElementBuilder.class);
		list.add(WsAdminConsole.class);
		list.add(PublicWs.class);
		list.add(WsReporting.class);
		list.add(WsMenu.class);
		list.add(WsModel.class);
		return list;

	}

	/**
	 * List of executor objects to be used for initialisation
	 * 
	 * @return Map with executors
	 */
	private ArrayList<ISvExecutorGroup> initExecutors() {
		ArrayList<ISvExecutorGroup> list = new ArrayList<ISvExecutorGroup>();
		list.add(new BusinessLogicExecutors());
		return list;

	}

	/**
	 * Implements BundleActivator.start(). Registers all instances of the JAXRS
	 * services as well as all objects implementing ISvExecutor interfaces using the
	 * bundle context;
	 * 
	 * @param context the framework context for the bundle.
	 */
	@SuppressWarnings("unchecked")
	public void start(BundleContext context) {

		log4j.info("Starting Triglav Rest OSGI bundle");

		ServiceRegistration svc = null;

		for (Class<?> c : jaxServiceClasses) {
			try {
				log4j.info("Registering service class: " + c.getName());
				svc = context.registerService(c.getName(), c.newInstance(), null);
			} catch (Exception e) {
				log4j.error("Can't register service class:" + c.getName(), e);
			}
			if (svc != null)
				this.registration.add(svc);
		}
		for (ISvExecutorGroup e : executorServiceClasses) {
			try {
				log4j.info("Registering executor service class: " + e.getClass().getName());
				svc = context.registerService(ISvExecutorGroup.class.getName(), e, null);
			} catch (Exception ex) {
				log4j.error("Can't register service class:" + e.getClass().getName(), ex);
			}
			if (svc != null)
				this.registration.add(svc);
		}

		tracker = new ServiceTracker<ModelFactory, ModelFactory>(context, ModelFactory.class, null);
		tracker.open();
		ModelFactoryRegistry.setTracker(tracker);
	}

	/**
	 * Implements BundleActivator.stop(). Unregistering all services which have been
	 * registered
	 * 
	 * @param context the framework context for the bundle.
	 */
	public void stop(BundleContext context) throws Exception {
		for (ServiceRegistration svc : registration) {
			svc.unregister();
		}
		if (tracker != null) {
			tracker.close();
		}
	}

}
