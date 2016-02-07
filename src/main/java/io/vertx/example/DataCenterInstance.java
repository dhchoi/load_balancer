package io.vertx.example;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

public class DataCenterInstance {
	public final String ip;
	public final String url;

	public DataCenterInstance(String ip, String url) {
		this.ip = ip;
		this.url = "http://" + url;
	}

	public String getIp() {
		return ip;
	}

	public String getUrl() {
		return url;
	}

	/**
	 * Execute the request on the Data Center Instance
	 * @param path
	 * @return URLConnection
	 * @throws IOException
	 */
	public URLConnection executeRequest(String path) throws IOException {
		URLConnection conn = openConnection(path);
		return conn;
	}

	/**
	 * Open a connection with the Data Center Instance
	 * @param path
	 * @return URLConnection
	 * @throws IOException
	 */
	private URLConnection openConnection(String path) throws IOException {
		URL url = new URL(path);
		URLConnection conn = url.openConnection();
		conn.setDoInput(true);
		conn.setDoOutput(false);
		return conn;
	}

	/**
	 * Checks health status of instance.
	 *
	 * @return true if instance is healthy
     */
	public boolean isHealthy() {
		try {
			HttpURLConnection httpURLConnection = (HttpURLConnection) executeRequest(url);
			httpURLConnection.setRequestMethod("GET");
			httpURLConnection.connect();

			if (httpURLConnection.getResponseCode() != 200) {
				return false;
			}

		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	@Override
	public String toString() {
		return ip;
	}
}
