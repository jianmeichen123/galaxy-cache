package com.galaxyinternet.framework.cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import net.spy.memcached.MemcachedClient;
import net.spy.memcached.OperationTimeoutException;
import redis.clients.jedis.ShardedJedis;
import redis.clients.jedis.ShardedJedisPool;
import redis.clients.util.SafeEncoder;

@SuppressWarnings("deprecation")
public class Cache {

	private MemcachedClient memcachedClient;

	private ShardedJedisPool jedisPool;

	private final CacheHelper cacheHelper = new CacheHelper();

	private long shutdownTimeout = 1000;

	private boolean async = false;

	private boolean local = false;

	private boolean useRedis = true;

	private ShardedJedis jedis;

	private final LocalCache<String, Object> localCache = new LocalCacheImpl();

	private int localTTL = 60 * 60;

	private static Logger logger = Logger.getLogger(Cache.class);

	public void setLocalTTL(int localTTL) {
		this.localTTL = localTTL;
	}

	public ShardedJedis getJedis() {
		jedis = jedisPool.getResource();
		return jedis;
	}

	public void returnJedis(ShardedJedis jedis) {
		jedisPool.returnResource(jedis);
	}

	/**
	 * Get with a single key and decode using the default transcoder.
	 * 
	 * @param key
	 *            the key to get
	 * @return the result from the cache (null if there is none)
	 * @throws OperationTimeoutException
	 *             if the global operation timeout is exceeded
	 * @throws IllegalStateException
	 *             in the rare circumstance where queue is too full to accept
	 *             any more requests
	 */
	public Object getByMemc(String key) {

		return memcachedClient.get(key);

	}
	
	/**
	 * 向集合中添加
	 * @param key
	 * @param plupload
	 */
    public void setRedisSetOBJ(String key, Object plupload){
    	ShardedJedis jedis = jedisPool.getResource();
		try {
			jedis.sadd(key.getBytes(), SerializeTranscoder.serialize(plupload));
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage());
        }finally {
			if (jedis != null)
				jedisPool.returnResource(jedis);
		}
		
	}
    
    /**
     * redis取出集合数据
     * @param key
     * @return
     */
	public List<Object> getRedisQuenOBJ(String key){
    	ShardedJedis jedis = jedisPool.getResource();
		List<Object> list = new ArrayList<Object>();
		try {
			Set<byte[]>  in = jedis.smembers(key.getBytes());
			Iterator<byte[]> t1=in.iterator() ;   
			  while(t1.hasNext()){   
			      byte[] bytes=(byte[]) t1.next();  
			      Object o=SerializeTranscoder.unserizlize(bytes);
			      list.add(o);
			  }  
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage());
        }finally {
			if (jedis != null)
				jedisPool.returnResource(jedis);
		}
		return list;
	}
    /**
     * 从集合中移除某个元素
     * @param key
     * @param plupload
     */
    public void removeRedisSetOBJ(String key, Object plupload){
    	ShardedJedis jedis = jedisPool.getResource();
		try {
			jedis.srem(key.getBytes(),  cacheHelper.objectToBytes(plupload));
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage());
        }finally {
			if (jedis != null)
				jedisPool.returnResource(jedis);
		}
		
	}
    
    /**
     * 移除key
     * @param key
     * @param plupload
     */
    public void removeRedisKeyOBJ(String key){
    	ShardedJedis jedis = jedisPool.getResource();
		try {
			jedis.del(key.getBytes());
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage());
        }finally {
			if (jedis != null)
				jedisPool.returnResource(jedis);
		}
		
	}

	/**
	 * 获取cache封装后的对象
	 * 
	 * @param key
	 * @return
	 */
	public Object getByRedis(String key) {
		ShardedJedis jedis = jedisPool.getResource();
		Object obj = null;
		try {
			byte[] bytes = jedis.get(SafeEncoder.encode(key));
			obj = cacheHelper.bytesToObject(bytes);
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage());
		} finally {
			if (jedis != null)
				jedisPool.returnResource(jedis);
		}
		return obj;

	}

	public Object hgetByRedis(String hashtable, String key) {
		ShardedJedis jedis = jedisPool.getResource();
		Object obj = null;
		try {
			byte[] bytes = jedis.hget(SafeEncoder.encode(hashtable), SafeEncoder.encode(key));
			obj = cacheHelper.bytesToObject(bytes);
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage());
		} finally {
			if (jedis != null)
				jedisPool.returnResource(jedis);
		}
		return obj;

	}
	
	public Map<String,Object> hgetAll(String hashtable)
	{
		ShardedJedis jedis = jedisPool.getResource();
		Map<String,Object> map = new HashMap<>();
		try {
			Map<byte[],byte[]> hash = jedis.hgetAll(SafeEncoder.encode(hashtable));
			if(hash != null && hash.size()>0)
			{
				for(Entry<byte[],byte[]> entry : hash.entrySet())
				{
					map.put(new String(entry.getKey(),"UTF-8"), cacheHelper.bytesToObject(entry.getValue()));
				}
			}
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage());
		} finally {
			if (jedis != null)
				jedisPool.returnResource(jedis);
		}
		return map;
	}
	
	/**
	 * 判key 和value 是否存在
	 * @param lockKey
	 * @param value
	 * @return
	 */
	public long setNx(String lockKey,String value){
		ShardedJedis jedis = jedisPool.getResource();
		long isExit = 0;
		try{
			isExit = jedis.setnx(lockKey,value);
		}catch(Exception e){
			logger.error(e.getLocalizedMessage());
		}finally {
			if (jedis != null)
				jedisPool.returnResource(jedis);
		}
		
		return isExit;
	}
	
	/**
	 * 重新存放键值
	 * @param lockKey
	 * @param value
	 * @return
	 */
	public String getSet(String lockKey,String value){
		 ShardedJedis jedis = jedisPool.getResource();
		 String getValue = null;
		 try{
			 getValue = jedis.getSet(lockKey, String.valueOf(value));
		 }catch(Exception e){
			 logger.error(e.getLocalizedMessage());
		 }finally {
				if (jedis != null)
					jedisPool.returnResource(jedis);
		 }
		 return getValue;
		
	}
	
	/**
	 * 获取value
	 * @param lockKey
	 * @param value
	 * @return
	 */
	public String getValue(String lockKey){
		 ShardedJedis jedis = jedisPool.getResource();
		 String getValue = null;
		 try{
			 getValue = jedis.get(lockKey);
		 }catch(Exception e){
			 logger.error(e.getLocalizedMessage());
		 }finally {
				if (jedis != null)
					jedisPool.returnResource(jedis);
		 }
		 return getValue;
		
	}
	
	/**
	 * 获取value
	 * @param lockKey
	 * @param value
	 * @return
	 */
	public void setValue(String lockKey,String value){
		 ShardedJedis jedis = jedisPool.getResource();
		 try{
			 jedis.set(lockKey,value);
		 }catch(Exception e){
			 logger.error(e.getLocalizedMessage());
		 }finally {
				if (jedis != null)
					jedisPool.returnResource(jedis);
		 }
		
	}
	

	/**
	 * 获取常规对象的方法
	 * 
	 * @param key
	 * @return
	 */
	public Object getObjectByRedis(String key) {
		ShardedJedis jedis = jedisPool.getResource();
		Object obj = null;
		try {
			obj = jedis.get(SafeEncoder.encode(key));
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage());
		} finally {
			if (jedis != null)
				jedisPool.returnResource(jedis);
		}
		return obj;

	}

	/**
	 * 先从本地缓存读，读到就返回，读不到再从redis或memcache读，以优化读性能
	 * 
	 * @param key
	 * @return
	 */
	public Object get(String key) {
		Object result;
		if (local == true) {
			result = localCache.get(key);
			if (result != null) {
				return result;
			}
		}
		if (useRedis == true) {
			result = getByRedis(key);
			if (result != null) {
				localCache.put(key, result, localTTL);
			}
			return result;
		} else {
			result = getByMemc(key);
			if (result != null) {
				localCache.put(key, result, localTTL);
			}
			return result;
		}

	}

	public Object hget(String hashtable, String key) {
		Object result;
		if (local == true) {
			result = localCache.get(key);
			if (result != null) {
				return result;
			}
		}
		if (useRedis == true) {
			result = hgetByRedis(hashtable, key);
			if (result != null) {
				localCache.put(key, result, localTTL);
			}
			return result;
		} else {
			result = getByMemc(key);
			if (result != null) {
				localCache.put(key, result, localTTL);
			}
			return result;
		}

	}

	public long getTTL(String key) {
		long ttl = 0;
		ShardedJedis jedis = jedisPool.getResource();
		try {
			ttl = jedis.ttl(SafeEncoder.encode(key));
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage());
		} finally {
			if (jedis != null)
				jedisPool.returnResource(jedis);
		}
		return ttl;
	}

	/**
	 * 只向分布式缓存中写，不需要向本地写
	 * 
	 * @param key
	 * @param value
	 * @param expiredTime
	 * @return
	 */
	public boolean set(String key, Integer expiredTime, Object value) {
		if (local == true) {
			localCache.put(key, value, expiredTime);
		}
		if (useRedis == true) {
			return setByRedis(key, value, expiredTime);
		} else {
			setByMemc(key, value, expiredTime);
			return true;
		}
	}

	public boolean hset(String hashtable, String key, String value) {
		if (local == true) {
			localCache.put(key, value);
		}
		if (useRedis == true) {
			return hsetByRedis(hashtable, key, value);
		} else {
			setByMemc(key, value, 0);
			return true;
		}
	}

	public boolean remove(String key) {
		localCache.remove(key);
		if (isUseRedis()) {
			ShardedJedis jedis = jedisPool.getResource();
			boolean tag = true;
			try {
				jedis.del(key);
			} catch (Exception e) {
				logger.error(e.getLocalizedMessage());
				tag = false;
			} finally {
				jedisPool.returnResource(jedis);
			}
			return tag;
		} else {
			Future<Boolean> future = memcachedClient.delete(key);
			try {
				return future.get(1, TimeUnit.SECONDS);
			} catch (Exception e) {
				future.cancel(false);
			}
			return false;
		}

	}

	/**
	 * 使用默认时间保存
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	public boolean set(String key, Object value) {
		if (local == true) {
			localCache.put(key, value, localTTL);
		}
		if (useRedis == true) {
			return setByRedis(key, value, 0);
		} else {
			return setByMemc(key, value, 0);
		}
	}

	public boolean setByRedis(String key, Object value, Integer expiredTime) {
		ShardedJedis jedis = jedisPool.getResource();
		boolean tag = true;
		try {
			jedis.set(SafeEncoder.encode(key), cacheHelper.objectToBytes(value));
			if (expiredTime > 0) {
				jedis.expire(SafeEncoder.encode(key), expiredTime);
			}
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage());
			tag = false;
		} finally {
			if (jedis != null)
				jedisPool.returnResource(jedis);
		}
		return tag;
	}

	public boolean hsetByRedis(String hashtable, String key, String value) {
		ShardedJedis jedis = jedisPool.getResource();
		boolean tag = true;
		try {
			jedis.hset(SafeEncoder.encode(hashtable), SafeEncoder.encode(key), cacheHelper.objectToBytes(value));
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage());
			tag = false;
		} finally {
			if (jedis != null)
				jedisPool.returnResource(jedis);
		}
		return tag;
	}
	
	public long incrBy(String key, long init)
	{
		ShardedJedis jedis = jedisPool.getResource();
		long rtn = 0L;
		try {
			jedis.incrBy(SafeEncoder.encode(key), init);
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage());
		} finally {
			if (jedis != null)
				jedisPool.returnResource(jedis);
		}
		return rtn;
	}

	public boolean setByMemc(String key, Object value, Integer expiredTime) {
		Future<Boolean> future = memcachedClient.set(key, expiredTime, value);
		try {
			return future.get(1, TimeUnit.SECONDS);
		} catch (Exception e) {
			future.cancel(false);
		}
		return false;
	}

	public MemcachedClient getMemcachedClient() {
		return memcachedClient;
	}

	public void setMemcachedClient(MemcachedClient memcachedClient) {
		this.memcachedClient = memcachedClient;
	}

	public ShardedJedisPool getJedisPool() {
		return jedisPool;
	}

	public void setJedisPool(ShardedJedisPool jedisPool) {
		this.jedisPool = jedisPool;
	}

	public CacheHelper getCacheHelper() {
		return cacheHelper;
	}

	public long getShutdownTimeout() {
		return shutdownTimeout;
	}

	public void setShutdownTimeout(long shutdownTimeout) {
		this.shutdownTimeout = shutdownTimeout;
	}

	public boolean isAsync() {
		return async;
	}

	public void setAsync(boolean async) {
		this.async = async;
	}

	public boolean isLocal() {
		return local;
	}

	public void setLocal(boolean local) {
		this.local = local;
	}

	public boolean isUseRedis() {
		return useRedis;
	}

	public void setUseRedis(boolean useRedis) {
		this.useRedis = useRedis;
	}

	public LocalCache<String, Object> getLocalCache() {
		return localCache;
	}

	public int getLocalTTL() {
		return localTTL;
	}

	public void setJedis(ShardedJedis jedis) {
		this.jedis = jedis;
	}


}
