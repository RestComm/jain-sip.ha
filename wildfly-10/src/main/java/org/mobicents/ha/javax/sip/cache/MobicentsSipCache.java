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

package org.mobicents.ha.javax.sip.cache;

import java.util.Properties;

import org.mobicents.ha.javax.sip.ClusteredSipStack;
import org.mobicents.ha.javax.sip.HASipDialog;
import org.mobicents.ha.javax.sip.ReplicationStrategy;
import org.restcomm.cluster.MobicentsCluster;

import gov.nist.javax.sip.stack.SIPClientTransaction;
import gov.nist.javax.sip.stack.SIPDialog;
import gov.nist.javax.sip.stack.SIPServerTransaction;

/**
 * Implementation of the SipCache interface, backed by a Restcomm Cache (JBoss Cache 3.X Cache).
 * The configuration of Restcomm Cache can be set throught the following Restcomm SIP Stack property :
 * <b>org.mobicents.ha.javax.sip.JBOSS_CACHE_CONFIG_PATH</b>
 * 
 * @author jean.deruelle@gmail.com
 * @author martins
 *
 */
public abstract class MobicentsSipCache implements SipCache {
	
	ClusteredSipStack clusteredSipStack = null;
	protected Properties configProperties;
	protected MobicentsCluster ctCluster;
	protected MobicentsCluster stCluster;
	protected MobicentsCluster sdCluster;
	
	private final String name;
	private final ClassLoader serializationClassLoader;
	
	protected static final String CLIENT_TRANSACTION_APPENDER = "ct";
	protected static final String SERVER_TRANSACTION_APPENDER = "st";
	protected static final String SIP_DIALOG_APPENDER = "sd";
	
	private static final String DEFAULT_NAME = "jain-sip-ha";
	
	private DialogDataRemovalListener dialogDataRemovalListener;
	private ServerTransactionDataRemovalListener serverTransactionDataRemovalListener;
	private ClientTransactionDataRemovalListener clientTransactionDataRemovalListener;
	
	/**
	 * 
	 */
	public MobicentsSipCache() {
		this(DEFAULT_NAME,null);
	}

	public MobicentsSipCache(String name) {
		this(name,null);
	}

	public MobicentsSipCache(ClassLoader serializationClassLoader) {
		this(DEFAULT_NAME,serializationClassLoader);
	}

	public MobicentsSipCache(String name, ClassLoader serializationClassLoader) {
		if (name == null) {
			throw new NullPointerException("null name");
		}
		this.name = name;
		this.serializationClassLoader = serializationClassLoader;
	}

	/* (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#putDialog(gov.nist.javax.sip.stack.SIPDialog)
	 */
	public void putDialog(SIPDialog dialog) throws SipCacheException {
	    HASipDialog haSipDialog = (HASipDialog) dialog;
        String dialogId = haSipDialog.getDialogIdToReplicate();
	    SIPDialogCacheData cache = new SIPDialogCacheData(dialogId, sdCluster.getMobicentsCache(), clusteredSipStack);
		cache.putSIPDialog(dialog);
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#evictDialog(java.lang.String)
	 */
	public void evictDialog(String dialogId) {
	    SIPDialogCacheData cache = new SIPDialogCacheData(dialogId, sdCluster.getMobicentsCache(), clusteredSipStack);
	    cache.evict();
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#updateDialog(gov.nist.javax.sip.stack.SIPDialog)
	 */
	public void updateDialog(SIPDialog dialog) throws SipCacheException {
	    SIPDialogCacheData cache = new SIPDialogCacheData(dialog.getDialogId(), sdCluster.getMobicentsCache(), clusteredSipStack);
	    cache.updateSIPDialog(dialog);
	}

	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#getDialog(java.lang.String)
	 */
	public SIPDialog getDialog(String dialogId) throws SipCacheException {
	    SIPDialogCacheData cache = new SIPDialogCacheData(dialogId, sdCluster.getMobicentsCache(), clusteredSipStack);
		return cache.getSIPDialog();
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#removeDialog(java.lang.String)
	 */
	public void removeDialog(String dialogId) throws SipCacheException {
	    SIPDialogCacheData cache = new SIPDialogCacheData(dialogId, sdCluster.getMobicentsCache(), clusteredSipStack);
	    cache.removeSIPDialog();
	}
	
	/* (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#setClusteredSipStack(org.mobicents.ha.javax.sip.ClusteredSipStack)
	 */
	public void setClusteredSipStack(ClusteredSipStack clusteredSipStack) {
		this.clusteredSipStack  = clusteredSipStack;
	}

	/* (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#setConfigurationProperties(java.util.Properties)
	 */
	public void setConfigurationProperties(Properties configurationProperties) {
		this.configProperties = configurationProperties;
	}

	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#init()
	 */
	public abstract void init() throws SipCacheException;
	
	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#start()
	 */
	public void start() throws SipCacheException {
	    sdCluster.startCluster();
	    
		//create 3 caches
		
		dialogDataRemovalListener = new DialogDataRemovalListener(clusteredSipStack);
		sdCluster.addDataRemovalListener(dialogDataRemovalListener);
		
		if(clusteredSipStack.getReplicationStrategy() == ReplicationStrategy.EarlyDialog) {
		    stCluster.startCluster();
		    ctCluster.startCluster();	        
		    
			serverTransactionDataRemovalListener = new ServerTransactionDataRemovalListener(clusteredSipStack);
			stCluster.addDataRemovalListener(serverTransactionDataRemovalListener);
			
			clientTransactionDataRemovalListener = new ClientTransactionDataRemovalListener(clusteredSipStack);
			ctCluster.addDataRemovalListener(clientTransactionDataRemovalListener);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#stop()
	 */
	public void stop() throws SipCacheException {
	    sdCluster.stopCluster();
		sdCluster.removeDataRemovalListener(dialogDataRemovalListener);
		if(clusteredSipStack.getReplicationStrategy() == ReplicationStrategy.EarlyDialog) {
		    stCluster.stopCluster();
			stCluster.removeDataRemovalListener(serverTransactionDataRemovalListener);
			ctCluster.stopCluster();
			ctCluster.removeDataRemovalListener(clientTransactionDataRemovalListener);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#inLocalMode()
	 */
	public boolean inLocalMode() {
		return sdCluster.getMobicentsCache().isLocalMode();
	}
	
	public String getName() {
		return name;
	}
	
	public ClassLoader getSerializationClassLoader() {
		return serializationClassLoader;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#getServerTransaction(java.lang.String)
	 */
	public SIPServerTransaction getServerTransaction(String transactionId) throws SipCacheException {
	    ServerTransactionCacheData cache = new ServerTransactionCacheData(transactionId, stCluster.getMobicentsCache(), clusteredSipStack);
	    return cache.getServerTransaction();
	}

	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#putServerTransaction(gov.nist.javax.sip.stack.SIPServerTransaction)
	 */
	public void putServerTransaction(SIPServerTransaction serverTransaction) throws SipCacheException {
	    ServerTransactionCacheData cache = new ServerTransactionCacheData(serverTransaction.getTransactionId(), stCluster.getMobicentsCache(), clusteredSipStack);
	    cache.putServerTransaction(serverTransaction);
	}

	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#removeServerTransaction(java.lang.String)
	 */
	public void removeServerTransaction(String transactionId) throws SipCacheException {
	    ServerTransactionCacheData cache = new ServerTransactionCacheData(transactionId, stCluster.getMobicentsCache(), clusteredSipStack);
	    cache.removeServerTransaction();
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#getClientTransaction(java.lang.String)
	 */
	public SIPClientTransaction getClientTransaction(String transactionId) throws SipCacheException {
	    ClientTransactionCacheData cache = new ClientTransactionCacheData(transactionId, ctCluster.getMobicentsCache(), clusteredSipStack);
	    return cache.getClientTransaction();
	}

	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#putClientTransaction(gov.nist.javax.sip.stack.SIPClientTransaction)
	 */
	public void putClientTransaction(SIPClientTransaction clientTransaction) throws SipCacheException {
	    ClientTransactionCacheData cache = new ClientTransactionCacheData(clientTransaction.getTransactionId(), ctCluster.getMobicentsCache(), clusteredSipStack);
	    cache.putClientTransaction(clientTransaction);
	}

	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#removeClientTransaction(java.lang.String)
	 */
	public void removeClientTransaction(String transactionId) throws SipCacheException {
	    ClientTransactionCacheData cache = new ClientTransactionCacheData(transactionId, ctCluster.getMobicentsCache(), clusteredSipStack);
	    cache.removeClientTransaction();
	}
}
