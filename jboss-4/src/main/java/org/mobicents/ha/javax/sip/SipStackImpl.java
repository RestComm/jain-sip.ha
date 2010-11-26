/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.mobicents.ha.javax.sip;

import gov.nist.core.StackLogger;
import gov.nist.javax.sip.stack.MessageProcessor;

import java.util.Properties;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.sip.PeerUnavailableException;
import javax.sip.ProviderDoesNotExistException;
import javax.sip.SipException;

/**
 * This class extends the ClusteredSipStack to provide an implementation backed by JBoss Cache 1.4.X
 * 
 * @author jean.deruelle@gmail.com
 *
 */
public class SipStackImpl extends ClusteredSipStackImpl implements NotificationListener, SipStackImplMBean {
	public static String JAIN_SIP_MBEAN_NAME = "org.mobicents.jain.sip:type=sip-stack,name=";
	public static String LOG4J_SERVICE_MBEAN_NAME = "jboss.system:service=Logging,type=Log4jService";
	
	ObjectName oname = null;
	MBeanServer mbeanServer = null;
	boolean isMBeanServerNotAvailable = false;
	
	public static final String SIP_DEFAULT_CACHE_CLASS_NAME = "org.mobicents.ha.javax.sip.cache.JBossASSipCache";
	
	public static final String DIALOG_ROOT = "Dialogs/";
	public static final String SERVER_TX_ROOT = "ServerTransactions/";
	public static final String CLIENT_TX_ROOT = "ClientTransactions/";
	
	public SipStackImpl(Properties configurationProperties) throws PeerUnavailableException {		
		super(updateConfigProperties(configurationProperties));		
	}
	
	private static final Properties updateConfigProperties(Properties configurationProperties) {
		if(configurationProperties.getProperty(ClusteredSipStack.CACHE_CLASS_NAME_PROPERTY) == null) {
			configurationProperties.setProperty(ClusteredSipStack.CACHE_CLASS_NAME_PROPERTY, SIP_DEFAULT_CACHE_CLASS_NAME);
		}
		return configurationProperties;
	}
	
	public int getNumberOfClientTransactions() {		
		return getClientTransactionTableSize();
	}

	public int getNumberOfDialogs() {
		return dialogTable.size();	
	}
	
	public int getNumberOfEarlyDialogs() {
		return earlyDialogTable.size();	
	}

	public int getNumberOfServerTransactions() {
		return getServerTransactionTableSize();
	}
	
	@Override
	public void start() throws ProviderDoesNotExistException, SipException {
		super.start();
		String mBeanName=JAIN_SIP_MBEAN_NAME + stackName;
		try {
			oname = new ObjectName(mBeanName);
			if (getMBeanServer() != null && !getMBeanServer().isRegistered(oname)) {
				getMBeanServer().registerMBean(this, oname);							
			}
		} catch (Exception e) {
			getStackLogger().logError("Could not register the stack as an MBean under the following name", e);
			throw new SipException("Could not register the stack as an MBean under the following name " + mBeanName + ", cause: " + e.getMessage(), e);
		}
		try {
			if(getStackLogger().isLoggingEnabled(StackLogger.TRACE_INFO)) {
				getStackLogger().logInfo("Adding notification listener for logging mbean \"" + LOG4J_SERVICE_MBEAN_NAME + "\" to server " + getMBeanServer());
			}
			getMBeanServer().addNotificationListener(new ObjectName(LOG4J_SERVICE_MBEAN_NAME), this, null, null);
		} catch (Exception e) {
			getStackLogger().logWarning("Could not register the stack as a Notification Listener of " + LOG4J_SERVICE_MBEAN_NAME + " runtime changes to log4j.xml won't affect SIP Stack Logging");
		}
	}
	
	@Override
	public void stop() {
		String mBeanName=JAIN_SIP_MBEAN_NAME + stackName;
		try {
			if (oname != null && getMBeanServer() != null && getMBeanServer().isRegistered(oname)) {
				getMBeanServer().unregisterMBean(oname);
			}
		} catch (Exception e) {
			getStackLogger().logError("Could not unregister the stack as an MBean under the following name" + mBeanName);
		}
		super.stop();
	}
	
	/**
	 * Get the current MBean Server.
	 * 
	 * @return
	 * @throws Exception
	 */
	public MBeanServer getMBeanServer() throws Exception {
		if (mbeanServer == null && !isMBeanServerNotAvailable) {
			try {
				mbeanServer = (MBeanServer) MBeanServerFactory.findMBeanServer(null).get(0);				
			} catch (Exception e) {
				getStackLogger().logStackTrace(StackLogger.TRACE_DEBUG);
				getStackLogger().logWarning("No Mbean Server available, so JMX statistics won't be available");
				isMBeanServerNotAvailable = true;
			}
		}
		return mbeanServer;
	}

	public boolean isLocalMode() {
		return getSipCache().inLocalMode();
	}
	
	/*
	 * (non-Javadoc)
	 * @see javax.management.NotificationListener#handleNotification(javax.management.Notification, java.lang.Object)
	 */
	public void handleNotification(Notification notification, Object handback) {
		getStackLogger().setStackProperties(super.getConfigurationProperties());
	}

	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.ClusteredSipStack#passivateDialog(org.mobicents.ha.javax.sip.HASipDialog)
	 */
	public void passivateDialog(HASipDialog dialog) {
		throw new UnsupportedOperationException("The dialog eviction feature is not available on JBoss AS 4.2.X");
	}

	public MessageProcessor[] getStackMessageProcessors() {
		return getMessageProcessors();
	}
}
