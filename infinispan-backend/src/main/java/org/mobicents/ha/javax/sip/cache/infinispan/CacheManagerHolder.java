/*
 * TeleStax, Open Source Cloud Communications.
 * Copyright 2011-2013 and individual contributors by the @authors tag. 
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

package org.mobicents.ha.javax.sip.cache.infinispan;

import java.io.IOException;

import org.infinispan.manager.DefaultCacheManager;

/**
 * This class holds a singleton DefaultCacheManager if there is no Infinispan cache allocated
 * under the given JNDI name @see org.mobicents.ha.javax.sip.cache.infinispan.InfinispanCache  
 * 
 * @author posfai.gergely@ext.alerant.hu
 * @author kokuti.andras@ext.alerant.hu
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
