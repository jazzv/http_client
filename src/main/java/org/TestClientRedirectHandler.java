package org;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.buffer.Buffer;
import rx.Observable;

public class TestClientRedirectHandler extends AbstractVerticle {
	
	protected static final Logger log = LoggerFactory.getLogger(TestClientRedirectHandler.class);
	
	public void start() {
		test();
	}
			
	public void test() {	
		
		String url = "http://t.co/Oj3GYaGzER";
		int maxRedirectsCount = 5;

		ClientRedirectHandler rh = new ClientRedirectHandler(vertx, url, maxRedirectsCount);

		Observable<Buffer> obs = rh.toObservable();
		
		obs.subscribe(buf -> {
			
			log.debug("buf = " + buf.toString("UTF-8"));
			
		}, t -> {
			log.error("ex", t);
		}, () -> {
			log.debug("complete");
			
			JsonArray urlsList = rh.getUrlsList();
			for (int i=0; i<urlsList.size(); i++) {
				JsonObject info = urlsList.getJsonObject(i);
				int statusCode = info.getInteger("statusCode");
				String _url = info.getString("url");
				log.debug("location " + i +" = " + _url + " statusCode = " + statusCode);
			}
			
		});
		
	}
}
