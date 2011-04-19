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

import gov.nist.javax.sip.stack.SIPClientTransaction;
import gov.nist.javax.sip.stack.SIPDialog;
import gov.nist.javax.sip.stack.SIPServerTransaction;

import java.util.Properties;

import org.jboss.cache.Fqn;
import org.jboss.cache.Region;
import org.mobicents.cache.MobicentsCache;
import org.mobicents.cluster.MobicentsCluster;
import org.mobicents.ha.javax.sip.ClusteredSipStack;
import org.mobicents.ha.javax.sip.ReplicationStrategy;

/**
 * Implementation of the SipCache interface, backed by a Mobicents Cache (JBoss Cache 3.X Cache).
 * The configuration of Mobicents Cache can be set throught the following Mobicents SIP Stack property :
 * <b>org.mobicents.ha.javax.sip.JBOSS_CACHE_CONFIG_PATH</b>
 * 
 * @author jean.deruelle@gmail.com
 * @author martins
 *
 */
public abstract class MobicentsSipCache implements SipCache {
	
	ClusteredSipStack clusteredSipStack = null;
	protected Properties configProperties;
	protected MobicentsCluster cluster;
	
	private final String name;
	private final ClassLoader serializationClassLoader;
	
	private static final String DEFAULT_NAME = "jain-sip-ha";
	
	private SIPDialogCacheData dialogsCacheData;
	private DialogDataRemovalListener dialogDataRemovalListener;
	private ServerTransactionCacheData serverTransactionCacheData;
	private ServerTransactionDataRemovalListener serverTransactionDataRemovalListener;
	private ClientTransactionCacheData clientTransactionCacheData;
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
		dialogsCacheData.putSIPDialog(dialog);
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#evictDialog(java.lang.String)
	 */
	public void evictDialog(String dialogId) {
		dialogsCacheData.evictSIPDialog(dialogId);
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#updateDialog(gov.nist.javax.sip.stack.SIPDialog)
	 */
	public void updateDialog(SIPDialog dialog) throws SipCacheException {
		dialogsCacheData.updateSIPDialog(dialog);
	}

	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#getDialog(java.lang.String)
	 */
	public SIPDialog getDialog(String dialogId) throws SipCacheException {
		return dialogsCacheData.getSIPDialog(dialogId);
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#removeDialog(java.lang.String)
	 */
	public void removeDialog(String dialogId) throws SipCacheException {
		dialogsCacheData.removeSIPDialog(dialogId);
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
		dialogsCacheData = new SIPDialogCacheData(Fqn.fromElements(name,SipCache.DIALOG_PARENT_FQN_ELEMENT),cluster.getMobicentsCache(), clusteredSipStack);
		dialogsCacheData.create();		
		dialogDataRemovalListener = new DialogDataRemovalListener(dialogsCacheData.getNodeFqn(), clusteredSipStack);
		cluster.addDataRemovalListener(dialogDataRemovalListener);
		if(clusteredSipStack.getReplicationStrategy() == ReplicationStrategy.EarlyDialog) {
			serverTransactionCacheData = new ServerTransactionCacheData(Fqn.fromElements(name,SipCache.SERVER_TX_PARENT_FQN_ELEMENT),cluster.getMobicentsCache(), clusteredSipStack);
			serverTransactionCacheData.create();
			serverTransactionDataRemovalListener = new ServerTransactionDataRemovalListener(serverTransactionCacheData.getNodeFqn(), clusteredSipStack);
			cluster.addDataRemovalListener(serverTransactionDataRemovalListener);
			clientTransactionCacheData = new ClientTransactionCacheData(Fqn.fromElements(name,SipCache.CLIENT_TX_PARENT_FQN_ELEMENT),cluster.getMobicentsCache(), clusteredSipStack);
			clientTransactionCacheData.create();
			clientTransactionDataRemovalListener = new ClientTransactionDataRemovalListener(clientTransactionCacheData.getNodeFqn(), clusteredSipStack);
			cluster.addDataRemovalListener(clientTransactionDataRemovalListener);
		}
		if (serializationClassLoader != null) {
			Region region = getMobicentsCache().getJBossCache().getRegion(dialogsCacheData.getNodeFqn(),true);
			region.registerContextClassLoader(serializationClassLoader);
			region.activate();
			if(clusteredSipStack.getReplicationStrategy() == ReplicationStrategy.EarlyDialog) {
				Region stxRegion = getMobicentsCache().getJBossCache().getRegion(serverTransactionCacheData.getNodeFqn(),true);
				stxRegion.registerContextClassLoader(serializationClassLoader);
				stxRegion.activate();
				Region ctxRegion = getMobicentsCache().getJBossCache().getRegion(clientTransactionCacheData.getNodeFqn(),true);
				ctxRegion.registerContextClassLoader(serializationClassLoader);
				ctxRegion.activate();
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#stop()
	 */
	public void stop() throws SipCacheException {
		dialogsCacheData.remove();
		cluster.removeDataRemovalListener(dialogDataRemovalListener);
		if(clusteredSipStack.getReplicationStrategy() == ReplicationStrategy.EarlyDialog) {
			serverTransactionCacheData.remove();
			cluster.removeDataRemovalListener(serverTransactionDataRemovalListener);
			clientTransactionCacheData.remove();
			cluster.removeDataRemovalListener(clientTransactionDataRemovalListener);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#inLocalMode()
	 */
	public boolean inLocalMode() {
		return getMobicentsCache().isLocalMode();
	}
	
	public String getName() {
		return name;
	}
	
	public ClassLoader getSerializationClassLoader() {
		return serializationClassLoader;
	}
	
	public MobicentsCache getMobicentsCache() {
		return cluster.getMobicentsCache();
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#getServerTransaction(java.lang.String)
	 */
	public SIPServerTransaction getServerTransaction(String transactionId) throws SipCacheException {
		return serverTransactionCacheData.getServerTransaction(transactionId);
	}

	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#putServerTransaction(gov.nist.javax.sip.stack.SIPServerTransaction)
	 */
	public void putServerTransaction(SIPServerTransaction serverTransaction) throws SipCacheException {
		serverTransactionCacheData.putServerTransaction(serverTransaction);
	}

	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#removeServerTransaction(java.lang.String)
	 */
	public void removeServerTransaction(String transactionId) throws SipCacheException {
		serverTransactionCacheData.removeServerTransaction(transactionId);
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#getClientTransaction(java.lang.String)
	 */
	public SIPClientTransaction getClientTransaction(String transactionId) throws SipCacheException {
		return clientTransactionCacheData.getClientTransaction(transactionId);
	}

	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#putClientTransaction(gov.nist.javax.sip.stack.SIPClientTransaction)
	 */
	public void putClientTransaction(SIPClientTransaction clientTransaction) throws SipCacheException {
		clientTransactionCacheData.putClientTransaction(clientTransaction);
	}

	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#removeClientTransaction(java.lang.String)
	 */
	public void removeClientTransaction(String transactionId) throws SipCacheException {
		clientTransactionCacheData.removeClientTransaction(transactionId);
	}
}
