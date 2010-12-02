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
import gov.nist.javax.sip.stack.SIPDialog;
import gov.nist.javax.sip.stack.SIPServerTransaction;

import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.transaction.UserTransaction;

import org.jboss.cache.CacheException;
import org.jboss.cache.Node;
import org.jboss.cache.PropertyConfigurator;
import org.jboss.cache.TreeCache;
import org.mobicents.ha.javax.sip.ClusteredSipStack;
import org.mobicents.ha.javax.sip.HASipDialog;
import org.mobicents.ha.javax.sip.SipStackImpl;

/**
 * Implementation of the SipCache interface, backed by a JBoss Cache 1.4.1 Tree Cache.
 * The configuration of the TreeCache can be set throught the following Mobicents SIP Stack property :
 * <b>org.mobicents.ha.javax.sip.TREE_CACHE_CONFIG_PATH</b>
 * 
 * @author jean.deruelle@gmail.com
 *
 */
public class JBossTreeSipCache extends AbstractJBossSipCache implements SipCache {
		
	public static final String TREE_CACHE_CONFIG_PATH = "org.mobicents.ha.javax.sip.TREE_CACHE_CONFIG_PATH";
	public static final String DEFAULT_FILE_CONFIG_PATH = "META-INF/replSync-service.xml"; 			
	
	protected TreeCache treeCache;	
	
	/**
	 * 
	 */
	public JBossTreeSipCache() {}

	/* (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#getDialog(java.lang.String)
	 */
	public SIPDialog getDialog(String dialogId) throws SipCacheException {		
		try {
			Node node = treeCache.get(SipStackImpl.DIALOG_ROOT + dialogId + CACHE_SEPARATOR + METADATA);
			if(node != null) {
				final Map<String, Object> dialogMetaData = node.getData();
				final Object dialogAppData = treeCache.get(SipStackImpl.DIALOG_ROOT + dialogId, APPDATA);
				
				return super.createDialog(dialogId, dialogMetaData, dialogAppData);
			} else {
				if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
					clusteredSipStack.getStackLogger().logDebug("key " + SipStackImpl.DIALOG_ROOT + dialogId + CACHE_SEPARATOR + METADATA + " not found in cache.");
				}
				return null;
			}
		} catch (CacheException e) {
			throw new SipCacheException("A problem occured while retrieving the following dialog " + dialogId + " from the TreeCache", e);
		} 
	}

	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#updateDialog(gov.nist.javax.sip.stack.SIPDialog)
	 */
	public void updateDialog(SIPDialog sipDialog) throws SipCacheException {
		final String dialogId = sipDialog.getDialogId();
		try {			
			Node node = treeCache.get(SipStackImpl.DIALOG_ROOT + dialogId + CACHE_SEPARATOR + METADATA);
			if(node != null) {
				final Map<String, Object> dialogMetaData = node.getData();
				final Object dialogAppData = treeCache.get(SipStackImpl.DIALOG_ROOT + dialogId, APPDATA);
				final HASipDialog haSipDialog = (HASipDialog) sipDialog;
				super.updateDialog(haSipDialog, dialogMetaData, dialogAppData);
			} else {
				if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
					clusteredSipStack.getStackLogger().logDebug("key " + SipStackImpl.DIALOG_ROOT + dialogId + CACHE_SEPARATOR + METADATA + " not found in cache.");
				}
			}
		} catch (CacheException e) {
			throw new SipCacheException("A problem occured while retrieving the following dialog " + dialogId + " from the TreeCache", e);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#putDialog(gov.nist.javax.sip.stack.SIPDialog)
	 */
	public void putDialog(SIPDialog dialog) throws SipCacheException {
		UserTransaction tx = null;
		try {
			Properties prop = new Properties();
			prop.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.cache.transaction.DummyContextFactory");
			tx = (UserTransaction) new InitialContext(prop).lookup("UserTransaction");
			if(tx != null) {
				tx.begin();
			}
			if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredSipStack.getStackLogger().logStackTrace();
			}
			final HASipDialog haSipDialog = (HASipDialog) dialog;
			final String dialogId = haSipDialog.getDialogIdToReplicate();
			if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredSipStack.getStackLogger().logDebug("put HA SIP Dialog " + dialog + " with dialogId " + dialogId + " in the cache");				
			}			
			final Map<String, Object> dialogMetaData = haSipDialog.getMetaDataToReplicate();
			if(dialogMetaData != null) {
				for (Entry<String, Object> metaData : dialogMetaData.entrySet()) {
					treeCache.put(SipStackImpl.DIALOG_ROOT + dialogId + CACHE_SEPARATOR +  METADATA, metaData.getKey(), metaData.getValue());
				}								
			}
			final Object dialogAppData = haSipDialog.getApplicationDataToReplicate();
			if(dialogAppData != null) {
				treeCache.put(SipStackImpl.DIALOG_ROOT + dialogId, APPDATA, dialogAppData);
			}
			if(tx != null) {
				tx.commit();
			}
		} catch (Exception e) {
			if(tx != null) {
				try { tx.rollback(); } catch(Throwable t) {}
			}
			throw new SipCacheException("A problem occured while putting the following dialog " + dialog.getDialogId() + "  into the TreeCache", e);
		} 
	}
	
	/* (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#removeDialog(java.lang.String)
	 */
	public void removeDialog(String dialogId) throws SipCacheException {
		UserTransaction tx = null;
		try {
			Properties prop = new Properties();
			prop.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.cache.transaction.DummyContextFactory");
			tx = (UserTransaction) new InitialContext(prop).lookup("UserTransaction");
			if(tx != null) {
				tx.begin();
			}
			if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredSipStack.getStackLogger().logDebug("remove HA SIP Dialog " + dialogId + " from the cache");				
			}
			treeCache.remove(SipStackImpl.DIALOG_ROOT + dialogId);
			if(tx != null) {
				tx.commit();
			}
		} catch (Exception e) {
			if(tx != null) {
				try { tx.rollback(); } catch(Throwable t) {}
			}
			throw new SipCacheException("A problem occured while removing the following dialog " + dialogId + " from the TreeCache", e);
		}
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

	public void init() throws SipCacheException {
		String pojoConfigurationPath = configProperties.getProperty(TREE_CACHE_CONFIG_PATH, DEFAULT_FILE_CONFIG_PATH);
		if (clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_INFO)) {
			clusteredSipStack.getStackLogger().logInfo(
					"Mobicents JAIN SIP Tree Cache Configuration path is : " + pojoConfigurationPath);
		}
		try {
			treeCache = new TreeCache();
			treeCache.createService();
			cacheListener = new JBossJainSipCacheListener(clusteredSipStack);
			treeCache.addTreeCacheListener(cacheListener);
			PropertyConfigurator config = new PropertyConfigurator(); // configure tree cache.
			config.configure(treeCache, pojoConfigurationPath);
		} catch (Exception e) {
			throw new SipCacheException("Couldn't init the TreeCache", e);
		}
	}

	public void start() throws SipCacheException {
		try {
			treeCache.start();
		} catch (Exception e) {
			throw new SipCacheException("Couldn't start the TreeCache", e);
		}
		if (clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_INFO)) {
			clusteredSipStack.getStackLogger().logInfo(
					"Mobicents JAIN SIP Tree Cache started, state: " + treeCache.getStateString() + 
					", Mode: " + treeCache.getCacheMode());
		}
	}

	public void stop() throws SipCacheException {
		treeCache.stop();
		treeCache.stopService();
		if (clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_INFO)) {
			clusteredSipStack.getStackLogger().logInfo(
					"Mobicents JAIN SIP Tree Cache stopped, state: " + treeCache.getStateString() + 
					", Mode: " + treeCache.getCacheMode());
		}
		treeCache.destroyService();
	}

	public boolean inLocalMode() {
		return false;
	}

	public TreeCache getCache() {
		return treeCache;
	}	
}
