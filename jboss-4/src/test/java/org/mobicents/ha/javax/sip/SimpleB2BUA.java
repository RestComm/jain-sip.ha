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

package org.mobicents.ha.javax.sip;

import java.text.ParseException;
import java.util.Iterator;
import java.util.Properties;
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
	
	public SimpleB2BUA(String stackName, int myPort, String ipAddress) throws NumberFormatException, SipException, TooManyListenersException, InvalidArgumentException, ParseException {
		properties = new Properties();
        properties.setProperty("org.mobicents.ha.javax.sip.CACHE_CLASS_NAME", "org.mobicents.ha.javax.sip.cache.JBossTreeSipCache");
        properties.setProperty("javax.sip.STACK_NAME", stackName);
        properties.setProperty(SIP_PORT_BIND, String.valueOf(myPort));
        //properties.setProperty("javax.sip.OUTBOUND_PROXY", Integer
        //                .toString(BALANCER_PORT));
        // You need 16 for logging traces. 32 for debug + traces.
        // Your code will limp at 32 but it is best for debugging.
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
        properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "logs/" +
                stackName + "debug.txt");
        properties.setProperty("gov.nist.javax.sip.SERVER_LOG", "logs/" +
                stackName + "log.xml");
        properties.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");
        properties.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "true");
        properties.setProperty("org.mobicents.ha.javax.sip.REPLICATION_STRATEGY", "ConfirmedDialogNoApplicationData");
        System.setProperty("jgroups.bind_addr", ipAddress);
        System.setProperty("java.net.preferIPv4Stack", "true");
		initStack(ipAddress);
	}
	
	private void initStack(String ipAddress) throws SipException, TooManyListenersException,
			NumberFormatException, InvalidArgumentException, ParseException {
		this.sipFactory = SipFactory.getInstance();
		this.sipFactory.setPathName("org.mobicents.ha");
//		this.sipFactory.setPathName("gov.nist");
		this.sipStack = this.sipFactory.createSipStack(properties);
		this.sipStack.start();
		this.listeningPoint = this.sipStack.createListeningPoint(properties.getProperty(
				SIP_BIND_ADDRESS, ipAddress), Integer.valueOf(properties
				.getProperty(SIP_PORT_BIND, "5060")), properties.getProperty(
				TRANSPORTS_BIND, "udp"));
		this.provider = this.sipStack.createSipProvider(this.listeningPoint);
		this.provider.addSipListener(this);
		this.headerFactory = sipFactory.createHeaderFactory();
		this.messageFactory = sipFactory.createMessageFactory();
		b2buaHandler = new SimpleB2BUAHandler(provider,headerFactory,messageFactory, Integer.parseInt(properties.getProperty(SIP_PORT_BIND)));
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
			if (responseEvent.getClientTransaction() == null) {
				// retransmission, drop it
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
                sipProvider.removeSipListener(this);
                sipStack.deleteSipProvider(sipProvider);
                sipProviderIterator = sipStack.getSipProviders();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cant remove the listening points or sip providers", e);
        }

        sipStack.stop();
        sipStack = null;
	}	
}
