package com.galaxyinternet.framework.cache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import redis.clients.util.SafeEncoder;

public class CacheHelper {

	public String buildKey(CacheModule module, String string) {
		String s = module.toString() + "_" + string;
		return s;
	}

	public byte[] encodeKey(String key) {
		return SafeEncoder.encode(key);
	}

	public Object bytesToObject(byte[] bytes) {
		if (bytes == null || bytes.length == 0) {
			return null;
		}

		try {
			ObjectInputStream inputStream;
			inputStream = new ObjectInputStream(new ByteArrayInputStream(bytes));
			Object obj = inputStream.readObject();

			return obj;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

		return null;
	}

	public byte[] objectToBytes(Object value) {
		if (value == null) {
			return null;
		}

		ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
		ObjectOutputStream outputStream;
		try {
			outputStream = new ObjectOutputStream(arrayOutputStream);

			outputStream.writeObject(value);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				arrayOutputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return arrayOutputStream.toByteArray();
	}

}
