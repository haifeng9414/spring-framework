/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.cors;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/**
 * Utility class for CORS request handling based on the
 * <a href="http://www.w3.org/TR/cors/">CORS W3C recommandation</a>.
 *
 * @author Sebastien Deleuze
 * @since 4.2
 */
public abstract class CorsUtils {
	/*
	cors全称是"跨域资源共享"（cross-origin resource sharing），用于访问跨域资源，cors需要浏览器和服务器同时支持。目前，所有浏览器都支持该功能，
	ie浏览器不能低于ie10。
	整个cors通信过程，都是浏览器自动完成，不需要用户参与。对于开发者来说，cors通信与同源的ajax通信没有差别，代码完全一样。浏览器一旦发现ajax请求跨源，
	就会自动添加一些附加的头信息，有时还会多出一次附加的请求，但用户不会有感觉。因此，实现cors通信的关键是服务器。只要服务器实现了cors接口，就可以跨源通信。
	Spring MVC作为请求的处理方，理所当然需要处理cors请求，下面是Spring MVC中用于判断cors请求的工具方法，这里对cors做一个简单介绍，以便于理解代码

	源自http://www.ruanyifeng.com/blog/2016/04/cors.html

	首先cors请求分为简单请求和非简单请求，浏览器对这两种请求的处理，是不一样的
	简单请求：
	1. 请求方法是以下三种方法之一：
	HEAD
	GET
	POST

	2. HTTP的头信息不超出以下几种字段：
	Accept
	Accept-Language
	Content-Language
	Last-Event-ID
	Content-Type：只限于三个值application/x-www-form-urlencoded、multipart/form-data、text/plain

	非简单请求：
	不满足上面两个条件的请求都是非简单请求

	对于简单请求，浏览器直接发出CORS请求。具体来说，就是在头信息之中，增加一个Origin字段。
	下面是一个例子，浏览器发现这次跨源AJAX请求是简单请求，就自动在头信息之中，添加一个Origin字段。

	GET /cors HTTP/1.1
	Origin: http://api.bob.com
	Host: api.alice.com
	Accept-Language: en-US
	Connection: keep-alive
	User-Agent: Mozilla/5.0...

	上面的头信息中，Origin字段用来说明，本次请求来自哪个源（协议 + 域名 + 端口）。服务器根据这个值，决定是否同意这次请求。
	如果Origin指定的源，不在许可范围内，服务器会返回一个正常的HTTP回应。浏览器发现，这个回应的头信息没有包含Access-Control-Allow-Origin字段（详见下文），
	就知道出错了，从而抛出一个错误，被XMLHttpRequest的onerror回调函数捕获。注意，这种错误无法通过状态码识别，因为HTTP回应的状态码有可能是200。

	如果Origin指定的域名在许可范围内，服务器返回的响应，会多出几个头信息字段。

	Access-Control-Allow-Origin: http://api.bob.com
	Access-Control-Allow-Credentials: true
	Access-Control-Expose-Headers: FooBar
	Content-Type: text/html; charset=utf-8

	上面的头信息之中，有三个与CORS请求相关的字段，都以Access-Control-开头。
	1. Access-Control-Allow-Origin
	该字段是必须的。它的值要么是请求时Origin字段的值，要么是一个*，表示接受任意域名的请求。

	2. Access-Control-Allow-Credentials
	该字段可选。它的值是一个布尔值，表示是否允许发送Cookie。默认情况下，Cookie不包括在CORS请求之中。设为true，即表示服务器明确许可，Cookie可以包含在请求中，
	一起发给服务器。这个值也只能设为true，如果服务器不要浏览器发送Cookie，删除该字段即可。

	3. Access-Control-Expose-Headers
	该字段可选。CORS请求时，XMLHttpRequest对象的getResponseHeader()方法只能拿到6个基本字段：Cache-Control、Content-Language、Content-Type、Expires、
	Last-Modified、Pragma。如果想拿到其他字段，就必须在Access-Control-Expose-Headers里面指定。上面的例子指定，getResponseHeader('FooBar')可以返回FooBar字段的值。

	上面说到，CORS请求默认不发送Cookie和HTTP认证信息。如果要把Cookie发到服务器，一方面要服务器同意，指定Access-Control-Allow-Credentials字段。

	Access-Control-Allow-Credentials: true

	另一方面，开发者必须在AJAX请求中打开withCredentials属性。

	var xhr = new XMLHttpRequest();
	xhr.withCredentials = true;

	否则，即使服务器同意发送Cookie，浏览器也不会发送。或者，服务器要求设置Cookie，浏览器也不会处理。但是，如果省略withCredentials设置，有的浏览器还是会一起发送Cookie。这时，
	可以显式关闭withCredentials。

	xhr.withCredentials = false;

	需要注意的是，如果要发送Cookie，Access-Control-Allow-Origin就不能设为星号，必须指定明确的、与请求网页一致的域名。同时，Cookie依然遵循同源政策，只有用服务器域名设置的Cookie才会上传，
	其他域名的Cookie并不会上传，且（跨源）原网页代码中的document.cookie也无法读取服务器域名下的Cookie。

	对于非简单请求，非简单请求是那种对服务器有特殊要求的请求，比如请求方法是PUT或DELETE，或者Content-Type字段的类型是application/json。
	非简单请求的CORS请求，会在正式通信之前，增加一次HTTP查询请求，称为"预检"请求（preflight）。
	浏览器先询问服务器，当前网页所在的域名是否在服务器的许可名单之中，以及可以使用哪些HTTP动词和头信息字段。只有得到肯定答复，浏览器才会发出正式的XMLHttpRequest请求，否则就报错。
	下面是一段浏览器的JavaScript脚本：

	var url = 'http://api.alice.com/cors';
	var xhr = new XMLHttpRequest();
	xhr.open('PUT', url, true);
	xhr.setRequestHeader('X-Custom-Header', 'value');
	xhr.send();

	上面代码中，HTTP请求的方法是PUT，并且发送一个自定义头信息X-Custom-Header。浏览器发现，这是一个非简单请求，就自动发出一个"预检"请求，要求服务器确认可以这样请求。下面是这个"预检"请求的HTTP头信息。

	OPTIONS /cors HTTP/1.1
	Origin: http://api.bob.com
	Access-Control-Request-Method: PUT
	Access-Control-Request-Headers: X-Custom-Header
	Host: api.alice.com
	Accept-Language: en-US
	Connection: keep-alive
	User-Agent: Mozilla/5.0...

	"预检"请求用的请求方法是OPTIONS，表示这个请求是用来询问的。头信息里面，关键字段是Origin，表示请求来自哪个源。
	除了Origin字段，"预检"请求的头信息包括两个特殊字段。
	1. Access-Control-Request-Method
	该字段是必须的，用来列出浏览器的CORS请求会用到哪些HTTP方法，上例是PUT。

	2. Access-Control-Request-Headers
	该字段是一个逗号分隔的字符串，指定浏览器CORS请求会额外发送的头信息字段，上例是X-Custom-Header。

	对于"预检请求的回应"，服务器收到"预检"请求以后，检查了Origin、Access-Control-Request-Method和Access-Control-Request-Headers字段以后，确认允许跨源请求，就可以做出回应。

	HTTP/1.1 200 OK
	Date: Mon, 01 Dec 2008 01:15:39 GMT
	Server: Apache/2.0.61 (Unix)
	Access-Control-Allow-Origin: http://api.bob.com
	Access-Control-Allow-Methods: GET, POST, PUT
	Access-Control-Allow-Headers: X-Custom-Header
	Content-Type: text/html; charset=utf-8
	Content-Encoding: gzip
	Content-Length: 0
	Keep-Alive: timeout=2, max=100
	Connection: Keep-Alive
	Content-Type: text/plain

	上面的HTTP回应中，关键的是Access-Control-Allow-Origin字段，表示http://api.bob.com可以请求数据。该字段也可以设为星号，表示同意任意跨源请求。

	Access-Control-Allow-Origin: *

	如果浏览器否定了"预检"请求，会返回一个正常的HTTP回应，但是没有任何CORS相关的头信息字段。这时，浏览器就会认定，服务器不同意预检请求，因此触发一个错误，被XMLHttpRequest对象的onerror回调函数捕获。控制台会打印出如下的报错信息。

	XMLHttpRequest cannot load http://api.alice.com.
	Origin http://api.bob.com is not allowed by Access-Control-Allow-Origin.

	服务器回应的其他CORS相关字段如下。

	Access-Control-Allow-Methods: GET, POST, PUT
	Access-Control-Allow-Headers: X-Custom-Header
	Access-Control-Allow-Credentials: true
	Access-Control-Max-Age: 1728000

	1. Access-Control-Allow-Methods
	该字段必需，它的值是逗号分隔的一个字符串，表明服务器支持的所有跨域请求的方法。注意，返回的是所有支持的方法，而不单是浏览器请求的那个方法。这是为了避免多次"预检"请求。

	2. Access-Control-Allow-Headers
	如果浏览器请求包括Access-Control-Request-Headers字段，则Access-Control-Allow-Headers字段是必需的。它也是一个逗号分隔的字符串，表明服务器支持的所有头信息字段，不限于浏览器在"预检"中请求的字段。

	3. Access-Control-Allow-Credentials
	该字段与简单请求时的含义相同。

	4. Access-Control-Max-Age
	该字段可选，用来指定本次预检请求的有效期，单位为秒。上面结果中，有效期是20天（1728000秒），即允许缓存该条回应1728000秒（即20天），在此期间，不用发出另一条预检请求。

	一旦服务器通过了"预检"请求，以后每次浏览器正常的CORS请求，就都跟简单请求一样，会有一个Origin头信息字段。服务器的回应，也都会有一个Access-Control-Allow-Origin头信息字段。
	下面是"预检"请求之后，浏览器的正常CORS请求。

	PUT /cors HTTP/1.1
	Origin: http://api.bob.com
	Host: api.alice.com
	X-Custom-Header: value
	Accept-Language: en-US
	Connection: keep-alive
	User-Agent: Mozilla/5.0...

	上面头信息的Origin字段是浏览器自动添加的。
	下面是服务器正常的回应。

	Access-Control-Allow-Origin: http://api.bob.com
	Content-Type: text/html; charset=utf-8

	上面头信息中，Access-Control-Allow-Origin字段是每次回应都必定包含的。
	 */

	/**
	 * Returns {@code true} if the request is a valid CORS one.
	 */
	public static boolean isCorsRequest(HttpServletRequest request) {
		// 根据上面的介绍，cors请求的请求头肯定包含Origin，所以可以以此作为依据
		return (request.getHeader(HttpHeaders.ORIGIN) != null);
	}

	/**
	 * Returns {@code true} if the request is a valid CORS pre-flight one.
	 */
	public static boolean isPreFlightRequest(HttpServletRequest request) {
		// 根据上面的介绍，对于比较复杂的cors请求，浏览器会先发送一个preflight request，而响应的跨域服务器也将返回一个preflight response，在服务器返回preflight response
		// 之后浏览器才会发送真正的cors请求，preflight request除了会携带Origin请求头，还会携带Access-Control-Request-Method请求头，用于表示真正的cors请求的
		// 方法是什么，如GET、POST等，这里以此判断是否请求为preflight
		return (isCorsRequest(request) && HttpMethod.OPTIONS.matches(request.getMethod()) &&
				request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD) != null);
	}

}
