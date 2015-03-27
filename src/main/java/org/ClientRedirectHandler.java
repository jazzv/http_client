package org;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.Vertx;
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
	private Vertx vertx;
	private int maxRedirectsCount;
	private JsonArray urlsList;
	private String initialUrl;
	
	public ClientRedirectHandler(Vertx vertx, String url, int maxRedirectsCount) {
		this.initialUrl = url;
		this.maxRedirectsCount = maxRedirectsCount;
		this.vertx = vertx;
		this.urlsList = new JsonArray();
	}

	private Observable<Buffer> unshorten(String url) {
		
		URI uri = null;
		try {
			uri = buildURI(url);
		} catch (Exception ex) {
			return Observable.error(new Exception("invalid url: " + url, ex));		
		}
		
		JsonObject info = new JsonObject();
		info.put("url", uri.toString());
		urlsList.add(info);
		
		// create client, and enable SSL support if needed
		HttpClientOptions options = new HttpClientOptions();
		if (isSSL(uri)) {
			options.setSsl(true);
			options.setTrustAll(true);
		}
		HttpClient client = vertx.createHttpClient(options);
		String requestURI = getRequestURI(uri);
		HttpClientRequest request = client.get(getPort(uri), uri.getHost(), requestURI);
		
		Observable<Buffer> obs = request.toObservable()
		.flatMap(response -> {
			final String location = response.getHeader("location");
			final int statusCode = response.statusCode();
			boolean redirect = false;
			
			// update current url info
			info.put("statusCode", statusCode);
			
			// 301, 302, 303, 307
			if (statusCode == HttpResponseStatus.MOVED_PERMANENTLY.code() ||					
					statusCode == HttpResponseStatus.FOUND.code() ||
					statusCode == HttpResponseStatus.SEE_OTHER.code() ||
					statusCode == HttpResponseStatus.TEMPORARY_REDIRECT.code()) {
				redirect = true;
			}
			
			// location must exists if is redirect
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
			
			// return or expand
			if (redirect) {
				// expand url
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

	private boolean isSSL(URI uri) {
		if ("https".equalsIgnoreCase(uri.getScheme())) {
			return true;
		}
		return false;
	}

	private String getRequestURI(URI uri) {
		String requestURI = uri.getRawPath();
		String query = uri.getRawQuery();
		if (query != null && !query.trim().isEmpty()) {
			requestURI += "?" + query;
		}
		String fragment = uri.getRawFragment();
		if (fragment != null && !fragment.trim().isEmpty()) {
			requestURI += "#" + fragment;
		}
		return requestURI;
	}

	private URI buildURI(String url) throws Exception {
    	URI uri = new URI(url);		
		
    	String host = uri.getHost();
    	if (host == null || host.trim().isEmpty()) {
    		JsonObject info = urlsList.getJsonObject(urlsList.size()-1);
    		String previousUrl = info.getString("url");
    		URI p = new URI(previousUrl);
    		uri = p.resolve(uri);
    	}
    	
		return uri;
	}

	private int getPort(URI uri) {
		int port = uri.getPort();
		if (port <= 0) {
			String scheme = uri.getScheme();
			if ("http".equalsIgnoreCase(scheme)) {
				port = 80;
			} else if ("https".equalsIgnoreCase(scheme)) {
				port = 443;
			}
		}		
		return port;
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
