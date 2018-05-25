package org.mobicents.ha.javax.sip;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.ListeningPointExt;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ClientPodResource;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.sip.ListeningPoint;

import org.mobicents.tools.heartbeat.api.Protocol;

public class MultiNetworkLoadBalancerHeartBeatingServiceKubeImpl implements LoadBalancerHeartBeatingService, MultiNetworkLoadBalancerHeartBeatingServiceImplMBean{

	private static StackLogger logger = CommonLogger.getLogger(MultiNetworkLoadBalancerHeartBeatingServiceKubeImpl.class);
	
	final static String POD_NAME = "org.mobicents.ha.javax.sip.POD_NAME";
	
	private KubernetesClient kube;
	private CopyOnWriteArrayList <SipLoadBalancer> balansersList = new CopyOnWriteArrayList<>();
	private CopyOnWriteArrayList <ListeningPoint> listeningPointsList = new CopyOnWriteArrayList<>();
	private String podName = "sip-server-node";
	private ClientPodResource<Pod, DoneablePod> currentPod;
	private String address = null;
	private String hostName = null;
	private Properties sipStackProperties = null;
	
	protected Set<LoadBalancerHeartBeatingListener> loadBalancerHeartBeatingListeners;
	protected String jvmRoute;
	//protected int heartBeatInterval = 5000;
	
	public MultiNetworkLoadBalancerHeartBeatingServiceKubeImpl()
	{
		loadBalancerHeartBeatingListeners = new CopyOnWriteArraySet<LoadBalancerHeartBeatingListener>();
	}
	
	@Override
	public void init(ClusteredSipStack clusteredSipStack, Properties stackProperties) 
	{
		this.sipStackProperties = stackProperties;
		this.kube = new DefaultKubernetesClient();
		//this.heartBeatInterval = Integer.parseInt(stackProperties.getProperty(HEARTBEAT_INTERVAL, "5000"));
		this.podName = stackProperties.getProperty(POD_NAME, "sip-server-node");
		this.currentPod = kube.pods().withName(podName);
		setLabelsFromStack();
	}

	@Override
	public void start() {
		if(!currentPod.get().getMetadata().getLabels().containsKey(Protocol.SESSION_ID))
		{
			currentPod.edit().editMetadata().addToLabels(Protocol.SESSION_ID, ""+System.currentTimeMillis());
		}
		else
		{
			currentPod.edit().editMetadata().removeFromLabels(Protocol.SESSION_ID);
			currentPod.edit().editMetadata().addToLabels(Protocol.SESSION_ID, ""+System.currentTimeMillis());
		}
	}

	@Override
	public void stop() 
	{
		this.kube.close();
		loadBalancerHeartBeatingListeners.clear();
	}

	@Override
	public String[] getBalancers() 
	{
		return this.balansersList.toArray(new String[balansersList.size()]);
	}

	@Override
	public boolean addBalancer(String addr, int sipPort, int httpPort, int rmiPort) throws IllegalArgumentException, NullPointerException, IOException {
		throw new UnsupportedOperationException("This algorithm only allows to add a Load Balancer tied to a sip connector");
	}

	@Override
	public boolean addBalancer(String hostName, int sipPort, int httpPort, int index, int rmiPort) throws IllegalArgumentException {
		throw new UnsupportedOperationException("This algorithm only allows to add a Load Balancer tied to a sip connector");
	}

	@Override
	public SipLoadBalancer[] getLoadBalancers() 
	{
		return balansersList.toArray(new SipLoadBalancer[] {});
	}

	@Override
	public boolean removeBalancer(String addr, int sipPort, int httpPort, int rmiPort) throws IllegalArgumentException {
		throw new UnsupportedOperationException("This algorithm only allows to remove a Load Balancer tied to a sip connector");
	}

	@Override
	public boolean removeBalancer(String hostName, int sipPort, int httpPort, int index, int rmiPort) throws IllegalArgumentException {
		throw new UnsupportedOperationException("This algorithm only allows to remove a Load Balancer tied to a sip connector");
	}

	@Override
	public void sendSwitchoverInstruction(SipLoadBalancer sipLoadBalancer, String fromJvmRoute, String toJvmRoute) {
		currentPod.edit().editMetadata().addToLabels(Protocol.SWITCHOVER,fromJvmRoute+","+toJvmRoute);
		
	}

	@Override
	public long getHeartBeatInterval() {
		throw new UnsupportedOperationException("This algorithm does not use heartbeat interval");
		//return heartBeatInterval;
	}

	@Override
	public void setHeartBeatInterval(long heartBeatInterval) {
		throw new UnsupportedOperationException("This algorithm does not use heartbeat interval");
//		if (heartBeatInterval < 100)
//			return;
//		
//		if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG))
//			logger.logDebug("Setting HeartBeatInterval from " + this.heartBeatInterval + " to " + heartBeatInterval);
//		this.heartBeatInterval = (int)heartBeatInterval;
		
	}

	@Override
	public void setJvmRoute(String jvmRoute) {
		this.jvmRoute = jvmRoute;
		
	}

	@Override
	public String getJvmRoute() {
		return jvmRoute;
	}

	@Override
	public void addLoadBalancerHeartBeatingListener(LoadBalancerHeartBeatingListener loadBalancerHeartBeatingListener) {
		loadBalancerHeartBeatingListeners.add(loadBalancerHeartBeatingListener);
		
	}

	@Override
	public void removeLoadBalancerHeartBeatingListener(LoadBalancerHeartBeatingListener loadBalancerHeartBeatingListener) {
		loadBalancerHeartBeatingListeners.remove(loadBalancerHeartBeatingListener);
		
	}

	@Override
	public void setGracefulShutdown(SipLoadBalancer sipLoadBalancer, boolean gracefullyShuttingDown) {

		currentPod.edit().editMetadata().addToLabels(Protocol.GRACEFUL_SHUTDOWN,"true");
	}

	@Override
	public void addSipConnector(ListeningPoint listeningPoint) {
		throw new UnsupportedOperationException("This algorithm doesn't let you specify a load balancer without connector");
		
	}

	@Override
	public void removeSipConnector(ListeningPoint listeningPoint) {
		throw new UnsupportedOperationException("This algorithm doesn't let you specify a load balancer without connector");
		
	}

	@Override
	public void addSipConnector(ListeningPoint listeningPoint, SipLoadBalancer loadBalancer) {
		
		if(balansersList.addIfAbsent(loadBalancer))
		{
			if(!currentPod.get().getMetadata().getLabels().containsKey(Protocol.LB_LABEL))
			{
				currentPod.edit().editMetadata().addToLabels(Protocol.LB_LABEL, loadBalancer.getAddress().getHostAddress());
			}
			else
			{
				currentPod.edit().editMetadata().removeFromLabels(Protocol.LB_LABEL);
				String balansersString = "";
				for(SipLoadBalancer balancer : balansersList)
					balansersString+=balancer.getAddress().getHostAddress();
				currentPod.edit().editMetadata().addToLabels(Protocol.LB_LABEL, balansersString);
			}
			
		}
		if(listeningPointsList.addIfAbsent(listeningPoint))
		{
			currentPod.edit().editMetadata().addToLabels(getData(listeningPoint));
		}
	}

	@Override
	public void removeSipConnector(ListeningPoint listeningPoint, SipLoadBalancer loadBalancer) {
		
		String transport = listeningPoint.getTransport().toUpperCase();
		switch(transport)
		{
			case ListeningPoint.UDP :  
				currentPod.edit().editMetadata().removeFromLabels(Protocol.UDP_PORT);
				break;
			case ListeningPoint.TCP :  
				currentPod.edit().editMetadata().removeFromLabels(Protocol.TCP_PORT);
				break;
			case ListeningPoint.TLS :  
				currentPod.edit().editMetadata().removeFromLabels(Protocol.TLS_PORT);
				break;
			case ListeningPointExt.WS :  
				currentPod.edit().editMetadata().removeFromLabels(Protocol.WS_PORT);
				break;
			case ListeningPointExt.WSS :  
				currentPod.edit().editMetadata().removeFromLabels(Protocol.WSS_PORT);
				break;
			case ListeningPointExt.SCTP :  
				currentPod.edit().editMetadata().removeFromLabels(Protocol.SCTP_PORT);
				break;
		}
		listeningPointsList.remove(listeningPoint);
		
		
	}

	@Override
	public void setCustomInfo(SipLoadBalancer sipLoadBalancer) 
	{
		
		Properties properties = sipLoadBalancer.getCustomInfo();
		for(String name : properties.stringPropertyNames())
		{
			currentPod.edit().editMetadata().addToLabels(name, properties.getProperty(name));
		}
		
	}
	
	private Map<String,String> getData(ListeningPoint listeningPoint)
	{
		Map <String,String> listeningPointData = new HashMap<>();

			String currAddress = listeningPoint.getIPAddress();
		
			if(currAddress == null) 
			{
				if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) 
					logger.logTrace("Address is null");
			} else 
			{
				if(address == null)
				{
					address = currAddress;
					if(address.equals("localhost")) address = "127.0.0.1";
					try {
						if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) 
							logger.logTrace("Trying to find address array for address " + address);
				
						InetAddress[] aArray = InetAddress.getAllByName(address);
						if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) 
						{
							for (int i = 0; i < aArray.length; i++) 
								logger.logTrace("Found " + aArray[i].getHostAddress());	
					
						}
						if (aArray != null && aArray.length > 0) 
						{
							hostName = aArray[0].getCanonicalHostName();
						}
					} catch (UnknownHostException e) 
					{
						logger.logError("An exception occurred while trying to retrieve the hostname of a sip connector", e);
					}
					listeningPointData.put(Protocol.HOST_NAME, hostName);
					listeningPointData.put(Protocol.IP, address);
				}

			
			String lpTransport = listeningPoint.getTransport().toUpperCase();
			switch(lpTransport)
			{
				case ListeningPoint.UDP :  
					listeningPointData.put(Protocol.UDP_PORT, ""+listeningPoint.getPort());
					break;
				case ListeningPoint.TCP :  
					listeningPointData.put(Protocol.TCP_PORT, ""+listeningPoint.getPort());
					break;
				case ListeningPoint.TLS :  
					listeningPointData.put(Protocol.TLS_PORT, ""+listeningPoint.getPort());
					break;
				case ListeningPointExt.WS :  
					listeningPointData.put(Protocol.WS_PORT, ""+listeningPoint.getPort());
					break;
				case ListeningPointExt.WSS :  
					listeningPointData.put(Protocol.WSS_PORT, ""+listeningPoint.getPort());
					break;
				case ListeningPointExt.SCTP :  
					listeningPointData.put(Protocol.SCTP_PORT, ""+listeningPoint.getPort());
					break;
			}
		}
		return listeningPointData;
	}
	
	private void setLabelsFromStack()
	{
		String httpPortString = sipStackProperties.getProperty(LOCAL_HTTP_PORT);
		String sslPortString = sipStackProperties.getProperty(LOCAL_SSL_PORT);
		String smppPortString = sipStackProperties.getProperty(LOCAL_SMPP_PORT);
		String smppSslPortString = sipStackProperties.getProperty(LOCAL_SMPP_SSL_PORT);
		
		if(httpPortString == null && sslPortString == null) {
			logger.logWarning("HTTP or HTTPS port couldn't be retrieved from System properties, trying with JMX");
			Integer httpPort = null;
			Boolean httpBound = false;
			Integer sslPort = null;
			Boolean sslBound = false;

			MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
			try {
                // https://telestax.atlassian.net/browse/JSIPHA-4 defaulting to standard-sockets
                String socketBindingGroup = sipStackProperties.getProperty(SOCKET_BINDING_GROUP, "standard-sockets");
				ObjectName http = new ObjectName("jboss.as:socket-binding-group=" + socketBindingGroup + ",socket-binding=http");
				httpPort = (Integer) mBeanServer.getAttribute(http, "boundPort");
				httpBound = (Boolean) mBeanServer.getAttribute(http, "bound");

				ObjectName https = new ObjectName("jboss.as:socket-binding-group=" + socketBindingGroup + ",socket-binding=https");
				sslPort = (Integer) mBeanServer.getAttribute(https, "boundPort");
				sslBound = (Boolean) mBeanServer.getAttribute(https, "bound");
				if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
					logger.logTrace("Dound httpPort " + httpPort + " and sslPort " + sslPort);
				}
			} catch (Exception e) {} //Ignore any exceptions

			if(httpBound && httpPort!=null){
				sipStackProperties.setProperty(LOCAL_HTTP_PORT, String.valueOf(httpPort));
			} else if (sslBound && sslPort!=null){
                sipStackProperties.setProperty(LOCAL_SSL_PORT, String.valueOf(sslPort));
			}
		}
		
		if(httpPortString != null)
			currentPod.edit().editMetadata().addToLabels(Protocol.HTTP_PORT, httpPortString);
		if(sslPortString != null)
			currentPod.edit().editMetadata().addToLabels(Protocol.SSL_PORT, sslPortString);
		if(smppPortString != null)
			currentPod.edit().editMetadata().addToLabels(Protocol.SMPP_PORT, smppPortString);
		if(smppSslPortString != null)
			currentPod.edit().editMetadata().addToLabels(Protocol.SMPP_SSL_PORT, smppSslPortString);
	}

}
