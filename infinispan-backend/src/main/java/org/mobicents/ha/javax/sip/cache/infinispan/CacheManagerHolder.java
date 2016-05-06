/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2016, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package org.mobicents.ha.javax.sip.cache.infinispan;

import java.io.IOException;

import org.infinispan.manager.DefaultCacheManager;

/**
 * This class holds a singleton DefaultCacheManager if there is no Infinispan cache allocated
 * under the given JNDI name @see org.mobicents.ha.javax.sip.cache.infinispan.InfinispanCache  
 * 
 * @author <A HREF="mailto:posfai.gergely@ext.alerant.hu">Gergely Posfai</A>
 * @author <A HREF="mailto:kokuti.andras@ext.alerant.hu">Andras Kokuti</A>
 *
 */

public class CacheManagerHolder {

	private static DefaultCacheManager cacheManager;
	
	
	public static DefaultCacheManager getManager(String configurationFile) throws IOException{
		if(cacheManager == null){
			synchronized (CacheManagerHolder.class) {
				if(cacheManager == null){
					
					cacheManager = new DefaultCacheManager(configurationFile);
					
				}
			}
			
		}
		
		return cacheManager;
		
	}
	
}
