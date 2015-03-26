package org;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.buffer.Buffer;
import io.vertx.rxjava.core.http.HttpClient;
import io.vertx.rxjava.core.http.HttpClientRequest;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.subjects.ReplaySubject;

public class ClientRedirectHandler 
{
	protected static final Logger log = LoggerFactory.getLogger(ClientRedirectHandler.class);
	
	private HttpClient client;
	private int maxRedirectsCount;
	private JsonArray urlsList;
	private String initialUrl;
	
	public ClientRedirectHandler(HttpClient client, String url, int maxRedirectsCount) {
		this.client = client;
		this.initialUrl = url;
		this.maxRedirectsCount = maxRedirectsCount;
		
		this.urlsList = new JsonArray();
		JsonObject jo = new JsonObject();
		jo.put("url", url);
		urlsList.add(jo);
	}

	private Observable<Buffer> unshorten(String url) {
				
		URI uri = null;
		try {
			uri = new URI(url);
		} catch (Exception ex) {
			return Observable.error(new Exception("invalid url", ex));		
		}

		int port = uri.getPort();
		if (port <= 0) {
			String scheme = uri.getScheme();
			if ("http".equalsIgnoreCase(scheme)) {
				port = 80;
			} else if ("https".equalsIgnoreCase(scheme)) {
				port = 443;
			} else {
				return Observable.error(new Exception("unknown port"));
			}
		}

		String requestURI = uri.getRawPath();
		String query = uri.getRawQuery();
		if (query != null && !query.trim().isEmpty()) {
			requestURI += "?" + query;
		}
		
		HttpClientRequest request = client.get(port, uri.getHost(), requestURI);
		Observable<Buffer> obs = request.toObservable()
		.flatMap(response -> {
			final String location = response.getHeader("location");
			final int statusCode = response.statusCode();
			boolean redirect = false;
			
			// 301, 302, 303, 307
			if (statusCode == HttpResponseStatus.MOVED_PERMANENTLY.code() ||					
					statusCode == HttpResponseStatus.FOUND.code() ||
					statusCode == HttpResponseStatus.SEE_OTHER.code() ||
					statusCode == HttpResponseStatus.TEMPORARY_REDIRECT.code()) {
				redirect = true;
			}
			
			if (redirect && (location == null || location.trim().isEmpty())) {
				return Observable.error(new Exception("location is empty"));
			}

			// redirect loop check
			if (redirect && isRedirectLoop(location)) {
				return Observable.error(new Exception("redirect loop detected"));
			}
			
			// max redirects count
			if (urlsList.size() > maxRedirectsCount) {
				return Observable.error(new Exception("max redirects count reached"));
			}
						
			// update current url info
			JsonObject info = urlsList.getJsonObject(urlsList.size()-1);
			info.put("statusCode", statusCode);
			
			// return or expand
			if (redirect) {
				// expand url
				JsonObject newInfo = new JsonObject();
				newInfo.put("url", location);
				urlsList.add(newInfo);
				return unshorten(location);
			} else {
				// return result
				return response.toObservable();
			}
		});				
		
		ReplaySubject<Buffer> subject = ReplaySubject.create();
		return subject.doOnSubscribe(() -> {
			obs.subscribe(subject);
			request.putHeader("User-Agent", "Mozilla/5.0");
			request.end();
		});
		
	}

	private boolean isRedirectLoop(String location) {
		for (int i=0; i<urlsList.size(); i++) {
			JsonObject info = urlsList.getJsonObject(i);
			String url = info.getString("url");
			if (location.equals(url)) {
				return true;
			}
		}
		return false;		
	}

	public Observable<Buffer> toObservable() {		
		return unshorten(initialUrl);
	}

	public Observable<Buffer> toObservableWholeBody() {
		return toObservable().reduce(Buffer.buffer(), Buffer::appendBuffer);
	}

	public JsonArray getUrlsList() {
		return this.urlsList;
	}
	
	/**
	 * TODO fix encoding in location header
	 * from: https://gist.github.com/alexlehm/9b8d3202552e223bc55e 
	 */
	private String fixStringEncoding(String s) {
		byte[] bytes = new byte[s.length()];
		for (int i = 0; i < s.length(); i++) {
			bytes[i] = (byte) (s.charAt(i) & 0xff);
		}
		try {
			return new String(bytes, "utf-8");
		} catch (Exception ex) {
			log.error("ex", ex);
			return "";
		}
	}

}
