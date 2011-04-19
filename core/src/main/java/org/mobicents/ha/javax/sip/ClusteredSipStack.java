/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
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
import gov.nist.javax.sip.stack.SIPDialog;
import gov.nist.javax.sip.stack.SIPTransaction;

import javax.management.MBeanServer;
import javax.sip.SipStack;
import javax.sip.address.Address;
import javax.sip.address.SipURI;

import org.mobicents.ha.javax.sip.cache.SipCache;

/**
 * This interface defines the method to be implemented by a SipStack that can be clustered.
 * 
 * @author jean.deruelle@gmail.com
 * @author martins
 *
 */
public interface ClusteredSipStack extends SipStack {	
	public static final String CACHE_CLASS_NAME_PROPERTY = "org.mobicents.ha.javax.sip.CACHE_CLASS_NAME";
	public static final String REPLICATION_STRATEGY_PROPERTY = "org.mobicents.ha.javax.sip.REPLICATION_STRATEGY";
	public static final String REPLICATE_APPLICATION_DATA = "org.mobicents.ha.javax.sip.REPLICATE_APPLICATION_DATA";
	
	SIPDialog getDialog(String dialogId);	
	SIPDialog putDialog(SIPDialog dialog);
	void removeDialog(SIPDialog dialog);		
	void remoteDialogRemoval(String dialogId);
	void passivateDialog(HASipDialog dialog);
	/**
	 * @param sipCache the sipCache to set
	 */
	void setSipCache(SipCache sipCache);
	/**
	 * @return the sipCache
	 */
	SipCache getSipCache();
	
	StackLogger getStackLogger();
	
	LoadBalancerHeartBeatingService getLoadBalancerHeartBeatingService();

	ReplicationStrategy getReplicationStrategy();
	
	/**
	 * Retrieves the {@link LoadBalancerElector} attached to the stack, if such
	 * feature is being used. The elector provides {@link Address} objects with
	 * {@link SipURI} pointing to each load balancer, useful to create outgoing
	 * requests.
	 * 
	 * @return
	 */
	LoadBalancerElector getLoadBalancerElector();

	void closeAllTcpSockets();
	
	MBeanServer getMBeanServer() throws Exception;	
	
    MessageProcessor[] getStackMessageProcessors();
     
    SIPTransaction findTransaction(String transactionId, boolean isServer);
    
    boolean isReplicateApplicationData();
	void remoteServerTransactionRemoval(String transactionId);
	void remoteClientTransactionRemoval(String transactionId);
}
