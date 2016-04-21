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

package org.mobicents.ha.javax.sip.cache.infinispan;

import gov.nist.core.StackLogger;
import gov.nist.javax.sip.stack.MessageChannel;
import gov.nist.javax.sip.stack.MessageProcessor;
import gov.nist.javax.sip.stack.MobicentsHASIPServerTransaction;
import gov.nist.javax.sip.stack.SIPServerTransaction;
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
 * This class modifies the original SIPServerTransactionCacheData ( @see org.mobicents.ha.javax.sip.cache.hz.SIPServerTransactionCacheData ) 
 * to provide an implementation backed by Infinispan Cache
 * 
 * @author posfai.gergely@ext.alerant.hu
 * @author kokuti.andras@ext.alerant.hu
 *
 */

public class SIPServerTransactionCacheData {
	
	private ClusteredSipStack stack;
	private StackLogger logger;
	private Cache<String, Object> serverTransactions;
	private Cache<String, Object> serverTransactionsApp;
	
	public SIPServerTransactionCacheData(ClusteredSipStack s, 
			Cache<String, Object> serverTXCache,
			Cache<String, Object> serverTxAppCache) {
		stack = s;
		logger = s.getStackLogger();
		serverTransactions = serverTXCache;
		serverTransactionsApp = serverTxAppCache;
	}
	
	public SIPServerTransaction getServerTransaction(String txId) 
			throws SipCacheException {
		SIPServerTransaction haSipServerTransaction = null;
		if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			logger.logDebug("getServerTransaction(" + txId + ")");
		
		try {
			final Map<String, Object> transactionMetaData = (Map<String, Object>)serverTransactions.get(txId);		
			final Object txAppData = serverTransactionsApp.get(txId);
				
			haSipServerTransaction = createServerTransaction(txId, transactionMetaData, txAppData);
			
		} catch (Exception e) {
			throw new SipCacheException(e);
		}
		
		return haSipServerTransaction;
	}

	public void putServerTransaction(SIPServerTransaction serverTransaction) 
			throws SipCacheException {
		if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			logger.logDebug("putServerTransaction(" + serverTransaction.getTransactionId() + ")");
		
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

	public void removeServerTransaction(String txId) 
			throws SipCacheException {
		if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			logger.logDebug("removeServerTransaction(" + txId + ")");
		serverTransactions.remove(txId);
		serverTransactionsApp.remove(txId);
	}
	
	public MobicentsHASIPServerTransaction createServerTransaction(String txId, Map<String, Object> transactionMetaData, Object transactionAppData) throws SipCacheException {
		MobicentsHASIPServerTransaction haServerTransaction = null; 
		if(transactionMetaData != null) {
			if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug("sipStack " + this + " server transaction " + txId + " is present in the distributed cache, recreating it locally");
			}
			String channelTransport = (String) transactionMetaData.get(MobicentsHASIPServerTransaction.TRANSPORT);
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(txId + " : transport " + channelTransport);
			}
			InetAddress channelIp = (InetAddress) transactionMetaData.get(MobicentsHASIPServerTransaction.PEER_IP);
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(txId + " : channel peer Ip address " + channelIp);
			}
			Integer channelPort = (Integer) transactionMetaData.get(MobicentsHASIPServerTransaction.PEER_PORT);
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(txId + " : channel peer port " + channelPort);
			}
			Integer myPort = (Integer) transactionMetaData.get(MobicentsHASIPServerTransaction.MY_PORT);
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
			if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug("sipStack " + this + " server transaction " + txId + " not found in the distributed cache");
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
}
