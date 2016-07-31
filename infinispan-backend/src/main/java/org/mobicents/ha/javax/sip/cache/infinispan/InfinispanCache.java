/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2016, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package org.mobicents.ha.javax.sip.cache.infinispan;

import gov.nist.core.CommonLogger;
import gov.nist.core.LogLevels;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.stack.SIPClientTransaction;
import gov.nist.javax.sip.stack.SIPDialog;
import gov.nist.javax.sip.stack.SIPServerTransaction;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.infinispan.Cache;
import org.infinispan.manager.CacheContainer;
import org.mobicents.ha.javax.sip.ClusteredSipStack;
import org.mobicents.ha.javax.sip.cache.SipCache;
import org.mobicents.ha.javax.sip.cache.SipCacheException;

/**
 * Implementation of the SipCache interface, backed by an Infinispan Cache
 * 
 * The configuration of Infinispan Cache can be set through the following Restcomm SIP Stack property :
 * <b>org.mobicents.ha.javax.sip.INFINISPAN_CACHE_CONFIG_PATH</b>
 * 
 * If there is an already existing Infinispan CacheManager instance to be used, then it can be plugged in by specifying its JNDI name through the following property:
 * <b>org.mobicents.ha.javax.sip.INFINISPAN_CACHEMANAGER_JNDI_NAME</b>
 * 
 * If neither the Infinispan cache configuration path property, nor the CacheManager JNDI name are specified, then a default Infinispan config will be used, which can be found at:
 * <b>META-INF/cache-configuration.xml</b> 
 * 
 * @author <A HREF="mailto:posfai.gergely@ext.alerant.hu">Gergely Posfai</A>
 * @author <A HREF="mailto:kokuti.andras@ext.alerant.hu">Andras Kokuti</A>
 *
 */
public class InfinispanCache implements SipCache {
	
	public static final String INFINISPAN_CACHE_CONFIG_PATH = "org.mobicents.ha.javax.sip.INFINISPAN_CACHE_CONFIG_PATH";
	public static final String DEFAULT_FILE_CONFIG_PATH = "META-INF/cache-configuration.xml"; 
	public static final String INFINISPAN_CACHEMANAGER_JNDI_NAME = "org.mobicents.ha.javax.sip.INFINISPAN_CACHEMANAGER_JNDI_NAME";
	private static StackLogger clusteredlogger = CommonLogger.getLogger(InfinispanCache.class);
	
	private ScheduledThreadPoolExecutor executor = null;
	private Properties configProperties = null;
	private ClusteredSipStack stack;
	private Cache<String, Object> dialogs;
	private Cache<String, Object> appDataMap;
	private Cache<String, Object> serverTransactions;
	private Cache<String, Object> serverTransactionsApp;
	private Cache<String, Object> clientTransactions;
	private Cache<String, Object> clientTransactionsApp;
	
	private CacheContainer cm;
	
	private SIPDialogCacheData dialogCacheData;
	private SIPServerTransactionCacheData serverTXCacheData;
	private SIPClientTransactionCacheData clientTXCacheData;
	
	public SIPDialog getDialog(String dialogId) throws SipCacheException {
		if (dialogId == null) 
			throw new SipCacheException("No dialogId");
		
		if (dialogCacheData != null)
			return dialogCacheData.getDialog(dialogId);
		else
			throw new SipCacheException("No SIPClientTransactionCache");
	}
	
	public void putDialog(SIPDialog dialog) throws SipCacheException {
		if (dialog == null) 
			throw new SipCacheException("SipDialog is null");
		
		if (dialogCacheData != null)
			dialogCacheData.putDialog(dialog);
		else
			throw new SipCacheException("No SIPClientTransactionCache");
	}
	
	public void updateDialog(SIPDialog dialog) throws SipCacheException {
		if (dialog == null) 
			throw new SipCacheException("SipDialog is null");
		
		if (dialogCacheData != null)
			dialogCacheData.updateDialog(dialog);
		else
			throw new SipCacheException("No SIPClientTransactionCache");
	}
	
	public void removeDialog(String dialogId) throws SipCacheException {
		if (dialogId == null) 
			throw new SipCacheException("No dialogId");
		
		if (dialogCacheData != null){
			dialogCacheData.removeDialog(dialogId);
		}else{
			throw new SipCacheException("No SIPClientTransactionCache");
		}
	}
	
	public void evictDialog(String dialogId) {
		if (dialogCacheData != null)
			dialogCacheData.evictDialog(dialogId);
	}
	
	public SIPClientTransaction getClientTransaction(String txId) 
			throws SipCacheException {
		if (clientTXCacheData != null)
			return clientTXCacheData.getClientTransaction(txId);
		else
			throw new SipCacheException("No SIPClientTransactionCache");
	}

	public void putClientTransaction(SIPClientTransaction clientTransaction) 
			throws SipCacheException {
		if (clientTXCacheData != null)
			clientTXCacheData.putClientTransaction(clientTransaction);
		else
			throw new SipCacheException("No SIPClientTransactionCache");
	}
	
	public void removeClientTransaction(String txId) 
			throws SipCacheException {
		if (clientTXCacheData != null)
			clientTXCacheData.removeClientTransaction(txId);
		else
			throw new SipCacheException("No SIPClientTransactionCache");
	}
	
	public SIPServerTransaction getServerTransaction(String txId) 
			throws SipCacheException {
		if (serverTXCacheData != null)
			return serverTXCacheData.getServerTransaction(txId);
		else
			throw new SipCacheException("No SIPServerTransactionCache");
	}

	public void putServerTransaction(SIPServerTransaction serverTransaction) 
			throws SipCacheException {
		if (serverTXCacheData != null)
			serverTXCacheData.putServerTransaction(serverTransaction);
		else
			throw new SipCacheException("No SIPServerTransactionCache");
	}

	public void removeServerTransaction(String txId) 
			throws SipCacheException {
		if (serverTXCacheData != null)
			serverTXCacheData.removeServerTransaction(txId);
		else
			throw new SipCacheException("No SIPServerTransactionCache");
	}
	
	public void init() throws SipCacheException {
		
		executor = new ScheduledThreadPoolExecutor(1);
		
		final String configurationPath = configProperties.getProperty(INFINISPAN_CACHE_CONFIG_PATH, DEFAULT_FILE_CONFIG_PATH);
		
		if (configProperties.containsKey(INFINISPAN_CACHEMANAGER_JNDI_NAME)){
			if(clusteredlogger.isLoggingEnabled(LogLevels.TRACE_INFO)) {
				clusteredlogger.logInfo(INFINISPAN_CACHEMANAGER_JNDI_NAME + " specified, trying to load Inifinispan CacheManager from JNDI " + configProperties.getProperty(INFINISPAN_CACHEMANAGER_JNDI_NAME));
			}
			executor.scheduleAtFixedRate(new Runnable() {
				
				static final int MAX_ATTEMPTS = 30;
				int attempts = 0;
				
				public void run() {
					attempts++;
					// Init Infinispan CacheManager
					if (configProperties.containsKey(INFINISPAN_CACHEMANAGER_JNDI_NAME)){
						try {
							InitialContext context = new InitialContext();
							String cacheManagerJndiName = configProperties.getProperty(INFINISPAN_CACHEMANAGER_JNDI_NAME);
							cm = (CacheContainer) context.lookup(cacheManagerJndiName);
							if(clusteredlogger.isLoggingEnabled(LogLevels.TRACE_INFO)) {
								clusteredlogger.logInfo("Found Inifinispan CacheManager: cacheManagerJndiName \"" + cacheManagerJndiName + "\" " + cm + " after attempts " + attempts);
							}
							executor.remove(this);
							executor.shutdown();
						} catch (NamingException e) {
							// Inifinispan CacheManager JNDI lookup failed: could not get InitialContext or lookup failed
							if(attempts > MAX_ATTEMPTS) {
								clusteredlogger.logError("Inifinispan CacheManager JNDI lookup failed: could not get InitialContext or lookup failed after attempts " + attempts + " stopping there", e);
								executor.remove(this);
								executor.shutdown();
							} else {
								if(clusteredlogger.isLoggingEnabled(LogLevels.TRACE_INFO)) {
									clusteredlogger.logInfo("Inifinispan CacheManager JNDI lookup failed: could not get InitialContext or lookup failed after attempts " + attempts + ", retrying every second");
								}
							}
							return;
						}
					}
					
					setupCacheStructures();
					
					if(dialogCacheData != null) {
						dialogCacheData.setDialogs(dialogs);
					}
					if(serverTXCacheData != null) {
						serverTXCacheData.setServerTransactions(serverTransactions);
						serverTXCacheData.setServerTransactionsApp(serverTransactionsApp);
					}
					if(clientTXCacheData != null) {
						clientTXCacheData.setClientTransactions(clientTransactions);
						clientTXCacheData.setClientTransactionsApp(clientTransactionsApp);
					}
				}
			} , 0, 1, TimeUnit.SECONDS);
		} else {
			if(clusteredlogger.isLoggingEnabled(LogLevels.TRACE_INFO)) {
				clusteredlogger.logInfo(INFINISPAN_CACHEMANAGER_JNDI_NAME + " not specified, trying to load Inifinispan CacheManager from configuration file " + configurationPath);
			}
			try {
				if (cm == null) {
					cm = CacheManagerHolder.getManager(configurationPath);
					if(clusteredlogger.isLoggingEnabled(LogLevels.TRACE_INFO)) {
						clusteredlogger.logInfo("Found Inifinispan CacheManager: configuration file from path \"" + configurationPath + "\" " + cm);
					}
				}
				setupCacheStructures();
			} catch (IOException e) {
				clusteredlogger.logError("Failed to init Inifinispan CacheManager: could not read configuration file from path \"" + configurationPath + "\"", e);
			}
			
			if(dialogCacheData != null) {
				dialogCacheData.setDialogs(dialogs);
				dialogCacheData.setAppDataMap(appDataMap);
			}
			if(serverTXCacheData != null) {
				serverTXCacheData.setServerTransactions(serverTransactions);
				serverTXCacheData.setServerTransactionsApp(serverTransactionsApp);
			}
			if(clientTXCacheData != null) {
				clientTXCacheData.setClientTransactions(clientTransactions);
				clientTXCacheData.setClientTransactionsApp(clientTransactionsApp);
			}
		}
	}

	private void setupCacheStructures() {
		InfinispanCacheListener dialogsCacheListener = new InfinispanCacheListener(stack);
		
		dialogs = cm.getCache("cache.dialogs");
		appDataMap = cm.getCache("cache.appdata");
		serverTransactions = cm.getCache("cache.serverTX");
		serverTransactionsApp = cm.getCache("cache.serverTXApp");
		clientTransactions = cm.getCache("cache.clientTX");
		clientTransactionsApp = cm.getCache("cache.clientTXApp");
		
		dialogs.addListener(dialogsCacheListener);
	}
	
	public void start() throws SipCacheException {
		dialogCacheData = new SIPDialogCacheData(stack, 
				dialogs, appDataMap);
		serverTXCacheData = new SIPServerTransactionCacheData(stack, 
				serverTransactions, serverTransactionsApp);
		clientTXCacheData = new SIPClientTransactionCacheData(stack, 
				clientTransactions, clientTransactionsApp);
	}

	public void stop() throws SipCacheException {
		clientTXCacheData = null;
		serverTXCacheData = null;
		dialogCacheData = null;
	}
	
	public void setConfigurationProperties(Properties configurationProperties) {
		this.configProperties = configurationProperties;
	}

	public boolean inLocalMode() {
		return false;
	}
	
	public void setClusteredSipStack(ClusteredSipStack clusteredStack) {
		stack = clusteredStack;
	}
	
	public CacheContainer getCacheManager(){
		return cm;
	}
}
