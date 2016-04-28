package org.mobicents.ha.javax.sip;

import java.io.IOException;
import java.util.Properties;

public class TestConstants {
	
	public static String getIpAddressFromProperties(){
		Properties p = new Properties();
		try {
			p.load(VariableRequestDirectionRecoveryTest.class.getClassLoader().getResourceAsStream("test.properties"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return p.getProperty("IP_ADDRESS","127.0.0.1");
	}

}
