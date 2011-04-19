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
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.header.Contact;
import gov.nist.javax.sip.header.Route;
import gov.nist.javax.sip.header.RouteList;
import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.parser.EventParser;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.sip.DialogState;
import javax.sip.PeerUnavailableException;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.header.ContactHeader;
import javax.sip.header.EventHeader;
import javax.sip.header.HeaderFactory;

import org.mobicents.ha.javax.sip.ClusteredSipStack;
import org.mobicents.ha.javax.sip.HASipDialog;
import org.mobicents.ha.javax.sip.ReplicationStrategy;

/**
 * @author jean.deruelle@gmail.com
 *
 */
public abstract class AbstractHASipDialog extends SIPDialog implements HASipDialog {
	private static StackLogger logger = CommonLogger.getLogger(AbstractHASipDialog.class);
	private static final long serialVersionUID = 1L;	
	public static final String B2BUA = "b2b";
	public static final String EVENT_HEADER = "eh";	
	public static final String REMOTE_TARGET = "rt";
	public static final String TERMINATE_ON_BYE = "tob";
	public static final String ROUTE_LIST = "rl";
	public static final String IS_REINVITE = "ir";
	public static final String LAST_RESPONSE = "lr";
	public static final String IS_SERVER = "is";
	public static final String FIRST_TX_METHOD = "ftm";
	public static final String FIRST_TX_ID = "ftid";
	public static final String FIRST_TX_PORT = "ftp";
	public static final String FIRST_TX_SECURE = "fts";
	public static final String CONTACT_HEADER = "ch";
	public static final String LOCAL_TAG = "ltag";
	public static final String REMOTE_TAG = "rtag";
	public static final String VERSION = "v";
	public static final String REMOTE_CSEQ = "rc";
	public static final String LOCAL_CSEQ = "lc";
	public static final String DIALOG_METHOD = "dm";
	public static final String ENABLE_CSEQ_VALIDATION = "dc";
	public static final String DIALOG_STATE = "ds";

	public boolean b2buaChanged;
	public boolean eventChanged;	
	public boolean remoteTargetChanged;
	public boolean terminateOnByeChanged;	
	public boolean isReinviteChanged;	
	public boolean storeFirstTxChanged;
	public boolean dialogStateChanged;	
	
	static AddressFactory addressFactory = null;
	static HeaderFactory headerFactory = null;		
	boolean isCreated = false;
	private AtomicLong version = new AtomicLong(0);
	private String lastResponseStringified = null;
	
	static {		
		try {
			headerFactory = SipFactory.getInstance().createHeaderFactory();
			addressFactory = SipFactory.getInstance().createAddressFactory();
		} catch (PeerUnavailableException e) {}
	}
	
	public AbstractHASipDialog(SIPTransaction transaction) {
		super(transaction);		
		isCreated = true;
	}
	
	public AbstractHASipDialog(SIPClientTransaction transaction, SIPResponse sipResponse) {
		super(transaction, sipResponse);
		isCreated = true;
	}
	
    public AbstractHASipDialog(SipProviderImpl sipProvider, SIPResponse sipResponse) {
		super(sipProvider, sipResponse);
		isCreated = true;
	}	

	/**
	 * Updates the local dialog transient attributes that were not serialized during the replication 
	 * @param sipStackImpl the sip Stack Impl that reloaded this dialog from the distributed cache
	 */
	public void initAfterLoad(ClusteredSipStack sipStackImpl) {
		String transport = getLastResponseTopMostVia().getTransport();
		Iterator<SipProviderImpl> providers = sipStackImpl.getSipProviders();
		boolean providerNotFound = true;
		while(providers.hasNext()) {
			SipProviderImpl providerImpl = providers.next();
			if(providerImpl.getListeningPoint(transport) != null) {
				setSipProvider(providerImpl);
				providerNotFound = false;
			}
		}
		if(providerNotFound) {
			throw new RuntimeException("No providers found for transport=" 
					+ transport + " on this node. Make sure connectors are configured for this transport");
		}
		setStack((SIPTransactionStack)sipStackImpl);
		setAssigned();
		firstTransactionPort = getSipProvider().getListeningPoint(getLastResponseTopMostVia().getTransport()).getPort();
		ackProcessed = true;
//		ackSeen = true;
	}			

	@SuppressWarnings("unchecked")
	public Map<String,Object> getMetaDataToReplicate() {
		Map<String,Object> dialogMetaData = new HashMap<String,Object>();
		dialogMetaData.put(VERSION, Long.valueOf(version.incrementAndGet()));
		if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug(getDialogIdToReplicate() + " : version " + version);
		}
		if(dialogStateChanged) {
			dialogMetaData.put(DIALOG_STATE, Integer.valueOf(getState().getValue()));
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(getDialogIdToReplicate() + " : dialogState " + getState());
			}
			dialogStateChanged = false;
		}
		boolean firstTimeReplication = version.get() == 1;		
		if(firstTimeReplication) {
			dialogMetaData.put(DIALOG_METHOD, getMethod());
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(getDialogIdToReplicate() + " : dialog method " + getMethod());
			}
		}
		dialogMetaData.put(LAST_RESPONSE, getLastResponseStringified());
		if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug(getDialogIdToReplicate() + " : lastResponse " + getLastResponseStringified());
		}
		if(isReinviteChanged) {
			dialogMetaData.put(IS_REINVITE, isReInvite());
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(getDialogIdToReplicate() + " : isReInvite " + isReInvite());
			}
			isReinviteChanged = false;
		}
		final List<SIPHeader> routeList = new ArrayList<SIPHeader>();
		final Iterator<SIPHeader> it = getRouteSet();
		while (it.hasNext()) {
			SIPHeader sipHeader = (SIPHeader) it.next();
			routeList.add(sipHeader);
		}
		final String[] routes = new String[routeList.size()];
		int i = 0;
		for (SIPHeader sipHeader : routeList) {
			routes[i++] = sipHeader.getHeaderValue().toString();
		}
		dialogMetaData.put(ROUTE_LIST, routes);
		if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug(getDialogIdToReplicate() + " : routes " + routes);
		}
		if(terminateOnByeChanged) {
			dialogMetaData.put(TERMINATE_ON_BYE, Boolean.valueOf(isTerminatedOnBye()));
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(getDialogIdToReplicate() + " : terminateOnBye " + isTerminatedOnBye());
			}
			terminateOnByeChanged = false;
		}
		if(remoteTargetChanged) {
			if(getRemoteTarget() != null) {
				dialogMetaData.put(REMOTE_TARGET, getRemoteTarget().toString());
			} else {
				dialogMetaData.put(REMOTE_TARGET, null);
			}
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(getDialogIdToReplicate() + " : remoteTarget " + getRemoteTarget());
			}		
			remoteTargetChanged = false;
		}
		if(eventChanged) {
			if(getEventHeader() != null) {
				dialogMetaData.put(EVENT_HEADER, getEventHeader().toString());
			} else {
				dialogMetaData.put(EVENT_HEADER, null);
			}
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(getDialogIdToReplicate() + " : evenHeader " + getEventHeader());
			}
			eventChanged = false;
		}
		if(b2buaChanged) {
			dialogMetaData.put(B2BUA, Boolean.valueOf(isBackToBackUserAgent()));
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(getDialogIdToReplicate() + " : isB2BUA " + isBackToBackUserAgent());
			}
			b2buaChanged = false;
		}
		if(storeFirstTxChanged) {
			dialogMetaData.put(IS_SERVER, Boolean.valueOf(isServer()));
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(getDialogIdToReplicate() + " : isServer " + isServer());
			}
			dialogMetaData.put(FIRST_TX_SECURE, Boolean.valueOf(firstTransactionSecure));
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(getDialogIdToReplicate() + " : firstTxSecure " + firstTransactionSecure);
			}					
			dialogMetaData.put(FIRST_TX_ID, firstTransactionId);
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(getDialogIdToReplicate() + " : firstTransactionId " + firstTransactionId);
			}
		dialogMetaData.put(ENABLE_CSEQ_VALIDATION, isSequnceNumberValidation());
		if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug(getDialogIdToReplicate() + " : CSeq validation is " + isSequnceNumberValidation());
		}
			dialogMetaData.put(FIRST_TX_METHOD, firstTransactionMethod);
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(getDialogIdToReplicate() + " : firstTransactionMethod " + firstTransactionMethod);
			}
			if(contactHeader != null) {
				dialogMetaData.put(CONTACT_HEADER, contactHeader.toString());
			}
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(getDialogIdToReplicate() + " : contactHeader " + contactHeader);
			}
			storeFirstTxChanged = false;
		}
		dialogMetaData.put(REMOTE_TAG, getRemoteTag());
		if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug(getDialogIdToReplicate() + " : remoteTag " + getRemoteTag());
		}
		dialogMetaData.put(LOCAL_TAG, getLocalTag());
		if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug(getDialogIdToReplicate() + " : localTag " + getLocalTag());
		}
		dialogMetaData.put(REMOTE_CSEQ, Long.valueOf(getRemoteSeqNumber()));
		if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug(getDialogIdToReplicate() + " : remoteCSeq " + getRemoteSeqNumber());
		}
		dialogMetaData.put(LOCAL_CSEQ, Long.valueOf(getLocalSeqNumber()));
		if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug(getDialogIdToReplicate() + " : localCSeq " + getLocalSeqNumber());
		}
		
		return dialogMetaData;
	}

	public Object getApplicationDataToReplicate() {
		return getApplicationData();
	}
	
	public void setMetaDataToReplicate(Map<String, Object> metaData, boolean recreation) {
		final ReplicationStrategy replicationStrategy = ((ClusteredSipStack)getStack()).getReplicationStrategy();
		if(replicationStrategy == ReplicationStrategy.EarlyDialog) {
			Integer dialogState = (Integer) metaData.get(DIALOG_STATE);
			if(dialogState!= null) {
				// the call to super is very important otherwise it triggers replication on dialog recreation
				super.setState(dialogState);				
			} 
		} else {
			// the call to super is very important otherwise it triggers replication on dialog recreation
			super.setState(DialogState._CONFIRMED);
		}
		lastResponseStringified = (String) metaData.get(LAST_RESPONSE);
		if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug(getDialogIdToReplicate() + " : lastResponse " + lastResponseStringified);
		}
		String dialogMethod = (String) metaData.get(DIALOG_METHOD);
		if(dialogMethod!= null) {
			method = dialogMethod;		
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(getDialogIdToReplicate() + " : dialog method " + method);
			}
		}
		version = new AtomicLong((Long)metaData.get(VERSION));
		if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug(getDialogIdToReplicate() + " : version " + version);
		}
		final Boolean isB2BUA = (Boolean) metaData.get(B2BUA);
		if(isB2BUA != null && isB2BUA == Boolean.TRUE) {
			setBackToBackUserAgent();
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(getDialogIdToReplicate() + " : isB2BUA " + isB2BUA);
			}
		}		
		final Boolean isReinvite = (Boolean) metaData.get(IS_REINVITE);
		if(isReinvite != null) {
			super.setReInviteFlag(isReinvite);
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(getDialogIdToReplicate() + " : isReInvite " + isReinvite);
			}
		}		
		final String eventHeaderStringified = (String) metaData.get(EVENT_HEADER);
		if(eventHeaderStringified != null) {
			try {
				super.setEventHeader((EventHeader)new EventParser(eventHeaderStringified).parse());
			} catch (ParseException e) {
				logger.logError("Unexpected exception while parsing a deserialized eventHeader", e);
			}
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(getDialogIdToReplicate() + " : evenHeader " + eventHeaderStringified);
			}	
		}			
		final String remoteTargetCache = (String) metaData.get(REMOTE_TARGET);
		if(remoteTargetCache != null) {
			Contact contact = new Contact();
			try {
				super.remotePartyStringified = remoteTargetCache;
				contact.setAddress(addressFactory.createAddress(remoteTargetCache));
				super.setRemoteTarget(contact);
			} catch (ParseException e) {
				logger.logError("Unexpected exception while parsing a deserialized remoteTarget address", e);
			}
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(getDialogIdToReplicate() + " : remoteTarget " + remoteTargetStringified);
			}
		}		
		final Boolean terminateOnBye = (Boolean) metaData.get(TERMINATE_ON_BYE);
		if(terminateOnBye != null) {
			try {
				terminateOnBye(terminateOnBye);
			} catch (SipException e) {
				// exception is never thrown
			}
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(getDialogIdToReplicate() + " : terminateOnBye " + terminateOnBye);
			}
		}		
		final String[] routes = (String[]) metaData.get(ROUTE_LIST);
		if(routes != null) {			
			final RouteList routeList = new RouteList();			
			for (String route : routes) {
				try {
					routeList.add((Route)headerFactory.createRouteHeader(addressFactory.createAddress(route)));
				} catch (ParseException e) {
					logger.logError("Unexpected exception while parsing a deserialized route address", e);
				}				
			}
			setRouteList(routeList);
		}
		if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug(getDialogIdToReplicate() + " : routes " + routes);
		}
		final Boolean isServer = (Boolean) metaData.get(IS_SERVER);
		if(isServer != null) {
			firstTransactionSeen = true;
			firstTransactionIsServerTransaction = isServer.booleanValue();
			setServerTransactionFlag(isServer.booleanValue());
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(getDialogIdToReplicate() + " : isServer " + isServer.booleanValue());
			}
		}				
		final Boolean firstTxSecure = (Boolean) metaData.get(FIRST_TX_SECURE);
		if(firstTxSecure != null) {
			firstTransactionSecure = firstTxSecure;
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(getDialogIdToReplicate() + " : firstTxSecure " + firstTxSecure);
			}
		}		
		final String firstTxId = (String) metaData.get(FIRST_TX_ID);
		if(firstTxId != null) {
			firstTransactionId = firstTxId;
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(getDialogIdToReplicate() + " : firstTransactionId " + firstTransactionId);
			}
		}
		final String firstTxMethod = (String) metaData.get(FIRST_TX_METHOD);
		if(firstTxMethod != null) { 
			firstTransactionMethod = firstTxMethod;
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(getDialogIdToReplicate() + " : firstTransactionMethod " + firstTransactionMethod);
			}
		}
		if(recreation && isServer()) {
			if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug("HA SIP Dialog is Server ? " + isServer() + ", thus switching parties on recreation");
			}
			Address remoteParty = getLocalParty();
			Address localParty = getRemoteParty();
			setLocalPartyInternal(localParty);
			setRemotePartyInternal(remoteParty);
			long remoteCSeq = getLocalSeqNumber();
			long localCSeq = getRemoteSeqNumber();
			localSequenceNumber = localCSeq;
			remoteSequenceNumber = remoteCSeq;
		}					
		String remoteTag = (String) metaData.get(REMOTE_TAG);
		setRemoteTagInternal(remoteTag);
		if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug(getDialogIdToReplicate() + " : remoteTag " + getRemoteTag());
		}
		String localTag = (String) metaData.get(LOCAL_TAG);
		setLocalTagInternal(localTag);
		if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug(getDialogIdToReplicate() + " : localTag " + getLocalTag());
		}
		Long remoteCSeq = (Long) metaData.get(REMOTE_CSEQ);
		if(remoteCSeq != null) {
			long cseq = remoteCSeq.longValue();
			if(getRemoteSeqNumber()>cseq) {
				if (logger.isLoggingEnabled(StackLogger.TRACE_INFO)) {
					logger.logInfo("Concurrency problem. Nodes are out" +
							" of sync. We will assume the local CSeq is the valid one. Enable request affinity to avoid this problem, remoteSequenceNumber=" + 
							getRemoteSeqNumber() + " while other node's remote CSeq" +
									" number=" + cseq);
				}
				// No need to update the number, it is greater, http://code.google.com/p/mobicents/issues/detail?id=2051
			} else {
				setRemoteSequenceNumber(cseq);
			}
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(getDialogIdToReplicate() + " : remoteCSeq " + getRemoteSeqNumber());
			}
		}
		Long localCSeq = (Long) metaData.get(LOCAL_CSEQ);
		if(localCSeq != null) {
			long cseq = localCSeq.longValue();
			if(localSequenceNumber>cseq) {
				if (logger.isLoggingEnabled(StackLogger.TRACE_INFO)) {
					logger.logInfo("Concurrency problem. Nodes are out" +
							" of sync. We will assume the local CSeq is the valid one. Enable request affinity to avoid this problem, localSequenceNumber=" 
							+ localSequenceNumber+ " while other node's local CSeq" +
									" number=" + cseq);
				}
				// No need to update the number, it is greater, http://code.google.com/p/mobicents/issues/detail?id=2051
			} else {
				localSequenceNumber = cseq;
			}
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(getDialogIdToReplicate() + " : localCSeq " + getLocalSeqNumber());
			}
		}		
		final Boolean enableCSeqValidation = (Boolean) metaData.get(ENABLE_CSEQ_VALIDATION);
		if(enableCSeqValidation != null) {
			if(!enableCSeqValidation) disableSequenceNumberValidation();
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(getDialogIdToReplicate() + " : CSeq validation is " + enableCSeqValidation);
			}
		}
	}
	
	public void setApplicationDataToReplicate(Object appData) {
		// the call to super is very important otherwise it triggers replication on dialog recreation
		super.setApplicationData(appData);
	}
	
	// not needed anymore the setLastResponse on final response will take care of it automatically
//	@Override
//	public void setState(int state) {
//		DialogState oldState = getState();
//		long previousVersion = -1;
//		if(version != null) {
//			previousVersion= version.get();
//		}
//		super.setState(state);
//		DialogState newState = getState();
//		// we replicate only if the state has really changed
//		// the fact of setting the last response upon recreation will trigger setState to be called and so the replication
//		// so we make sure to replicate only if the dialog has been created
//		// also  we replicate only if the version is the same, otherwise it means setState already triggered a replication by putting the dialog in the stack and therefore in the cache
//		if(!newState.equals(oldState) & newState == DialogState.CONFIRMED && previousVersion != -1 && previousVersion == version.get()){
//			replicateState();
//		}
//	}

	protected abstract void replicateState();

	public void setLocalTagInternal(String localTag) {
		super.myTag = localTag;
	}
	
	public void setRemoteTagInternal(String remoteTag) {
		super.hisTag = remoteTag;
	}

	public void setLocalPartyInternal(Address localParty) {
		super.localParty = localParty;
	}

	public void setRemotePartyInternal(Address remoteParty) {
		super.remoteParty = remoteParty;
	}

	public void setContactHeader(ContactHeader contactHeader) {
		this.contactHeader = (Contact) contactHeader;
	}
		
	@Override
	public void setLastResponse(SIPTransaction transaction,
			SIPResponse sipResponse) {		
		// version can be null on dialog recreation
		if(version != null) {
			boolean lastResponseChanged = false;
			long previousVersion = version.get(); 			
			// for 2xx w/o 1xx
			final ReplicationStrategy replicationStrategy = ((ClusteredSipStack)getStack()).getReplicationStrategy();
			// set to confirmed dialog strategy at least
			int lowerStatusCodeToReplicateOn = 200;
			if(replicationStrategy == ReplicationStrategy.EarlyDialog) {
				lowerStatusCodeToReplicateOn = 101; 
			}
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(dialogId  + " lowerStatusCodeToReplicateOn = " + lowerStatusCodeToReplicateOn);
				logger.logDebug(dialogId  + " lastResponseStr = " + lastResponseStringified);
				logger.logDebug(dialogId  + " sipResponse = " + sipResponse);
			}
			if(sipResponse != null && getLastResponseStringified() == null && sipResponse.getStatusCode() >= lowerStatusCodeToReplicateOn) {
				lastResponseChanged = true;
			}
			String responseStringified = sipResponse.toString(); 
			if(sipResponse != null && getLastResponseStringified() != null && sipResponse.getStatusCode() >= lowerStatusCodeToReplicateOn && !responseStringified.equals(this.getLastResponseStringified())) {
				lastResponseChanged = true;
			}		
			super.setLastResponse(transaction, sipResponse);
			lastResponseStringified = responseStringified;
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug(dialogId  + " lastResponseChanged = " + lastResponseChanged);
				logger.logDebug(dialogId  + " previousVersion = " + previousVersion);
				logger.logDebug(dialogId  + " currentVersion = " + version.get());
			}
			// we replicate only if the version is the same, otherwise it means lastResponse already triggered a replication by putting the dialog in the stack and therefore in the cache		
			if(lastResponseChanged && previousVersion == version.get()) {
				// don't consider it a retrans even if it is on the new node taking over
				if(replicationStrategy == ReplicationStrategy.EarlyDialog) {
					sipResponse.setRetransmission(false);
				}
				replicateState();
			}
		}
		// causes REINVITE after NOTIFY on failover to fail with NPE since method attribute is not yet initialized
//		else {
//			super.setLastResponse(transaction, sipResponse);
//		}
	}

	public void setLastResponse(SIPResponse lastResponse) {
		// the call to super is very important otherwise it triggers replication on dialog recreation
		super.setLastResponse(null, lastResponse);
	}	

	/**
	 * @return the isRemoteTagSet
	 */
	public boolean isRemoteTagSet() {
		return hisTag != null && hisTag.trim().length() > 0 ? true : false;
	}
	
	/**
	 * @return the isRemoteTagSet
	 */
	public boolean isLocalTagSet() {
		return myTag != null && myTag.trim().length() > 0 ? true : false;
	}
	
	/**
	 * @return the version
	 */
	public long getVersion() {
		return version.get();
	}		

	public String getDialogIdToReplicate() {
		return getDialogId();
		// No need for this anymore since we replicate only when the last response is a final one
//		String cid = this.getCallId().getCallId();
//		StringBuilder retval = new StringBuilder(cid);
//        retval.append(Separators.COLON);
//        retval.append(myTag);
//        retval.append(Separators.COLON);
//        retval.append(hisTag);        
//        return retval.toString().toLowerCase();
	}
	
	public String getLastResponseStringified() {
		return lastResponseStringified;
	}	
	
	@Override
	public void setReInviteFlag(boolean reInviteFlag) {
		boolean oldReinvite = isReInvite();
		super.setReInviteFlag(reInviteFlag);
		if(reInviteFlag != oldReinvite) {
			isReinviteChanged = true;
		}
	}
	
	@Override
	protected void storeFirstTransactionInfo(SIPDialog dialog, SIPTransaction transaction) {
		super.storeFirstTransactionInfo(dialog, transaction);
		storeFirstTxChanged = true;
	}
	
	@Override
	public void setRemoteTarget(ContactHeader contact) {		
		super.setRemoteTarget(contact);
		remoteTargetChanged = true;
	}
	
	@Override
	public void setState(int state) {
		DialogState oldState = this.getState();
		super.setState(state);
		final ReplicationStrategy replicationStrategy = ((ClusteredSipStack)getStack()).getReplicationStrategy();
		if(replicationStrategy == ReplicationStrategy.EarlyDialog && (oldState == null  || oldState.getValue() != state && state != DialogState.TERMINATED.getValue())) { 
			dialogStateChanged = true;
			if (logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug("dialogStateChanged");
			}
		}
	}
}
