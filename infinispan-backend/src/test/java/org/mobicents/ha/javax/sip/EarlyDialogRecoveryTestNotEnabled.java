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


package org.mobicents.ha.javax.sip;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogState;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.PeerUnavailableException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.Transaction;
import javax.sip.TransactionState;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.mobicents.ha.javax.sip.cache.infinispan.CacheManagerHolder;

import gov.nist.javax.sip.stack.AbstractHASipDialog;
import gov.nist.javax.sip.stack.SIPDialog;
import gov.nist.javax.sip.stack.SIPServerTransaction;
import junit.framework.TestCase;

/**
 * Test case for the early dialog recovery. For more information please see the tests below.
 * 
 * @author <A HREF="mailto:jean.deruelle@gmail.com">Jean Deruelle</A>
 * @author <A HREF="mailto:posfai.gergely@ext.alerant.hu">Gergely Posfai</A>
 * @author <A HREF="mailto:kokuti.andras@ext.alerant.hu">Andras Kokuti</A>
 *
 */

public class EarlyDialogRecoveryTestNotEnabled extends TestCase {
	
	private static final String myAddress = "127.0.0.1";
	
	class Shootme implements SipListener {

		private SipStack sipStack;
		private AddressFactory addressFactory;
		private MessageFactory messageFactory;
		private HeaderFactory headerFactory;
		
		private String stackName;

		private String cacheName;

		public int myPort = 5070;

		protected ServerTransaction inviteTid;

		private Response okResponse;

		private Request inviteRequest;

		protected Dialog dialog;

		public boolean immediateAnswer = true;

		private SipProvider sipProvider;

		private boolean firstTxComplete = false;
		private boolean byeComplete = false;
		private boolean dialogRemoved = false;
		
		private ReplicationStrategy replicationStrategy;
		
		public Shootme(String stackName, String cacheName, int myPort, boolean immediateAnswer) {
			this.stackName = stackName;
			this.cacheName = cacheName;
			this.myPort = myPort;
			this.immediateAnswer = immediateAnswer;
			this.replicationStrategy = ReplicationStrategy.ConfirmedDialog;
			System.setProperty("java.net.preferIPv4Stack", "true");
		}
		
		public Shootme(String stackName, String cacheName, int myPort,
				boolean immediateAnswer, ReplicationStrategy rs) {
			this.stackName = stackName;
			this.cacheName = cacheName;
			this.myPort = myPort;
			this.immediateAnswer = immediateAnswer;
			this.replicationStrategy = rs;
			System.setProperty("java.net.preferIPv4Stack", "true");
		}

		class ByeTask extends TimerTask {
			public void run() {
				try {
					Request byeRequest = dialog.createRequest(Request.BYE);
					ClientTransaction ct = sipProvider
							.getNewClientTransaction(byeRequest);
					dialog.sendRequest(ct);
					
				} catch (Exception ex) {
					ex.printStackTrace();
					fail("Shootme: unexpected exception sending bye: " + ex.toString());
				}
			}

		}

		class MyTimerTask extends TimerTask {
			Shootme shootme;

			public MyTimerTask(Shootme shootme) {
				this.shootme = shootme;

			}

			public void run() {
				shootme.sendInviteOK();
			}

		}

		protected static final String usageString = "java "
				+ "examples.shootist.Shootist \n"
				+ ">>>> is your class path set to the root?";

		public void processRequest(RequestEvent requestEvent) {
			Request request = requestEvent.getRequest();
			ServerTransaction serverTransactionId = requestEvent
					.getServerTransaction();

			/*System.out.println("\n\nShootme: request " + request.getMethod()
					+ " received at " + sipStack.getStackName()
					+ " with server transaction id " + serverTransactionId);
			*/

			if (request.getMethod().equals(Request.INVITE)) {
				processInvite(requestEvent, serverTransactionId);
			} else if (request.getMethod().equals(Request.ACK)) {
				processAck(requestEvent, serverTransactionId);
			} else if (request.getMethod().equals(Request.BYE)) {
				processBye(requestEvent, serverTransactionId);
			} else if (request.getMethod().equals(Request.CANCEL)) {
				processCancel(requestEvent, serverTransactionId);
			} else {
				try {
					serverTransactionId.sendResponse(messageFactory
							.createResponse(202, request));

					// send one back
					SipProvider prov = (SipProvider) requestEvent.getSource();
					Request refer = requestEvent.getDialog().createRequest(
							"REFER");
					requestEvent.getDialog().sendRequest(
							prov.getNewClientTransaction(refer));

				} catch (SipException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InvalidArgumentException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ParseException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		}

		public void processResponse(ResponseEvent responseEvent) {
			Dialog dialog = responseEvent.getDialog();
			CSeqHeader cSeqHeader = (CSeqHeader) responseEvent.getResponse()
					.getHeader(CSeqHeader.NAME);
			try {
				if (responseEvent.getResponse().getStatusCode() >= 200
						&& cSeqHeader.getMethod().equalsIgnoreCase(
								Request.INVITE)) {
					Request ackRequest = dialog.createAck(cSeqHeader
							.getSeqNumber());
					System.out.println("Shootme: sending ACK");
					dialog.sendAck(ackRequest);

				} else if (responseEvent.getResponse().getStatusCode() == 200
						&& cSeqHeader.getMethod().equalsIgnoreCase(
								Request.BYE)) {
					byeComplete = true;
				}
			} catch (SipException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

		/**
		 * Process the ACK request. Send the bye and complete the call flow.
		 */
		public void processAck(RequestEvent requestEvent,
				ServerTransaction serverTransaction) {
			try {
				Dialog dialog = serverTransaction.getDialog();
				System.out.println("Shootme: got an ACK! ");
				System.out.println("Shootme: dialog State = " + dialog.getState());
				firstTxComplete = true;

			} catch (Exception ex) {
				ex.printStackTrace();
			}

		}

		/**
		 * Process the invite request.
		 */
		public void processInvite(RequestEvent requestEvent,
				ServerTransaction serverTransaction) {
			SipProvider sipProvider = (SipProvider) requestEvent.getSource();
			Request request = requestEvent.getRequest();
			try {
				System.out.println("Shootme: got an Invite sending Trying");
				Response response = messageFactory.createResponse(Response.RINGING, request);
				ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
				toHeader.setTag("4321"); // Application is supposed to set.

				ServerTransaction st = requestEvent.getServerTransaction();

				if (st == null) {
					st = sipProvider.getNewServerTransaction(request);
				}
				dialog = st.getDialog();
				inviteTid = st;
				inviteRequest = request;
				dialog.setApplicationData(((SIPServerTransaction)st).getTransactionId());

				st.sendResponse(response);

				Thread.sleep(1000);
				
				if (this.immediateAnswer) {
					this.okResponse = messageFactory.createResponse(Response.OK,
							request);
					Address address = addressFactory.createAddress("Shootme <sip:"
							+ myAddress + ":" + myPort + ">");
					ContactHeader contactHeader = headerFactory
							.createContactHeader(address);
					response.addHeader(contactHeader);
					toHeader = (ToHeader) okResponse.getHeader(ToHeader.NAME);
					toHeader.setTag("4321"); // Application is supposed to set.
					okResponse.addHeader(contactHeader);
					
					sendInviteOK();
				}
				
			} catch (Exception ex) {
				ex.printStackTrace();
				System.exit(0);
			}
		}

		private void sendInviteOK() {
			try {
				if (inviteTid.getState() != TransactionState.COMPLETED) {
					System.out.println("Shootme: dialog state before 200: "
							+ inviteTid.getDialog().getState());
					inviteTid.sendResponse(okResponse);
					System.out.println("Shootme: dialog state after 200: "
							+ inviteTid.getDialog().getState());
				}
			} catch (SipException ex) {
				ex.printStackTrace();
			} catch (InvalidArgumentException ex) {
				ex.printStackTrace();
			}
		}

		/**
		 * Process the bye request.
		 */
		public void processBye(RequestEvent requestEvent,
				ServerTransaction serverTransactionId) {
			Request request = requestEvent.getRequest();
			try {
				System.out.println("Shootme:  got a bye sending OK.");
				Response response = messageFactory.createResponse(200, request);
				serverTransactionId.sendResponse(response);
				System.out.println("Shootme: dialog State is "
						+ serverTransactionId.getDialog().getState());
			} catch (Exception ex) {
				ex.printStackTrace();
				System.exit(0);

			}
		}

		public void processCancel(RequestEvent requestEvent,
				ServerTransaction serverTransactionId) {
			Request request = requestEvent.getRequest();
			try {
				System.out.println("Shootme:  got a cancel.");
				if (serverTransactionId == null) {
					fail("Shootme:  null tid.");
					return;
				}
				Response response = messageFactory.createResponse(200, request);
				serverTransactionId.sendResponse(response);
				if (dialog.getState() != DialogState.CONFIRMED) {
					response = messageFactory.createResponse(
							Response.REQUEST_TERMINATED, inviteRequest);
					inviteTid.sendResponse(response);
				}

			} catch (Exception ex) {
				ex.printStackTrace();
				System.exit(0);

			}
		}

		public void processTimeout(javax.sip.TimeoutEvent timeoutEvent) {
			Transaction transaction;
			if (timeoutEvent.isServerTransaction()) {
				transaction = timeoutEvent.getServerTransaction();
			} else {
				transaction = timeoutEvent.getClientTransaction();
			}
			System.out.println("Shootme: dialog = " + transaction.getDialog());
			System.out.println("Shootme: dialogState = "
					+ transaction.getDialog().getState());
			fail("Shootme: transaction Timed out");
		}

		public void init() {
			SipFactory sipFactory = null;
			sipStack = null;
			sipFactory = SipFactory.getInstance();
			sipFactory.setPathName("org.mobicents.ha");
			Properties properties = new Properties();
			properties.setProperty("javax.sip.STACK_NAME", stackName);
			// You need 16 for logging traces. 32 for debug + traces.
			// Your code will limp at 32 but it is best for debugging.
			properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
			properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "logs/"
					+ stackName + "debug.txt");
			properties.setProperty("gov.nist.javax.sip.SERVER_LOG", "logs/"
					+ stackName + "log.xml");
			properties.setProperty("org.mobicents.ha.javax.sip.REPLICATION_STRATEGY", 
					replicationStrategy.toString());
	        properties.setProperty(
					"org.mobicents.ha.javax.sip.CACHE_CLASS_NAME",
					"org.mobicents.ha.javax.sip.cache.infinispan.InfinispanCache");
			properties.setProperty(
					"org.mobicents.ha.javax.sip.HAZELCAST_INSTANCE_NAME",
					cacheName);
			properties.setProperty("org.mobicents.ha.javax.sip.REPLICATE_APPLICATION_DATA", "true");

			try {
				// Create SipStack object
				sipStack = sipFactory.createSipStack(properties);
				
			} catch (PeerUnavailableException e) {
				// could not find
				// gov.nist.jain.protocol.ip.sip.SipStackImpl
				// in the classpath
				e.printStackTrace();
				System.err.println(e.getMessage());
				if (e.getCause() != null)
					e.getCause().printStackTrace();
				System.exit(0);
			}

			try {
				headerFactory = sipFactory.createHeaderFactory();
				addressFactory = sipFactory.createAddressFactory();
				messageFactory = sipFactory.createMessageFactory();
				ListeningPoint lp = sipStack.createListeningPoint(myAddress,
						myPort, ListeningPoint.UDP);

				sipProvider = sipStack.createSipProvider(lp);
				sipProvider.addSipListener(this);
				sipStack.start();
				
			} catch (Exception ex) {
				ex.printStackTrace();
				fail("Shootme: unexpected exception");
			}
		}

		public void processIOException(IOExceptionEvent exceptionEvent) {
			fail("IOException");

		}

		public void processTransactionTerminated(
				TransactionTerminatedEvent transactionTerminatedEvent) {
			if (transactionTerminatedEvent.isServerTransaction())
				System.out.println("Shootme: transaction terminated -> " 
						+ transactionTerminatedEvent.getServerTransaction().getBranchId());
			else
				System.out.println("Shootme: transaction terminated -> " 
						+ transactionTerminatedEvent.getClientTransaction().getBranchId());
		}

		public void processDialogTerminated(
				DialogTerminatedEvent dialogTerminatedEvent) {
			dialogRemoved = true;
			System.out.println("Shootme: dialog terminated -> " 
					+ dialogTerminatedEvent.getDialog().getDialogId());
		}

		public void stop() {
			sipStack.stop();
		}

		public void checkState() {
			if (firstTxComplete)
				System.out.println("Shootme: first transaction completed. State OK.");
			else
				fail("firstTxComplete " + firstTxComplete);
		}
		
		public void checkDialogRemoved() {
			if (!dialogRemoved)
				fail("dialog not removed");
		}
		
		public void checkByeState() {
			if (byeComplete)
				System.out.println("Shootme: BYE completed. State OK.");
			else
				fail("No BYE response received, byeComplete=" + byeComplete);
		}
		
		public void recoverDialog(String id) {
			dialog = ((SipStackImpl)sipStack).getDialog(id);
		}
		
		public void sendBye() {
			System.out.println("Shootme: sending bye");
			new Timer().schedule(new ByeTask(), 0);
		}
		
		public void send200Invite() {
			System.out.println("Shootme: sending 200 OK");
			String txId = (String)dialog.getApplicationData();
			inviteTid = (ServerTransaction)((ClusteredSipStack)sipStack).findTransaction(txId, true);
			try {
				okResponse = messageFactory.createResponse(Response.OK, inviteTid.getRequest());
				Address address = addressFactory.createAddress("Shootme <sip:"+ myAddress + ":" + myPort + ">");
				ContactHeader contactHeader = headerFactory
						.createContactHeader(address);
				okResponse.addHeader(contactHeader);
				ToHeader toHeader = (ToHeader) okResponse.getHeader(ToHeader.NAME);
				toHeader.setTag("4321"); // Application is supposed to set.
				okResponse.addHeader(contactHeader);
				
				sendInviteOK();
				
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}
	}

	class Shootist implements SipListener {

		private SipProvider sipProvider;

		private SipStack sipStack;
		private AddressFactory addressFactory;
		private MessageFactory messageFactory;
		private HeaderFactory headerFactory;

		private ContactHeader contactHeader;

		private ListeningPoint udpListeningPoint;

		private ClientTransaction inviteTid;

		private Dialog dialog;
		
		private String recordRouteAddr;

		private boolean byeTaskRunning;

		public int myPort = 5050;

		private boolean okToByeReceived;
		// Save the created ACK request, to respond to retransmitted 2xx
		private Request ackRequest;

		private String stackName;

		class ByeTask extends TimerTask {
			
			public void run() {
				try {
					if (dialog != null && dialog.getState() != DialogState.TERMINATED) {
						Request byeRequest = dialog.createRequest(Request.BYE);
						if (byeRequest.getHeader(RouteHeader.NAME) != null) {
							byeRequest.removeHeader(RouteHeader.NAME);
						}
						ClientTransaction ct = sipProvider
								.getNewClientTransaction(byeRequest);
						dialog.sendRequest(ct);
					}
					
				} catch (Exception ex) {
					ex.printStackTrace();
					fail("Unexpected exception ");
				}

			}

		}

		public Shootist(String stackName) {
			this.stackName = stackName;
		}

		public void processRequest(RequestEvent requestReceivedEvent) {
			Request request = requestReceivedEvent.getRequest();
			ServerTransaction serverTransactionId = requestReceivedEvent
					.getServerTransaction();

			/*System.out.println("\n\nRequest " + request.getMethod()
					+ " received at " + sipStack.getStackName()
					+ " with server transaction id " + serverTransactionId);
			*/

			// We are the UAC so the only request we get is the BYE.
			if (request.getMethod().equals(Request.BYE))
				processBye(request, serverTransactionId);
			else {
				if (!request.getMethod().equals(Request.ACK)) {
					// not used in basic reinvite
					if (((CSeqHeader) request.getHeader(CSeqHeader.NAME))
							.getSeqNumber() == 1
							&& ((ToHeader) request.getHeader(ToHeader.NAME))
									.getAddress().getURI().toString()
									.contains("ReInviteSubsNotify")) {
						try {
							serverTransactionId.sendResponse(messageFactory
									.createResponse(202, request));
						} catch (Exception e) {
							e.printStackTrace();
							fail("Unxepcted exception ");
						}
					} else {
						processInvite(requestReceivedEvent, serverTransactionId);
					}
				} else {
					if (request.getMethod().equals(Request.ACK)) {

					}
				}
			}

		}

		/**
		 * Process the invite request.
		 */
		public void processInvite(RequestEvent requestEvent,
				ServerTransaction serverTransaction) {
			SipProvider sipProvider = (SipProvider) requestEvent.getSource();
			Request request = requestEvent.getRequest();
			if (!((ToHeader) requestEvent.getRequest().getHeader(ToHeader.NAME))
					.getAddress().getURI().toString().contains("ReInvite")
					&& !((FromHeader) requestEvent.getRequest().getHeader(
							FromHeader.NAME)).getAddress().getURI().toString()
							.contains("LittleGuy")) {
				throw new IllegalStateException(
						"The From and To Headers are reversed !!!!");
			}
			try {
				System.out.println("shootme: got an Invite sending Trying");
				// System.out.println("shootme: " + request);
				Response response = messageFactory.createResponse(
						Response.RINGING, request);
				ServerTransaction st = requestEvent.getServerTransaction();

				if (st == null) {
					st = sipProvider.getNewServerTransaction(request);
				}
				dialog = st.getDialog();
				st.sendResponse(response);

				Thread.sleep(1000);

				Response okResponse = messageFactory.createResponse(
						Response.OK, request);
				Address address = addressFactory.createAddress("Shootme <sip:"
						+ myAddress + ":" + myPort + ">");
				ContactHeader contactHeader = headerFactory
						.createContactHeader(address);
				response.addHeader(contactHeader);
				okResponse.addHeader(contactHeader);
				// this.inviteTid = st;
				// Defer sending the OK to simulate the phone ringing.
				// Answered in 1 second ( this guy is fast at taking calls)
				// this.inviteRequest = request;

				if (inviteTid.getState() != TransactionState.COMPLETED) {
					System.out.println("shootme: Dialog state before 200: "
							+ inviteTid.getDialog().getState());
					st.sendResponse(okResponse);
					System.out.println("shootme: Dialog state after 200: "
							+ inviteTid.getDialog().getState());
				}
			} catch (Exception ex) {
				ex.printStackTrace();
				System.exit(0);
			}
		}

		public void processBye(Request request,	ServerTransaction serverTransactionId) {
			try {
				System.out.println("Shootist:  got a bye .");
				if (serverTransactionId == null) {
					System.out.println("Shootist:  null TID.");
					return;
				}
				Dialog dialog = serverTransactionId.getDialog();
				System.out.println("Dialog State = " + dialog.getState());
				// check route
				if (recordRouteAddr != null) {
					RouteHeader route = (RouteHeader)request.getHeader(RouteHeader.NAME);
					assertNotNull(route);
					assertEquals(recordRouteAddr, route.getAddress().getURI().toString());
				}
				System.out.println("Shootist:  Sending OK.");
				System.out.println("Dialog State = " + dialog.getState());
				Response response = messageFactory.createResponse(200, request);
				serverTransactionId.sendResponse(response);

			} catch (Exception ex) {
				fail("Unexpected exception");

			}
		}

		public void processResponse(ResponseEvent responseReceivedEvent) {
			Response response = (Response) responseReceivedEvent.getResponse();
			ClientTransaction tid = responseReceivedEvent
					.getClientTransaction();
			CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);

			System.out.println("Shootist: response received -> Status Code = "
					+ response.getStatusCode() + " " + cseq);

			if (tid == null) {

				// RFC3261: MUST respond to every 2xx
				if (ackRequest != null && dialog != null) {
					System.out.println("re-sending ACK");
					try {
						dialog.sendAck(ackRequest);
						
					} catch (SipException se) {
						se.printStackTrace();
						fail("Unxpected exception ");
					}
				}
				return;
			}
			System.out.println("Shootist: dialog State is " + tid.getDialog().getState());

			try {
				if (response.getStatusCode() == Response.OK) {
					if (cseq.getMethod().equals(Request.INVITE)) {
						System.out.println("Shootist: dialog after 200 OK  " + dialog);
						System.out.println("Shootist: dialog State after 200 OK  "
								+ dialog.getState());
						Request ackRequest = dialog.createAck(cseq.getSeqNumber());
						if (ackRequest.getHeader(RouteHeader.NAME) != null) {
							ackRequest.removeHeader(RouteHeader.NAME);
						}
						System.out.println("Shootist: sending ACK");
						dialog.sendAck(ackRequest);

					} else if (cseq.getMethod().equals(Request.CANCEL)) {
						if (dialog.getState() == DialogState.CONFIRMED) {
							// oops cancel went in too late. Need to hang up the
							// dialog.
							System.out
									.println("Shootist: sending BYE -- cancel went in too late !!");
							Request byeRequest = dialog
									.createRequest(Request.BYE);
							ClientTransaction ct = sipProvider
									.getNewClientTransaction(byeRequest);
							dialog.sendRequest(ct);

						}

					} else if (cseq.getMethod().equals(Request.BYE)) {
						okToByeReceived = true;
					}
				}
			} catch (Exception ex) {
				ex.printStackTrace();
				System.exit(0);
			}

		}

		public void processTimeout(javax.sip.TimeoutEvent timeoutEvent) {
			fail("Shootist: transaction Timed out");
		}

		public void sendCancel() {
			try {
				System.out.println("Shootist: sending cancel");
				Request cancelRequest = inviteTid.createCancel();
				ClientTransaction cancelTid = sipProvider
						.getNewClientTransaction(cancelRequest);
				cancelTid.sendRequest();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

		public void sendBye() {
			System.out.println("Shootist: sending bye");
			new Timer().schedule(new ByeTask(), 100);
		}

		public void init(String from, String peer) {
			SipFactory sipFactory = null;
			sipStack = null;
			sipFactory = SipFactory.getInstance();
			sipFactory.setPathName("gov.nist");
			Properties properties = new Properties();
			String transport = "udp";
			String peerHostPort = peer;
			properties.setProperty("javax.sip.STACK_NAME", stackName);

			properties.setProperty("gov.nist.javax.sip.DEBUG_LOG",
					"logs/shootistdebug.txt");
			properties.setProperty("gov.nist.javax.sip.SERVER_LOG",
					"logs/shootistlog.xml");

			// Drop the client connection after we are done with the
			// transaction.
			properties.setProperty(
					"gov.nist.javax.sip.CACHE_CLIENT_CONNECTIONS", "false");
			// Set to 0 (or NONE) in your production code for max speed.
			// You need 16 (or TRACE) for logging traces. 32 (or DEBUG) for
			// debug + traces.
			// Your code will limp at 32 but it is best for debugging.
			properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "DEBUG");
			properties.setProperty("org.mobicents.ha.javax.sip.HEARTBEAT_IP", "127.0.0.1");
			properties.setProperty("org.mobicents.ha.javax.sip.HEARTBEAT_PORT","2222");

			try {
				// Create SipStack object
				sipStack = sipFactory.createSipStack(properties);
				
			} catch (PeerUnavailableException e) {
				// could not find gov.nist.jain.protocol.ip.sip.SipStackImpl
				// in the classpath
				e.printStackTrace();
				System.err.println(e.getMessage());
				System.exit(0);
			}

			try {
				headerFactory = sipFactory.createHeaderFactory();
				addressFactory = sipFactory.createAddressFactory();
				messageFactory = sipFactory.createMessageFactory();
				udpListeningPoint = sipStack.createListeningPoint(myAddress,
						myPort, "udp");
				sipProvider = sipStack.createSipProvider(udpListeningPoint);
				Shootist listener = this;
				sipProvider.addSipListener(listener);

				String fromName = from;
				String fromSipAddress = "here.com";
				String fromDisplayName = "The Master Blaster";

				String toSipAddress = "there.com";
				String toUser = "LittleGuy";
				String toDisplayName = "The Little Blister";

				// create From Header
				SipURI fromAddress = addressFactory.createSipURI(fromName,
						fromSipAddress);

				Address fromNameAddress = addressFactory
						.createAddress(fromAddress);
				fromNameAddress.setDisplayName(fromDisplayName);
				FromHeader fromHeader = headerFactory.createFromHeader(
						fromNameAddress, "12345");

				// create To Header
				SipURI toAddress = addressFactory.createSipURI(toUser,
						toSipAddress);
				Address toNameAddress = addressFactory.createAddress(toAddress);
				toNameAddress.setDisplayName(toDisplayName);
				ToHeader toHeader = headerFactory.createToHeader(toNameAddress,
						null);

				// create Request URI
				SipURI requestURI = addressFactory.createSipURI(toUser,
						peerHostPort);

				// Create ViaHeaders
				ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
				String ipAddress = udpListeningPoint.getIPAddress();
				ViaHeader viaHeader = headerFactory.createViaHeader(ipAddress,
						sipProvider.getListeningPoint(transport).getPort(),
						transport, null);

				// Add via headers
				viaHeaders.add(viaHeader);
			
				// Create ContentTypeHeader
				ContentTypeHeader contentTypeHeader = headerFactory
						.createContentTypeHeader("application", "sdp");

				// Create a new CallId header
				CallIdHeader callIdHeader = sipProvider.getNewCallId();

				// Create a new Cseq header
				CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.INVITE);

				// Create a new MaxForwardsHeader
				MaxForwardsHeader maxForwards = headerFactory
						.createMaxForwardsHeader(70);

				// Create the request.
				Request request = messageFactory.createRequest(requestURI,
						Request.INVITE, callIdHeader, cSeqHeader, fromHeader,
						toHeader, viaHeaders, maxForwards);
				// Create contact headers
				String host = myAddress;

				SipURI contactUrl = addressFactory.createSipURI(fromName, host);
				contactUrl.setPort(udpListeningPoint.getPort());
				contactUrl.setLrParam();

				// Create the contact name address.
				SipURI contactURI = addressFactory.createSipURI(fromName, host);
				contactURI.setPort(sipProvider.getListeningPoint(transport)
						.getPort());

				Address contactAddress = addressFactory
						.createAddress(contactURI);

				// Add the contact address.
				contactAddress.setDisplayName(fromName);

				contactHeader = headerFactory
						.createContactHeader(contactAddress);
				request.addHeader(contactHeader);
				
				// Add RecordRoute
				if (recordRouteAddr != null) {
					System.out.println("Shootist: add Record-route: " + recordRouteAddr);
					Address rrAddr = addressFactory.createAddress(recordRouteAddr);
					RecordRouteHeader rr = headerFactory.createRecordRouteHeader(rrAddr);
					request.addHeader(rr);
				}

				String sdpData = "v=0\r\n"
						+ "o=4855 13760799956958020 13760799956958020"
						+ " IN IP4  129.6.55.78\r\n"
						+ "s=mysession session\r\n" + "p=+46 8 52018010\r\n"
						+ "c=IN IP4  129.6.55.78\r\n" + "t=0 0\r\n"
						+ "m=audio 6022 RTP/AVP 0 4 18\r\n"
						+ "a=rtpmap:0 PCMU/8000\r\n"
						+ "a=rtpmap:4 G723/8000\r\n"
						+ "a=rtpmap:18 G729A/8000\r\n" + "a=ptime:20\r\n";
				byte[] contents = sdpData.getBytes();

				request.setContent(contents, contentTypeHeader);

				// Create the client transaction.
				inviteTid = sipProvider.getNewClientTransaction(request);
				
				// send the request out.
				inviteTid.sendRequest();

				dialog = inviteTid.getDialog();

			} catch (Exception ex) {
				System.out.println(ex.getMessage());
				ex.printStackTrace();
				fail("Unxpected exception ");
			}
		}

		public void processIOException(IOExceptionEvent exceptionEvent) {
			System.out.println("IOException happened for "
					+ exceptionEvent.getHost() + " port = "
					+ exceptionEvent.getPort());

		}

		public void processTransactionTerminated(
				TransactionTerminatedEvent transactionTerminatedEvent) {
		}

		public void processDialogTerminated(
				DialogTerminatedEvent dialogTerminatedEvent) {
		}

		public void stop() {
			sipStack.stop();
		}

		/**
		 * Checks Bye OK successfully received
		 */
		public void checkState() {
			if (okToByeReceived) {
				System.out.println("Shootist call terminated successfully");
			} else {
				fail("No response to Bye received");
			}
		}
		
		public void addRecordRoute(String rr) {
			recordRouteAddr = rr;
		}
	}

	/**
	 * SHOTIST1           SHOOTME1         SHOOTME2 
	 * INVITE ----------------> 
	 * 	 <---------------- 180 OK
	 *              stop #1 & create #2 
	 * 	 <--------------------------------- 200 OK
	 * ACK ------------------------------------> 
	 * BYE ------------------------------------> 
	 *   <--------------------------------- 200 OK
	 */
	public void testEarlyDialog() throws Exception {
		
		BasicConfigurator.configure();
		Logger.getRootLogger().removeAllAppenders();
		
		System.out.println("\r\n>>>>>>>>>> Early dialog <<<<<<<<<<<\r\n");

		// create invite initiator
		Shootist shootist = new Shootist("shootist3");
		
		// create and start first receiver
		Shootme shootme1 = new Shootme("shootme5", "jain-sip-ha3", 5085, false, ReplicationStrategy.EarlyDialog);
		System.out.println(">>>> Start Shootme1");
		Thread.sleep(1000);
		shootme1.init();
		
		// get dialogs cache created by shootme1
		DefaultCacheManager cm = CacheManagerHolder.getManager("META-INF/cache-configuration.xml");
		Cache<String, Object> dialogs = cm.getCache("cache.dialogs");
		Cache<String, Object> serverTXs = cm.getCache("cache.serverTX");
		Cache<String, Object> appData = cm.getCache("cache.appdata");

		// start test sending an invite
		System.out.println(">>>> Start Shootist");
		shootist.init("shootist", "127.0.0.1:5085"); // shoot peer1

		Thread.sleep(2000);
		
		// compare dialog metadata with cache metadata
		String dialogId = shootme1.dialog.getDialogId();
		Map<String, Object> cachedMetaData = (Map<String, Object>) dialogs.get(shootme1.dialog.getDialogId());
		Object data = appData.get(shootme1.dialog.getDialogId());

		assertNotNull(dialogId);
		assertNotNull(cachedMetaData);
		
		assertEquals(((SIPDialog)shootme1.dialog).getState(), DialogState.EARLY);
		assertEquals(((SIPDialog)shootme1.dialog).getState().getValue(), 
				cachedMetaData.get(AbstractHASipDialog.DIALOG_STATE));
		
		String txId = (String)shootme1.dialog.getApplicationData();
		assertNotNull(txId);
		assertNotNull(data);
		assertEquals(txId, (String)data);
		
		// kill shootme
		shootme1.stop();
		shootme1 = null;
		
		System.out.println(">>>> Kill Shootme1. Dialog cached succesfully.");
		
		Thread.sleep(2000);
		
		// ---- Recover dialog in new shootme instance ----
		Shootme shootme2 = new Shootme("shootme6", "jain-sip-ha3", 5085, false, ReplicationStrategy.EarlyDialog);

		// start shootme2
		System.out.println(">>>> Start Shootme2");
		shootme2.init();

		Thread.sleep(1000);

		shootme2.recoverDialog(dialogId);
		String txId2 = (String)shootme2.dialog.getApplicationData();
		assertNotNull(txId2);
		assertEquals(txId, txId2);
		assertEquals(((SIPDialog)shootme2.dialog).getState(), DialogState.EARLY);
		assertEquals(((SIPDialog)shootme2.dialog).getState().getValue(), 
				cachedMetaData.get(AbstractHASipDialog.DIALOG_STATE));
		
		shootme2.send200Invite();
		
		Thread.sleep(1000);
		
		shootme2.checkState();

		Thread.sleep(1000);
		
		shootist.sendBye();

		Thread.sleep(2000);

		shootist.checkState();
		
		// wait for dialog and transaction removal
		System.out.println(">>>> Wait for dialog to terminate on Shootme2");
		Thread.sleep(7000);
		shootme2.checkDialogRemoved();
		assertNull(dialogs.get(dialogId));
		assertNull(serverTXs.get(txId));
		
		System.out.println(">>>> Call recovered succesfully.");

		// clean resources
		shootist.stop();
		shootme2.stop();
		shootme2 = null;
		shootist = null;
		Thread.sleep(2000);
	}


	/**
	 * SHOTIST1           SHOOTME1         SHOOTME2 
	 * INVITE ----------------> 
	 * 	 <---------------- 180 OK
	 * 	 <---------------- 200 OK
	 * ACK -------------------> 
	 * BYE -------------------> 
	 *   <---------------- 200 OK
	 *                        stop #1 & create #2
	 *                               (terminate&release) 
	 */
	public void testTerminatedDialog() throws Exception {
		
		BasicConfigurator.configure();
		Logger.getRootLogger().removeAllAppenders();
		
		System.out.println("\r\n>>>>>>>>>> Terminated dialog <<<<<<<<<<<\r\n");

		// create invite initiator
		Shootist shootist = new Shootist("shootist4");
		
		// create and start first receiver
		Shootme shootme1 = new Shootme("shootme7", "jain-sip-ha4", 5080, true, ReplicationStrategy.EarlyDialog);
		System.out.println(">>>> Start Shootme1");
		Thread.sleep(1000);
		shootme1.init();
		
		DefaultCacheManager cm = CacheManagerHolder.getManager("META-INF/cache-configuration.xml");
		Cache<String, Object> dialogs = cm.getCache("cache.dialogs");
		
		// start test sending an invite
		System.out.println(">>>> Start Shootist");
		shootist.init("shootist", "127.0.0.1:5080"); // shoot peer1

		Thread.sleep(2000);
		
		// hangup
		shootist.sendBye();
		Thread.sleep(1000);
		shootist.checkState();

		// check dialog state
		assertNotNull(shootme1.dialog);
		String dialogId = shootme1.dialog.getDialogId();
		assertNotNull(dialogId);
		assertEquals(shootme1.dialog.getState(), DialogState.TERMINATED);
		
		// kill shootme
		shootme1.stop();
		shootme1 = null;
		System.out.println(">>>> Kill Shootme1. Session disconnected successfully.");
		
		Thread.sleep(1000);
		
		// ---- Recover dialog in new shootme instance ----
		Shootme shootme2 = new Shootme("shootme8", "jain-sip-ha4", 5080, false, ReplicationStrategy.EarlyDialog);

		// start shootme2
		System.out.println(">>>> Start Shootme2");
		shootme2.init();
		Thread.sleep(1000);
		
		// check dialog metada
		Map<String, Object> cachedMetaData = (Map<String, Object>) dialogs.get(dialogId);
		assertNotNull(cachedMetaData);
		assertEquals(cachedMetaData.get(AbstractHASipDialog.DIALOG_STATE), DialogState.TERMINATED.getValue());
		
		// recover dialog from cache
		shootme2.recoverDialog(dialogId);
		
		// check dialog state
		assertNotNull(shootme2.dialog);
		assertEquals(((SIPDialog)shootme2.dialog).getState(), DialogState.TERMINATED);
		assertEquals(((SIPDialog)shootme2.dialog).getState().getValue(), 
				cachedMetaData.get(AbstractHASipDialog.DIALOG_STATE));
				
		// wait for dialog and transaction removal
		System.out.println(">>>> Wait for dialog to terminate and clear from cache");
		Thread.sleep(12000);
		shootme2.checkDialogRemoved();
		
		assertNull(dialogs.get(dialogId));
		
		System.out.println(">>>> Dialog cleared succesfully.");

		// clean resources
		shootist.stop();
		shootme2.stop();
		shootme2 = null;
		shootist = null;
		Thread.sleep(2000);
	}

}
