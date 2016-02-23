package com.galaxyinternet.framework.cache;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.spy.memcached.MemcachedClient;
import net.spy.memcached.OperationTimeoutException;

import org.apache.log4j.Logger;

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
			return setByRedis(key, value, localTTL);
		} else {
			return setByMemc(key, value, localTTL);
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
