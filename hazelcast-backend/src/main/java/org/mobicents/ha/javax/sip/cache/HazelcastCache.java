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

package org.mobicents.ha.javax.sip.cache;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.stack.AbstractHASipDialog;
import gov.nist.javax.sip.stack.MessageChannel;
import gov.nist.javax.sip.stack.MessageProcessor;
import gov.nist.javax.sip.stack.MobicentsHASIPClientTransaction;
import gov.nist.javax.sip.stack.MobicentsHASIPServerTransaction;
import gov.nist.javax.sip.stack.SIPClientTransaction;
import gov.nist.javax.sip.stack.SIPDialog;
import gov.nist.javax.sip.stack.SIPServerTransaction;
import gov.nist.javax.sip.stack.SIPTransactionStack;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.text.ParseException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.sip.PeerUnavailableException;
import javax.sip.SipFactory;
import javax.sip.address.Address;
import javax.sip.header.ContactHeader;

import org.mobicents.ha.javax.sip.ClusteredSipStack;
import org.mobicents.ha.javax.sip.HASipDialog;
import org.mobicents.ha.javax.sip.HASipDialogFactory;

import com.hazelcast.config.ClasspathXmlConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

/**
 * Implementation of the SipCache interface, backed by a Hazelcast Cache 3.0.X
 * The configuration of Hazelcast Cache can be set throught the following Mobicents SIP Stack property :
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
	
	public SIPDialog getDialog(String dialogId) throws SipCacheException {
		if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			clusteredlogger.logTrace("getDialog("+ dialogId +")");
		
		Object metaData = dialogs.get(dialogId);
		Object appData = appDataMap.get(dialogId);
		if (metaData != null) {
			SIPDialog d = (SIPDialog) createDialog(dialogId, (Map<String, Object>) metaData, appData);
			return d;
			
		} else {
			return null;
		}
	}
	
	public void putDialog(SIPDialog dialog) throws SipCacheException {
		if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			clusteredlogger.logDebug("putDialog(" + dialog.getDialogId() + ")");
		
		final HASipDialog haSipDialog = (HASipDialog) dialog;
		
		Object dialogMetaData = haSipDialog.getMetaDataToReplicate(); 
		/*if (clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			clusteredlogger.logDebug("HA SIP Dialog " + dialog.getDialogId() + " remoteParty  = " + ((Map<String, Object>)dialogMetaData).get(AbstractHASipDialog.REMOTE_TARGET));
			clusteredlogger.logDebug("HA SIP Dialog " + dialog.getDialogId() + " remoteParty  = " + ((ConfirmedReplicationSipDialog)dialog).getRemoteTarget());
		}*/
		if (dialogMetaData != null) {
			if (dialogs.containsKey(dialog.getDialogId())) {
				Map<String, Object> cachedMetaData = (Map<String, Object>)dialogs.get(dialog.getDialogId());
				Long currentVersion = (Long)((Map<String, Object>)dialogMetaData).get(AbstractHASipDialog.VERSION);
				Long cacheVersion = (Long)((Map<String, Object>)dialogMetaData).get(AbstractHASipDialog.VERSION);
				if ( cacheVersion.longValue() < currentVersion.longValue()) {
					for(Entry<String, Object> e : ((Map<String, Object>)dialogMetaData).entrySet()) {
						cachedMetaData.put(e.getKey(), e.getValue());
					}
					dialogs.replace(dialog.getDialogId(), cachedMetaData);
				}
				
			} else {
				dialogs.put(dialog.getDialogId(), dialogMetaData);
			}
		}
		
		Object dialogAppData = haSipDialog.getApplicationDataToReplicate();
		if (dialogAppData != null) {
			if (appDataMap.containsKey(dialog.getDialogId()))
				appDataMap.replace(dialog.getDialogId(), dialogAppData);
			else 
				appDataMap.put(dialog.getDialogId(), dialogAppData);
		}
	}
	
	public void updateDialog(SIPDialog dialog) throws SipCacheException {
		if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			clusteredlogger.logDebug("updateDialog(" + dialog.getDialogId() + ")");
		
		final HASipDialog haSipDialog = (HASipDialog) dialog;
		final Object dialogMetaData = dialogs.get(dialog.getDialogId());
		final Object dialogAppData = appDataMap.get(dialog.getDialogId()); 
	    
		updateDialog(haSipDialog, (Map<String, Object>)dialogMetaData, dialogAppData);
	}
	
	public void removeDialog(String dialogId) throws SipCacheException {
		if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			clusteredlogger.logDebug("removeDialog(" + dialogId + ")");
		
		dialogs.remove(dialogId);
	}
	
	public void evictDialog(String dialogId) {
		if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			clusteredlogger.logDebug("evictDialog(" + dialogId + ")");
		
		dialogs.remove(dialogId);
	}
	
	public SIPClientTransaction getClientTransaction(String txId) 
			throws SipCacheException {
		SIPClientTransaction haSipClientTransaction = null;
		if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			clusteredlogger.logDebug("getServerTransaction(" + txId + ")");
		
		try {
			final Map<String, Object> transactionMetaData = (Map<String, Object>)clientTransactions.get(txId);		
			final Object txAppData = clientTransactionsApp.get(txId);
				
			haSipClientTransaction = createClientTransaction(txId, transactionMetaData, txAppData);
			
		} catch (Exception e) {
			throw new SipCacheException(e);
		}
		
		return haSipClientTransaction;
	}

	public SIPServerTransaction getServerTransaction(String txId) 
			throws SipCacheException {
		SIPServerTransaction haSipServerTransaction = null;
		if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			clusteredlogger.logDebug("getServerTransaction(" + txId + ")");
		
		try {
			final Map<String, Object> transactionMetaData = (Map<String, Object>)serverTransactions.get(txId);		
			final Object txAppData = serverTransactionsApp.get(txId);
				
			haSipServerTransaction = createServerTransaction(txId, transactionMetaData, txAppData);
			
		} catch (Exception e) {
			throw new SipCacheException(e);
		}
		
		return haSipServerTransaction;
	}
	
	public void putClientTransaction(SIPClientTransaction clientTransaction) 
			throws SipCacheException {
		if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			clusteredlogger.logDebug("putClientTransaction(" + clientTransaction.getTransactionId() + ")");
		
		try {
			final MobicentsHASIPClientTransaction haClientTransaction = (MobicentsHASIPClientTransaction) clientTransaction;
			
			// metadata
			Map<String, Object> metaData = haClientTransaction.getMetaDataToReplicate();
			clientTransactions.put(clientTransaction.getTransactionId(), metaData);
			
			// app data
			final Object transactionAppData = haClientTransaction.getApplicationDataToReplicate();
			if(transactionAppData != null) {
				clientTransactionsApp.put(clientTransaction.getTransactionId(), transactionAppData);
			}
		} catch (Exception e) {
			throw new SipCacheException(e);
		}
	}

	public void putServerTransaction(SIPServerTransaction serverTransaction) 
			throws SipCacheException {
		if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			clusteredlogger.logDebug("putServerTransaction(" + serverTransaction.getTransactionId() + ")");
		
		try {
			final MobicentsHASIPServerTransaction haServerTransaction = (MobicentsHASIPServerTransaction) serverTransaction;
			
			// meta data
			Map<String, Object> metaData = haServerTransaction.getMetaDataToReplicate();
			serverTransactions.put(serverTransaction.getTransactionId(), metaData);
			
			// app data
			final Object transactionAppData = haServerTransaction.getApplicationDataToReplicate();
			if(transactionAppData != null) {
				serverTransactionsApp.put(serverTransaction.getTransactionId(), transactionAppData);
			}
		} catch (Exception e) {
			throw new SipCacheException(e);
		}
	}
	
	public void removeClientTransaction(String txId) 
			throws SipCacheException {
		if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			clusteredlogger.logDebug("removeClientTransaction(" + txId + ")");
		clientTransactions.remove(txId);
		clientTransactionsApp.remove(txId);
	}

	public void removeServerTransaction(String txId) 
			throws SipCacheException {
		if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			clusteredlogger.logDebug("removeServerTransaction(" + txId + ")");
		serverTransactions.remove(txId);
		serverTransactionsApp.remove(txId);
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
		
	}

	public void stop() throws SipCacheException {
		
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

	public HASipDialog createDialog(String dialogId, Map<String, Object> dialogMetaData, 
			Object dialogAppData) throws SipCacheException {
		HASipDialog haSipDialog = null; 
		if(dialogMetaData != null) {
			if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredlogger.logDebug("sipStack " + this + " dialog " + dialogId + " is present in the distributed cache, recreating it locally");
			}
			
			final String lastResponseStringified = (String) dialogMetaData.get(AbstractHASipDialog.LAST_RESPONSE);
			try {
				final SIPResponse lastResponse = (SIPResponse) SipFactory.getInstance().createMessageFactory().createResponse(lastResponseStringified);
				haSipDialog = HASipDialogFactory.createHASipDialog(stack.getReplicationStrategy(), (SipProviderImpl)stack.getSipProviders().next(), lastResponse);
				haSipDialog.setDialogId(dialogId);
				updateDialogMetaData(dialogMetaData, dialogAppData, haSipDialog, true);
				// setLastResponse won't be called on recreation since version will be null on recreation			
				haSipDialog.setLastResponse(lastResponse);				
				if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
					clusteredlogger.logDebug("HA SIP Dialog " + dialogId + " localTag  = " + haSipDialog.getLocalTag());
					clusteredlogger.logDebug("HA SIP Dialog " + dialogId + " remoteTag  = " + haSipDialog.getRemoteTag());
					clusteredlogger.logDebug("HA SIP Dialog " + dialogId + " localParty = " + haSipDialog.getLocalParty());
					clusteredlogger.logDebug("HA SIP Dialog " + dialogId + " remoteParty  = " + haSipDialog.getRemoteParty());
				}
				
			} catch (PeerUnavailableException e) {
				throw new SipCacheException("A problem occured while retrieving the following dialog " + dialogId + " from the Cache", e);
			} catch (ParseException e) {
				throw new SipCacheException("A problem occured while retrieving the following dialog " + dialogId + " from the Cache", e);
			}
		}
		
		return haSipDialog;
	}
	
	public void updateDialog(HASipDialog haSipDialog, Map<String, Object> dialogMetaData, 
			Object dialogAppData) throws SipCacheException {
		if(dialogMetaData != null) {			
			final long currentVersion = haSipDialog.getVersion();
			final Long cacheVersion = ((Long)dialogMetaData.get(AbstractHASipDialog.VERSION)); 
			if(cacheVersion != null && currentVersion < cacheVersion.longValue()) {
				if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
					clusteredlogger.logDebug("HA SIP Dialog " + haSipDialog + " with dialogId " + haSipDialog.getDialogIdToReplicate() + " is older " + currentVersion + " than the one in the cache " + cacheVersion + " updating it");
				}
				try {
					final String lastResponseStringified = (String) dialogMetaData.get(AbstractHASipDialog.LAST_RESPONSE);				
					final SIPResponse lastResponse = (SIPResponse) SipFactory.getInstance().createMessageFactory().createResponse(lastResponseStringified);
					haSipDialog.setLastResponse(lastResponse);
					updateDialogMetaData(dialogMetaData, dialogAppData, haSipDialog, false);
				
				}  catch (PeerUnavailableException e) {
					throw new SipCacheException("A problem occured while retrieving the following dialog " + haSipDialog.getDialogIdToReplicate() + " from the TreeCache", e);
				} catch (ParseException e) {
					throw new SipCacheException("A problem occured while retrieving the following dialog " + haSipDialog.getDialogIdToReplicate() + " from the TreeCache", e);
				}
			} else {
				if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
					clusteredlogger.logDebug("HA SIP Dialog " + haSipDialog + " with dialogId " + haSipDialog.getDialogIdToReplicate() + " is not older " + currentVersion + " than the one in the cache " + cacheVersion + ", not updating it");
				}
			}
		}
	}
	
	
	/**
	 * Update the haSipDialog passed in param with the dialogMetaData and app meta data
	 * @param dialogMetaData
	 * @param dialogAppData
	 * @param haSipDialog
	 * @throws ParseException
	 * @throws PeerUnavailableException
	 */
	private void updateDialogMetaData(Map<String, Object> dialogMetaData, Object dialogAppData, HASipDialog haSipDialog, boolean recreation) 
			throws ParseException, PeerUnavailableException {
		haSipDialog.setMetaDataToReplicate(dialogMetaData, recreation);
		haSipDialog.setApplicationDataToReplicate(dialogAppData);
		final String contactStringified = (String) dialogMetaData.get(AbstractHASipDialog.CONTACT_HEADER);
		if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			clusteredlogger.logDebug("contactStringified " + contactStringified);
		}
		
		if(contactStringified != null) {
			Address contactAddress = SipFactory.getInstance().createAddressFactory().createAddress(contactStringified);
			ContactHeader contactHeader = SipFactory.getInstance().createHeaderFactory().createContactHeader(contactAddress);
			if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredlogger.logDebug("contactHeader " + contactHeader);
				clusteredlogger.logDebug("contactURI " + contactHeader.getAddress().getURI());
			}
			haSipDialog.setContactHeader(contactHeader);
		}
	}
	
	public MobicentsHASIPServerTransaction createServerTransaction(String txId, Map<String, Object> transactionMetaData, Object transactionAppData) throws SipCacheException {
		MobicentsHASIPServerTransaction haServerTransaction = null; 
		if(transactionMetaData != null) {
			if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredlogger.logDebug("sipStack " + this + " server transaction " + txId + " is present in the distributed cache, recreating it locally");
			}
			String channelTransport = (String) transactionMetaData.get(MobicentsHASIPServerTransaction.TRANSPORT);
			if (clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredlogger.logDebug(txId + " : transport " + channelTransport);
			}
			InetAddress channelIp = (InetAddress) transactionMetaData.get(MobicentsHASIPServerTransaction.PEER_IP);
			if (clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredlogger.logDebug(txId + " : channel peer Ip address " + channelIp);
			}
			Integer channelPort = (Integer) transactionMetaData.get(MobicentsHASIPServerTransaction.PEER_PORT);
			if (clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredlogger.logDebug(txId + " : channel peer port " + channelPort);
			}
			Integer myPort = (Integer) transactionMetaData.get(MobicentsHASIPServerTransaction.MY_PORT);
			if (clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredlogger.logDebug(txId + " : my port " + myPort);
			}
			MessageChannel messageChannel = null;
			MessageProcessor[] messageProcessors = stack.getStackMessageProcessors();
			for (MessageProcessor messageProcessor : messageProcessors) {
				if(messageProcessor.getTransport().equalsIgnoreCase(channelTransport)) {
					try {
						messageChannel = messageProcessor.createMessageChannel(channelIp, channelPort);
					} catch (IOException e) {
						clusteredlogger.logError("couldn't recreate the message channel on ip address " 
								+ channelIp + " and port " + channelPort, e);
					}
					break;
				}
			}
			
			haServerTransaction = new MobicentsHASIPServerTransaction((SIPTransactionStack) stack, messageChannel);
			haServerTransaction.setBranch(txId);
			try {
				updateServerTransactionMetaData(transactionMetaData, transactionAppData, haServerTransaction, true);						
			} catch (PeerUnavailableException e) {
				throw new SipCacheException("A problem occured while retrieving the following transaction " + txId + " from the Cache", e);
			} catch (ParseException e) {
				throw new SipCacheException("A problem occured while retrieving the following transaction " + txId + " from the Cache", e);
			}
		} else {
			if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredlogger.logDebug("sipStack " + this + " server transaction " + txId + " not found in the distributed cache");
			}
		}
		
		return haServerTransaction;
	}

	/**
	 * Update the haSipDialog passed in param with the dialogMetaData and app meta data
	 * @param transactionMetaData
	 * @param transactionAppData
	 * @param haServerTransaction
	 * @throws ParseException
	 * @throws PeerUnavailableException
	 */
	private void updateServerTransactionMetaData(Map<String, Object> transactionMetaData, Object transactionAppData, MobicentsHASIPServerTransaction haServerTransaction, boolean recreation) throws ParseException,
			PeerUnavailableException {
		haServerTransaction.setMetaDataToReplicate(transactionMetaData, recreation);
		haServerTransaction.setApplicationDataToReplicate(transactionAppData);		
	}
	
	public MobicentsHASIPClientTransaction createClientTransaction(String txId, Map<String, Object> transactionMetaData, Object transactionAppData) throws SipCacheException {
		MobicentsHASIPClientTransaction haClientTransaction = null; 
		if(transactionMetaData != null) {
			if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredlogger.logDebug("sipStack " + this + " client transaction " + txId + " is present in the distributed cache, recreating it locally");
			}
			String channelTransport = (String) transactionMetaData.get(MobicentsHASIPClientTransaction.TRANSPORT);
			if (clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredlogger.logDebug(txId + " : transport " + channelTransport);
			}
			InetAddress channelIp = (InetAddress) transactionMetaData.get(MobicentsHASIPClientTransaction.PEER_IP);
			if (clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredlogger.logDebug(txId + " : channel peer Ip address " + channelIp);
			}
			Integer channelPort = (Integer) transactionMetaData.get(MobicentsHASIPClientTransaction.PEER_PORT);
			if (clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredlogger.logDebug(txId + " : channel peer port " + channelPort);
			}
			Integer myPort = (Integer) transactionMetaData.get(MobicentsHASIPClientTransaction.MY_PORT);
			if (clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredlogger.logDebug(txId + " : my port " + myPort);
			}
			MessageChannel messageChannel = null;
			MessageProcessor[] messageProcessors = stack.getStackMessageProcessors();
			for (MessageProcessor messageProcessor : messageProcessors) {
				if(messageProcessor.getTransport().equalsIgnoreCase(channelTransport)) {
					try {
						messageChannel = messageProcessor.createMessageChannel(channelIp, channelPort);
					} catch (IOException e) {
						clusteredlogger.logError("couldn't recreate the message channel on ip address " 
								+ channelIp + " and port " + channelPort, e);
					}
					break;
				}
			}
			
			haClientTransaction = new MobicentsHASIPClientTransaction((SIPTransactionStack) stack, messageChannel);
			haClientTransaction.setBranch(txId);
			try {
				updateClientTransactionMetaData(transactionMetaData, transactionAppData, haClientTransaction, true);						
			} catch (PeerUnavailableException e) {
				throw new SipCacheException("A problem occured while retrieving the following transaction " + txId + " from the Cache", e);
			} catch (ParseException e) {
				throw new SipCacheException("A problem occured while retrieving the following transaction " + txId + " from the Cache", e);
			}
		} else {
			if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredlogger.logDebug("sipStack " + this + " client transaction " + txId + " not found in the distributed cache");
			}
		}
		
		return haClientTransaction;
	}

	/**
	 * Update the haSipDialog passed in param with the dialogMetaData and app meta data
	 * @param transactionMetaData
	 * @param transactionAppData
	 * @param haClientTransaction
	 * @throws ParseException
	 * @throws PeerUnavailableException
	 */
	private void updateClientTransactionMetaData(Map<String, Object> transactionMetaData, Object transactionAppData, MobicentsHASIPClientTransaction haClientTransaction, boolean recreation) throws ParseException,
			PeerUnavailableException {
		haClientTransaction.setMetaDataToReplicate(transactionMetaData, recreation);
		if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			clusteredlogger.logDebug("updating application data with the one from cache " + transactionAppData);
		}
		haClientTransaction.setApplicationDataToReplicate(transactionAppData);		
	}
}
