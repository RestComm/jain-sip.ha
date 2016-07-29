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

import gov.nist.core.StackLogger;
import gov.nist.javax.sip.stack.MessageChannel;
import gov.nist.javax.sip.stack.MessageProcessor;
import gov.nist.javax.sip.stack.MobicentsHASIPClientTransaction;
import gov.nist.javax.sip.stack.SIPClientTransaction;
import gov.nist.javax.sip.stack.SIPTransactionStack;

import java.io.IOException;
import java.net.InetAddress;
import java.text.ParseException;
import java.util.Map;

import javax.sip.PeerUnavailableException;

import org.mobicents.ha.javax.sip.ClusteredSipStack;
import org.mobicents.ha.javax.sip.cache.SipCacheException;

import org.infinispan.Cache;

/**
 * This class modifies the original SIPClientTransactionCacheData ( @see org.mobicents.ha.javax.sip.cache.hz.SIPClientTransactionCacheData ) 
 * to provide an implementation backed by Infinispan Cache
 * 
 * @author <A HREF="mailto:posfai.gergely@ext.alerant.hu">Gergely Posfai</A>
 * @author <A HREF="mailto:kokuti.andras@ext.alerant.hu">Andras Kokuti</A>
 *
 */

public class SIPClientTransactionCacheData {
	
	private ClusteredSipStack stack;
	private StackLogger logger;
	private Cache<String, Object> clientTransactions;
	private Cache<String, Object> clientTransactionsApp;
	
	public SIPClientTransactionCacheData(
			ClusteredSipStack s, Cache<String, Object> clientTXCache,
			Cache<String, Object> clientTxAppCache) {
		stack = s;
		logger = s.getStackLogger();
		setClientTransactions(clientTXCache);
		setClientTransactionsApp(clientTxAppCache);
	}
	
	public SIPClientTransaction getClientTransaction(String txId) 
			throws SipCacheException {
		SIPClientTransaction haSipClientTransaction = null;
		if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			logger.logDebug("getServerTransaction(" + txId + ")");
		
		try {
			final Map<String, Object> transactionMetaData = (Map<String, Object>)getClientTransactions().get(txId);		
			final Object txAppData = getClientTransactionsApp().get(txId);
				
			haSipClientTransaction = createClientTransaction(txId, transactionMetaData, txAppData);
			
		} catch (Exception e) {
			throw new SipCacheException(e);
		}
		
		return haSipClientTransaction;
	}

	public void putClientTransaction(SIPClientTransaction clientTransaction) 
			throws SipCacheException {
		if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			logger.logDebug("putClientTransaction(" + clientTransaction.getTransactionId() + ")");
		
		try {
			final MobicentsHASIPClientTransaction haClientTransaction = (MobicentsHASIPClientTransaction) clientTransaction;
			
			// metadata
			Map<String, Object> metaData = haClientTransaction.getMetaDataToReplicate();
			getClientTransactions().put(clientTransaction.getTransactionId(), metaData);
			
			// app data
			final Object transactionAppData = haClientTransaction.getApplicationDataToReplicate();
			if(transactionAppData != null) {
				getClientTransactionsApp().put(clientTransaction.getTransactionId(), transactionAppData);
			}
		} catch (Exception e) {
			throw new SipCacheException(e);
		}
	}
	
	public void removeClientTransaction(String txId) 
			throws SipCacheException {
		if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			logger.logDebug("removeClientTransaction(" + txId + ")");
		getClientTransactions().remove(txId);
		getClientTransactionsApp().remove(txId);
	}
	
	public MobicentsHASIPClientTransaction createClientTransaction(String txId, Map<String, Object> transactionMetaData, Object transactionAppData) throws SipCacheException {
		MobicentsHASIPClientTransaction haClientTransaction = null; 
		if(transactionMetaData != null) {
			if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug("sipStack " + this + " client transaction " + txId + " is present in the distributed cache, recreating it locally");
			}
			String channelTransport = (String) transactionMetaData.get(MobicentsHASIPClientTransaction.TRANSPORT);
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(txId + " : transport " + channelTransport);
			}
			InetAddress channelIp = (InetAddress) transactionMetaData.get(MobicentsHASIPClientTransaction.PEER_IP);
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(txId + " : channel peer Ip address " + channelIp);
			}
			Integer channelPort = (Integer) transactionMetaData.get(MobicentsHASIPClientTransaction.PEER_PORT);
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(txId + " : channel peer port " + channelPort);
			}
			Integer myPort = (Integer) transactionMetaData.get(MobicentsHASIPClientTransaction.MY_PORT);
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(txId + " : my port " + myPort);
			}
			MessageChannel messageChannel = null;
			MessageProcessor[] messageProcessors = stack.getStackMessageProcessors();
			for (MessageProcessor messageProcessor : messageProcessors) {
				if(messageProcessor.getTransport().equalsIgnoreCase(channelTransport)) {
					try {
						messageChannel = messageProcessor.createMessageChannel(channelIp, channelPort);
					} catch (IOException e) {
						logger.logError("couldn't recreate the message channel on ip address " 
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
			if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug("sipStack " + this + " client transaction " + txId + " not found in the distributed cache");
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
		if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug("updating application data with the one from cache " + transactionAppData);
		}
		haClientTransaction.setApplicationDataToReplicate(transactionAppData);		
	}

	/**
	 * @return the clientTransactions
	 */
	public Cache<String, Object> getClientTransactions() {
		return clientTransactions;
	}

	/**
	 * @param clientTransactions the clientTransactions to set
	 */
	public void setClientTransactions(Cache<String, Object> clientTransactions) {
		this.clientTransactions = clientTransactions;
	}

	/**
	 * @return the clientTransactionsApp
	 */
	public Cache<String, Object> getClientTransactionsApp() {
		return clientTransactionsApp;
	}

	/**
	 * @param clientTransactionsApp the clientTransactionsApp to set
	 */
	public void setClientTransactionsApp(Cache<String, Object> clientTransactionsApp) {
		this.clientTransactionsApp = clientTransactionsApp;
	}

}
