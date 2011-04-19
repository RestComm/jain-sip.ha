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

import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.MessageChannel;
import gov.nist.javax.sip.stack.MobicentsHASIPClientTransaction;
import gov.nist.javax.sip.stack.MobicentsHASIPServerTransaction;
import gov.nist.javax.sip.stack.SIPClientTransaction;
import gov.nist.javax.sip.stack.SIPServerTransaction;
import gov.nist.javax.sip.stack.SIPTransactionStack;

import javax.sip.SipStack;

import org.mobicents.ext.javax.sip.TransactionFactory;

/**
 * @author jean.deruelle@gmail.com
 *
 */
public class MobicentsHATransactionFactory implements TransactionFactory {

	private SIPTransactionStack sipStack;
	
	/* (non-Javadoc)
	 * @see org.mobicents.ext.javax.sip.TransactionFactory#createClientTransaction(gov.nist.javax.sip.message.SIPRequest, gov.nist.javax.sip.stack.MessageChannel)
	 */
	public SIPClientTransaction createClientTransaction(SIPRequest sipRequest,
			MessageChannel encapsulatedMessageChannel) {
		MobicentsHASIPClientTransaction ct = new MobicentsHASIPClientTransaction(sipStack,
				encapsulatedMessageChannel);
        ct.setOriginalRequest(sipRequest);
        return ct;
	}

	/* (non-Javadoc)
	 * @see org.mobicents.ext.javax.sip.TransactionFactory#createServerTransaction(gov.nist.javax.sip.stack.MessageChannel)
	 */
	public SIPServerTransaction createServerTransaction(
			MessageChannel encapsulatedMessageChannel) {
		return new MobicentsHASIPServerTransaction(sipStack, encapsulatedMessageChannel);
	}

	/* (non-Javadoc)
	 * @see org.mobicents.ext.javax.sip.TransactionFactory#setSipStack(javax.sip.SipStack)
	 */
	public void setSipStack(SipStack sipStack) {
		this.sipStack = (SIPTransactionStack) sipStack;
	}

}
