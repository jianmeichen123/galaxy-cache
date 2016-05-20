package com.galaxyinternet.framework.cache.cluster;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import redis.clients.jedis.JedisCluster;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath*:applicationContext-cache-cluster.xml" })
public class ClusterTest extends AbstractJUnit4SpringContextTests {

	@Autowired
	JedisCluster jedisCluster;

	@Test
	public void set() {
		System.out.println(jedisCluster.getClusterNodes().size());
		jedisCluster.set("name", "keifer123");
	}

	@Test
	public void get() {
		String result = jedisCluster.get("name");
		System.out.println("result====>>>" + result);
	}

}
