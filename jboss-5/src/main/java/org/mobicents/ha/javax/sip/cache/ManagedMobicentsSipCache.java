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
package org.mobicents.ha.javax.sip.cache;

import gov.nist.core.StackLogger;
import gov.nist.javax.sip.stack.SIPClientTransaction;
import gov.nist.javax.sip.stack.SIPDialog;
import gov.nist.javax.sip.stack.SIPServerTransaction;

import java.util.Properties;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

import org.jboss.cache.CacheException;
import org.jboss.cache.CacheManager;
import org.jboss.cache.Fqn;
import org.jboss.cache.Node;
import org.jboss.ha.framework.server.CacheManagerLocator;
import org.mobicents.cache.MobicentsCache;
import org.mobicents.ha.javax.sip.ClusteredSipStack;
import org.mobicents.ha.javax.sip.SipStackImpl;

/**
 * Implementation of the SipCache interface, backed by a Mobicents Cache (JBoss Cache 3.X Cache).
 * The configuration of Mobicents Cache can be set throught the following Mobicents SIP Stack property :
 * <b>org.mobicents.ha.javax.sip.JBOSS_CACHE_CONFIG_PATH</b>
 * 
 * @author jean.deruelle@gmail.com
 *
 */
public class ManagedMobicentsSipCache extends MobicentsSipCache {

	public static final String JBOSS_CACHE_CONFIG_PATH = "org.mobicents.ha.javax.sip.JBOSS_CACHE_CONFIG_PATH";
	public static final String DEFAULT_FILE_CONFIG_PATH = "META-INF/cache-configuration.xml"; 
	public static final String STANDALONE = "org.mobicents.ha.javax.sip.cache.MobicentsSipCache.standalone";
	public static final String CACHE_NAME = "org.mobicents.ha.javax.sip.cache.MobicentsSipCache.cacheName";
	public static final String DEFAULT_CACHE_NAME = "jain-sip-cache";
	
	protected TransactionManager transactionManager;
	protected JBossJainSipCacheListener cacheListener;
	
	protected Node<String, SIPDialog> dialogRootNode = null;
	protected Node<String, SIPClientTransaction> clientTxRootNode = null;
	protected Node<String, SIPServerTransaction> serverTxRootNode = null;
	
	/**
	 * 
	 */
	public ManagedMobicentsSipCache() {
		super();
	}

	@Override
	public void putDialog(SIPDialog dialog) throws SipCacheException {
		boolean createdTx = false;
		try {
			if(transactionManager.getTransaction() == null) {
				transactionManager.begin();
				createdTx = true;
			}				
			super.putDialog(dialog);
			if(createdTx) {
				transactionManager.commit();
			}
		} catch (Exception e) {
			try {
				if (createdTx) {
					transactionManager.rollback();
				}
				else {
					transactionManager.setRollbackOnly();
				}
			} catch(Throwable t) {}
			throw new SipCacheException("A problem occured while putting the following dialog " + dialog.getDialogId() + "  into Mobicents Cache", e);
		} 
	}

	/* (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#removeDialog(java.lang.String)
	 */
	public void removeDialog(String dialogId) throws SipCacheException {
		boolean createdTx = false;
		try {
			if(transactionManager.getTransaction() == null) {
				transactionManager.begin();
				createdTx = true;
			}				
			super.removeDialog(dialogId);
			if(createdTx) {
				transactionManager.commit();
			}
		} catch (Exception e) {
			try {
				if (createdTx) {
					transactionManager.rollback();
				}
				else {
					transactionManager.setRollbackOnly();
				}
			} catch(Throwable t) {}
			throw new SipCacheException("A problem occured while removing the dialog with id " + dialogId + "  from Mobicents Cache", e);
		} 
	}

	@Override
	public void init() throws SipCacheException {			
		try {			
			if(configProperties.getProperty(ManagedMobicentsSipCache.STANDALONE) == null || "false".equals(configProperties.getProperty(ManagedMobicentsSipCache.STANDALONE))) {
				CacheManagerLocator locator = CacheManagerLocator.getCacheManagerLocator();
				// Locator accepts as param a set of JNDI properties to help in lookup;
				// this isn't necessary inside the AS
				CacheManager cacheManager = locator.getCacheManager(null);
				if (clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_INFO)) {
					clusteredSipStack.getStackLogger().logInfo(
							"Mobicents JAIN SIP JBoss Cache Manager instance : " + cacheManager);
				}
				cache = new MobicentsCache(cacheManager, configProperties.getProperty(CACHE_NAME,DEFAULT_CACHE_NAME));
			} else {
				String pojoConfigurationPath = configProperties.getProperty(JBOSS_CACHE_CONFIG_PATH, DEFAULT_FILE_CONFIG_PATH);
				if (clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_INFO)) {
					clusteredSipStack.getStackLogger().logInfo(
							"Mobicents JAIN SIP JBoss Cache Configuration path is : " + pojoConfigurationPath);
				}
				cache = new MobicentsCache(pojoConfigurationPath);
			}									
			cacheListener = new JBossJainSipCacheListener(clusteredSipStack);
			cache.getJBossCache().addCacheListener(cacheListener);			
		} catch (Exception e) {
			throw new SipCacheException("Couldn't init Mobicents Cache", e);
		}
		SIPDialogCacheData dialogRoot = new SIPDialogCacheData(Fqn.fromElements(SipStackImpl.DIALOG_ROOT), cache);
		dialogRoot.create();
	}

	public void start() throws SipCacheException {
		// Cache has already been starte, nothing to do here
		transactionManager = cache.getJBossCache().getConfiguration().getRuntimeConfig().getTransactionManager();
	}

	public void stop() throws SipCacheException {
		cache.stop();		
	}

}
