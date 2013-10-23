package org.mobicents.ha.javax.sip.cache;

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

import com.hazelcast.core.IMap;

public class SIPClientTransactionCacheData {
	
	private ClusteredSipStack stack;
	private StackLogger logger;
	private IMap<String, Object> clientTransactions;
	private IMap<String, Object> clientTransactionsApp;
	
	public SIPClientTransactionCacheData(
			ClusteredSipStack s, IMap<String, Object> clientTXCache,
			IMap<String, Object> clientTxAppCache) {
		stack = s;
		logger = s.getStackLogger();
		clientTransactions = clientTXCache;
		clientTransactionsApp = clientTxAppCache;
	}
	
	public SIPClientTransaction getClientTransaction(String txId) 
			throws SipCacheException {
		SIPClientTransaction haSipClientTransaction = null;
		if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			logger.logDebug("getServerTransaction(" + txId + ")");
		
		try {
			final Map<String, Object> transactionMetaData = (Map<String, Object>)clientTransactions.get(txId);		
			final Object txAppData = clientTransactionsApp.get(txId);
				
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
	
	public void removeClientTransaction(String txId) 
			throws SipCacheException {
		if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			logger.logDebug("removeClientTransaction(" + txId + ")");
		clientTransactions.remove(txId);
		clientTransactionsApp.remove(txId);
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

}
