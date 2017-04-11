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

/**
 * 
 */
package org.mobicents.ha.javax.sip;

import gov.nist.core.StackLogger;
import gov.nist.javax.sip.address.SipUri;

import java.text.ParseException;

import javax.sip.address.Address;
import javax.sip.address.AddressFactory;

/**
 * Base code for a {@link LoadBalancerElector} which is also a
 * {@link LoadBalancerHeartBeatingService}.
 * 
 * @author martins
 * 
 */
public abstract class AbstractLoadBalancerElector implements
		LoadBalancerElector, LoadBalancerHeartBeatingListener {

	/**
	 * 
	 */
	protected LoadBalancerHeartBeatingService service;
	
	/**
	 * 
	 */
	protected AddressFactory addressFactory;
	
	/**
	 * 
	 */
	protected StackLogger logger;

	/**
	 * Adds the specified load balancer {@link Address}.
	 * @param address
	 */
	abstract void addLoadBalancer(SipLoadBalancer address);
	
	/**
	 * Creates a {@link Address} with a {@link SipUri}, from the specified
	 * balancer description. Note that the uri has no transport set.
	 * 
	 * @param balancerDescription
	 * @return
	 */
	private Address createAddress(String balancerDescription) {
		String host = balancerDescription;
		int sipPort = LoadBalancerHeartBeatingServiceImpl.DEFAULT_LB_SIP_PORT;
		if (balancerDescription
				.indexOf(LoadBalancerHeartBeatingServiceImpl.BALANCER_SIP_PORT_CHAR_SEPARATOR) != -1) {
			String[] balancerDescriptionSplitted = balancerDescription
					.split(LoadBalancerHeartBeatingServiceImpl.BALANCER_SIP_PORT_CHAR_SEPARATOR);
			host = balancerDescriptionSplitted[0];
			try {
				sipPort = Integer.parseInt(balancerDescriptionSplitted[1]);
			} catch (NumberFormatException e) {
				logger.logError(
						"Impossible to parse the following sip balancer port "
								+ balancerDescriptionSplitted[1], e);
				return null;
			}
		}
		return createAddress(host, sipPort);
	}

	/**
	 * Creates a {@link Address} with a {@link SipUri}, from the specified host
	 * and port. Note that the uri has no transport set.
	 * 
	 * @param host
	 * @param port
	 * @return
	 */
	private Address createAddress(String host, int port) {
		SipUri sipUri = new SipUri();
		try {
			sipUri.setHost(host);
		} catch (ParseException e) {
			logger.logError("Bad load balancer host " + host, e);
			return null;
		}
		sipUri.setPort(port);
		return addressFactory.createAddress(sipUri);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.mobicents.ha.javax.sip.LoadBalancerElector#getLoadBalancer()
	 */
	public abstract Address getLoadBalancer();

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingListener#loadBalancerAdded
	 * (org.mobicents.ha.javax.sip.SipLoadBalancer)
	 */
	public void loadBalancerAdded(SipLoadBalancer balancerDescription) {
			addLoadBalancer(balancerDescription);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @seeorg.mobicents.ha.javax.sip.LoadBalancerHeartBeatingListener#
	 * loadBalancerRemoved(org.mobicents.ha.javax.sip.SipLoadBalancer)
	 */
	public void loadBalancerRemoved(SipLoadBalancer balancerDescription) {
		Address address = createAddress(balancerDescription.getAddress()
				.getHostName(), balancerDescription.getSipPort());
		if (address != null) {
			removeLoadBalancer(address);
		}
	}

	/* (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.LoadBalancerElector#setAddressFactory(javax.sip.address.AddressFactory)
	 */
	public void setAddressFactory(AddressFactory addressFactory) throws NullPointerException {
		if (addressFactory == null) {
			throw new NullPointerException("null addressFactory");
		}
		this.addressFactory = addressFactory;		
	}
	
	/* (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.LoadBalancerElector#setService(org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingService)
	 */
	public void setService(LoadBalancerHeartBeatingService service) throws IllegalStateException, NullPointerException {
		if (service == null) {
			throw new NullPointerException("null service");
		}
		if (addressFactory == null || logger == null) {
			throw new IllegalStateException();
		}
		this.service = service;
		service.addLoadBalancerHeartBeatingListener(this);
		for (SipLoadBalancer balancer : service.getLoadBalancers()) {
			addLoadBalancer(balancer);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.LoadBalancerElector#setStackLogger(gov.nist.core.StackLogger)
	 */
	public void setStackLogger(StackLogger logger) throws NullPointerException {
		if (logger == null) {
			throw new NullPointerException("null logger");
		}
		this.logger = logger;		
	}
	
	/**
	 * Removes the specified load balancer {@link Address}.
	 * @param address
	 */
	abstract void removeLoadBalancer(Address address);
}
