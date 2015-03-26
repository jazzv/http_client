# http_client
vertx 3 http client with redirect handling


```java
	private void test() {	
		
		// settings
		String url = "http://t.co/Oj3GYaGzER";
		int maxRedirectsCount = 5;
		HttpClient client = vertx.createHttpClient();

		// init
		ClientRedirectHandler rh = new ClientRedirectHandler(client, url, maxRedirectsCount);

		// run
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
```

#### Output:
buf = <!DOCTYPE html>.....

buf = ...

buf = ....
 
complete

location 0 = http://t.co/Oj3GYaGzER statusCode = 301

location 1 = http://aja.me/msxc statusCode = 301

location 2 = http://trib.al/gATHbk1 statusCode = 301

location 3 = http://www.aljazeera.net/news/arabic/2015/3/25/.... statusCode = 200


