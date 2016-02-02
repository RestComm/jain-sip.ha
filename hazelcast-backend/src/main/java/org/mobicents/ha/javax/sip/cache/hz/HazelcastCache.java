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

package org.mobicents.ha.javax.sip.cache.hz;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.stack.SIPClientTransaction;
import gov.nist.javax.sip.stack.SIPDialog;
import gov.nist.javax.sip.stack.SIPServerTransaction;

import java.io.FileNotFoundException;
import java.util.Properties;

import org.mobicents.ha.javax.sip.ClusteredSipStack;
import org.mobicents.ha.javax.sip.cache.SipCache;
import org.mobicents.ha.javax.sip.cache.SipCacheException;

import com.hazelcast.config.ClasspathXmlConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

/**
 * Implementation of the SipCache interface, backed by a Hazelcast Cache 3.0.X
 * The configuration of Hazelcast Cache can be set throught the following Restcomm SIP Stack property :
 * <b>org.mobicents.ha.javax.sip.HAZELCAST_CACHE_CONFIG_PATH</b>
 * 
 * @author icivico@gmail.com
 *
 */
public class HazelcastCache implements SipCache {
	
	public static final String HAZELCAST_CACHE_CONFIG_PATH = "org.mobicents.ha.javax.sip.HAZELCAST_CACHE_CONFIG_PATH";
	public static final String DEFAULT_FILE_CONFIG_PATH = "META-INF/cache-configuration.xml"; 
	public static final String HAZELCAST_INSTANCE_NAME = "org.mobicents.ha.javax.sip.HAZELCAST_INSTANCE_NAME";
	public static final String DEFAULT_HAZELCAST_INSTANCE_NAME = "jain-sip-ha";
	private static StackLogger clusteredlogger = CommonLogger.getLogger(HazelcastCache.class);
	
	private Properties configProperties = null;
	protected HazelcastInstance hz;
	private ClusteredSipStack stack;
	private IMap<String, Object> dialogs;
	private IMap<String, Object> appDataMap;
	private IMap<String, Object> serverTransactions;
	private IMap<String, Object> serverTransactionsApp;
	private IMap<String, Object> clientTransactions;
	private IMap<String, Object> clientTransactionsApp;
	
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
		
		if (dialogCacheData != null)
			dialogCacheData.removeDialog(dialogId);
		else
			throw new SipCacheException("No SIPClientTransactionCache");
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
		Config cfg = null;
		String instanceName = configProperties.getProperty(HAZELCAST_INSTANCE_NAME, 
				DEFAULT_HAZELCAST_INSTANCE_NAME);
		hz = Hazelcast.getHazelcastInstanceByName(instanceName);
		if (hz == null) {
			String pojoConfigurationPath = configProperties.getProperty(HAZELCAST_CACHE_CONFIG_PATH);
			if (pojoConfigurationPath != null) {
				if (clusteredlogger.isLoggingEnabled(StackLogger.TRACE_INFO)) {
					clusteredlogger.logInfo(
							"Mobicents JAIN SIP Hazelcast Cache Configuration path is : " + pojoConfigurationPath);
				}
				try {
					cfg = new XmlConfigBuilder(pojoConfigurationPath).build();
					
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
				
			} else {
				cfg = new ClasspathXmlConfig(DEFAULT_FILE_CONFIG_PATH);
				
			}
			cfg.setInstanceName(instanceName);
	        hz = Hazelcast.newHazelcastInstance(cfg);
		}
		dialogs = hz.getMap("cache.dialogs");
		appDataMap = hz.getMap("cache.appdata");
		serverTransactions = hz.getMap("cache.serverTX");
		serverTransactionsApp = hz.getMap("cache.serverTXApp");
		clientTransactions = hz.getMap("cache.clientTX");
		clientTransactionsApp = hz.getMap("cache.clientTXApp");
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
}
