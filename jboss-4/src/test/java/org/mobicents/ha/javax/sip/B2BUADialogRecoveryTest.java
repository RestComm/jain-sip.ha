package org.mobicents.ha.javax.sip;

import gov.nist.javax.sip.stack.SIPDialog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
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
import javax.sip.header.EventHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.Header;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import junit.framework.TestCase;
/**
 * This test aims to test Mobicents Jain Sip failover recovery.
 * Issue 1407 http://code.google.com/p/mobicents/issues/detail?id=1407
 * 
 * @author <A HREF="mailto:jean.deruelle@gmail.com">Jean Deruelle</A>
 *
 */
public class B2BUADialogRecoveryTest extends TestCase {

	public static final String IP_ADDRESS = "192.168.0.11";
	
    public static final int BALANCER_PORT = 5050;

    private static AddressFactory addressFactory;

    private static MessageFactory messageFactory;

    private static HeaderFactory headerFactory;


    Shootist shootist;

    Shootme shootme;
    
    SimpleB2BUA b2buaNode1;
    
    SimpleB2BUA b2buaNode2;

    class Shootme implements SipListener {


        private SipStack sipStack;

        private static final String myAddress = IP_ADDRESS;

        private String stackName;

        public int myPort = 5070;

        protected ServerTransaction inviteTid;

        private Response okResponse;

        private Request inviteRequest;

        private Dialog dialog;

        public boolean callerSendsBye = true;

        private  SipProvider sipProvider;

        private boolean byeTaskRunning;

		private boolean secondReinviteSent;

		private boolean firstReinviteSent;

		private boolean firstTxComplete;
		private boolean firstReInviteComplete;
		private boolean secondReInviteComplete;
		private boolean byeReceived;
		private boolean subscribeTxComplete;
		private boolean notifyTxComplete;

        public Shootme(String stackName, int myPort, boolean callerSendsBye) {
            this.stackName = stackName;
            this.myPort = myPort;
            this.callerSendsBye = callerSendsBye;
            System.setProperty("jgroups.bind_addr", IP_ADDRESS);
            System.setProperty("java.net.preferIPv4Stack", "true");
        }

        class ByeTask  extends TimerTask {
            Dialog dialog;
            public ByeTask(Dialog dialog)  {
                this.dialog = dialog;
            }
            public void run () {
                try {
                   Request byeRequest = this.dialog.createRequest(Request.BYE);
                   ClientTransaction ct = sipProvider.getNewClientTransaction(byeRequest);
                   dialog.sendRequest(ct);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    fail("Unexpected exception ");
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

		private static final long TIMEOUT = 10000;



        public void processRequest(RequestEvent requestEvent) {
            Request request = requestEvent.getRequest();
            ServerTransaction serverTransactionId = requestEvent
                    .getServerTransaction();

            System.out.println("\n\nRequest " + request.getMethod()
                    + " received at " + sipStack.getStackName()
                    + " with server transaction id " + serverTransactionId);

            if (request.getMethod().equals(Request.INVITE)) {
                processInvite(requestEvent, serverTransactionId);
            } else if (request.getMethod().equals(Request.ACK)) {
                processAck(requestEvent, serverTransactionId);
            } else if (request.getMethod().equals(Request.BYE)) {
                processBye(requestEvent, serverTransactionId);
            } else if (request.getMethod().equals(Request.SUBSCRIBE)) {
                processSubscribe(requestEvent, serverTransactionId);
            } else if (request.getMethod().equals(Request.CANCEL)) {
                processCancel(requestEvent, serverTransactionId);
            } else {
                try {
                    serverTransactionId.sendResponse( messageFactory.createResponse( 202, request ) );

                    // send one back
                    SipProvider prov = (SipProvider) requestEvent.getSource();
                    Request refer = requestEvent.getDialog().createRequest("REFER");
                    requestEvent.getDialog().sendRequest( prov.getNewClientTransaction(refer) );

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
        	CSeqHeader cSeqHeader = (CSeqHeader)responseEvent.getResponse().getHeader(CSeqHeader.NAME);
        	try {
        		if(responseEvent.getResponse().getStatusCode() >= 200 && cSeqHeader.getMethod().equalsIgnoreCase(Request.INVITE)) {
	        		Request ackRequest = dialog.createAck(cSeqHeader.getSeqNumber());
	        		int port = 5080;
	                if(firstReinviteSent) {
	                	port = 5081;
	                	firstReinviteSent = false;
	                	firstReInviteComplete = true;
	                }
	                if(secondReinviteSent) {
	                	secondReInviteComplete = true;
	                }
	        		System.out.println("Sending ACK");
	        		((SipURI)ackRequest.getRequestURI()).setPort(port);
					dialog.sendAck(ackRequest);
															
					if(!secondReinviteSent) {
						Thread.sleep(2000);
						Request request = dialog.createRequest("INVITE");
		                ((SipURI)request.getRequestURI()).setPort(5080);
		                final ClientTransaction ct = sipProvider.getNewClientTransaction(request);
		                dialog.sendRequest(ct);
		                secondReinviteSent = true;
					}
        		} else if(responseEvent.getResponse().getStatusCode() >= 200 && cSeqHeader.getMethod().equalsIgnoreCase(Request.NOTIFY)) {
        			notifyTxComplete = true;
        			Thread.sleep(5000);
        			 Request request = dialog.createRequest("INVITE");                
                     ((SipURI)request.getRequestURI()).setPort(5081);                
                     final ClientTransaction ct = sipProvider.getNewClientTransaction(request);
                     firstReinviteSent = true;
                     dialog.sendRequest(ct); 
        		}
			} catch (SipException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
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
                System.out.println("shootme: got an ACK! ");
                System.out.println("Dialog State = " + dialog.getState());
                firstTxComplete = true;     
                
                // used in basic reinvite
                if(!firstReinviteSent && !((FromHeader)requestEvent.getRequest().getHeader(FromHeader.NAME)).getAddress().getURI().toString().contains("ReInviteSubsNotify")) {
					Thread.sleep(5000);
        			 Request request = dialog.createRequest("INVITE");                
                     ((SipURI)request.getRequestURI()).setPort(5081);                
                     final ClientTransaction ct = sipProvider.getNewClientTransaction(request);
                     firstReinviteSent = true;
                     dialog.sendRequest(ct); 
				}
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
                System.out.println("shootme: got an Invite sending Trying");
                // System.out.println("shootme: " + request);
                Response response = messageFactory.createResponse(Response.RINGING,
                        request);
                ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
                toHeader.setTag("4321"); // Application is supposed to set.
                
                ServerTransaction st = requestEvent.getServerTransaction();

                if (st == null) {
                    st = sipProvider.getNewServerTransaction(request);
                }
                dialog = st.getDialog();

                st.sendResponse(response);

                Thread.sleep(1000);
                
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
                this.inviteTid = st;
                // Defer sending the OK to simulate the phone ringing.
                // Answered in 1 second ( this guy is fast at taking calls)
                this.inviteRequest = request;

                sendInviteOK();
            } catch (Exception ex) {
                ex.printStackTrace();
                System.exit(0);
            }
        }

        private void sendInviteOK() {
            try {
                if (inviteTid.getState() != TransactionState.COMPLETED) {
                    System.out.println("shootme: Dialog state before 200: "
                            + inviteTid.getDialog().getState());
                    inviteTid.sendResponse(okResponse);
                    System.out.println("shootme: Dialog state after 200: "
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
            SipProvider sipProvider = (SipProvider) requestEvent.getSource();
            Request request = requestEvent.getRequest();
            Dialog dialog = requestEvent.getDialog();
            System.out.println("local party = " + dialog.getLocalParty());
            try {
                System.out.println("shootme:  got a bye sending OK.");
                Response response = messageFactory.createResponse(200, request);
                serverTransactionId.sendResponse(response);
                System.out.println("Dialog State is "
                        + serverTransactionId.getDialog().getState());
                byeReceived = true;
            } catch (Exception ex) {
                ex.printStackTrace();
                System.exit(0);

            }
        }
        
        /**
         * Process the bye request.
         */
        public void processSubscribe(RequestEvent requestEvent,
                ServerTransaction serverTransactionId) {
            SipProvider sipProvider = (SipProvider) requestEvent.getSource();
            Request request = requestEvent.getRequest();
            Dialog dialog = requestEvent.getDialog();   
            
            try {
                ServerTransaction st = requestEvent.getServerTransaction();

                if (st == null) {
                    st = sipProvider.getNewServerTransaction(request);
                }
                System.out.println("shootme:  got a subscribe sending OK.");
                Response response = messageFactory.createResponse(200, request);
    			response.addHeader(headerFactory.createHeader(ExpiresHeader.NAME, "3600"));
                st.sendResponse(response);
                System.out.println("Dialog State is "
                        + st.getDialog().getState());
                subscribeTxComplete = true;
                
                Thread.sleep(5000);
                
                Request notify = st.getDialog().createRequest(Request.NOTIFY);
                notify.addHeader(headerFactory.createHeader(SubscriptionStateHeader.NAME, SubscriptionStateHeader.ACTIVE));
                notify.addHeader(headerFactory.createHeader(EventHeader.NAME, "presence"));
                ((SipURI)notify.getRequestURI()).setUser(null);
                ((SipURI)notify.getRequestURI()).setHost(IP_ADDRESS);
                ((SipURI)notify.getRequestURI()).setPort(5080);
                st.getDialog().sendRequest(sipProvider.getNewClientTransaction(notify));
            } catch (Exception ex) {
                ex.printStackTrace();
                System.exit(0);

            }
        }

        public void processCancel(RequestEvent requestEvent,
                ServerTransaction serverTransactionId) {
            SipProvider sipProvider = (SipProvider) requestEvent.getSource();
            Request request = requestEvent.getRequest();
            try {
                System.out.println("shootme:  got a cancel.");
                if (serverTransactionId == null) {
                    System.out.println("shootme:  null tid.");
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
            System.out.println("state = " + transaction.getState());
            System.out.println("dialog = " + transaction.getDialog());
            System.out.println("dialogState = "
                    + transaction.getDialog().getState());
            System.out.println("Transaction Time out");
        }

        public void init() {
            SipFactory sipFactory = null;
            sipStack = null;
            sipFactory = SipFactory.getInstance();
            sipFactory.setPathName("gov.nist");
            Properties properties = new Properties();
            properties.setProperty("javax.sip.STACK_NAME", stackName);
            //properties.setProperty("javax.sip.OUTBOUND_PROXY", Integer
            //                .toString(BALANCER_PORT));
            // You need 16 for logging traces. 32 for debug + traces.
            // Your code will limp at 32 but it is best for debugging.
            properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
            properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "logs/" +
                    stackName + "debug.txt");
            properties.setProperty("gov.nist.javax.sip.SERVER_LOG", "logs/" +
                    stackName + "log.xml");

            try {
                // Create SipStack object
                sipStack = sipFactory.createSipStack(properties);
                System.out.println("sipStack = " + sipStack);
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

                Shootme listener = this;

                sipProvider = sipStack.createSipProvider(lp);
                System.out.println("udp provider " + sipProvider);
                sipProvider.addSipListener(listener);
//                if(dialogs != null) {
//                    Collection<Dialog> serializedDialogs = simulateDialogSerialization(dialogs);
//                    for (Dialog dialog : serializedDialogs) {
//                        ((SIPDialog)dialog).setSipProvider((SipProviderImpl)sipProvider);
//                        ((SipStackImpl)sipStack).putDialog((SIPDialog)dialog);
//                    }
//                    this.dialog = (SIPDialog)serializedDialogs.iterator().next();
//                }
                sipStack.start();
                if(!callerSendsBye && this.dialog != null) {
                    try {
                       Request byeRequest = this.dialog.createRequest(Request.BYE);
                       ClientTransaction ct = sipProvider.getNewClientTransaction(byeRequest);
                       System.out.println("sending BYE " + byeRequest);
                       dialog.sendRequest(ct);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        fail("Unexpected exception ");
                    }
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
                ex.printStackTrace();
                fail("Unexpected exception");
            }
        }


        private Collection<Dialog> simulateDialogSerialization(
                Collection<Dialog> dialogs) {
            Collection<Dialog> serializedDialogs = new ArrayList<Dialog>();
            for (Dialog dialog : dialogs) {
                try{
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ObjectOutputStream out = new ObjectOutputStream(baos);
                    out.writeObject(dialog);
                    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                    ObjectInputStream in =new ObjectInputStream(bais);
                    SIPDialog serializedDialog = (SIPDialog)in.readObject();
                    serializedDialogs.add(serializedDialog);
                    out.close();
                    in.close();
                    baos.close();
                    bais.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
            return serializedDialogs;
        }

        public void processIOException(IOExceptionEvent exceptionEvent) {
            System.out.println("IOException");

        }

        public void processTransactionTerminated(
                TransactionTerminatedEvent transactionTerminatedEvent) {
            if (transactionTerminatedEvent.isServerTransaction())
                System.out.println("Transaction terminated event recieved"
                        + transactionTerminatedEvent.getServerTransaction());
            else
                System.out.println("Transaction terminated "
                        + transactionTerminatedEvent.getClientTransaction());

        }

        public void processDialogTerminated(
                DialogTerminatedEvent dialogTerminatedEvent) {
            System.out.println("Dialog terminated event recieved");
            Dialog d = dialogTerminatedEvent.getDialog();
            System.out.println("Local Party = " + d.getLocalParty());

        }

        public void stop() {
            stopSipStack(sipStack, this);
        }

		public void checkState(boolean reinviteSubsNotify) {
			if(reinviteSubsNotify) {
				if(firstTxComplete && subscribeTxComplete && notifyTxComplete  && firstReInviteComplete && secondReInviteComplete && byeReceived) {
					System.out.println("shootme state OK " );
				} else {
					fail("firstTxComplete " + firstTxComplete + " && subscribeTxComplete " + subscribeTxComplete + " && notifyComplete " + notifyTxComplete + " && firstReInviteComplete " + firstReInviteComplete + "&& secondReInviteComplete " + secondReInviteComplete + " && byeReceived " + byeReceived);
				}
			} else {
				if(firstTxComplete && firstReInviteComplete && secondReInviteComplete && byeReceived) {
					System.out.println("shootme state OK " );
				} else {
					fail("firstTxComplete " + firstTxComplete + " && firstReInviteComplete " + firstReInviteComplete + "&& secondReInviteComplete " + secondReInviteComplete + " && byeReceived " + byeReceived);
				}
			}
		}

    }
    class Shootist implements SipListener {

        private  SipProvider sipProvider;

        private SipStack sipStack;

        private ContactHeader contactHeader;

        private ListeningPoint udpListeningPoint;

        private ClientTransaction inviteTid;

        private Dialog dialog;

        private boolean byeTaskRunning;

        public boolean callerSendsBye = true;
        
        private static final String myAddress = IP_ADDRESS;

        public int myPort = 5060;

		private boolean sendSubscribe ;
        
        private boolean firstTxComplete;
		private boolean firstReInviteComplete;
		private boolean secondReInviteComplete;
		private boolean thirdReInviteComplete;
		private boolean okToByeReceived;
		// Save the created ACK request, to respond to retransmitted 2xx
        private Request ackRequest;

		private boolean notifyTxComplete;
		private boolean subscribeTxComplete;

		private String stackName;
        
        class ByeTask  extends TimerTask {
            Dialog dialog;
            public ByeTask(Dialog dialog)  {
                this.dialog = dialog;
            }
            public void run () {
                try {
                   Request byeRequest = this.dialog.createRequest(Request.BYE);
                   ClientTransaction ct = sipProvider.getNewClientTransaction(byeRequest);
                   dialog.sendRequest(ct);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    fail("Unexpected exception ");
                }

            }

        }

        public Shootist(String stackName, boolean callerSendsBye) {
            this.callerSendsBye = callerSendsBye;
            this.stackName = stackName;
        }

        public void processRequest(RequestEvent requestReceivedEvent) {
            Request request = requestReceivedEvent.getRequest();
            ServerTransaction serverTransactionId = requestReceivedEvent
                    .getServerTransaction();

            System.out.println("\n\nRequest " + request.getMethod()
                    + " received at " + sipStack.getStackName()
                    + " with server transaction id " + serverTransactionId);

            // We are the UAC so the only request we get is the BYE.
            if (request.getMethod().equals(Request.BYE))
                processBye(request, serverTransactionId);
            else if(request.getMethod().equals(Request.NOTIFY)) {
            	try {
					serverTransactionId.sendResponse( messageFactory.createResponse(200,request) );
					notifyTxComplete = true;
				} catch (Exception e) {					
					e.printStackTrace();
					fail("Unxepcted exception ");
				}
            } else {
            	if(!request.getMethod().equals(Request.ACK)) {
            		// not used in basic reinvite
            		if(((CSeqHeader) request.getHeader(CSeqHeader.NAME)).getSeqNumber() == 1 && ((ToHeader)request.getHeader(ToHeader.NAME)).getAddress().getURI().toString().contains("ReInviteSubsNotify")) {
		                try {
		                    serverTransactionId.sendResponse( messageFactory.createResponse(202,request) );
		                } catch (Exception e) {
		                    e.printStackTrace();
		                    fail("Unxepcted exception ");
		                }
	          		} else {
            			processInvite(requestReceivedEvent, serverTransactionId);
            		}
            	} else {
            		if(request.getMethod().equals(Request.ACK)) {
	            		long cseq = ((CSeqHeader) request.getHeader(CSeqHeader.NAME)).getSeqNumber();
	            		switch ((int) cseq) {
						case 1:
							firstReInviteComplete = true;
							// not used in basic reinvite
							if(sendSubscribe) {
								try {
									Request subscribe = requestReceivedEvent.getDialog().createRequest(Request.SUBSCRIBE);
									requestReceivedEvent.getDialog().sendRequest(sipProvider.getNewClientTransaction(subscribe));
								} catch (SipException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
									fail("Unxepcted exception ");
								}
							}
							
							break;
						case 2:
							secondReInviteComplete = true;
							break;	
						case 3:
							secondReInviteComplete = true;
							break;
							
						case 4:
							thirdReInviteComplete = true;
							break;
	
						default:
							break;
						}
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
            if(!((ToHeader)requestEvent.getRequest().getHeader(ToHeader.NAME)).getAddress().getURI().toString().contains("ReInvite") && !((FromHeader)requestEvent.getRequest().getHeader(FromHeader.NAME)).getAddress().getURI().toString().contains("LittleGuy")) {
            	throw new IllegalStateException("The From and To Headers are reversed !!!!");
            }
            try {
                System.out.println("shootme: got an Invite sending Trying");
                // System.out.println("shootme: " + request);
                Response response = messageFactory.createResponse(Response.RINGING,
                        request);
                ServerTransaction st = requestEvent.getServerTransaction();

                if (st == null) {
                    st = sipProvider.getNewServerTransaction(request);
                }
                dialog = st.getDialog();
                st.sendResponse(response);

                Thread.sleep(1000);
                
                Response okResponse = messageFactory.createResponse(Response.OK,
                        request);
                Address address = addressFactory.createAddress("Shootme <sip:"
                        + myAddress + ":" + myPort + ">");
                ContactHeader contactHeader = headerFactory
                        .createContactHeader(address);
                response.addHeader(contactHeader);
                okResponse.addHeader(contactHeader);
//                this.inviteTid = st;
                // Defer sending the OK to simulate the phone ringing.
                // Answered in 1 second ( this guy is fast at taking calls)
//                this.inviteRequest = request;

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

        public void processBye(Request request,
                ServerTransaction serverTransactionId) {
            try {
                System.out.println("shootist:  got a bye .");
                if (serverTransactionId == null) {
                    System.out.println("shootist:  null TID.");
                    return;
                }
                Dialog dialog = serverTransactionId.getDialog();
                System.out.println("Dialog State = " + dialog.getState());
                Response response = messageFactory.createResponse(200, request);
                serverTransactionId.sendResponse(response);
                System.out.println("shootist:  Sending OK.");
                System.out.println("Dialog State = " + dialog.getState());

            } catch (Exception ex) {
                fail("Unexpected exception");

            }
        }

        public void processResponse(ResponseEvent responseReceivedEvent) {        	
            System.out.println("Got a response");
            Response response = (Response) responseReceivedEvent.getResponse();
            ClientTransaction tid = responseReceivedEvent.getClientTransaction();
            CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);

            System.out.println("Response received : Status Code = "
                    + response.getStatusCode() + " " + cseq);

            if(cseq.getMethod().equalsIgnoreCase(Request.SUBSCRIBE)) {
            	subscribeTxComplete = true;
            	return;
            }
            
            if (tid == null) {

                // RFC3261: MUST respond to every 2xx
                if (ackRequest!=null && dialog!=null) {
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
            // If the caller is supposed to send the bye
            if ( callerSendsBye && !byeTaskRunning) {
                byeTaskRunning = true;
                new Timer().schedule(new ByeTask(dialog), 50000) ;
            }
            System.out.println("transaction state is " + tid.getState());
            System.out.println("Dialog = " + tid.getDialog());
            System.out.println("Dialog State is " + tid.getDialog().getState());

            assertSame("Checking dialog identity",tid.getDialog(), this.dialog);

            try {
                if (response.getStatusCode() == Response.OK) {
                    if (cseq.getMethod().equals(Request.INVITE)) {
                        System.out.println("Dialog after 200 OK  " + dialog);
                        System.out.println("Dialog State after 200 OK  " + dialog.getState());
                        Request ackRequest = dialog.createAck(cseq.getSeqNumber());
                        System.out.println("Sending ACK");
                        dialog.sendAck(ackRequest);

                        firstTxComplete = true;
                        // JvB: test REFER, reported bug in tag handling
//                      Request referRequest = dialog.createRequest("REFER");
//                      //simulating a balancer that will forward the request to the recovery node
//                      SipURI referRequestURI = addressFactory.createSipURI(null, "127.0.0.1:5080");
//                      referRequest.setRequestURI(referRequestURI);
//                      dialog.sendRequest(  sipProvider.getNewClientTransaction(referRequest));
//
                    } else if (cseq.getMethod().equals(Request.CANCEL)) {
                        if (dialog.getState() == DialogState.CONFIRMED) {
                            // oops cancel went in too late. Need to hang up the
                            // dialog.
                            System.out
                                    .println("Sending BYE -- cancel went in too late !!");
                            Request byeRequest = dialog.createRequest(Request.BYE);
                            ClientTransaction ct = sipProvider
                                    .getNewClientTransaction(byeRequest);
                            dialog.sendRequest(ct);

                        }

                    } else  if (cseq.getMethod().equals(Request.BYE)) {
                    	okToByeReceived = true;
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                System.exit(0);
            }

        }

        public void processTimeout(javax.sip.TimeoutEvent timeoutEvent) {

            System.out.println("Transaction Time out");
        }

        public void sendCancel() {
            try {
                System.out.println("Sending cancel");
                Request cancelRequest = inviteTid.createCancel();
                ClientTransaction cancelTid = sipProvider
                        .getNewClientTransaction(cancelRequest);
                cancelTid.sendRequest();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        public void init(String from) {
            SipFactory sipFactory = null;
            sipStack = null;
            sipFactory = SipFactory.getInstance();
            sipFactory.setPathName("gov.nist");
            Properties properties = new Properties();
            // If you want to try TCP transport change the following to
            String transport = "udp";
            String peerHostPort = IP_ADDRESS + ":" + 5080;
            //properties.setProperty("javax.sip.OUTBOUND_PROXY", peerHostPort + "/"
            //      + transport);
            // If you want to use UDP then uncomment this.
            properties.setProperty("javax.sip.STACK_NAME", stackName);

            // The following properties are specific to nist-sip
            // and are not necessarily part of any other jain-sip
            // implementation.
            // You can set a max message size for tcp transport to
            // guard against denial of service attack.
            properties.setProperty("gov.nist.javax.sip.DEBUG_LOG",
                    "logs/shootistdebug.txt");
            properties.setProperty("gov.nist.javax.sip.SERVER_LOG",
                    "logs/shootistlog.xml");

            // Drop the client connection after we are done with the transaction.
            properties.setProperty("gov.nist.javax.sip.CACHE_CLIENT_CONNECTIONS",
                    "false");
            // Set to 0 (or NONE) in your production code for max speed.
            // You need 16 (or TRACE) for logging traces. 32 (or DEBUG) for debug + traces.
            // Your code will limp at 32 but it is best for debugging.
            properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "DEBUG");

            try {
                // Create SipStack object
                sipStack = sipFactory.createSipStack(properties);
                System.out.println("createSipStack " + sipStack);
            } catch (PeerUnavailableException e) {
                // could not find
                // gov.nist.jain.protocol.ip.sip.SipStackImpl
                // in the classpath
                e.printStackTrace();
                System.err.println(e.getMessage());
                System.exit(0);
            }

            try {
                headerFactory = sipFactory.createHeaderFactory();
                addressFactory = sipFactory.createAddressFactory();
                messageFactory = sipFactory.createMessageFactory();
                udpListeningPoint = sipStack.createListeningPoint(IP_ADDRESS, 5060, "udp");
                sipProvider = sipStack.createSipProvider(udpListeningPoint);
                Shootist listener = this;
                sipProvider.addSipListener(listener);

                String fromName = from;
                String fromSipAddress = "here.com";
                String fromDisplayName = "The Master Blaster";

                String toSipAddress = "there.com";
                String toUser = "LittleGuy";
                String toDisplayName = "The Little Blister";

                // create >From Header
                SipURI fromAddress = addressFactory.createSipURI(fromName,
                        fromSipAddress);

                Address fromNameAddress = addressFactory.createAddress(fromAddress);
                fromNameAddress.setDisplayName(fromDisplayName);
                FromHeader fromHeader = headerFactory.createFromHeader(
                        fromNameAddress, "12345");

                // create To Header
                SipURI toAddress = addressFactory
                        .createSipURI(toUser, toSipAddress);
                Address toNameAddress = addressFactory.createAddress(toAddress);
                toNameAddress.setDisplayName(toDisplayName);
                ToHeader toHeader = headerFactory.createToHeader(toNameAddress,
                        null);

                // create Request URI
                SipURI requestURI = addressFactory.createSipURI(toUser,
                        peerHostPort);

                // Create ViaHeaders

                ArrayList viaHeaders = new ArrayList();
                String ipAddress = udpListeningPoint.getIPAddress();
                ViaHeader viaHeader = headerFactory.createViaHeader(ipAddress,
                        sipProvider.getListeningPoint(transport).getPort(),
                        transport, null);

                // add via headers
                viaHeaders.add(viaHeader);

                // Create ContentTypeHeader
                ContentTypeHeader contentTypeHeader = headerFactory
                        .createContentTypeHeader("application", "sdp");

                // Create a new CallId header
                CallIdHeader callIdHeader = sipProvider.getNewCallId();

                // Create a new Cseq header
                CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L,
                        Request.INVITE);

                // Create a new MaxForwardsHeader
                MaxForwardsHeader maxForwards = headerFactory
                        .createMaxForwardsHeader(70);

                // Create the request.
                Request request = messageFactory.createRequest(requestURI,
                        Request.INVITE, callIdHeader, cSeqHeader, fromHeader,
                        toHeader, viaHeaders, maxForwards);
                // Create contact headers
                String host = IP_ADDRESS;

                SipURI contactUrl = addressFactory.createSipURI(fromName, host);
                contactUrl.setPort(udpListeningPoint.getPort());
                contactUrl.setLrParam();

                // Create the contact name address.
                SipURI contactURI = addressFactory.createSipURI(fromName, host);
                contactURI.setPort(sipProvider.getListeningPoint(transport)
                        .getPort());

                Address contactAddress = addressFactory.createAddress(contactURI);

                // Add the contact address.
                contactAddress.setDisplayName(fromName);

                contactHeader = headerFactory.createContactHeader(contactAddress);
                request.addHeader(contactHeader);

                // You can add extension headers of your own making
                // to the outgoing SIP request.
                // Add the extension header.
                Header extensionHeader = headerFactory.createHeader("My-Header",
                        "my header value");
                request.addHeader(extensionHeader);

                String sdpData = "v=0\r\n"
                        + "o=4855 13760799956958020 13760799956958020"
                        + " IN IP4  129.6.55.78\r\n" + "s=mysession session\r\n"
                        + "p=+46 8 52018010\r\n" + "c=IN IP4  129.6.55.78\r\n"
                        + "t=0 0\r\n" + "m=audio 6022 RTP/AVP 0 4 18\r\n"
                        + "a=rtpmap:0 PCMU/8000\r\n" + "a=rtpmap:4 G723/8000\r\n"
                        + "a=rtpmap:18 G729A/8000\r\n" + "a=ptime:20\r\n";
                byte[] contents = sdpData.getBytes();

                request.setContent(contents, contentTypeHeader);
                // You can add as many extension headers as you
                // want.

                extensionHeader = headerFactory.createHeader("My-Other-Header",
                        "my new header value ");
                request.addHeader(extensionHeader);

                Header callInfoHeader = headerFactory.createHeader("Call-Info",
                        "<http://www.antd.nist.gov>");
                request.addHeader(callInfoHeader);

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
            System.out.println("Transaction terminated event recieved");
        }

        public void processDialogTerminated(
                DialogTerminatedEvent dialogTerminatedEvent) {
            System.out.println("dialogTerminatedEvent");

        }

        public void stop() {
            stopSipStack(sipStack, this);
        }

		public void checkState(boolean reinviteSubsNotify) {
			if(reinviteSubsNotify) {
				if(firstTxComplete && firstReInviteComplete && subscribeTxComplete && notifyTxComplete && secondReInviteComplete && thirdReInviteComplete && okToByeReceived) {
					System.out.println("shootist state OK " );
				} else {
					fail("firstTxComplete " + firstTxComplete + " && firstReInviteComplete  " + firstReInviteComplete + " && subscribeTxComplete " + subscribeTxComplete + " && notifyComplete " + notifyTxComplete + "&& secondReInviteComplete " + secondReInviteComplete + "&& thirdReInviteComplete " + thirdReInviteComplete + " && okToByeReceived " + okToByeReceived);
				}
			} else {
				if(firstTxComplete && firstReInviteComplete && secondReInviteComplete && okToByeReceived) {
					System.out.println("shootist state OK " );
				} else {
					fail("firstTxComplete " + firstTxComplete + " && firstReInviteComplete  " + firstReInviteComplete + " && secondReInviteComplete " + secondReInviteComplete + " && okToByeReceived " + okToByeReceived);
				}
			}
		}

		public void setSendSubscribe(boolean b) {
			sendSubscribe  = b;
		}
    }

    public static void stopSipStack(SipStack sipStack, SipListener listener) {
        Iterator<SipProvider> sipProviderIterator = sipStack.getSipProviders();
        try{
            while (sipProviderIterator.hasNext()) {
                SipProvider sipProvider = sipProviderIterator.next();
                ListeningPoint[] listeningPoints = sipProvider.getListeningPoints();
                for (ListeningPoint listeningPoint : listeningPoints) {
                    sipProvider.removeListeningPoint(listeningPoint);
                    sipStack.deleteListeningPoint(listeningPoint);
                    listeningPoints = sipProvider.getListeningPoints();
                }
                sipProvider.removeSipListener(listener);
                sipStack.deleteSipProvider(sipProvider);
                sipProviderIterator = sipStack.getSipProviders();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cant remove the listening points or sip providers", e);
        }

        sipStack.stop();
        sipStack = null;
    }

    /**
     * UA1			B2BUA (Engine1)			B2BUA (Engine2)			UA2
	 * INVITE (CSeq 1)
	 * ----------------------->
	 * 		
	 * 				INVITE (CSeq 1)
	 * 				------------------------------------------------->
	 * 	INVITE (CSeq 1)
	 * <------------------------
	 * 
	 * SUBSCRIBE(CSeq 2)
	 * ----------------------->
	 * 		
	 * 				SUBSCRIBE (CSeq 2)
	 * 				------------------------------------------------->
	 * 
	 * 									NOTIFY (CSeq 1)
	 *				<-------------------------------------------------
	 * 		NOTIFY (CSeq 2)
	 * <------------------------
	 * 
	 * 											INVITE (CSeq 2)
	 * 								             <---------------------
	 * 					INVITE (CSeq 3)
	 * <------------------------------------------
	 * 									INVITE (CSeq 3)
	 *  				      <----------------------------------------
	 *  	INVITE (CSeq 4)
	 *  <---------------------
	 *  BYE (CSeq 3)
	 *  ----------------------->
	 *  								BYE (CSeq 3)
	 *  						------------------------------------->
     */
    public void testDialogFailoverReInviteSubsNotify() throws Exception {

        shootist = new Shootist("shootist_subsnotify", true);
        shootme = new Shootme("shootme_subsnotify", 5070, true);

        b2buaNode1 = new SimpleB2BUA("b2buaNode1_subsnotify", 5080, IP_ADDRESS);
        Thread.sleep(5000);
        b2buaNode2 = new SimpleB2BUA("b2buaNode2_subsnotify", 5081, IP_ADDRESS);

        shootme.init();
        shootist.setSendSubscribe(true);
        shootist.init("ReInviteSubsNotify");
        
        
        Thread.sleep(60000);
        
        shootme.checkState(true);
        shootist.checkState(true);
        // make sure dialogs are removed on both nodes
        // non regression for Issue 1418
        // http://code.google.com/p/mobicents/issues/detail?id=1418
        assertTrue(b2buaNode1.checkDialogsRemoved());
        assertTrue(b2buaNode2.checkDialogsRemoved());
        
        b2buaNode1.stop();
        b2buaNode2.stop();
        
        shootist.stop();
        shootme.stop();
        Thread.sleep(5000);
    }
    
    /**
     * UA1			B2BUA (Engine1)			B2BUA (Engine2)			UA2
	 * INVITE (CSeq 1)
	 * ----------------------->
	 * 		
	 * 				INVITE (CSeq 1)
	 * 				-------------------------------------------------> 	
	 * 
	 * 											INVITE (CSeq 1)
	 * 								             <---------------------
	 * 					INVITE (CSeq 2)
	 * <------------------------------------------
	 * 									INVITE (CSeq 2)
	 *  				      <----------------------------------------
	 *  	INVITE (CSeq 3)
	 *  <---------------------
	 *  BYE (CSeq 2)
	 *  ----------------------->
	 *  								BYE (CSeq 2)
	 *  						------------------------------------->
     */
    public void testDialogFailoverReInvite() throws Exception {

        shootist = new Shootist("shootist_reinvite", true);
        shootme = new Shootme("shootme_reinvite", 5070, true);

        b2buaNode1 = new SimpleB2BUA("b2buaNode1_reinvite", 5080, IP_ADDRESS);
        Thread.sleep(5000);
        b2buaNode2 = new SimpleB2BUA("b2buaNode2_reinvite", 5081, IP_ADDRESS);

        shootme.init();
        shootist.init("ReInvite");        
        Thread.sleep(60000);
        
        shootme.checkState(false);
        shootist.checkState(false);
        // make sure dialogs are removed on both nodes
        // non regression for Issue 1418
        // http://code.google.com/p/mobicents/issues/detail?id=1418
        assertTrue(b2buaNode1.checkDialogsRemoved());
        assertTrue(b2buaNode2.checkDialogsRemoved());
        
        b2buaNode1.stop();
        b2buaNode2.stop();
        
        shootist.stop();
        shootme.stop();
        Thread.sleep(5000);
    }
}
