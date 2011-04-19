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

package gov.nist.javax.sip.stack;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.ResponseExt;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;

import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import javax.sip.PeerUnavailableException;
import javax.sip.SipFactory;
import javax.sip.message.Request;

import org.mobicents.ha.javax.sip.ClusteredSipStack;
import org.mobicents.ha.javax.sip.cache.SipCacheException;

/**
 * @author jean.deruelle@gmail.com
 *
 */
public class MobicentsHASIPServerTransaction extends MobicentsSIPServerTransaction {

	private static StackLogger logger = CommonLogger.getLogger(MobicentsHASIPServerTransaction.class);
	public static final String MY_PORT = "mp";
	public static final String PEER_PORT = "cp";
	public static final String PEER_IP = "cip";
	public static final String TRANSPORT = "ct";
	public static final String CURRENT_STATE = "cs";
	public static final String DIALOG_ID = "did";
	public static final String ORIGINAL_REQUEST = "req";
	String localDialogId;
	int peerReliablePort = -1;
	
	public MobicentsHASIPServerTransaction(SIPTransactionStack sipStack,
			MessageChannel newChannelToUse) {
		super(sipStack, newChannelToUse);
	}

	public Map<String, Object> getMetaDataToReplicate() {
		Map<String,Object> transactionMetaData = new HashMap<String,Object>();
		
		transactionMetaData.put(ORIGINAL_REQUEST, getOriginalRequest().toString());
		if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug(transactionId + " : original request " + getOriginalRequest());
		}
		if(dialogId != null) {
			transactionMetaData.put(DIALOG_ID, dialogId);
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(transactionId + " : dialog Id " + dialogId);
			}
		} else if(localDialogId != null) {
			transactionMetaData.put(DIALOG_ID, localDialogId);
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(transactionId + " : dialog Id " + localDialogId);
			}
		}
		if(getState() != null) {
			transactionMetaData.put(CURRENT_STATE, Integer.valueOf(getState().getValue()));
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(transactionId + " : current state " + getState());
			}
		}
		transactionMetaData.put(TRANSPORT, getMessageChannel().getTransport());
		if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug(transactionId + " : message channel transport " + getTransport());
		}
		transactionMetaData.put(PEER_IP, getMessageChannel().getPeerInetAddress());
		if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug(transactionId + " : message channel ip " + getMessageChannel().getPeerInetAddress());
		}
		int peerPort = getMessageChannel().getPeerPort();
		if(isReliable()) {
			peerPort = peerReliablePort;
		}
		transactionMetaData.put(PEER_PORT, Integer.valueOf(peerPort));
		if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug(transactionId + " : message channel peer port " + peerPort);
		}
		transactionMetaData.put(MY_PORT, Integer.valueOf(getMessageChannel().getPort()));
		if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug(transactionId + " : message channel my port " + getMessageChannel().getPort());
		}
		
		return transactionMetaData;
	}		
	
	@Override
	public void sendMessage(SIPMessage message) throws IOException {
		final SIPResponse response = (SIPResponse) message;
		if(response != null && Request.INVITE.equals(getMethod()) && response.getStatusCode() > 100 && response.getStatusCode() < 200) {
			this.localDialogId = response.getDialogId(true);
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(transactionId + " : local dialog Id " + localDialogId);
			}			
			if(isReliable()) {
				this.peerReliablePort = ((ResponseExt)response).getTopmostViaHeader().getPort();
				if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
					logger.logDebug(transactionId + " : peer Reliable Port " + peerReliablePort);
				}
			}
			// store the tx when the response will be sent
			try {
				((ClusteredSipStack)sipStack).getSipCache().putServerTransaction(this);
			} catch (SipCacheException e) {
				logger.logError("problem storing server transaction " + transactionId + " into the distributed cache", e);
			}
		}
		super.sendMessage(message);
	}

	public void setMetaDataToReplicate(Map<String, Object> transactionMetaData,
			boolean recreation) throws PeerUnavailableException, ParseException {
		String originalRequestString = (String) transactionMetaData.get(ORIGINAL_REQUEST);
		if(originalRequestString != null) {
			final SIPRequest origRequest = (SIPRequest) SipFactory.getInstance().createMessageFactory().createRequest(originalRequestString);			
			setOriginalRequest(origRequest);
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(transactionId + " : original Request " + originalRequest);
			}
		}
		String dialogId = (String) transactionMetaData.get(DIALOG_ID);
		if(dialogId != null) {
			SIPDialog sipDialog = sipStack.getDialog(dialogId);
			if(sipDialog != null) {
				setDialog(sipDialog, dialogId);
				sipDialog.addTransaction(this);
			}
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(transactionId + " : dialog Id " + dialogId + " dialog " + sipDialog);
			}
		}
		Integer state = (Integer) transactionMetaData.get(CURRENT_STATE);
		if(state != null && super.getState() == null) {
			setState(state);
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(transactionId + " : state " + getState());
			}
		}		
	}

	@Override
	public void setApplicationData(Object applicationData) {
		super.setApplicationData(applicationData);		
	}
	
	public Object getApplicationDataToReplicate() {
		if(((ClusteredSipStack)getSIPStack()).isReplicateApplicationData()) {
			return getApplicationData();
		}
		return null;
	}
	
	public void setApplicationDataToReplicate(Object appData) {
		super.setApplicationData(appData);
	}
}
