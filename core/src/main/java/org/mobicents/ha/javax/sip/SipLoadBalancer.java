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

/*
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

import java.io.Serializable;
import java.net.InetAddress;

import javax.sip.PeerUnavailableException;
import javax.sip.SipFactory;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.HeaderFactory;
import javax.sip.header.RouteHeader;


/**
 * 
 * @author <A HREF="mailto:jean.deruelle@gmail.com">Jean Deruelle</A> 
 *
 */
public class SipLoadBalancer implements Serializable {
	private static SipFactory sipFactory = SipFactory.getInstance();
	private static AddressFactory addressFactory;
	private static HeaderFactory headerFactory;
	static {
		try {
			addressFactory = sipFactory.createAddressFactory();
			headerFactory = sipFactory.createHeaderFactory();
		} catch (PeerUnavailableException e) {
			throw new RuntimeException("Problem with factory creation", e);
		}
	}
	private static final long serialVersionUID = 1L;
	private InetAddress address;
	private int sipPort;
	private int rmiPort;
	private transient LoadBalancerHeartBeatingService loadBalancerHeartBeatingService;
	private transient RouteHeader balancerRouteHeaderUdp;
	private transient RouteHeader balancerRouteHeaderTcp;
	private transient boolean available;
	private transient boolean displayWarning;
	private transient Address sipAddress;
	/**
	 * @param address
	 * @param sipPort
	 * @param hostName
	 */
	public SipLoadBalancer(LoadBalancerHeartBeatingService loadBalancerHeartBeatingService, InetAddress address, int sipPort, int rmiPort) {
		super();
		this.available = false;
		this.displayWarning = true;
		this.address = address;
		this.sipPort = sipPort;
		this.rmiPort = rmiPort;
		this.loadBalancerHeartBeatingService = loadBalancerHeartBeatingService;
		try {
			javax.sip.address.SipURI sipUriUdp = addressFactory.createSipURI(null, address.getHostAddress());
			sipUriUdp.setPort(sipPort);
			sipUriUdp.setLrParam();
			javax.sip.address.SipURI sipAddressUri = (SipURI) sipUriUdp.clone();
			sipUriUdp.setTransportParam("udp");
			javax.sip.address.SipURI sipUriTcp = (SipURI) sipUriUdp.clone();
			sipUriTcp.setTransportParam("tcp");
			
			javax.sip.address.Address routeAddressUdp = 
				addressFactory.createAddress(sipUriUdp);
			balancerRouteHeaderUdp = 
				headerFactory.createRouteHeader(routeAddressUdp);
			
			javax.sip.address.Address routeAddressTcp = 
				addressFactory.createAddress(sipUriTcp);
			balancerRouteHeaderTcp = 
				headerFactory.createRouteHeader(routeAddressTcp);
			
			sipAddress = addressFactory.createAddress(sipAddressUri);
			
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	/**
	 * @param address the address to set
	 */
	public void setAddress(InetAddress address) {
		this.address = address;
	}
	/**
	 * @return the address
	 */
	public InetAddress getAddress() {
		return address;
	}
	/**
	 * @param sipPort the sipPort to set
	 */
	public void setSipPort(int sipPort) {
		this.sipPort = sipPort;
	}
	/**
	 * @return the sipPort
	 */
	public int getSipPort() {
		return sipPort;
	}
	public int getRmiPort() {
		return rmiPort;
	}
	public void setRmiPort(int rmiPort) {
		this.rmiPort = rmiPort;
	}
	public RouteHeader getBalancerRouteHeaderTcp() {
		return balancerRouteHeaderTcp;
	}
	public RouteHeader getBalancerRouteHeaderUdp() {
		return balancerRouteHeaderUdp;
	}
	public void setBalancerRouteHeaderTcp(RouteHeader balancerRouteHeader) {
		this.balancerRouteHeaderTcp = balancerRouteHeader;
	}
	public void setBalancerRouteHeaderUdp(RouteHeader balancerRouteHeader) {
		this.balancerRouteHeaderUdp = balancerRouteHeader;
	}
	public boolean isAvailable() {
		return available;
	}
	public void setAvailable(boolean available) {
		this.available = available;
	}
	public boolean isDisplayWarning() {
		return displayWarning;
	}
	public void setDisplayWarning(boolean displayWarning) {
		this.displayWarning = displayWarning;
	}
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((address == null) ? 0 : address.hashCode());
		result = prime * result + sipPort;
		result = prime * result + rmiPort;
		return result;
	}
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SipLoadBalancer other = (SipLoadBalancer) obj;
		if (address == null) {
			if (other.address != null)
				return false;
		} else if (!address.equals(other.address))
			return false;
		if (sipPort != other.sipPort)
			return false;
		if (rmiPort != other.rmiPort)
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		return getAddress() + ":" + getSipPort() + ":" + getRmiPort();
	}

	public void switchover(String fromJvmRoute, String toJvmRoute) {
		loadBalancerHeartBeatingService.sendSwitchoverInstruction(this, fromJvmRoute, toJvmRoute);
	}
	public Address getSipAddress() {
		return sipAddress;
	}
	public void setSipAddress(Address sipAddress) {
		this.sipAddress = sipAddress;
	}
}
