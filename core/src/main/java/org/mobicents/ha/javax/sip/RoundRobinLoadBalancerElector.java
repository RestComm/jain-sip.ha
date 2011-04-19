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

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sip.address.Address;

/**
 * Round robin implementation of the abstract load balancer elector.
 * 
 * @author martins
 *
 */
public class RoundRobinLoadBalancerElector extends AbstractLoadBalancerElector {

	/**
	 * the list of balancer addresses
	 */
	final ArrayList<SipLoadBalancer> balancers = new ArrayList<SipLoadBalancer>();
	
	/**
	 * index use to iterate the balancer's list
	 */
	AtomicInteger index = new AtomicInteger(0);
	
	/* (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.AbstractLoadBalancerElector#addLoadBalancer(javax.sip.address.Address)
	 */
	@Override
	void addLoadBalancer(SipLoadBalancer address) {
		synchronized (balancers) {
			balancers.add(address);
		}
	}
	
	/**
	 * Computes the index of the next balancer to retrieve. Adaptation of the {@link AtomicInteger} incrementAndGet() code.
	 *  
	 * @return
	 */
	private int getNextIndex() {
		for (;;) {
            int current = index.get();
            int next = (current == balancers.size() ? 1 : current + 1);
            if (index.compareAndSet(current, next))
                return next-1;
        }
	}
	
	/* (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.AbstractLoadBalancerElector#getLoadBalancer()
	 */
	@Override
	public Address getLoadBalancer() {
		return getLoadBalancer(true).getSipAddress();	
	}

	private SipLoadBalancer getLoadBalancer(boolean secondChance) {
		SipLoadBalancer result = null;
		switch (balancers.size()) {
		case 1:
			result = balancers.get(0);
			if(!result.isAvailable()) return null;
			return result;
		case 0:	
			return null;
		default:
			try {
				result = balancers.get(getNextIndex());
				if(!result.isAvailable()) {
					for(int tries = 0; tries<balancers.size(); tries++) {
						result = balancers.get(getNextIndex());
						if(result.isAvailable()) return result;
					}
				}
			}
			catch(IndexOutOfBoundsException e) {
				if (secondChance) {
					// the exception can happen when removing a balancer, since this is not frequent lets give a second chance
					return getLoadBalancer(false);
				}
				else {
					if (logger.isLoggingEnabled()) {
						logger.logError("Failed to get load balancer",e);
					}
					return null;
				}				
			}
		}
		return result;
	}
	
	/* (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.AbstractLoadBalancerElector#removeLoadBalancer(javax.sip.address.Address)
	 */
	@Override
	void removeLoadBalancer(Address address) {
		synchronized (balancers) {
			balancers.remove(address);
		}
	}

	public SipLoadBalancer getLoadBalancerExt() {
		return getLoadBalancer(true);
	}

}
