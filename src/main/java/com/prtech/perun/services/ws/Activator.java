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
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.http.HttpService;
import org.osgi.util.tracker.ServiceTracker;

import com.prtech.svarog.SvConf;
import com.prtech.svarog_interfaces.IPerunPlugin;
import com.prtech.svarog_interfaces.ISvExecutor;

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
	 * The context path on the http server under which the static content from
	 * the /www folder inside the bundle will be served
	 */
	static final String httpContextPath = "/perun";

	/**
	 * Directory inside the bundle which will be served at the context path
	 */
	static final String httpLocalDir = "/www";
	/**
	 * Logger instance from the Svarog classloader so we log our Svarog specific
	 * info outside of the OSGI container
	 */
	static final Logger log4j = SvConf.getLogger(Activator.class);

	/**
	 * List of service registrations which we use for unregistering when
	 * cleaning up
	 */
	private ArrayList<ServiceRegistration> registration = new ArrayList<ServiceRegistration>();

	/**
	 * List of JAXRS Service classes which we will later use for registration in
	 * the bundle startup
	 */
	private ArrayList<Class<?>> jaxServiceClasses = initClasses();

	/**
	 * List of classes implementing ISvExecutor which we will later use for
	 * registration in the bundle startup
	 */
	private ArrayList<ISvExecutor> executorServiceClasses = initExecutors();

	/**
	 * Member used to track the http services in order to register path for
	 * serving static JS/Other content
	 */
	private ServiceTracker httpTracker;

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
		list.add(AdminConsole.class);
		list.add(PublicWs.class);
		return list;

	}

	/**
	 * List of executor objects to be used for initialisation
	 * 
	 * @return Map with executors
	 */
	private ArrayList<ISvExecutor> initExecutors() {
		ArrayList<ISvExecutor> list = new ArrayList<ISvExecutor>();
		list.add(new LoginExecutor());
		return list;

	}

	/**
	 * Implements BundleActivator.start(). Registers all instances of the JAXRS
	 * services as well as all objects implementing ISvExecutor interfaces using
	 * the bundle context;
	 * 
	 * @param context
	 *            the framework context for the bundle.
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
		for (ISvExecutor e : executorServiceClasses) {
			try {
				log4j.info("Registering executor service class: " + e.getClass().getName());
				svc = context.registerService(ISvExecutor.class.getName(), e, null);
			} catch (Exception ex) {
				log4j.error("Can't register service class:" + e.getClass().getName(), ex);
			}
			if (svc != null)
				this.registration.add(svc);
		}
		
		httpTracker = new ServiceTracker(context, HttpService.class.getName(), null) {
			public void removedService(ServiceReference reference, Object service) {
				// HTTP service is no longer available, unregister our
				// resources...
				try {
					((HttpService) service).unregister(httpContextPath);
				} catch (IllegalArgumentException exception) {
					// Ignore; servlet registration probably failed earlier
					// on...
				}
			}

			public Object addingService(ServiceReference reference) {
				// HTTP service is available, register our resources...
				HttpService httpService = (HttpService) this.context.getService(reference);
				try {
					httpService.registerResources(httpContextPath, httpLocalDir, null);
				} catch (Exception exception) {
					exception.printStackTrace();
				}
				return httpService;
			}
		};
		// start tracking all HTTP services...
		httpTracker.open();

	}

	/**
	 * Implements BundleActivator.stop(). Unregistering all services which have
	 * been registered
	 * 
	 * @param context
	 *            the framework context for the bundle.
	 */
	public void stop(BundleContext context) throws Exception {
		for (ServiceRegistration svc : registration) {
			svc.unregister();
		}

	}

}
