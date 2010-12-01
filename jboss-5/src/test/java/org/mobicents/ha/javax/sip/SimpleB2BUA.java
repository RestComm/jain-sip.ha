package org.mobicents.ha.javax.sip;

import gov.nist.javax.sip.ResponseEventExt;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TooManyListenersException;
import java.util.concurrent.atomic.AtomicLong;

import javax.sip.Dialog;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.header.HeaderFactory;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;

import org.mobicents.ha.javax.sip.cache.ManagedMobicentsSipCache;
import org.mobicents.tools.sip.balancer.NodeRegisterRMIStub;
import org.mobicents.tools.sip.balancer.SIPNode;

public class SimpleB2BUA implements SipListener {
 
	private static final String SIP_BIND_ADDRESS = "javax.sip.IP_ADDRESS";
	private static final String SIP_PORT_BIND = "javax.sip.PORT";
	private static final String TRANSPORTS_BIND = "javax.sip.TRANSPORT";
	//private static final String STACK_NAME_BIND = "javax.sip.STACK_NAME";
	
	private SipFactory sipFactory;
	private SipStack sipStack;
	private ListeningPoint listeningPoint;
	private SipProvider provider;
	private HeaderFactory headerFactory;
	private MessageFactory messageFactory;
	private Properties properties;
	private SimpleB2BUAHandler b2buaHandler;
	private TimerTask keepaliveTask;
	private String transport;
	
	public SimpleB2BUA(String stackName, int myPort, String ipAddress, String transport, ReplicationStrategy replicationStrategy, boolean useLoadBalancer) throws NumberFormatException, SipException, TooManyListenersException, InvalidArgumentException, ParseException {
		this.transport = transport;
		properties = new Properties();        
        properties.setProperty("javax.sip.STACK_NAME", stackName);
        properties.setProperty(SIP_PORT_BIND, String.valueOf(myPort));        
        if(useLoadBalancer) {
        	properties.setProperty("javax.sip.OUTBOUND_PROXY", ipAddress + ":" + Integer.toString(5065) + "/" + transport);
        }
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
		initStack(ipAddress, transport);
	}
	
	public void initStack(String ipAddress, String transport) throws SipException, TooManyListenersException,
			NumberFormatException, InvalidArgumentException, ParseException {
		this.sipFactory = SipFactory.getInstance();
		this.sipFactory.setPathName("org.mobicents.ha");
//		this.sipFactory.setPathName("gov.nist");
		this.sipStack = this.sipFactory.createSipStack(properties);
		this.sipStack.start();
		this.listeningPoint = this.sipStack.createListeningPoint(properties.getProperty(
				SIP_BIND_ADDRESS, ipAddress), Integer.valueOf(properties
				.getProperty(SIP_PORT_BIND, "5060")), properties.getProperty(
				TRANSPORTS_BIND, transport));
		this.provider = this.sipStack.createSipProvider(this.listeningPoint);
		this.provider.addSipListener(this);
		this.headerFactory = sipFactory.createHeaderFactory();
		this.messageFactory = sipFactory.createMessageFactory();
		b2buaHandler = new SimpleB2BUAHandler(provider,headerFactory,messageFactory, Integer.parseInt(properties.getProperty(SIP_PORT_BIND)), transport);
		properties.setProperty(SIP_BIND_ADDRESS, ipAddress);
	}

	private AtomicLong counter = new AtomicLong();
	
	private String getNextCounter() {
		long l = counter.incrementAndGet();
		return Long.toString(l);
	}
	
	// XXX -- SipListenerMethods - here we process incoming data

	public void processIOException(IOExceptionEvent arg0) {}

	public void processRequest(RequestEvent requestEvent) {

		if (requestEvent.getRequest().getMethod().equals(Request.INVITE)) {
			b2buaHandler.processInvite(requestEvent);
		}
		else if (requestEvent.getRequest().getMethod().equals(Request.SUBSCRIBE)) {
			b2buaHandler.processSubscribe(requestEvent);
		}
		else if (requestEvent.getRequest().getMethod().equals(Request.NOTIFY)) {
			b2buaHandler.processNotify(requestEvent);
		}
		else if (requestEvent.getRequest().getMethod().equals(Request.BYE)) {
			b2buaHandler.processBye(requestEvent);
		}
		else if (requestEvent.getRequest().getMethod().equals(Request.ACK)) {
			b2buaHandler.processAck(requestEvent);
		}
		else {
			((SipStackImpl)sipStack).getStackLogger().logError("Received unexpected sip request: "+requestEvent.getRequest());
			Dialog dialog = requestEvent.getDialog();
			if (dialog != null) {
				dialog.setApplicationData(null);
			}
		}
	}

	public void processResponse(ResponseEvent responseEvent) {

		Dialog dialog = responseEvent.getDialog();
				
		if (dialog != null) {
			System.out.println("dialog is " +  dialog.getDialogId() + " for response " +responseEvent.getResponse() );
			if (((ResponseEventExt)responseEvent).isRetransmission()) {
				// retransmission, drop it
				System.out.println("dropping retransmission for response " +responseEvent.getResponse() + "on dialog " + dialog.getDialogId());
				return;
			}			
			if (b2buaHandler != null) {				
				switch (responseEvent.getResponse().getStatusCode()) {
				case 100:
					// ignore
					break;
				case 180:
					b2buaHandler.process180(responseEvent);
					break;
				case 200:
					b2buaHandler.process200(responseEvent);
					break;	
				case 202:
					b2buaHandler.process200(responseEvent);
					break;
				default:
					System.err.println("Received unexpected sip response: "+responseEvent.getResponse());
					dialog.setApplicationData(null);
					break;
				}
			} else {
				((SipStackImpl)sipStack).getStackLogger().logError("Received response on dialog with id that does not matches a active call: "+responseEvent.getResponse());
			}
		} else {
			((SipStackImpl)sipStack).getStackLogger().logError("Received response without dialog: "+responseEvent.getResponse());
		}
	}

	public void processTimeout(TimeoutEvent arg0) {}

	public void processTransactionTerminated(
			TransactionTerminatedEvent txTerminatedEvent) {}

	public void processDialogTerminated(DialogTerminatedEvent dte) {
		dte.getDialog().setApplicationData(null);
	}
	
	public boolean checkDialogsRemoved() {
		if(((SipStackImpl)sipStack).getDialog(b2buaHandler.getIncomingDialogId()) != null) {
			return false;
		}
		if(((SipStackImpl)sipStack).getDialog(b2buaHandler.getOutgoingDialogId()) != null) {
			return false;
		}
		return true;
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
        provider = null;
        sipFactory.resetFactory();
        
        if(stopSipStack){
        	sipStack.stop();
        	sipStack = null;
        	headerFactory = null;
            messageFactory = null;
            sipFactory = null;
            b2buaHandler = null;
            properties = null;
        }                     
	}

	/**
	 * @param b2buaHandler the b2buaHandler to set
	 */
	public void setB2buaHandler(SimpleB2BUAHandler b2buaHandler) {
		this.b2buaHandler = b2buaHandler;
	}

	/**
	 * @return the b2buaHandler
	 */
	public SimpleB2BUAHandler getB2buaHandler() {
		return b2buaHandler;
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
