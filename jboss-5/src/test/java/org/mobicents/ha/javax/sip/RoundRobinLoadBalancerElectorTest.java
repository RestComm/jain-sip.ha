/**
 * 
 */
package org.mobicents.ha.javax.sip;

import gov.nist.core.StackLogger;
import gov.nist.javax.sip.address.AddressFactoryImpl;

import java.io.IOException;
import java.util.Properties;

import javax.sip.address.Address;
import javax.sip.address.SipURI;

import junit.framework.TestCase;

/**
 * @author martins
 *
 */
public class RoundRobinLoadBalancerElectorTest  extends TestCase {

	/**
	 * Simple test of the {@link RoundRobinLoadBalancerElector} functionality
	 */
	public void test() {
		String[] balancers = {"192.168.0.1:5061","192.168.0.2:5062","192.168.0.3:5063"};
		RoundRobinLoadBalancerElector elector = new RoundRobinLoadBalancerElector();
		LoadBalancerHeartBeatingService service = new TestLoadBalancerHeartBeatingService(balancers);
		elector.setStackLogger(new TestStackLogger());
		elector.setAddressFactory(new AddressFactoryImpl());
		elector.setService(service);
		
		for (int i = 1; i < 4;i++) {
			Address address = elector.getLoadBalancer();
			assertNotNull(address);
			SipURI sipURI = (SipURI) address.getURI();
			assertTrue("Load balancer elected #"+i+" is not the correct balancer", sipURI.getHost().equals("192.168.0."+i) && sipURI.getPort() == 5060+i);
		}
		
		Address address = elector.getLoadBalancer();
		assertNotNull(address);
		SipURI sipURI = (SipURI) address.getURI();
		assertTrue("Load balancer elected #4 is not the first balancer", sipURI.getHost().equals("192.168.0.1") && sipURI.getPort() == 5061);
		
		elector.removeLoadBalancer(address);
		
		address = elector.getLoadBalancer();
		assertNotNull(address);
		sipURI = (SipURI) address.getURI();
		assertTrue("Load balancer elected #5 is not the third balancer", sipURI.getHost().equals("192.168.0.3") && sipURI.getPort() == 5063);
	}
	
	static class TestLoadBalancerHeartBeatingService implements LoadBalancerHeartBeatingService {

		@SuppressWarnings("unused")
		private LoadBalancerHeartBeatingListener listener;
		private final String[] balancers;
		
		/**
		 * 
		 */
		public TestLoadBalancerHeartBeatingService(String[] balancers) {
			this.balancers = balancers;
		}
		
		/* (non-Javadoc)
		 * @see org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingService#addBalancer(java.lang.String, int, int)
		 */
		public boolean addBalancer(String addr, int sipPort, int rmiPort)
				throws IllegalArgumentException, NullPointerException,
				IOException {
			// TODO Auto-generated method stub
			return false;
		}

		/* (non-Javadoc)
		 * @see org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingService#addBalancer(java.lang.String, int, int, int)
		 */
		public boolean addBalancer(String hostName, int sipPort, int index,
				int rmiPort) throws IllegalArgumentException {
			// TODO Auto-generated method stub
			return false;
		}

		/* (non-Javadoc)
		 * @see org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingService#addLoadBalancerHeartBeatingListener(org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingListener)
		 */
		public void addLoadBalancerHeartBeatingListener(
				LoadBalancerHeartBeatingListener loadBalancerHeartBeatingListener) {
			this.listener = loadBalancerHeartBeatingListener;			
		}

		/* (non-Javadoc)
		 * @see org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingService#getBalancers()
		 */
		public String[] getBalancers() {
			return balancers;
		}

		/* (non-Javadoc)
		 * @see org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingService#getHeartBeatInterval()
		 */
		public long getHeartBeatInterval() {
			// TODO Auto-generated method stub
			return 0;
		}

		/* (non-Javadoc)
		 * @see org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingService#getJvmRoute()
		 */
		public String getJvmRoute() {
			// TODO Auto-generated method stub
			return null;
		}

		/* (non-Javadoc)
		 * @see org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingService#init(org.mobicents.ha.javax.sip.ClusteredSipStack, java.util.Properties)
		 */
		public void init(ClusteredSipStack clusteredSipStack,
				Properties stackProperties) {
			// TODO Auto-generated method stub
			
		}

		/* (non-Javadoc)
		 * @see org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingService#removeBalancer(java.lang.String, int, int)
		 */
		public boolean removeBalancer(String addr, int sipPort, int rmiPort)
				throws IllegalArgumentException {
			// TODO Auto-generated method stub
			return false;
		}

		/* (non-Javadoc)
		 * @see org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingService#removeBalancer(java.lang.String, int, int, int)
		 */
		public boolean removeBalancer(String hostName, int sipPort, int index,
				int rmiPort) throws IllegalArgumentException {
			// TODO Auto-generated method stub
			return false;
		}

		/* (non-Javadoc)
		 * @see org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingService#removeLoadBalancerHeartBeatingListener(org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingListener)
		 */
		public void removeLoadBalancerHeartBeatingListener(
				LoadBalancerHeartBeatingListener loadBalancerHeartBeatingListener) {
			// TODO Auto-generated method stub
			
		}

		/* (non-Javadoc)
		 * @see org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingService#sendSwitchoverInstruction(org.mobicents.ha.javax.sip.SipLoadBalancer, java.lang.String, java.lang.String)
		 */
		public void sendSwitchoverInstruction(SipLoadBalancer sipLoadBalancer,
				String fromJvmRoute, String toJvmRoute) {
			// TODO Auto-generated method stub
			
		}

		/* (non-Javadoc)
		 * @see org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingService#setHeartBeatInterval(long)
		 */
		public void setHeartBeatInterval(long heartBeatInterval) {
			// TODO Auto-generated method stub
			
		}

		/* (non-Javadoc)
		 * @see org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingService#setJvmRoute(java.lang.String)
		 */
		public void setJvmRoute(String jvmRoute) {
			// TODO Auto-generated method stub
			
		}

		/* (non-Javadoc)
		 * @see org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingService#start()
		 */
		public void start() {
			// TODO Auto-generated method stub
			
		}

		/* (non-Javadoc)
		 * @see org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingService#stop()
		 */
		public void stop() {
			// TODO Auto-generated method stub
			
		}

		public SipLoadBalancer[] getLoadBalancers() {
			// TODO Auto-generated method stub
			return null;
		}
		
	}
	
	static class TestStackLogger implements StackLogger {

		/* (non-Javadoc)
		 * @see gov.nist.core.StackLogger#disableLogging()
		 */
		public void disableLogging() {
			// TODO Auto-generated method stub
			
		}

		/* (non-Javadoc)
		 * @see gov.nist.core.StackLogger#enableLogging()
		 */
		public void enableLogging() {
			// TODO Auto-generated method stub
			
		}

		/* (non-Javadoc)
		 * @see gov.nist.core.StackLogger#getLineCount()
		 */
		public int getLineCount() {
			// TODO Auto-generated method stub
			return 0;
		}

		/* (non-Javadoc)
		 * @see gov.nist.core.StackLogger#getLoggerName()
		 */
		public String getLoggerName() {
			// TODO Auto-generated method stub
			return null;
		}

		/* (non-Javadoc)
		 * @see gov.nist.core.StackLogger#isLoggingEnabled()
		 */
		public boolean isLoggingEnabled() {
			return true;
		}

		/* (non-Javadoc)
		 * @see gov.nist.core.StackLogger#isLoggingEnabled(int)
		 */
		public boolean isLoggingEnabled(int arg0) {
			// TODO Auto-generated method stub
			return false;
		}

		/* (non-Javadoc)
		 * @see gov.nist.core.StackLogger#logDebug(java.lang.String)
		 */
		public void logDebug(String arg0) {
			// TODO Auto-generated method stub
			
		}

		/* (non-Javadoc)
		 * @see gov.nist.core.StackLogger#logError(java.lang.String)
		 */
		public void logError(String arg0) {
			// TODO Auto-generated method stub
			
		}

		/* (non-Javadoc)
		 * @see gov.nist.core.StackLogger#logError(java.lang.String, java.lang.Exception)
		 */
		public void logError(String arg0, Exception arg1) {
			System.out.println(arg0);
			arg1.printStackTrace();
		}

		/* (non-Javadoc)
		 * @see gov.nist.core.StackLogger#logException(java.lang.Throwable)
		 */
		public void logException(Throwable arg0) {
			// TODO Auto-generated method stub
			
		}

		/* (non-Javadoc)
		 * @see gov.nist.core.StackLogger#logFatalError(java.lang.String)
		 */
		public void logFatalError(String arg0) {
			// TODO Auto-generated method stub
			
		}

		/* (non-Javadoc)
		 * @see gov.nist.core.StackLogger#logInfo(java.lang.String)
		 */
		public void logInfo(String arg0) {
			// TODO Auto-generated method stub
			
		}

		/* (non-Javadoc)
		 * @see gov.nist.core.StackLogger#logStackTrace()
		 */
		public void logStackTrace() {
			// TODO Auto-generated method stub
			
		}

		/* (non-Javadoc)
		 * @see gov.nist.core.StackLogger#logStackTrace(int)
		 */
		public void logStackTrace(int arg0) {
			// TODO Auto-generated method stub
			
		}

		/* (non-Javadoc)
		 * @see gov.nist.core.StackLogger#logTrace(java.lang.String)
		 */
		public void logTrace(String arg0) {
			// TODO Auto-generated method stub
			
		}

		/* (non-Javadoc)
		 * @see gov.nist.core.StackLogger#logWarning(java.lang.String)
		 */
		public void logWarning(String arg0) {
			// TODO Auto-generated method stub
			
		}

		/* (non-Javadoc)
		 * @see gov.nist.core.StackLogger#setBuildTimeStamp(java.lang.String)
		 */
		public void setBuildTimeStamp(String arg0) {
			// TODO Auto-generated method stub
			
		}

		/* (non-Javadoc)
		 * @see gov.nist.core.StackLogger#setStackProperties(java.util.Properties)
		 */
		public void setStackProperties(Properties arg0) {
			// TODO Auto-generated method stub
			
		}
		
	}
}
