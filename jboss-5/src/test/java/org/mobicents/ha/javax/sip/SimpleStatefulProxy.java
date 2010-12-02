package org.mobicents.ha.javax.sip;

import gov.nist.javax.sip.RequestEventExt;
import gov.nist.javax.sip.ResponseEventExt;
import gov.nist.javax.sip.message.MessageExt;
import gov.nist.javax.sip.message.SIPRequest;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import javax.sip.ClientTransaction;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentLengthHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.jboss.cache.CacheException;
import org.jboss.cache.Fqn;
import org.mobicents.ha.javax.sip.cache.ManagedMobicentsSipCache;
import org.mobicents.ha.javax.sip.cache.MobicentsSipCache;
import org.mobicents.tools.sip.balancer.NodeRegisterRMIStub;
import org.mobicents.tools.sip.balancer.SIPNode;

public class SimpleStatefulProxy implements SipListener {
	private static final String SIP_BIND_ADDRESS = "javax.sip.IP_ADDRESS";
	private static final String SIP_PORT_BIND = "javax.sip.PORT";
	private static final String TRANSPORTS_BIND = "javax.sip.TRANSPORT";
	
	private SipFactory sipFactory;
	private SipStack sipStack;
	private ListeningPoint listeningPoint;
	private Properties properties;
	private SipProvider sipProvider;
	private MessageFactory messageFactory;
	private HeaderFactory headerFactory;
	private AddressFactory addressFactory;
	private ServerTransaction serverTransaction;
	private ClientTransaction clientTransaction;
	private String currentCtxBranchId;
	private Integer currentCtxRemotePort;
	int myPort;
	String transport;
	String ipAddress;
	private TimerTask keepaliveTask;
	
	public SimpleStatefulProxy(String stackName, String ipAddress, int port, String transport, ReplicationStrategy replicationStrategy) {
//		this.localTag = localTag;		
		myPort = port;
		this.transport = transport;
		this.ipAddress = ipAddress;
		properties = new Properties();        
        properties.setProperty("javax.sip.STACK_NAME", stackName);
        properties.setProperty(SIP_PORT_BIND, String.valueOf(myPort));        
        properties.setProperty("javax.sip.OUTBOUND_PROXY", ipAddress + ":" + Integer.toString(5065) + "/" + transport);
        // You need 16 for logging traces. 32 for debug + traces.
        // Your code will limp at 32 but it is best for debugging.
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
        properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "logs/" +
                stackName + "debug.txt");
        properties.setProperty("gov.nist.javax.sip.SERVER_LOG", "logs/" +
                stackName + "log.xml");
        properties.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");
        properties.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "true");
        properties.setProperty("org.mobicents.ha.javax.sip.REPLICATION_STRATEGY", replicationStrategy.toString());
        properties.setProperty(ManagedMobicentsSipCache.STANDALONE, "true");
        System.setProperty("jgroups.bind_addr", ipAddress);
        System.setProperty("jgroups.udp.mcast_addr", "FFFF::232.5.5.5");
        System.setProperty("jboss.server.log.threshold", "DEBUG");
        System.setProperty("jbosscache.config.validate", "false");
		try {
			initStack(ipAddress, transport);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void initStack(String ipAddress, String transport) throws Exception {
		
		this.sipFactory = SipFactory.getInstance();
		this.sipFactory.setPathName("org.mobicents.ha");
		//this.sipFactory.setPathName("gov.nist");
		this.sipStack = this.sipFactory.createSipStack(properties);
		this.sipStack.start();
		this.listeningPoint = this.sipStack.createListeningPoint(properties.getProperty(
				SIP_BIND_ADDRESS, ipAddress), Integer.valueOf(properties
				.getProperty(SIP_PORT_BIND, "5060")), properties.getProperty(
				TRANSPORTS_BIND, transport));
		this.sipProvider = this.sipStack.createSipProvider(this.listeningPoint);
		this.sipProvider.addSipListener(this);
		this.headerFactory = sipFactory.createHeaderFactory();
		this.messageFactory = sipFactory.createMessageFactory();		
		this.addressFactory = sipFactory.createAddressFactory();
		properties.setProperty(SIP_BIND_ADDRESS, ipAddress);
	}
	
	private void storeServerTransactionId(String branchId) {
		((ClusteredSipStack)sipProvider.getSipStack()).getStackLogger().logDebug("Storing transaction Id " + branchId);
		try {
			((MobicentsSipCache)((ClusteredSipStack)sipProvider.getSipStack()).getSipCache()).getMobicentsCache().getJBossCache().put(Fqn.fromString("STX_IDS"), "serverTransactionId", branchId);
		} catch (CacheException e) {
			// TODO Auto-generated catch block
			((SipStackImpl)sipStack).getStackLogger().logError("unexpected exception", e);
		}
	}
	
	private void storeClientTransactionId(String branchId, int port) {
		((ClusteredSipStack)sipProvider.getSipStack()).getStackLogger().logDebug("Storing client Id " + branchId + " and remote port" + port);
		currentCtxBranchId = branchId;
		currentCtxRemotePort = Integer.valueOf(port);
		try {
			((MobicentsSipCache)((ClusteredSipStack)sipProvider.getSipStack()).getSipCache()).getMobicentsCache().getJBossCache().put(Fqn.fromString("CTX_IDS"), "clientTransactionId", branchId);
			((MobicentsSipCache)((ClusteredSipStack)sipProvider.getSipStack()).getSipCache()).getMobicentsCache().getJBossCache().put(Fqn.fromString("CTX_PORT"), "clientTransactionRemotePort", currentCtxRemotePort);
		} catch (CacheException e) {
			// TODO Auto-generated catch block
			((SipStackImpl)sipStack).getStackLogger().logError("unexpected exception", e);
		}
	}
	
	/**
	 * @return the outgoingDialog
	 */
	public ServerTransaction getServerTransaction() {
		if(serverTransaction != null) {
			return serverTransaction;
		}
		String serverTransactionId = null;
		try {
			serverTransactionId = (String) ((MobicentsSipCache)((ClusteredSipStack)sipProvider.getSipStack()).getSipCache()).getMobicentsCache().getJBossCache().get(Fqn.fromString("STX_IDS"), "serverTransactionId");
		} catch (CacheException e) {
			((SipStackImpl)sipStack).getStackLogger().logError("unexpected exception", e);
		}
		((ClusteredSipStack)sipStack).getStackLogger().logInfo("server transaction Id " + serverTransactionId);
		if(serverTransactionId == null) {
			return null;
		}
		serverTransaction =  (ServerTransaction) ((ClusteredSipStack)sipProvider.getSipStack()).findTransaction(serverTransactionId, true);
		return serverTransaction;
	}
	
	/**
	 * @return the outgoingDialog
	 */
	public ClientTransaction getClientTransaction() {
		if(clientTransaction != null) {
			return clientTransaction;
		}
		String clientTransactionId = null;
		try {
			clientTransactionId = (String) ((MobicentsSipCache)((ClusteredSipStack)sipProvider.getSipStack()).getSipCache()).getMobicentsCache().getJBossCache().get(Fqn.fromString("CTX_IDS"), "clientTransactionId");
		} catch (CacheException e) {
			((SipStackImpl)sipStack).getStackLogger().logError("unexpected exception", e);
		}
		((ClusteredSipStack)sipStack).getStackLogger().logInfo("client transaction Id " + clientTransactionId);
		if(clientTransactionId == null) {
			return null;
		}
		return (ClientTransaction) ((ClusteredSipStack)sipProvider.getSipStack()).findTransaction(clientTransactionId, false);
	}
	
	/**
	 * @return the outgoingDialog
	 */
	public String getClientTransactionId() {
		String clientTransactionId = currentCtxBranchId;
		if(clientTransactionId == null) {
			try {
				clientTransactionId = (String) ((MobicentsSipCache)((ClusteredSipStack)sipProvider.getSipStack()).getSipCache()).getMobicentsCache().getJBossCache().get(Fqn.fromString("CTX_IDS"), "clientTransactionId");
			} catch (CacheException e) {
				((SipStackImpl)sipStack).getStackLogger().logError("unexpected exception", e);
			}		
		}
		return clientTransactionId;
	}
	
	/**
	 * @return the outgoingDialog
	 */
	public int getClientTransactionPort() {
		Integer clientTransactionRemotePort = currentCtxRemotePort;
		if(clientTransactionRemotePort == null) {
			try {
				clientTransactionRemotePort = (Integer) ((MobicentsSipCache)((ClusteredSipStack)sipProvider.getSipStack()).getSipCache()).getMobicentsCache().getJBossCache().get(Fqn.fromString("CTX_PORT"), "clientTransactionRemotePort");
			} catch (CacheException e) {
				((SipStackImpl)sipStack).getStackLogger().logError("unexpected exception", e);
			}		
		}
		return clientTransactionRemotePort.intValue();
	}
	
	public void processInvite(RequestEvent requestEvent) {
		//System.out.println("Got invite: "+requestEvent.getRequest());
		try {
			serverTransaction = requestEvent.getServerTransaction();
			if (serverTransaction == null) {
				try {
					serverTransaction = sipProvider.getNewServerTransaction(requestEvent.getRequest());
				}
				catch (Exception e) {
					((SipStackImpl)sipStack).getStackLogger().logError("unexpected exception", e);
					return;
				}
			}
			storeServerTransactionId(serverTransaction.getBranchId());
			serverTransaction.sendResponse(messageFactory.createResponse(100, requestEvent.getRequest()));
			int peerPort = 5070;
			if(((MessageExt)requestEvent.getRequest()).getToHeader().getTag() != null) {
				peerPort = 5050;
				((MessageExt)requestEvent.getRequest()).removeLast(RouteHeader.NAME);			
			}
			forwardInvite(peerPort);			
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}	
	
	/**
	 * @param incomingDialog2
	 * @return
	 * @throws SipException 
	 * @throws InvalidArgumentException 
	 * @throws ParseException 
	 */
	private void forwardInvite(int peerPort) throws SipException, ParseException, InvalidArgumentException {
		Request request = createRequest(getServerTransaction().getRequest(), peerPort);
		Address address = null;
		try {
			address = addressFactory.createAddress("sip:" + ipAddress + ":" + myPort);
		} catch (ParseException e) {
			e.printStackTrace();
			throw new SipException(e.getMessage());
		}
		RecordRouteHeader recordRouteHeader = headerFactory.createRecordRouteHeader(address);
		request.addFirst(recordRouteHeader);
		RouteHeader routeHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
		if(routeHeader != null && (((SipURI)routeHeader.getAddress().getURI()).getPort() == 5080 || ((SipURI)routeHeader.getAddress().getURI()).getPort() == 5081)) {
			request.removeFirst(RouteHeader.NAME);
		}
		request.addHeader(headerFactory.createViaHeader(ipAddress, myPort, transport, null));
		ClientTransaction ct = sipProvider.getNewClientTransaction(request);		
		clientTransaction = ct;
		clientTransaction.sendRequest();		
		storeClientTransactionId(clientTransaction.getBranchId(), peerPort);
	}

	@SuppressWarnings("unchecked")
	private Request createRequest(Request origRequest, int peerPort) throws SipException {

		final SIPRequest request = (SIPRequest) origRequest.clone();			
		((SipURI)request.getRequestURI()).setPort(peerPort);
		
		return request;		
	}

	public void processAck(RequestEvent requestEvent) {
		int remotePort = ((RequestEventExt)requestEvent).getRemotePort() ;
		if(ListeningPoint.TCP.equalsIgnoreCase(transport)) {
			remotePort = ((MessageExt)requestEvent.getRequest()).getTopmostViaHeader().getPort();
		}
		((ClusteredSipStack)sipStack).getStackLogger().logDebug("remotePort = " + remotePort);
		try {			
			final Request ack = (Request) requestEvent.getRequest().clone();
			String branchId = getClientTransactionId();
			ack.addHeader(headerFactory.createViaHeader(ipAddress, myPort, transport, branchId));
			((SipURI)ack.getRequestURI()).setPort(getClientTransactionPort());
			ack.removeLast(RouteHeader.NAME);
			RouteHeader routeHeader = (RouteHeader) ack.getHeader(RouteHeader.NAME);
			if(routeHeader != null && (((SipURI)routeHeader.getAddress().getURI()).getPort() == 5080 || ((SipURI)routeHeader.getAddress().getURI()).getPort() == 5081)) {
				ack.removeFirst(RouteHeader.NAME);
			}
			sipProvider.sendRequest(ack);
		} catch (Exception e) {
			((SipStackImpl)sipStack).getStackLogger().logError("unexpected exception", e);
		} 
	}

	public void processBye(RequestEvent requestEvent) {
		try {
			serverTransaction = requestEvent.getServerTransaction();
			if (serverTransaction == null) {
				try {
					serverTransaction = sipProvider.getNewServerTransaction(requestEvent.getRequest());
				}
				catch (Exception e) {
					((SipStackImpl)sipStack).getStackLogger().logError("unexpected exception", e);
					return;
				}
			}
			int remotePort = ((RequestEventExt)requestEvent).getRemotePort() ;
			if(ListeningPoint.TCP.equalsIgnoreCase(transport)) {
				remotePort = ((MessageExt)requestEvent.getRequest()).getTopmostViaHeader().getPort();
			}
			((ClusteredSipStack)sipStack).getStackLogger().logDebug("remotePort = " + remotePort);			
			final Request request = (Request) requestEvent.getRequest().clone();
			((SipURI)request.getRequestURI()).setPort(5070);
			request.removeLast(RouteHeader.NAME);
			RouteHeader routeHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);
			if(routeHeader != null && (((SipURI)routeHeader.getAddress().getURI()).getPort() == 5080 || ((SipURI)routeHeader.getAddress().getURI()).getPort() == 5081)) {
				request.removeFirst(RouteHeader.NAME);
			}
			request.addHeader(headerFactory.createViaHeader(ipAddress, myPort, transport, null));
			final ClientTransaction ct = sipProvider.getNewClientTransaction(request);
			ct.sendRequest();						
		}
		catch (Exception e) {
			((SipStackImpl)sipStack).getStackLogger().logError("unexpected exception", e);
		}
	}
	
//	public void processSubscribe(RequestEvent requestEvent) {
//		try {
//			Response response = messageFactory.createResponse(200, requestEvent.getRequest());
//			response.addHeader(headerFactory.createHeader(ExpiresHeader.NAME, "3600"));
//			requestEvent.getServerTransaction().sendResponse(response);
//			Dialog dialog = getOutgoingDialog();
//			Request request = dialog.createRequest(Request.SUBSCRIBE);
//			((SipURI)request.getRequestURI()).setPort(5070);
//			final ClientTransaction ct = sipProvider.getNewClientTransaction(request);
//			dialog.sendRequest(ct);						
//		}
//		catch (Exception e) {
//			((SipStackImpl)sipStack).getStackLogger().logError("unexpected exception", e);
//		}
//	}
//	
//	public void processNotify(RequestEvent requestEvent) {
//		try {
//			requestEvent.getServerTransaction().sendResponse(messageFactory.createResponse(200, requestEvent.getRequest()));
//			Dialog dialog = getIncomingDialog();
//			Request request = dialog.createRequest(Request.NOTIFY);
//			request.addHeader(headerFactory.createHeader(SubscriptionStateHeader.NAME, SubscriptionStateHeader.ACTIVE));
//			request.addHeader(headerFactory.createHeader(EventHeader.NAME, "presence"));
//            ((SipURI)request.getRequestURI()).setUser(null);
////            ((SipURI)request.getRequestURI()).setHost(IP_ADDRESS);
//			((SipURI)request.getRequestURI()).setPort(5050);
//			final ClientTransaction ct = sipProvider.getNewClientTransaction(request);
//			dialog.sendRequest(ct);						
//		}
//		catch (Exception e) {
//			((SipStackImpl)sipStack).getStackLogger().logError("unexpected exception", e);
//		}
//	}
	
	public void process180(ResponseEvent responseEvent) {
		try {
			forwardResponse(responseEvent.getResponse());
		}
		catch (Exception e) {
			((SipStackImpl)sipStack).getStackLogger().logError("unexpected exception", e);
		}
	}
	
	/**
	 * @param responseEvent
	 * @throws InvalidArgumentException 
	 */
	@SuppressWarnings("unchecked")
	private void forwardResponse(Response receivedResponse) throws SipException, InvalidArgumentException {				
		System.out.println("Forwarding " + receivedResponse);
		final ServerTransaction origServerTransaction = this.getServerTransaction();
		Response forgedResponse = (Response) receivedResponse.clone();			
		forgedResponse.removeFirst(ViaHeader.NAME);
		origServerTransaction.sendResponse(forgedResponse);		
	}

	public void process200(ResponseEvent responseEvent) {
		try {
			boolean isRetransmission = ((ResponseEventExt)responseEvent).isRetransmission();
			ClientTransaction clientTransaction = ((ResponseEventExt)responseEvent).getClientTransaction();
			((ClusteredSipStack)sipStack).getStackLogger().logDebug("clientTransaction = " + clientTransaction + ", isRetransmission " + isRetransmission);
			final CSeqHeader cSeqHeader = (CSeqHeader) responseEvent.getResponse().getHeader(CSeqHeader.NAME); 			
			if (cSeqHeader.getMethod().equals(Request.INVITE)) {
				processInvite200(responseEvent,cSeqHeader);
			}
			else if (cSeqHeader.getMethod().equals(Request.BYE) || cSeqHeader.getMethod().equals(Request.SUBSCRIBE) || cSeqHeader.getMethod().equals(Request.NOTIFY)) {
				processBye200(responseEvent);
			}
			else {
				System.err.println("Unexpected response: "+responseEvent.getResponse());
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * @param responseEvent
	 * @throws SipException 
	 * @throws InvalidArgumentException 
	 */
	private void processInvite200(ResponseEvent responseEvent,CSeqHeader cseq) throws InvalidArgumentException, SipException {
		// lets ack it ourselves to avoid UAS retransmissions due to
		// forwarding of this response and further UAC Ack
		// note that the app does not handles UAC ACKs		
//		String outgoingDialogId = responseEvent.getDialog().getDialogId();
		int remotePort = ((ResponseEventExt)responseEvent).getRemotePort() ;
		if(ListeningPoint.TCP.equalsIgnoreCase(transport)) {
			remotePort = ((MessageExt)responseEvent.getResponse()).getTopmostViaHeader().getPort();
		}
		((ClusteredSipStack)sipStack).getStackLogger().logDebug("remotePort = " + remotePort);
//		if(remotePort == 5065 || remotePort == 5081) {
//			storeOutgoingDialogId(outgoingDialogId);
//		}
//		if(myPort == 5080 && getOutgoingDialogId() == null) {
//			storeOutgoingDialogId(outgoingDialogId);
//		}		
		forwardResponse(responseEvent.getResponse());			
	}		

	/**
	 * @param responseEvent
	 */
	private void processBye200(ResponseEvent responseEvent) {
		try {
			forwardResponse(responseEvent.getResponse());
		} catch (SipException e) {
			e.printStackTrace();
		} catch (InvalidArgumentException e) {
			e.printStackTrace();
		}
	}

	private static Set<String> HEADERS_TO_OMMIT_ON_RESPONSE_COPY;

	private static Set<String> getHeadersToOmmitOnResponseCopy() {
		if (HEADERS_TO_OMMIT_ON_RESPONSE_COPY == null) {
			final Set<String> set = new HashSet<String>();
			set.add(RouteHeader.NAME);
			set.add(RecordRouteHeader.NAME);
			set.add(ViaHeader.NAME);
			set.add(CallIdHeader.NAME);
			set.add(CSeqHeader.NAME);
			set.add(ContactHeader.NAME);
			set.add(FromHeader.NAME);
			set.add(ToHeader.NAME);
			set.add(ContentLengthHeader.NAME);
			HEADERS_TO_OMMIT_ON_RESPONSE_COPY = Collections.unmodifiableSet(set);
		}
		return HEADERS_TO_OMMIT_ON_RESPONSE_COPY;
	}

	public void checkState() {
		// TODO Auto-generated method stub
		
	}	
	
	public void pingBalancer() {
		final SIPNode appServerNode = new SIPNode(sipStack.getStackName(), properties.getProperty(SIP_BIND_ADDRESS));
		if(ListeningPoint.UDP.equalsIgnoreCase(transport)) {
			appServerNode.getProperties().put("udpPort",  Integer.parseInt(properties.getProperty(SIP_PORT_BIND)));
		} else {
			appServerNode.getProperties().put("tcpPort",  Integer.parseInt(properties.getProperty(SIP_PORT_BIND)));
		}
		keepaliveTask = new TimerTask() {
			@Override
			public void run() {
				ArrayList<SIPNode> nodes = new ArrayList<SIPNode>();
				nodes.add(appServerNode);
				sendKeepAliveToBalancers(nodes);
			}
		};
		new Timer().schedule(keepaliveTask, 0, 1000);
	}
	
	 private void sendKeepAliveToBalancers(ArrayList<SIPNode> info) {
 		if(true) {
 			Thread.currentThread().setContextClassLoader(NodeRegisterRMIStub.class.getClassLoader());
 			try {
 				Registry registry = LocateRegistry.getRegistry(properties.getProperty(SIP_BIND_ADDRESS), 2000);
 				NodeRegisterRMIStub reg=(NodeRegisterRMIStub) registry.lookup("SIPBalancer");
 				reg.handlePing(info);
 			} catch (Exception e) {
 				if(sipStack != null) {
 					((ClusteredSipStack)sipStack).getStackLogger().logError("couldn't contact the LB, cancelling the keepalive task");
 				}
 				keepaliveTask.cancel();
 			}
 		}

 	}	
	 
	 public void stopPingBalancer() {
		 keepaliveTask.cancel();
		 Thread.currentThread().setContextClassLoader(NodeRegisterRMIStub.class.getClassLoader());
		try {
			Registry registry = LocateRegistry.getRegistry(properties.getProperty(SIP_BIND_ADDRESS), 2000);
			NodeRegisterRMIStub reg=(NodeRegisterRMIStub) registry.lookup("SIPBalancer");
			final SIPNode appServerNode = new SIPNode(sipStack.getStackName(), properties.getProperty(SIP_BIND_ADDRESS));
			if(ListeningPoint.UDP.equalsIgnoreCase(transport)) {
				appServerNode.getProperties().put("udpPort",  Integer.parseInt(properties.getProperty(SIP_PORT_BIND)));
			} else {
				appServerNode.getProperties().put("tcpPort",  Integer.parseInt(properties.getProperty(SIP_PORT_BIND)));
			}
			ArrayList<SIPNode> nodes = new ArrayList<SIPNode>();
			nodes.add(appServerNode);
			reg.forceRemoval(nodes);
		} catch (Exception e) {
			
		}
	 }
	 
	 public void stop() {
		stop(true);
	}
	
	public void stop(boolean stopSipStack) {
		Iterator<SipProvider> sipProviderIterator = sipStack.getSipProviders();
        try{
            while (sipProviderIterator.hasNext()) {
                SipProvider sipProvider = sipProviderIterator.next();
                if(!ListeningPoint.TCP.equalsIgnoreCase(transport)) {
	                ListeningPoint[] listeningPoints = sipProvider.getListeningPoints();
	                for (ListeningPoint listeningPoint : listeningPoints) {
	                    sipProvider.removeListeningPoint(listeningPoint);
	                    sipStack.deleteListeningPoint(listeningPoint);
	                    listeningPoints = sipProvider.getListeningPoints();
	                }
                }
                sipProvider.removeSipListener(this);
                sipStack.deleteSipProvider(sipProvider);                
                sipProviderIterator = sipStack.getSipProviders();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cant remove the listening points or sip providers", e);
        }

        listeningPoint = null;
        sipProvider = null;
        sipFactory.resetFactory();
        serverTransaction = null;
        clientTransaction = null;
        currentCtxBranchId = null;
        currentCtxRemotePort = null;
        if(stopSipStack){
        	sipStack.stop();
        	sipStack = null;
        	headerFactory = null;
            messageFactory = null;
            sipFactory = null;
            properties = null;
        }                     
	}


	public void processDialogTerminated(
			DialogTerminatedEvent dialogTerminatedEvent) {}

	public void processIOException(IOExceptionEvent exceptionEvent) {}

	public void processRequest(RequestEvent requestEvent) {
		if (requestEvent.getRequest().getMethod().equals(Request.INVITE)) {
			processInvite(requestEvent);
		}		
		else if (requestEvent.getRequest().getMethod().equals(Request.BYE)) {
			processBye(requestEvent);
		}
		else if (requestEvent.getRequest().getMethod().equals(Request.ACK)) {
			processAck(requestEvent);
		}
		else {
			((ClusteredSipStack)sipStack).getStackLogger().logError("Received unexpected sip request: "+requestEvent.getRequest());			
		}
	}

	public void processResponse(ResponseEvent responseEvent) {
		
		if(responseEvent.getClientTransaction() != null) {
			System.out.println("transaction is " +  responseEvent.getClientTransaction().getBranchId() + " for response " +responseEvent.getResponse() );
		} else {
			System.out.println("null tx for response " +responseEvent.getResponse() );
		}
		switch (responseEvent.getResponse().getStatusCode()) {
		case 100:
			// ignore
			break;
		case 180:
			process180(responseEvent);
			break;
		case 200:
			process200(responseEvent);
			break;	
		case 202:
			process200(responseEvent);
			break;
		default:
			System.err.println("Received unexpected sip response: "+responseEvent.getResponse());			
			break;
		}			
	}

	public void processTimeout(TimeoutEvent timeoutEvent) {}

	public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {}

	public boolean checkTransactionsRemoved() {
		System.out.println("client transaction table size " +  ((SipStackImpl)sipStack).getClientTransactionTableSize());
		if(((SipStackImpl)sipStack).getClientTransactionTableSize() > 0) {
			return false;
		}
		System.out.println("server transaction table size " +  ((SipStackImpl)sipStack).getServerTransactionTableSize());
		if(((SipStackImpl)sipStack).getServerTransactionTableSize() > 0) {			
			return false;
		}
		return true;
	}
	
}
