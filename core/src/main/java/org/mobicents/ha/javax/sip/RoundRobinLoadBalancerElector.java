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
	final ArrayList<Address> balancers = new ArrayList<Address>();
	
	/**
	 * index use to iterate the balancer's list
	 */
	AtomicInteger index = new AtomicInteger(0);
	
	/* (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.AbstractLoadBalancerElector#addLoadBalancer(javax.sip.address.Address)
	 */
	@Override
	void addLoadBalancer(Address address) {
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
		return getLoadBalancer(true);	
	}

	private Address getLoadBalancer(boolean secondChance) {
		switch (balancers.size()) {
		case 1:
			return balancers.get(0);
		case 0:	
			return null;
		default:
			try {
				return balancers.get(getNextIndex());
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

}
