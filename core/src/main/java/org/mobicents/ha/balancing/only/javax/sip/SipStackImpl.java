/*
 * TeleStax, Open Source Cloud Communications.
 * Copyright 2011-2013 and individual contributors by the @authors tag. 
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
package org.mobicents.ha.balancing.only.javax.sip;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.stack.MessageProcessor;

import java.util.Properties;

import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.sip.PeerUnavailableException;
import javax.sip.ProviderDoesNotExistException;
import javax.sip.SipException;

import org.mobicents.ha.javax.sip.ClusteredSipStack;
import org.mobicents.ha.javax.sip.ClusteredSipStackImpl;
import org.mobicents.ha.javax.sip.HASipDialog;
import org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingService;
import org.mobicents.ha.javax.sip.MultiNetworkLoadBalancerHeartBeatingServiceImpl;
import org.mobicents.ha.javax.sip.cache.NoCache;

/**
 * This class extends the ClusteredSipStack to provide an implementation backed by JBoss Cache 3.X
 * 
 * @author jean.deruelle@gmail.com
 *
 */
public class SipStackImpl extends ClusteredSipStackImpl implements SipStackImplMBean, NotificationListener {
	private static StackLogger logger = CommonLogger.getLogger(SipStackImpl.class);	
	public static String LOG4J_SERVICE_MBEAN_NAME = "jboss.system:service=Logging,type=Log4jService";
	
	public SipStackImpl(Properties configurationProperties) throws PeerUnavailableException {		
		super(updateConfigProperties(configurationProperties));		
	}
	
	@Override
	public void start() throws ProviderDoesNotExistException, SipException {
		super.start();		
		try {
			if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug("Adding notification listener for logging mbean \"" + LOG4J_SERVICE_MBEAN_NAME + "\" to server " + getMBeanServer());
			}
			getMBeanServer().addNotificationListener(new ObjectName(LOG4J_SERVICE_MBEAN_NAME), this, null, null);
		} catch (Exception e) {
			logger.logWarning("Could not register the stack as a Notification Listener of " + LOG4J_SERVICE_MBEAN_NAME + " runtime changes to log4j.xml won't affect SIP Stack Logging");
		}
	}
	
	@Override
	public void stop() {
		try {
			if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug("Removing notification listener for logging mbean \"" + LOG4J_SERVICE_MBEAN_NAME + "\" to server " + getMBeanServer());
			}
			getMBeanServer().removeNotificationListener(new ObjectName(LOG4J_SERVICE_MBEAN_NAME), this, null, null);
		} catch (Exception e) {
			logger.logWarning("Could not deregister the stack as a Notification Listener of " + LOG4J_SERVICE_MBEAN_NAME + " runtime changes to log4j.xml won't affect SIP Stack Logging");
		}
		super.stop();
	}
	
	private static final Properties updateConfigProperties(Properties configurationProperties) {
		if(configurationProperties.getProperty(ClusteredSipStack.CACHE_CLASS_NAME_PROPERTY) == null) {
			configurationProperties.setProperty(ClusteredSipStack.CACHE_CLASS_NAME_PROPERTY, NoCache.class.getName());
		}
		if(configurationProperties.getProperty(LoadBalancerHeartBeatingService.LB_HB_SERVICE_CLASS_NAME) == null) {
			configurationProperties.setProperty(LoadBalancerHeartBeatingService.LB_HB_SERVICE_CLASS_NAME, MultiNetworkLoadBalancerHeartBeatingServiceImpl.class.getName());
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
	
	public boolean isLocalMode() {
		return getSipCache().inLocalMode();
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.ClusteredSipStack#passivateDialog(org.mobicents.ha.javax.sip.HASipDialog)
	 */
	public void passivateDialog(HASipDialog dialog) {
		// not supported on load balancing mode only
	}

	public MessageProcessor[] getStackMessageProcessors() {
		return getMessageProcessors();
	}
}
